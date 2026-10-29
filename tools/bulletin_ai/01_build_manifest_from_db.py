#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
from typing import Any, Dict, List

import psycopg
from psycopg.rows import dict_row

from lib import ensure_dir, load_dotenv, slug_from_url, write_json

SQL = """
SELECT
    p.id AS program_id,
    p.name AS program_name,
    p.program_type,
    p.degree_type,
    p.college,
    pcv.id AS catalog_version_id,
    pcv.catalog_year,
    pcv.version_label,
    pcp.source_url
FROM program_catalog_version pcv
JOIN program p ON p.id = pcv.program_id
JOIN program_catalog_policy pcp ON pcp.pcv_id = pcv.id
WHERE pcp.source_url IS NOT NULL
  AND (%(catalog_year)s::int IS NULL OR pcv.catalog_year = %(catalog_year)s::int)
  AND (%(catalog_label)s::text IS NULL OR pcv.version_label = %(catalog_label)s::text)
ORDER BY pcv.catalog_year DESC, p.name ASC;
"""


def build_manifest(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    items = []
    for row in rows:
        source_url = row["source_url"]
        catalog_label = row["version_label"] or str(row["catalog_year"])
        items.append(
            {
                "programId": row["program_id"],
                "programName": row["program_name"],
                "programType": row["program_type"],
                "degreeType": row["degree_type"],
                "college": row["college"],
                "catalogVersionId": row["catalog_version_id"],
                "catalogYear": row["catalog_year"],
                "catalogLabel": catalog_label,
                "sourceUrl": source_url,
                "slug": slug_from_url(source_url),
            }
        )
    return items


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Build manifest from the program catalog DB")
    parser.add_argument("--output", required=True, help="Output JSON file")
    parser.add_argument("--catalog-year", type=int, default=None)
    parser.add_argument("--catalog-label", default=None)
    parser.add_argument("--db-url", default=os.getenv("DATABASE_URL"))
    args = parser.parse_args()

    if not args.db_url:
        raise SystemExit("DATABASE_URL is required (or pass --db-url)")

    params = {
        "catalog_year": args.catalog_year,
        "catalog_label": args.catalog_label,
    }

    with psycopg.connect(args.db_url, row_factory=dict_row) as conn:
        rows = conn.execute(SQL, params).fetchall()

    manifest = build_manifest(rows)

    output_path = Path(args.output)
    ensure_dir(output_path.parent)
    write_json(output_path, manifest)
    unique_programs = {item["programId"] for item in manifest}
    labels = {}
    for item in manifest:
        label = item.get("catalogLabel") or "unknown"
        labels[label] = labels.get(label, 0) + 1
    label_summary = ", ".join(f"{label}={count}" for label, count in sorted(labels.items()))
    print(f"Wrote {len(manifest)} entries to {output_path}")
    print(f"Programs: {len(unique_programs)} | Catalog labels: {label_summary}")


if __name__ == "__main__":
    main()
