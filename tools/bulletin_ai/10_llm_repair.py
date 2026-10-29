#!/usr/bin/env python3
import argparse
import json
import os
import re
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Dict, List, Optional, Set

import requests
from bs4 import BeautifulSoup
from tqdm import tqdm

from lib import (
    clamp_text,
    iter_manifest_items,
    load_dotenv,
    normalize_text,
    read_json,
    read_text,
    sha256_text,
    write_json,
)

SYSTEM_PROMPT = """
You are a repair assistant for a university bulletin requirement parser.
Return JSON only. Do not include extra text.

Patch schema:
{
  "confidence": 0.0,
  "ops": [
    {
      "op": "set_field|remove_child|move_child|add_course_option|remove_course_option",
      "selector": { "id": 123, "sourceRef": "css", "path": ["Program Requirements", "Core"] },
      "field": "kind|name|notes|footnoteText|nRequired|creditMin|creditMax|doubleCountAllowed|doubleCountCap|levelMin|levelCap100|levelCap200|levelCap300|levelCap400|levelCap100Courses|levelCap200Courses|levelCap300Courses|levelCap400Courses",
      "value": "...",
      "childSelector": { "id": 456, "sourceRef": "css", "path": ["..."] },
      "targetSelector": { "id": 789, "sourceRef": "css", "path": ["..."] },
      "course": { "code": "ABC 123", "title": "Title", "credits": 3 }
    }
  ]
}

Rules:
- Only propose ops you are confident in and can justify with snippet evidence.
- Do not invent course codes; only use codes present in the snippet text.
- Prefer selector by sourceRef or id; if not, use path.
""".strip()

PROMPT_TEMPLATE = """
Analyze the requirement HTML snippet against the parsed requirement tree summary.
Return only patch operations you are confident in.

Reviewer guidance (if provided):
{reviewer_note}

Lint hints:
{lint}

Course codes detected in snippet:
{course_codes}

HTML snippet (focused, may be partial):
{snippet}

Parsed tree summary:
{summary}
""".strip()


def iter_nodes(node: Dict, path: Optional[List[str]] = None):
    if not isinstance(node, dict):
        return
    if path is None:
        path = []
    name = (node.get("name") or "").strip()
    next_path = path + [name] if name else path
    yield node, next_path
    for child in node.get("children", []) or []:
        yield from iter_nodes(child, next_path)


def summarize_tree(root: Dict) -> List[str]:
    lines: List[str] = []
    for node, path in iter_nodes(root):
        name = node.get("name") or ""
        kind = node.get("kind") or ""
        node_id = node.get("id")
        n_required = node.get("nrequired")
        if n_required is None:
            n_required = node.get("nRequired")
        credit_min = node.get("creditMin")
        credit_max = node.get("creditMax")
        course_options = node.get("courseOptions") or []
        course_codes = [option.get("code") for option in course_options if option.get("code")]
        course_preview = ", ".join(course_codes[:6])
        source_ref = (node.get("source") or {}).get("ref")

        parts = []
        parts.append(f"id={node_id}" if node_id is not None else "id=?")
        parts.append(f"kind={kind}")
        if name:
            parts.append(f"name={name}")
        if source_ref:
            parts.append(f"ref={source_ref}")
        if n_required is not None:
            parts.append(f"n={n_required}")
        if credit_min is not None or credit_max is not None:
            parts.append(f"credits={credit_min or ''}-{credit_max or ''}")
        if course_codes:
            parts.append(f"courses={len(course_codes)} [{course_preview}]")

        indent = "  " * (len(path) - 1 if path else 0)
        lines.append(indent + " | ".join(parts))
        if len(lines) >= 500:
            break
    return lines


def extract_course_codes(snippet_html: str) -> List[str]:
    if not snippet_html:
        return []
    try:
        soup = BeautifulSoup(snippet_html, "html.parser")
        text = soup.get_text(" ")
    except Exception:
        text = snippet_html
    tokens = set()
    for match in re_course_code.findall(text):
        tokens.add(f"{match[0]} {match[1]}".strip())
    return sorted(tokens)


re_course_code = re.compile(r"\b([A-Z]{2,5})\s?(\d{3}[A-Z]?)\b")


def build_prompt(
    snippet: str,
    summary_lines: List[str],
    lint: Dict,
    reviewer_note: str,
    course_codes: List[str],
    prompt_max_chars: int,
) -> str:
    lint_json = json.dumps(lint or {}, ensure_ascii=True)
    reviewer_text = reviewer_note.strip() if reviewer_note else "None."
    codes_text = ", ".join(course_codes[:80]) if course_codes else "None detected."
    return PROMPT_TEMPLATE.format(
        reviewer_note=clamp_text(reviewer_text, 1000),
        lint=lint_json,
        course_codes=codes_text,
        snippet=clamp_text(snippet, prompt_max_chars),
        summary="\n".join(summary_lines[:400]),
    )


def call_ollama(model: str, prompt: str, url: str, num_ctx: int) -> str:
    payload = {
        "model": model,
        "prompt": prompt,
        "system": SYSTEM_PROMPT,
        "stream": False,
        "options": {"temperature": 0, "top_p": 0.1, "repeat_penalty": 1.0},
    }
    if num_ctx > 0:
        payload["options"]["num_ctx"] = num_ctx
    response = requests.post(url, json=payload, timeout=180)
    response.raise_for_status()
    data = response.json()
    return data.get("response", "")


def parse_json_response(text: str) -> Optional[Dict]:
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            return None
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            return None


def load_review_notes(review_queue: str) -> Dict[str, str]:
    if not review_queue:
        return {}
    path = Path(review_queue)
    if not path.exists():
        return {}
    data = read_json(path)
    notes = {}
    for entry in data.get("items", []) or []:
        note = entry.get("reviewNote")
        if not note:
            continue
        key = f"{entry.get('catalogLabel')}::{entry.get('slug')}"
        notes[key] = note
    return notes


def process_item(item: Dict, args, review_notes: Dict[str, str]) -> str:
    catalog_label = item.get("catalogLabel") or "unknown"
    slug = item.get("slug") or "unknown"
    snippet_path = Path(args.snippet_root) / catalog_label / f"{slug}.html"
    parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
    lint_path = Path(args.lint_root) / catalog_label / f"{slug}.json"
    output_path = Path(args.output_root) / catalog_label / f"{slug}.json"

    if not snippet_path.exists() or not parsed_path.exists():
        return "missing_input"

    lint_data = read_json(lint_path) if lint_path.exists() else {}
    if args.only_flagged and lint_data.get("issueCount", 0) == 0:
        return "not_flagged"

    snippet = read_text(snippet_path)
    parsed = read_json(parsed_path)
    root = parsed.get("root") if isinstance(parsed, dict) else None
    if not root:
        return "empty_root"

    summary_lines = summarize_tree(root)
    course_codes = extract_course_codes(snippet)
    review_key = f"{catalog_label}::{slug}"
    reviewer_note = review_notes.get(review_key, "")
    prompt = build_prompt(
        snippet,
        summary_lines,
        lint_data,
        reviewer_note,
        course_codes,
        args.prompt_max_chars,
    )
    prompt_hash = sha256_text(prompt)

    if output_path.exists() and not args.force:
        existing = read_json(output_path)
        if existing.get("promptHash") == prompt_hash:
            return "cached"

    raw = call_ollama(args.model, prompt, args.ollama_url, args.num_ctx)
    parsed_json = parse_json_response(raw) or {"ops": [], "confidence": 0.0}
    write_json(
        output_path,
        {
            "model": args.model,
            "promptHash": prompt_hash,
            "ops": parsed_json.get("ops", []),
            "confidence": parsed_json.get("confidence", 0.0),
            "raw": raw,
        },
    )
    return "processed"


def main() -> None:
    load_dotenv()
    num_ctx_env = os.getenv("OLLAMA_NUM_CTX")
    num_ctx_default = int(num_ctx_env) if num_ctx_env and num_ctx_env.isdigit() else 0
    prompt_env = os.getenv("OLLAMA_PROMPT_MAX_CHARS")
    prompt_default = int(prompt_env) if prompt_env and prompt_env.isdigit() else 12000
    parser = argparse.ArgumentParser(description="Run local LLM repair suggestions via Ollama")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--model", default="llama3.1:8b")
    parser.add_argument("--ollama-url", default=os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate"))
    parser.add_argument("--num-ctx", type=int, default=num_ctx_default)
    parser.add_argument("--prompt-max-chars", type=int, default=prompt_default)
    parser.add_argument("--review-queue", default=os.getenv("REVIEW_QUEUE", ""))
    parser.add_argument("--snippet-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--lint-root", default="tools/bulletin_ai/lint")
    parser.add_argument("--output-root", default="tools/bulletin_ai/repairs")
    parser.add_argument("--max-workers", type=int, default=2)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--only-flagged", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    review_notes = load_review_notes(args.review_queue)
    lock = threading.Lock()
    failures: List[str] = []
    counts: Dict[str, int] = {}

    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = {
            executor.submit(process_item, item, args, review_notes): item for item in items
        }
        for future in tqdm(as_completed(futures), total=len(futures), desc="repairs"):
            try:
                status = future.result()
                with lock:
                    counts[status] = counts.get(status, 0) + 1
            except Exception as exc:  # noqa: BLE001
                with lock:
                    failures.append(str(exc))

    if failures:
        print(f"Repair failures: {len(failures)}")
    summary_parts = [f"{key}={counts.get(key, 0)}" for key in sorted(counts.keys())]
    summary = " ".join(summary_parts) if summary_parts else "no outputs"
    print(f"Repair summary: {summary}")


if __name__ == "__main__":
    main()
