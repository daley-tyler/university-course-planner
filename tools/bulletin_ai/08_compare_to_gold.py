#!/usr/bin/env python3
import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

from tqdm import tqdm

from lib import (
    iter_manifest_items,
    load_dotenv,
    normalize_text,
    read_json,
    sha256_text,
    write_json,
)

CHOICE_KINDS = {"ANY_OF", "N_OF"}


def slug_from_expected_filename(path: Path) -> str:
    name = path.name
    if name.endswith(".expected.json"):
        return name[: -len(".expected.json")]
    return path.stem


def course_code_from_option(option: Dict) -> Optional[str]:
    code = option.get("code")
    if code:
        return str(code).strip()
    subject = option.get("subjectCode") or option.get("subject")
    number = option.get("courseNumber") or option.get("number")
    if subject and number:
        return f"{subject} {number}".strip()
    return None


def iter_nodes(node: Optional[Dict], path: Optional[List[str]] = None) -> Iterable[Tuple[Dict, List[str]]]:
    if not isinstance(node, dict):
        return
    if path is None:
        path = []
    name = (node.get("name") or "").strip()
    next_path = path + [name] if name else path
    yield node, next_path
    for child in node.get("children", []) or []:
        yield from iter_nodes(child, next_path)


def normalize_node(node: Optional[Dict]) -> Optional[Dict]:
    if not isinstance(node, dict):
        return None
    courses = sorted(
        {
            code
            for option in node.get("courseOptions", []) or []
            if (code := course_code_from_option(option))
        }
    )
    attrs = sorted(
        {
            str(option.get("code")).strip()
            for option in node.get("attributeOptions", []) or []
            if option.get("code")
        }
    )
    children = [normalize_node(child) for child in node.get("children", []) or []]
    children = [child for child in children if child is not None]
    return {
        "name": normalize_text(node.get("name") or ""),
        "kind": node.get("kind") or "",
        "nRequired": node.get("nRequired"),
        "creditMin": node.get("creditMin"),
        "creditMax": node.get("creditMax"),
        "doubleCountAllowed": node.get("doubleCountAllowed"),
        "doubleCountCap": node.get("doubleCountCap"),
        "levelMin": node.get("levelMin"),
        "levelCap100": node.get("levelCap100"),
        "levelCap200": node.get("levelCap200"),
        "levelCap300": node.get("levelCap300"),
        "levelCap400": node.get("levelCap400"),
        "levelCap100Courses": node.get("levelCap100Courses"),
        "levelCap200Courses": node.get("levelCap200Courses"),
        "levelCap300Courses": node.get("levelCap300Courses"),
        "levelCap400Courses": node.get("levelCap400Courses"),
        "notes": normalize_text(node.get("notes") or ""),
        "footnoteText": normalize_text(node.get("footnoteText") or ""),
        "courses": courses,
        "attrs": attrs,
        "children": children,
    }


def tree_hash(node: Optional[Dict]) -> Optional[str]:
    normalized = normalize_node(node)
    if normalized is None:
        return None
    payload = json.dumps(normalized, sort_keys=True, ensure_ascii=True)
    return sha256_text(payload)


def collect_course_codes(root: Optional[Dict]) -> List[str]:
    codes: List[str] = []
    for node, _ in iter_nodes(root, []):
        for option in node.get("courseOptions", []) or []:
            code = course_code_from_option(option)
            if code:
                codes.append(code)
    return sorted(set(codes))


def group_course_codes(node: Dict) -> List[str]:
    codes = []
    for option in node.get("courseOptions", []) or []:
        code = course_code_from_option(option)
        if code:
            codes.append(code)
    if codes:
        return sorted(set(codes))
    for child in node.get("children", []) or []:
        for option in child.get("courseOptions", []) or []:
            code = course_code_from_option(option)
            if code:
                codes.append(code)
    return sorted(set(codes))


def collect_choice_groups(root: Optional[Dict]) -> List[Tuple[str, Optional[int], Tuple[str, ...]]]:
    groups = []
    for node, _ in iter_nodes(root, []):
        kind = node.get("kind")
        if kind not in CHOICE_KINDS:
            continue
        codes = group_course_codes(node)
        if not codes:
            continue
        n_required = node.get("nRequired")
        groups.append((kind, n_required, tuple(codes)))
    return sorted(set(groups))


def collect_kind_counts(root: Optional[Dict]) -> Dict[str, int]:
    counts: Counter = Counter()
    for node, _ in iter_nodes(root, []):
        kind = node.get("kind") or "UNKNOWN"
        counts[kind] += 1
    return dict(counts)


def collect_double_count_caps(root: Optional[Dict]) -> Tuple[List[int], int]:
    caps: List[int] = []
    allow_false = 0
    for node, _ in iter_nodes(root, []):
        cap = node.get("doubleCountCap")
        if cap is not None:
            try:
                caps.append(int(cap))
            except (TypeError, ValueError):
                continue
        allow = node.get("doubleCountAllowed")
        if allow is False:
            allow_false += 1
    return sorted(caps), allow_false


def compare_sets(expected: List[str], actual: List[str]) -> Dict:
    expected_set = set(expected)
    actual_set = set(actual)
    matched = expected_set & actual_set
    missing = sorted(expected_set - actual_set)
    extra = sorted(actual_set - expected_set)
    precision = len(matched) / len(actual_set) if actual_set else 1.0
    recall = len(matched) / len(expected_set) if expected_set else 1.0
    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "missing": missing,
        "extra": extra,
    }


def compare_groups(expected: List[Tuple[str, Optional[int], Tuple[str, ...]]],
                   actual: List[Tuple[str, Optional[int], Tuple[str, ...]]]) -> Dict:
    expected_set = set(expected)
    actual_set = set(actual)
    matched = expected_set & actual_set
    precision = len(matched) / len(actual_set) if actual_set else 1.0
    recall = len(matched) / len(expected_set) if expected_set else 1.0

    def to_dict(group: Tuple[str, Optional[int], Tuple[str, ...]]) -> Dict:
        return {"kind": group[0], "nRequired": group[1], "courses": list(group[2])}

    missing = [to_dict(group) for group in sorted(expected_set - actual_set)]
    extra = [to_dict(group) for group in sorted(actual_set - expected_set)]
    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "missing": missing,
        "extra": extra,
    }


def pick_manifest_item(items: List[Dict], slug: str, catalog_label: Optional[str]) -> Optional[Dict]:
    candidates = [item for item in items if item.get("slug") == slug]
    if catalog_label:
        candidates = [item for item in candidates if item.get("catalogLabel") == catalog_label]
    if not candidates:
        return None
    candidates.sort(
        key=lambda item: (
            item.get("catalogYear") or 0,
            str(item.get("catalogLabel") or ""),
        ),
        reverse=True,
    )
    return candidates[0]


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Compare parsed trees to golden fixtures")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file")
    parser.add_argument(
        "--gold-root",
        action="append",
        default=[],
        help="Directory with *.expected.json files (can be used multiple times)",
    )
    parser.add_argument("--parsed-root", default="tools/bulletin_ai/parsed")
    parser.add_argument("--output-root", default="tools/bulletin_ai/golden_diff")
    parser.add_argument("--summary", default="tools/bulletin_ai/golden_diff/summary.json")
    parser.add_argument("--catalog-label", default="")
    min_pass_rate_env = os.getenv("GOLD_MIN_PASS_RATE")
    min_pass_rate_default = float(min_pass_rate_env) if min_pass_rate_env else None
    max_mismatches_env = os.getenv("GOLD_MAX_MISMATCHES")
    max_mismatches_default = int(max_mismatches_env) if max_mismatches_env and max_mismatches_env.isdigit() else None
    parser.add_argument("--min-pass-rate", type=float, default=min_pass_rate_default)
    parser.add_argument("--max-mismatches", type=int, default=max_mismatches_default)
    args = parser.parse_args()

    manifest = read_json(Path(args.manifest))
    manifest_items = list(iter_manifest_items(manifest))

    gold_roots = args.gold_root or [
        "backend/src/test/resources/bulletin",
        "tools/bulletin_ai/golden/expected",
    ]

    gold_entries: Dict[str, Dict] = {}
    for root_path in gold_roots:
        root = Path(root_path)
        if not root.exists():
            continue
        for path in root.glob("*.expected.json"):
            slug = slug_from_expected_filename(path)
            try:
                data = read_json(path)
            except Exception:  # noqa: BLE001
                continue
            gold_entries[slug] = {"path": str(path), "data": data}

    results = []
    mismatches = 0
    missing_parsed = 0

    for slug, entry in tqdm(sorted(gold_entries.items()), desc="compare-gold"):
        expected_root = entry["data"]
        manifest_item = pick_manifest_item(manifest_items, slug, args.catalog_label or None)
        if not manifest_item:
            continue
        catalog_label = manifest_item.get("catalogLabel") or "unknown"
        parsed_path = Path(args.parsed_root) / catalog_label / f"{slug}.json"
        if not parsed_path.exists():
            missing_parsed += 1
            continue
        parsed = read_json(parsed_path)
        actual_root = parsed.get("root") if isinstance(parsed, dict) else None

        expected_codes = collect_course_codes(expected_root)
        actual_codes = collect_course_codes(actual_root)
        course_compare = compare_sets(expected_codes, actual_codes)

        expected_groups = collect_choice_groups(expected_root)
        actual_groups = collect_choice_groups(actual_root)
        group_compare = compare_groups(expected_groups, actual_groups)

        expected_caps, expected_allow_false = collect_double_count_caps(expected_root)
        actual_caps, actual_allow_false = collect_double_count_caps(actual_root)
        double_count_match = expected_caps == actual_caps and expected_allow_false == actual_allow_false

        diff = {
            "slug": slug,
            "catalogLabel": catalog_label,
            "expectedPath": entry["path"],
            "actualPath": str(parsed_path),
            "treeHashExpected": tree_hash(expected_root),
            "treeHashActual": tree_hash(actual_root),
            "metrics": {
                "courseCodePrecision": course_compare["precision"],
                "courseCodeRecall": course_compare["recall"],
                "choiceGroupPrecision": group_compare["precision"],
                "choiceGroupRecall": group_compare["recall"],
                "doubleCountRulesMatch": double_count_match,
            },
            "counts": {
                "expectedCourses": len(expected_codes),
                "actualCourses": len(actual_codes),
                "expectedChoiceGroups": len(expected_groups),
                "actualChoiceGroups": len(actual_groups),
            },
            "missingCourseCodes": course_compare["missing"],
            "extraCourseCodes": course_compare["extra"],
            "missingChoiceGroups": group_compare["missing"],
            "extraChoiceGroups": group_compare["extra"],
            "doubleCountCapsExpected": expected_caps,
            "doubleCountCapsActual": actual_caps,
            "doubleCountAllowFalseExpected": expected_allow_false,
            "doubleCountAllowFalseActual": actual_allow_false,
            "kindCountsExpected": collect_kind_counts(expected_root),
            "kindCountsActual": collect_kind_counts(actual_root),
        }

        reasons = []
        if diff["missingCourseCodes"]:
            reasons.append("missing_course_codes")
        if diff["extraCourseCodes"]:
            reasons.append("extra_course_codes")
        if diff["missingChoiceGroups"]:
            reasons.append("missing_choice_groups")
        if diff["extraChoiceGroups"]:
            reasons.append("extra_choice_groups")
        if not double_count_match:
            reasons.append("double_count_rules")
        diff["mismatchReasons"] = reasons
        diff["hasMismatch"] = len(reasons) > 0
        if diff["hasMismatch"]:
            mismatches += 1

        output_path = Path(args.output_root) / catalog_label / f"{slug}.json"
        write_json(output_path, diff)
        results.append(diff)

    compared = len(results)
    pass_rate = (compared - mismatches) / compared if compared else 1.0
    summary = {
        "goldFiles": len(gold_entries),
        "compared": compared,
        "mismatches": mismatches,
        "missingParsed": missing_parsed,
        "mismatchSlugs": [item["slug"] for item in results if item["hasMismatch"]],
        "passRate": round(pass_rate, 4),
        "minPassRate": args.min_pass_rate,
        "maxMismatches": args.max_mismatches,
    }
    write_json(Path(args.summary), summary)
    print(f"Wrote gold comparison summary to {args.summary}")
    print(f"Gold compare: compared={summary['compared']} mismatches={summary['mismatches']} missing_parsed={missing_parsed}")

    failed = False
    if args.min_pass_rate is not None and pass_rate < args.min_pass_rate:
        print(f"Gold gate failed: pass rate {pass_rate:.4f} < {args.min_pass_rate:.4f}")
        failed = True
    if args.max_mismatches is not None and mismatches > args.max_mismatches:
        print(f"Gold gate failed: mismatches {mismatches} > {args.max_mismatches}")
        failed = True
    if failed:
        sys.exit(2)


if __name__ == "__main__":
    main()
