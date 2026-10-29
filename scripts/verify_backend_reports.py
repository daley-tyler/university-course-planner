"""Check that CI ran the real parser and PostgreSQL tests without hidden skips."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


MANUAL_EXPORT = "com.courseplanner.api.Bulletin.ReqTreeExportTest"
REQUIRED_SUITES = {
    "com.courseplanner.api.Bulletin.ProgramPipelineIntegrationTest": 1,
    "com.courseplanner.api.Bulletin.Requirements.RequirementTreeParserGoldenTest": 30,
    "com.courseplanner.api.Bulletin.Requirements.RequirementTreeParserRegressionTest": 6,
    "com.courseplanner.api.Bulletin.Requirements.RequirementTreeParserCorpusSmokeTest": 1,
    "com.courseplanner.api.CoursePlannerApiApplicationTests": 6,
    "com.courseplanner.api.normalizer.CourseNormalizerIntegrationTest": 1,
    "com.courseplanner.api.normalizer.CourseNormalizerE2ETest": 2,
}


def verify(directory: Path) -> None:
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        raise SystemExit(f"No Surefire reports found in {directory}")

    errors = []
    suites = {}
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for report in reports:
        suite = ET.parse(report).getroot()
        name = suite.attrib["name"]
        counts = {key: int(suite.get(key, "0")) for key in totals}
        if name in suites:
            errors.append(f"Duplicate report for suite: {name}")
        suites[name] = counts
        for key in totals:
            totals[key] += counts[key]
        if counts["failures"] or counts["errors"]:
            errors.append(f"{name}: {counts['failures']} failures, {counts['errors']} errors")
        if counts["skipped"] and name != MANUAL_EXPORT:
            errors.append(f"{name}: {counts['skipped']} application tests skipped")

    for name, minimum in REQUIRED_SUITES.items():
        counts = suites.get(name)
        if counts is None:
            errors.append(f"Missing required suite: {name}")
        elif counts["tests"] - counts["skipped"] < minimum:
            errors.append(f"{name}: expected at least {minimum} executed tests")

    passed = totals["tests"] - totals["skipped"]
    if passed < 79:
        errors.append(f"Expected at least 79 executed backend tests, found {passed}")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"{passed} backend tests passed; {totals['skipped']} manual export utility skipped.")
    print("Exact parser fixtures and PostgreSQL application/normalizer suites all executed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "reports", nargs="?", type=Path,
        default=Path(__file__).resolve().parents[1] / "backend/target/surefire-reports",
    )
    verify(parser.parse_args().reports)
