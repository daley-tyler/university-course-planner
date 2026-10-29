#!/usr/bin/env python3
import argparse
import time
from pathlib import Path

import requests
from tqdm import tqdm

from lib import ensure_dir, iter_manifest_items, load_dotenv, read_json, write_text

USER_AGENT = "BulletinAIAudit/1.0"


def fetch_url(url: str, timeout: int) -> str:
    response = requests.get(url, headers={"User-Agent": USER_AGENT}, timeout=timeout)
    response.raise_for_status()
    return response.text


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Download bulletin HTML fixtures")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--output-root", default="tools/bulletin_ai/fixtures/html")
    parser.add_argument("--sleep", type=float, default=0.2, help="Seconds between requests")
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    errors = []
    total = 0
    fetched = 0
    skipped = 0
    for item in tqdm(items, desc="fetch-html"):
        total += 1
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        dest = Path(args.output_root) / catalog_label / f"{slug}.html"
        if dest.exists() and not args.force:
            skipped += 1
            continue
        ensure_dir(dest.parent)
        try:
            html = fetch_url(item["sourceUrl"], args.timeout)
            write_text(dest, html)
            fetched += 1
        except Exception as exc:  # noqa: BLE001
            errors.append({"url": item.get("sourceUrl"), "error": str(exc)})
        if args.sleep:
            time.sleep(args.sleep)

    if errors:
        print(f"Fetch errors: {len(errors)}")
    print(f"Fetch summary: total={total} fetched={fetched} skipped={skipped} errors={len(errors)}")


if __name__ == "__main__":
    main()
