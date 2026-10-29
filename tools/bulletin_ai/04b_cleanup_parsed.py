#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
from typing import Dict, List, Optional

from tqdm import tqdm

from lib import iter_manifest_items, load_dotenv, normalize_text, read_json, write_json


def note_key(node: Dict) -> str:
    for key in ("notes", "footnoteText", "name"):
        value = node.get(key)
        if value:
            normalized = normalize_text(value)
            if normalized:
                return normalized
    return ""


def is_unnamed(node: Dict) -> bool:
    name = (node.get("name") or "").strip().lower()
    return not name or name == "unnamed requirement block"


def dedupe_note_children(children: List[Dict]) -> List[Dict]:
    seen = set()
    output = []
    for child in children:
        if child.get("kind") == "NOTE_ONLY":
            key = note_key(child)
            if key and key in seen:
                continue
            if key:
                seen.add(key)
        output.append(child)
    return output


def cleanup_node(node: Optional[Dict]) -> Optional[Dict]:
    if not isinstance(node, dict):
        return None

    children = [cleanup_node(child) for child in node.get("children", []) or []]
    children = [child for child in children if child is not None]
    children = dedupe_note_children(children)
    node["children"] = children

    if is_unnamed(node):
        has_options = bool(node.get("courseOptions") or node.get("attributeOptions"))
        has_notes = bool(node.get("notes") or node.get("footnoteText"))
        if not has_options and not has_notes and len(children) == 1:
            child = children[0]
            if node.get("source") and not child.get("source"):
                child["source"] = node["source"]
            return child

    return node


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Deterministic cleanup of parsed requirement trees")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--backup-root", default="")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    backup_root = Path(args.backup_root) if args.backup_root else None
    processed = 0
    cleaned = 0
    skipped = 0
    missing = 0

    for item in tqdm(items, desc="cleanup-parsed"):
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
        if not parsed_path.exists():
            missing += 1
            continue

        payload = read_json(parsed_path)
        root = payload.get("root") if isinstance(payload, dict) else None
        if not root:
            skipped += 1
            continue

        before = None
        try:
            before = json.dumps(root, sort_keys=True, ensure_ascii=True)
        except Exception:
            before = None

        cleaned_root = cleanup_node(root)
        if not cleaned_root:
            skipped += 1
            continue

        if not args.force and before is not None:
            try:
                after = json.dumps(cleaned_root, sort_keys=True, ensure_ascii=True)
            except Exception:
                after = None
            if after is not None and after == before:
                skipped += 1
                continue

        if not args.force and before is None:
            skipped += 1
            continue

        if backup_root:
            backup_path = backup_root / catalog_label / f"{slug}.json"
            if not backup_path.exists():
                write_json(backup_path, payload)

        payload["root"] = cleaned_root
        write_json(parsed_path, payload)
        processed += 1
        cleaned += 1

    print(
        f"Cleanup summary: total={len(items)} cleaned={cleaned} skipped={skipped} missing_parsed={missing}"
    )


if __name__ == "__main__":
    main()
