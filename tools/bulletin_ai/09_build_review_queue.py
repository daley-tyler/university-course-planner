#!/usr/bin/env python3
import argparse
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from tqdm import tqdm

from lib import iter_manifest_items, load_dotenv, read_json, write_json


def read_json_if_exists(path: Path) -> Optional[Dict]:
    if not path.exists():
        return None
    try:
        return read_json(path)
    except Exception:  # noqa: BLE001
        return None


def lint_summary(path: Path) -> Dict:
    data = read_json_if_exists(path)
    if not data:
        return {"issueCount": 0, "types": []}
    types = [issue.get("type") for issue in data.get("issues", []) or [] if issue.get("type")]
    return {
        "issueCount": data.get("issueCount", 0),
        "types": types,
    }


def llm_summary(path: Path, min_confidence: float) -> Dict:
    data = read_json_if_exists(path)
    if not data:
        return {"issueCount": 0, "types": [], "model": None}
    issues = []
    for issue in data.get("issues", []) or []:
        confidence = issue.get("confidence", 0)
        try:
            confidence_value = float(confidence)
        except (TypeError, ValueError):
            confidence_value = 0.0
        if confidence_value < min_confidence:
            continue
        issues.append(issue)
    types = [issue.get("type") for issue in issues if issue.get("type")]
    return {
        "issueCount": len(issues),
        "types": types,
        "model": data.get("model"),
    }


def diff_summary(path: Path) -> Dict:
    data = read_json_if_exists(path)
    if not data:
        return {"hasMismatch": False}
    return {
        "hasMismatch": data.get("hasMismatch", False),
        "reasons": data.get("mismatchReasons", []),
        "missingCourseCodes": data.get("missingCourseCodes", []),
        "extraCourseCodes": data.get("extraCourseCodes", []),
        "missingChoiceGroups": data.get("missingChoiceGroups", []),
        "extraChoiceGroups": data.get("extraChoiceGroups", []),
        "metrics": data.get("metrics", {}),
        "expectedPath": data.get("expectedPath"),
    }


def score_entry(lint: Dict, llm: Dict, diff: Dict) -> int:
    score = 0
    score += lint.get("issueCount", 0) * 3
    score += llm.get("issueCount", 0) * 2
    if diff.get("hasMismatch"):
        score += 10
        score += len(diff.get("missingCourseCodes", [])) * 2
        score += len(diff.get("missingChoiceGroups", [])) * 3
        if "double_count_rules" in diff.get("reasons", []):
            score += 5
    return score


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Build a review queue for requirement parsing")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--lint-root", default="tools/bulletin_ai/lint")
    parser.add_argument("--llm-root", default="tools/bulletin_ai/llm")
    parser.add_argument("--diff-root", default="tools/bulletin_ai/golden_diff")
    parser.add_argument("--snippet-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--output", default="tools/bulletin_ai/review/queue.json")
    parser.add_argument("--summary", default="tools/bulletin_ai/review/summary.json")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--catalog-label", default="")
    parser.add_argument("--min-llm-confidence", type=float, default=0.6)
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.catalog_label:
        items = [item for item in items if item.get("catalogLabel") == args.catalog_label]
    if args.limit > 0:
        items = items[: args.limit]

    existing_status = {}
    existing_notes = {}
    existing_queue = read_json_if_exists(Path(args.output))
    if existing_queue:
        for entry in existing_queue.get("items", []) or []:
            key = f"{entry.get('catalogLabel')}::{entry.get('slug')}"
            status_value = entry.get("status")
            note_value = entry.get("reviewNote")
            if status_value:
                existing_status[key] = status_value
            if note_value:
                existing_notes[key] = note_value

    queue = []
    lint_flagged = 0
    llm_flagged = 0
    gold_flagged = 0

    for item in tqdm(items, desc="review-queue"):
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"

        lint_path = Path(args.lint_root) / catalog_label / f"{slug}.json"
        llm_path = Path(args.llm_root) / catalog_label / f"{slug}.json"
        diff_path = Path(args.diff_root) / catalog_label / f"{slug}.json"
        snippet_path = Path(args.snippet_root) / catalog_label / f"{slug}.html"
        parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"

        lint_info = lint_summary(lint_path)
        llm_info = llm_summary(llm_path, args.min_llm_confidence)
        diff_info = diff_summary(diff_path)

        reasons = []
        if lint_info.get("issueCount", 0) > 0:
            lint_flagged += 1
            reason_types = ", ".join(sorted(set(lint_info.get("types", []))))
            reasons.append(f"lint:{reason_types or 'issues'}")
        if llm_info.get("issueCount", 0) > 0:
            llm_flagged += 1
            reason_types = ", ".join(sorted(set(llm_info.get("types", []))))
            reasons.append(f"llm:{reason_types or 'issues'}")
        if diff_info.get("hasMismatch"):
            gold_flagged += 1
            reasons.append("gold:mismatch")

        if not reasons:
            continue

        entry = {
            "programId": item.get("programId"),
            "programName": item.get("programName"),
            "catalogLabel": catalog_label,
            "slug": slug,
            "sourceUrl": item.get("sourceUrl"),
            "score": score_entry(lint_info, llm_info, diff_info),
            "reasons": reasons,
            "lint": lint_info,
            "llm": llm_info,
            "gold": diff_info,
            "paths": {
                "snippet": str(snippet_path),
                "parsed": str(parsed_path),
                "lint": str(lint_path),
                "llm": str(llm_path),
                "goldDiff": str(diff_path),
                "goldExpected": diff_info.get("expectedPath"),
            },
            "status": existing_status.get(f"{catalog_label}::{slug}", "pending"),
            "reviewNote": existing_notes.get(f"{catalog_label}::{slug}", ""),
        }
        queue.append(entry)

    queue.sort(key=lambda item: item["score"], reverse=True)

    output_payload = {
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "totalQueued": len(queue),
        "items": queue,
    }
    summary = {
        "totalQueued": len(queue),
        "lintFlagged": lint_flagged,
        "llmFlagged": llm_flagged,
        "goldFlagged": gold_flagged,
        "topLintTypes": Counter(
            t for entry in queue for t in entry.get("lint", {}).get("types", [])
        ).most_common(10),
        "topLlmTypes": Counter(
            t for entry in queue for t in entry.get("llm", {}).get("types", [])
        ).most_common(10),
    }

    write_json(Path(args.output), output_payload)
    write_json(Path(args.summary), summary)
    print(f"Wrote review queue to {args.output}")
    print(f"Queue summary: total={len(queue)} lint={lint_flagged} llm={llm_flagged} gold={gold_flagged}")


if __name__ == "__main__":
    main()
