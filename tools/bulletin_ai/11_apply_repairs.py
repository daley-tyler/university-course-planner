#!/usr/bin/env python3
import argparse
import copy
import re
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

from bs4 import BeautifulSoup
from tqdm import tqdm

from lib import iter_manifest_items, load_dotenv, normalize_text, read_json, read_text, write_json

ALLOWED_FIELDS = {
    "kind",
    "name",
    "notes",
    "footnoteText",
    "nRequired",
    "nrequired",
    "creditMin",
    "creditMax",
    "doubleCountAllowed",
    "doubleCountCap",
    "levelMin",
    "levelCap100",
    "levelCap200",
    "levelCap300",
    "levelCap400",
    "levelCap100Courses",
    "levelCap200Courses",
    "levelCap300Courses",
    "levelCap400Courses",
}

ALLOWED_KINDS = {
    "ALL_OF",
    "ANY_OF",
    "N_OF",
    "LEAF_COURSES",
    "NOTE_ONLY",
    "DOUBLE_COUNT_RULE",
    "COURSE_CAP",
    "LEVEL_AT_LEAST",
    "GRADE_MIN",
    "GPA_AT_LEAST",
    "RESIDENCY_MIN",
    "CONCURRENCY_BLOCK",
    "TRACK_OPTION",
    "EXPERIENCE",
    "CREDIT_AT_LEAST",
}

CONSTRAINT_KINDS = {
    "CREDIT_AT_LEAST",
    "LEVEL_AT_LEAST",
    "COURSE_CAP",
    "GRADE_MIN",
    "RESIDENCY_MIN",
    "GPA_AT_LEAST",
}

COURSE_CODE_RE = re.compile(r"\b([A-Z]{2,5})\s?(\d{3}[A-Z]?)\b")


def iter_nodes(root: Dict, path: Optional[List[str]] = None) -> Iterable[Tuple[Dict, List[str]]]:
    if not isinstance(root, dict):
        return
    if path is None:
        path = []
    name = (root.get("name") or "").strip()
    next_path = path + [name] if name else path
    yield root, next_path
    for child in root.get("children", []) or []:
        yield from iter_nodes(child, next_path)


def iter_nodes_with_parent(
    root: Dict, parent: Optional[Dict] = None, path: Optional[List[str]] = None
) -> Iterable[Tuple[Dict, Optional[Dict], List[str]]]:
    if not isinstance(root, dict):
        return
    if path is None:
        path = []
    name = (root.get("name") or "").strip()
    next_path = path + [name] if name else path
    yield root, parent, next_path
    for child in root.get("children", []) or []:
        yield from iter_nodes_with_parent(child, root, next_path)


def normalize_path(path: List) -> List[str]:
    normalized: List[str] = []
    for value in path:
        if value is None:
            continue
        if not isinstance(value, str):
            if isinstance(value, (int, float)):
                value = str(value)
            else:
                continue
        text = normalize_text(value)
        if text:
            normalized.append(text)
    return normalized


def match_selector(root: Dict, selector: Dict) -> List[Tuple[Dict, Optional[Dict]]]:
    if not selector:
        return []
    selector_id = selector.get("id")
    if selector_id is not None:
        try:
            selector_id = int(selector_id)
        except (TypeError, ValueError):
            selector_id = selector.get("id")
    selector_ref = selector.get("sourceRef")
    selector_path = selector.get("path")
    normalized_selector_path = normalize_path(selector_path) if isinstance(selector_path, list) else []

    matches = []
    for node, parent, path in iter_nodes_with_parent(root):
        if selector_id is not None and node.get("id") != selector_id:
            continue
        if selector_ref:
            ref = (node.get("source") or {}).get("ref")
            if ref != selector_ref:
                continue
        if normalized_selector_path:
            if normalize_path(path) != normalized_selector_path:
                continue
        matches.append((node, parent))
    return matches


def get_nrequired(node: Dict) -> Optional[int]:
    value = node.get("nrequired")
    if value is None:
        value = node.get("nRequired")
    return value


def set_nrequired(node: Dict, value: Optional[int]) -> None:
    node["nrequired"] = value
    if "nRequired" in node:
        node["nRequired"] = value


def extract_course_codes_from_snippet(snippet_html: str) -> List[str]:
    if not snippet_html:
        return []
    try:
        text = BeautifulSoup(snippet_html, "html.parser").get_text(" ")
    except Exception:
        text = snippet_html
    codes = set()
    for match in COURSE_CODE_RE.findall(text):
        codes.add(f"{match[0]} {match[1]}")
    return sorted(codes)


def is_empty_node(node: Dict) -> bool:
    return (
        not node.get("children")
        and not node.get("courseOptions")
        and not node.get("attributeOptions")
    )


def validate_tree(root: Dict) -> List[str]:
    errors = []
    for node, _ in iter_nodes(root):
        kind = node.get("kind")
        if kind and kind not in ALLOWED_KINDS:
            errors.append(f"Invalid kind: {kind}")

        credit_min = node.get("creditMin")
        credit_max = node.get("creditMax")
        if credit_min is not None and credit_min < 0:
            errors.append("Negative creditMin")
        if credit_max is not None and credit_max < 0:
            errors.append("Negative creditMax")

        n_required = get_nrequired(node)
        if n_required is not None and n_required < 0:
            errors.append("Negative nRequired")

        if is_empty_node(node) and kind not in CONSTRAINT_KINDS and kind != "NOTE_ONLY":
            errors.append("Empty non-constraint node")

        if kind in {"ANY_OF", "N_OF"} and n_required is not None:
            option_count = len(node.get("courseOptions") or []) + len(node.get("children") or [])
            if option_count > 0 and n_required > option_count:
                errors.append("nRequired exceeds option count")
    return errors


def apply_ops(root: Dict, ops: List[Dict], allowed_codes: List[str]) -> Tuple[List[Dict], List[Dict], List[str]]:
    applied = []
    rejected = []
    errors: List[str] = []
    allowed_set = set(allowed_codes)

    for op in ops:
        op_type = op.get("op")
        selector = op.get("selector") or {}
        if op_type == "set_field":
            field = op.get("field")
            if field not in ALLOWED_FIELDS:
                rejected.append({**op, "reason": "field_not_allowed"})
                continue
            matches = match_selector(root, selector)
            if len(matches) != 1:
                rejected.append({**op, "reason": "selector_not_unique"})
                continue
            node, _ = matches[0]
            value = op.get("value")
            if field in {"nRequired", "nrequired"}:
                try:
                    value = int(value) if value is not None else None
                except (TypeError, ValueError):
                    rejected.append({**op, "reason": "invalid_nrequired"})
                    continue
                set_nrequired(node, value)
            elif field == "kind":
                if value not in ALLOWED_KINDS:
                    rejected.append({**op, "reason": "invalid_kind"})
                    continue
                node[field] = value
            else:
                node[field] = value
            applied.append(op)
            continue

        if op_type == "remove_child":
            child_selector = op.get("childSelector") or {}
            matches = match_selector(root, child_selector)
            if len(matches) != 1:
                rejected.append({**op, "reason": "child_selector_not_unique"})
                continue
            child_node, parent = matches[0]
            if parent is None:
                rejected.append({**op, "reason": "cannot_remove_root"})
                continue
            parent["children"] = [child for child in parent.get("children", []) if child is not child_node]
            applied.append(op)
            continue

        if op_type == "move_child":
            child_selector = op.get("childSelector") or {}
            target_selector = op.get("targetSelector") or {}
            child_matches = match_selector(root, child_selector)
            target_matches = match_selector(root, target_selector)
            if len(child_matches) != 1 or len(target_matches) != 1:
                rejected.append({**op, "reason": "selector_not_unique"})
                continue
            child_node, parent = child_matches[0]
            target_node, _ = target_matches[0]
            if parent is None:
                rejected.append({**op, "reason": "cannot_move_root"})
                continue
            if child_node is target_node:
                rejected.append({**op, "reason": "child_is_target"})
                continue
            parent["children"] = [child for child in parent.get("children", []) if child is not child_node]
            target_node.setdefault("children", []).append(child_node)
            applied.append(op)
            continue

        if op_type == "add_course_option":
            course = op.get("course") or {}
            code = course.get("code")
            if not code or code not in allowed_set:
                rejected.append({**op, "reason": "course_not_in_snippet"})
                continue
            matches = match_selector(root, selector)
            if len(matches) != 1:
                rejected.append({**op, "reason": "selector_not_unique"})
                continue
            node, _ = matches[0]
            options = node.get("courseOptions") or []
            if any(option.get("code") == code for option in options):
                rejected.append({**op, "reason": "course_exists"})
                continue
            options.append(
                {
                    "code": code,
                    "title": course.get("title"),
                    "credits": course.get("credits"),
                }
            )
            node["courseOptions"] = options
            applied.append(op)
            continue

        if op_type == "remove_course_option":
            course = op.get("course") or {}
            code = course.get("code")
            if not code:
                rejected.append({**op, "reason": "missing_course_code"})
                continue
            matches = match_selector(root, selector)
            if len(matches) != 1:
                rejected.append({**op, "reason": "selector_not_unique"})
                continue
            node, _ = matches[0]
            options = node.get("courseOptions") or []
            next_options = [option for option in options if option.get("code") != code]
            node["courseOptions"] = next_options
            applied.append(op)
            continue

        rejected.append({**op, "reason": "unsupported_op"})

    errors.extend(validate_tree(root))
    return applied, rejected, errors


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Apply LLM repair patches")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument("--repairs-root", default="tools/bulletin_ai/repairs")
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--snippet-root", default="tools/bulletin_ai/fixtures/snippets")
    parser.add_argument("--output-root", default="tools/bulletin_ai/repaired")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    items = list(iter_manifest_items(manifest))
    if args.limit > 0:
        items = items[: args.limit]

    processed = 0
    skipped = 0
    for item in tqdm(items, desc="apply-repairs"):
        catalog_label = item.get("catalogLabel") or "unknown"
        slug = item.get("slug") or "unknown"
        repair_path = Path(args.repairs_root) / catalog_label / f"{slug}.json"
        parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
        snippet_path = Path(args.snippet_root) / catalog_label / f"{slug}.html"
        output_path = Path(args.output_root) / catalog_label / f"{slug}.json"

        if not repair_path.exists() or not parsed_path.exists():
            skipped += 1
            continue
        if output_path.exists() and not args.force:
            skipped += 1
            continue

        parsed = read_json(parsed_path)
        root = parsed.get("root") if isinstance(parsed, dict) else None
        if not root:
            skipped += 1
            continue

        repair = read_json(repair_path)
        ops = repair.get("ops", []) or []
        if not ops:
            skipped += 1
            continue

        snippet_html = read_text(snippet_path) if snippet_path.exists() else ""
        allowed_codes = extract_course_codes_from_snippet(snippet_html)

        working_root = copy.deepcopy(root)
        applied, rejected, errors = apply_ops(working_root, ops, allowed_codes)
        meta = {
            "model": repair.get("model"),
            "confidence": repair.get("confidence"),
            "appliedOps": applied,
            "rejectedOps": rejected,
            "errors": errors,
            "valid": len(errors) == 0,
        }
        output = {"root": working_root, "meta": meta}
        write_json(output_path, output)
        processed += 1

    print(f"Apply summary: processed={processed} skipped={skipped}")


if __name__ == "__main__":
    main()
