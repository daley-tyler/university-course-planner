#!/usr/bin/env python3
import argparse
import os
from pathlib import Path

import requests
from tqdm import tqdm

from lib import ensure_dir, iter_manifest_items, load_dotenv, read_json, write_json


def fetch_requirements(api_base: str, program_id: int, catalog_label: str):
    url = f"{api_base}/api/programs/{program_id}/requirements"
    params = {"catalogLabel": catalog_label} if catalog_label else {}
    response = requests.get(url, params=params, timeout=20)
    response.raise_for_status()
    return response.json()


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Fetch requirement trees from backend API")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--api-base", default=os.getenv("API_BASE", "http://localhost:8080"))
    parser.add_argument("--output-root", default="tools/bulletin_ai/parsed")
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
    missing_program = 0
    for item in tqdm(items, desc="fetch-requirements"):
        total += 1
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        program_id = item.get("programId")
        if program_id is None:
            missing_program += 1
            continue
        out_path = Path(args.output_root) / catalog_label / f"{slug}.json"
        if out_path.exists() and not args.force:
            skipped += 1
            continue
        try:
            data = fetch_requirements(args.api_base, int(program_id), catalog_label)
            ensure_dir(out_path.parent)
            write_json(out_path, data)
            fetched += 1
        except Exception as exc:  # noqa: BLE001
            errors.append({"programId": program_id, "error": str(exc)})

    if errors:
        print(f"API errors: {len(errors)}")
    print(
        f"API summary: total={total} fetched={fetched} skipped={skipped} missing_program={missing_program} errors={len(errors)}"
    )


if __name__ == "__main__":
    main()
