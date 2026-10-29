#!/usr/bin/env python3
import argparse
import json
import os
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Dict, List, Optional

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
You are a QA assistant for a university bulletin requirement parser.
Return JSON only. Do not include extra text.

Only report issues when you can quote exact text from the HTML snippet.
Ignore duplicate wrapper nodes named "Program Requirements" or "Requirements".

Schema:
{
  "issues": [
    {
      "type": "TRACKS|DUPLICATE_NOTES|UNNAMED_BLOCK|MISSING_GROUP_LABEL|EMPTY_NODE|OTHER",
      "summary": "short summary",
      "evidence": "exact substring from the HTML snippet",
      "suggested_rule": "deterministic parser rule suggestion",
      "confidence": 0.0
    }
  ],
  "overall_confidence": 0.0
}
""".strip()

PROMPT_TEMPLATE = """
Analyze the requirement HTML snippet against the parsed requirement tree summary.
Identify mismatches, missing structure, or duplicated notes.
Focus on tracks, unnamed blocks, and note duplication.
If you cannot cite exact text from the HTML snippet, do not report an issue.

Reviewer guidance (if provided):
{reviewer_note}

Lint hints:
{lint}

HTML snippet (focused, may be partial):
{snippet}

Parsed tree summary:
{summary}
""".strip()


def summarize_tree(node: Dict, depth: int = 0, lines: Optional[List[str]] = None) -> List[str]:
    if lines is None:
        lines = []
    name = node.get("name") or ""
    kind = node.get("kind") or ""
    n_required = node.get("nRequired")
    credit_min = node.get("creditMin")
    credit_max = node.get("creditMax")
    course_count = len(node.get("courseOptions") or [])
    attr_count = len(node.get("attributeOptions") or [])
    notes = node.get("notes") or node.get("footnoteText") or ""
    notes = clamp_text(normalize_text(notes), 120)

    parts = [f"{kind} | {name}"]
    if n_required is not None:
        parts.append(f"n={n_required}")
    if credit_min is not None or credit_max is not None:
        parts.append(f"credits={credit_min or ''}-{credit_max or ''}")
    if course_count:
        parts.append(f"courses={course_count}")
    if attr_count:
        parts.append(f"attrs={attr_count}")
    if notes:
        parts.append(f"notes={notes}")

    indent = "  " * depth
    lines.append(indent + " | ".join(parts))

    for child in node.get("children", []) or []:
        summarize_tree(child, depth + 1, lines)

    return lines


def build_prompt(
    snippet: str,
    summary_lines: List[str],
    lint: Dict,
    reviewer_note: str,
    prompt_max_chars: int,
) -> str:
    lint_json = json.dumps(lint or {}, ensure_ascii=True)
    reviewer_text = reviewer_note.strip() if reviewer_note else ""
    if not reviewer_text:
        reviewer_text = "None."
    return PROMPT_TEMPLATE.format(
        lint=lint_json,
        snippet=clamp_text(snippet, prompt_max_chars),
        summary="\n".join(summary_lines[:400]),
        reviewer_note=clamp_text(reviewer_text, 1000),
    )


def iter_nodes(node: Dict, depth: int = 0):
    if node is None:
        return
    yield node, depth
    for child in node.get("children", []) or []:
        yield from iter_nodes(child, depth + 1)


def collect_source_refs(root: Dict, depth_limit: int = 2) -> List[str]:
    refs = []
    for node, depth in iter_nodes(root, 0):
        if depth > depth_limit:
            continue
        source = node.get("source") or {}
        ref = source.get("ref")
        if ref:
            refs.append(ref)
    return refs


def extract_context_snippet(snippet_html: str, root: Dict, prompt_max_chars: int) -> str:
    if not snippet_html or not root:
        return snippet_html
    try:
        soup = BeautifulSoup(snippet_html, "html.parser")
    except Exception:
        return snippet_html

    refs = collect_source_refs(root, depth_limit=2)
    if not refs:
        return snippet_html

    sections = []
    seen = set()
    for ref in refs:
        element = None
        try:
            element = soup.select_one(ref)
        except Exception:
            element = None
        if not element and ref.startswith("#textcontainer"):
            cleaned = ref.replace("#textcontainer", "", 1).strip()
            if cleaned.startswith(">"):
                cleaned = cleaned[1:].strip()
            try:
                element = soup.select_one(cleaned)
            except Exception:
                element = None
        if not element:
            continue
        container = element.find_parent("table", class_="sc_courselist") or element
        html = str(container)
        key = normalize_text(html)
        if not key or key in seen:
            continue
        seen.add(key)
        sections.append(html)
        if len(sections) >= 6:
            break

    if not sections:
        return snippet_html

    combined = "\n".join(sections)
    return clamp_text(combined, prompt_max_chars)


def coalesce_root(root: Dict) -> Dict:
    children = root.get("children") if isinstance(root, dict) else None
    if not children or len(children) != 1:
        return root
    child = children[0]
    if not isinstance(child, dict):
        return root
    root_name = normalize_text(root.get("name") or "")
    child_name = normalize_text(child.get("name") or "")
    if root.get("kind") == child.get("kind") and root_name and root_name == child_name:
        return child
    return root


def call_ollama(model: str, prompt: str, url: str, num_ctx: int) -> str:
    payload = {
        "model": model,
        "prompt": prompt,
        "system": SYSTEM_PROMPT,
        "stream": False,
        "options": {
            "temperature": 0,
            "top_p": 0.1,
            "repeat_penalty": 1.0,
        },
    }
    if num_ctx > 0:
        payload["options"]["num_ctx"] = num_ctx
    response = requests.post(url, json=payload, timeout=120)
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


def process_item(item: Dict, args) -> str:
    catalog_label = item.get("catalogLabel") or "unknown"
    slug = item.get("slug") or "unknown"
    snippet_path = Path(args.snippet_root) / catalog_label / f"{slug}.html"
    parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
    lint_path = Path(args.lint_root) / catalog_label / f"{slug}.json"
    output_path = Path(args.output_root) / catalog_label / f"{slug}.json"

    if not snippet_path.exists() or not parsed_path.exists():
        return "missing_input"

    if args.only_flagged and lint_path.exists():
        lint_data = read_json(lint_path)
        if lint_data.get("issueCount", 0) == 0:
            return "not_flagged"
    elif args.only_flagged and not lint_path.exists():
        return "missing_lint"
    else:
        lint_data = read_json(lint_path) if lint_path.exists() else {}

    snippet = read_text(snippet_path)
    parsed = read_json(parsed_path)
    root = parsed.get("root") if isinstance(parsed, dict) else None
    if not root:
        return "empty_root"

    summary_root = coalesce_root(root)
    summary_lines = summarize_tree(summary_root)
    context_snippet = extract_context_snippet(snippet, root, args.prompt_max_chars)
    review_key = f"{catalog_label}::{slug}"
    reviewer_note = args.review_notes.get(review_key, "") if hasattr(args, "review_notes") else ""
    prompt = build_prompt(context_snippet, summary_lines, lint_data, reviewer_note, args.prompt_max_chars)
    prompt_hash = sha256_text(prompt)

    if output_path.exists() and not args.force:
        existing = read_json(output_path)
        if existing.get("promptHash") == prompt_hash:
            return "cached"

    raw = call_ollama(args.model, prompt, args.ollama_url, args.num_ctx)
    parsed_json = parse_json_response(raw) or {"issues": [], "overall_confidence": 0.0}

    write_json(
        output_path,
        {
            "model": args.model,
            "promptHash": prompt_hash,
            "issues": parsed_json.get("issues", []),
            "overall_confidence": parsed_json.get("overall_confidence", 0.0),
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
    parser = argparse.ArgumentParser(description="Run local LLM audit via Ollama")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--model", default="llama3.1:8b")
    parser.add_argument("--ollama-url", default=os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate"))
    parser.add_argument("--num-ctx", type=int, default=num_ctx_default)
    parser.add_argument("--prompt-max-chars", type=int, default=prompt_default)
    parser.add_argument("--review-queue", default=os.getenv("REVIEW_QUEUE", ""))
    parser.add_argument("--snippet-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--lint-root", default="tools/bulletin_ai/lint")
    parser.add_argument("--output-root", default="tools/bulletin_ai/llm")
    parser.add_argument("--max-workers", type=int, default=2)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--only-flagged", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    review_notes = {}
    if args.review_queue:
        review_path = Path(args.review_queue)
        if review_path.exists():
            queue = read_json(review_path)
            for entry in queue.get("items", []) or []:
                note = entry.get("reviewNote")
                if not note:
                    continue
                key = f"{entry.get('catalogLabel')}::{entry.get('slug')}"
                review_notes[key] = note
    args.review_notes = review_notes

    lock = threading.Lock()
    failures = []
    counts = {}

    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = {executor.submit(process_item, item, args): item for item in items}
        for future in tqdm(as_completed(futures), total=len(futures), desc="llm"):
            try:
                status = future.result()
                with lock:
                    counts[status] = counts.get(status, 0) + 1
            except Exception as exc:  # noqa: BLE001
                with lock:
                    failures.append(str(exc))

    if failures:
        print(f"LLM failures: {len(failures)}")
    summary_parts = [f"{key}={counts.get(key, 0)}" for key in sorted(counts.keys())]
    summary = " ".join(summary_parts) if summary_parts else "no outputs"
    print(f"LLM summary: {summary}")


if __name__ == "__main__":
    main()
