#!/usr/bin/env python3
import argparse
import re
from pathlib import Path
from typing import Dict, List, Optional

from tqdm import tqdm

from lib import iter_manifest_items, load_dotenv, normalize_text, read_json, write_json

CONSTRAINT_KINDS = {
    "CREDIT_AT_LEAST",
    "LEVEL_AT_LEAST",
    "COURSE_CAP",
    "GRADE_MIN",
    "RESIDENCY_MIN",
    "GPA_AT_LEAST",
}
SELECTOR_TEXT_PATTERN = re.compile(r"\b(select|choose|take|complete)\b", re.IGNORECASE)


def traverse(node: Dict, path: List[str]):
    name = node.get("name") or ""
    current_path = path + [name] if name else path
    yield node, current_path
    for child in node.get("children", []) or []:
        yield from traverse(child, current_path)


def node_note_key(node: Dict) -> str:
    for key in ("notes", "footnoteText", "name"):
        value = node.get(key)
        if value:
            normalized = normalize_text(value)
            if normalized:
                return normalized
    return ""


def lint_tree(root: Optional[Dict]) -> Dict:
    issues = []
    if not root:
        return {
            "issues": [{"type": "EMPTY_TREE", "count": 1, "examples": []}],
            "issueCount": 1,
        }

    unnamed = []
    empty_nodes = []
    selector_without_children = []
    note_duplicates = []
    note_missing = []
    track_groups = []

    for node, path in traverse(root, []):
        name = (node.get("name") or "").strip()
        kind = node.get("kind")
        children = node.get("children") or []
        course_options = node.get("courseOptions") or []
        attribute_options = node.get("attributeOptions") or []

        if not name or name.lower() == "unnamed requirement block":
            unnamed.append(" / ".join([p for p in path if p]))

        if kind == "NOTE_ONLY":
            if not node_note_key(node):
                note_missing.append(" / ".join([p for p in path if p]))

        is_empty = not children and not course_options and not attribute_options
        if is_empty and kind not in CONSTRAINT_KINDS and kind != "NOTE_ONLY":
            empty_nodes.append(" / ".join([p for p in path if p]))

        selector_text = " ".join(
            value
            for value in [
                (node.get("name") or ""),
                (node.get("notes") or ""),
                (node.get("footnoteText") or ""),
            ]
            if value
        )
        if is_empty and selector_text and SELECTOR_TEXT_PATTERN.search(selector_text):
            selector_without_children.append(" / ".join([p for p in path if p]))

        if name.lower().startswith("tracks"):
            has_track_option = any(
                (child.get("kind") == "TRACK_OPTION") for child in children
            )
            if not has_track_option:
                track_groups.append(" / ".join([p for p in path if p]))

        note_children = [child for child in children if child.get("kind") == "NOTE_ONLY"]
        if len(note_children) > 1:
            seen = set()
            for note in note_children:
                key = node_note_key(note)
                if not key:
                    continue
                if key in seen:
                    note_duplicates.append(" / ".join([p for p in path if p]))
                    break
                seen.add(key)

    if unnamed:
        issues.append({"type": "UNNAMED_NODE", "count": len(unnamed), "examples": unnamed[:5]})
    if empty_nodes:
        issues.append({"type": "EMPTY_NODE", "count": len(empty_nodes), "examples": empty_nodes[:5]})
    if selector_without_children:
        issues.append(
            {
                "type": "SELECTOR_NO_CHILDREN",
                "count": len(selector_without_children),
                "examples": selector_without_children[:5],
            }
        )
    if note_duplicates:
        issues.append({"type": "DUPLICATE_NOTE", "count": len(note_duplicates), "examples": note_duplicates[:5]})
    if note_missing:
        issues.append({"type": "NOTE_WITHOUT_TEXT", "count": len(note_missing), "examples": note_missing[:5]})
    if track_groups:
        issues.append({"type": "TRACK_GROUP_MISSING_OPTIONS", "count": len(track_groups), "examples": track_groups[:5]})

    return {"issues": issues, "issueCount": sum(issue["count"] for issue in issues)}


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Lint requirement trees")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--output-root", default="tools/bulletin_ai/lint")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    total = 0
    linted = 0
    skipped = 0
    missing = 0
    for item in tqdm(items, desc="lint"):
        total += 1
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
        if not parsed_path.exists():
            missing += 1
            continue
        out_path = Path(args.output_root) / catalog_label / f"{slug}.json"
        if out_path.exists() and not args.force:
            skipped += 1
            continue

        parsed = read_json(parsed_path)
        root = parsed.get("root") if isinstance(parsed, dict) else None
        lint = lint_tree(root)
        write_json(out_path, lint)
        linted += 1

    print(f"Lint summary: total={total} linted={linted} skipped={skipped} missing_parsed={missing}")


if __name__ == "__main__":
    main()
