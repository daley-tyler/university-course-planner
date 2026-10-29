#!/usr/bin/env python3
import argparse
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List

from bs4 import BeautifulSoup
from tqdm import tqdm

from lib import iter_manifest_items, load_dotenv, normalize_text, read_json, read_text, write_json


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Aggregate LLM audit outputs")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--llm-root", default="tools/bulletin_ai/llm")
    parser.add_argument("--snippet-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--output", default="tools/bulletin_ai/aggregate/report.json")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--min-confidence", type=float, default=0.6)
    parser.add_argument("--no-require-evidence", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    issue_counts = Counter()
    rule_counts = Counter()
    issue_by_type: Dict[str, List[str]] = defaultdict(list)
    total_pages = 0
    missing = 0
    missing_snippets = 0
    require_evidence = not args.no_require_evidence

    for item in tqdm(items, desc="aggregate"):
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        path = Path(args.llm_root) / catalog_label / f"{slug}.json"
        if not path.exists():
            missing += 1
            continue
        total_pages += 1
        data = read_json(path)
        snippet_text = None
        snippet_missing = False
        if require_evidence:
            snippet_path = Path(args.snippet_root) / catalog_label / f"{slug}.html"
            if not snippet_path.exists():
                missing_snippets += 1
                snippet_missing = True
            else:
                html = read_text(snippet_path)
                snippet_text = normalize_text(BeautifulSoup(html, "html.parser").get_text(" "))
        if snippet_missing and require_evidence:
            continue
        for issue in data.get("issues", []) or []:
            issue_type = issue.get("type", "OTHER")
            confidence = issue.get("confidence")
            try:
                confidence_value = float(confidence)
            except (TypeError, ValueError):
                confidence_value = 0.0
            if confidence_value < args.min_confidence:
                continue
            if require_evidence:
                evidence = normalize_text(issue.get("evidence") or "")
                if not evidence:
                    continue
                if snippet_text and evidence not in snippet_text:
                    continue
            issue_counts[issue_type] += 1
            issue_by_type[issue_type].append(slug)
            rule = issue.get("suggested_rule") or ""
            if rule:
                rule_counts[rule] += 1

    report = {
        "totalPagesAnalyzed": total_pages,
        "missingReports": missing,
        "missingSnippets": missing_snippets,
        "issueCounts": dict(issue_counts),
        "topSuggestedRules": rule_counts.most_common(20),
        "examplesByType": {k: v[:10] for k, v in issue_by_type.items()},
    }

    write_json(Path(args.output), report)
    print(f"Wrote report to {args.output}")
    print(f"Aggregate summary: analyzed={total_pages} missing_reports={missing}")


if __name__ == "__main__":
    main()
