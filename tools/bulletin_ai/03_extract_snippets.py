#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
from typing import List

from bs4 import BeautifulSoup
from tqdm import tqdm

from lib import ensure_dir, iter_manifest_items, load_dotenv, read_json, read_text, write_text

DEFAULT_PARSER = os.getenv("BS4_PARSER", "html.parser")

ALLOWED_SELECTOR = "table.sc_courselist, h1, h2, h3, h4, p, ul, ol, dl.sc_footnotes"


def extract_snippet(html: str, parser: str) -> str:
    soup = BeautifulSoup(html, parser)
    container = soup.select_one("#textcontainer")
    if container is None:
        container = soup.body or soup

    matches: List = container.select(ALLOWED_SELECTOR)
    match_set = set(matches)

    filtered = []
    for el in matches:
        parent = el.parent
        nested = False
        while parent is not None and parent is not container:
            if parent in match_set:
                nested = True
                break
            parent = parent.parent
        if not nested:
            filtered.append(el)

    parts = []
    for el in filtered:
        parts.append(str(el))

    return "\n".join(parts).strip()


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Extract requirement-only HTML snippets")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--html-root", default="tools/bulletin_ai/fixtures/html")
    parser.add_argument("--output-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--parser", default=DEFAULT_PARSER, help="BeautifulSoup parser (default: html.parser)")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    total = 0
    written = 0
    skipped = 0
    missing = 0
    for item in tqdm(items, desc="snippets"):
        total += 1
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        html_path = Path(args.html_root) / catalog_label / f"{slug}.html"
        if not html_path.exists():
            missing += 1
            continue
        out_path = Path(args.output_root) / catalog_label / f"{slug}.html"
        if out_path.exists() and not args.force:
            skipped += 1
            continue
        html = read_text(html_path)
        snippet = extract_snippet(html, args.parser)
        ensure_dir(out_path.parent)
        write_text(out_path, snippet)
        written += 1

    print(
        f"Snippet summary: total={total} written={written} skipped={skipped} missing_html={missing}"
    )


if __name__ == "__main__":
    main()
