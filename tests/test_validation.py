"""Check publication validation fails closed, without Docker or external services."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from verify_backend_reports import REQUIRED_SUITES, MANUAL_EXPORT  # noqa: E402


class ReportValidationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.reports = Path(self.temporary.name)
        for name, minimum in REQUIRED_SUITES.items():
            self.write_suite(name, minimum)
        self.write_suite("example.OtherUnitTests", 79 - sum(REQUIRED_SUITES.values()))
        self.write_suite(MANUAL_EXPORT, 1, skipped=1)

    def write_suite(self, name, tests, **counts):
        fields = dict(name=name, tests=str(tests), failures="0", errors="0", skipped="0")
        fields.update({key: str(value) for key, value in counts.items()})
        ET.ElementTree(ET.Element("testsuite", fields)).write(self.reports / f"TEST-{name}.xml")

    def run_verifier(self):
        return subprocess.run([sys.executable, str(ROOT / "scripts/verify_backend_reports.py"), str(self.reports)],
                              capture_output=True, text=True)

    def test_complete_run_accepts_only_the_manual_skip(self):
        result = self.run_verifier()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("79 backend tests passed; 1 manual", result.stdout)

    def test_test_failure_is_rejected(self):
        self.write_suite("example.OtherUnitTests", 32, failures=1)
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_test_error_is_rejected(self):
        self.write_suite("example.OtherUnitTests", 32, errors=1)
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_skipped_database_suite_is_rejected(self):
        name = "com.courseplanner.api.Bulletin.ProgramPipelineIntegrationTest"
        self.write_suite(name, 1, skipped=1)
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_missing_golden_suite_is_rejected(self):
        name = next(name for name in REQUIRED_SUITES if name.endswith("GoldenTest"))
        (self.reports / f"TEST-{name}.xml").unlink()
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_missing_other_unit_tests_is_rejected(self):
        (self.reports / "TEST-example.OtherUnitTests.xml").unlink()
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_no_reports_is_rejected(self):
        for path in self.reports.iterdir():
            path.unlink()
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_duplicate_suite_cannot_inflate_the_count(self):
        source = self.reports / "TEST-example.OtherUnitTests.xml"
        source.with_name("TEST-example.OtherUnitTests 2.xml").write_bytes(source.read_bytes())
        result = self.run_verifier()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Duplicate report for suite", result.stderr)


class SyntheticSpecificationTest(unittest.TestCase):
    def test_all_committed_examples_match_the_independent_specification(self):
        result = subprocess.run([sys.executable, str(ROOT / "scripts/build_synthetic_parser_fixtures.py"), "--check"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("30 independently specified", result.stdout)


if __name__ == "__main__":
    unittest.main()
