import importlib.util
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "audit_stub_failures.py"
SPEC = importlib.util.spec_from_file_location("audit_stub_failures_tested", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class AuditStubFailuresTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.entry = self.root / "entries" / ("a" * 64)
        (self.entry / "source").mkdir(parents=True)
        (self.entry / "stub-sources" / "mail").mkdir(parents=True)
        (self.entry / "source" / "Example.java").write_text(
            "class Example {\n  Missing value;\n}\n", encoding="utf-8")
        (self.entry / "stub-sources" / "mail" / "Missing.java").write_text(
            "package mail; public class Missing {}\n", encoding="utf-8")
        (self.entry / "compilation.properties").write_text(
            "# negative entry\n"
            "cacheVersion=3\nstatus=failed\nstubGeneratorVersion=3\n"
            "diagnosticSummary=L2 compiler.err.cant.resolve.location\\: cannot find symbol\n",
            encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_collects_exact_source_and_generated_stubs(self):
        failures = MODULE.collect(self.root, "3")
        self.assertEqual(1, len(failures))
        self.assertEqual("Example.java", failures[0]["source_name"])
        self.assertEqual("compiler.err.cant.resolve.location", failures[0]["primary_code"])
        self.assertEqual(1, len(failures[0]["stubs"]))
        report = MODULE.render_report(failures, str(self.root), "3", 1)
        self.assertIn("Missing value", report)
        self.assertIn("Failed immutable entries: **1**", report)

    def test_source_excerpt_finds_line_inside_compilation_failure_wrapper(self):
        source = self.entry / "source" / "Example.java"
        excerpt = MODULE.source_excerpt(
            source, "Failed to compile [/tmp/source [L2 compiler.err.doesnt.exist: missing]]")
        self.assertIn("Missing value", excerpt)

    def test_preserves_hyphenated_javac_diagnostic_code(self):
        match = MODULE.DIAGNOSTIC_CODE.search(
            "L2 compiler.err.non-static.cant.be.ref: non-static variable")
        self.assertIsNotNone(match)
        self.assertEqual("compiler.err.non-static.cant.be.ref", match.group(0))

    def test_bundle_contains_private_failure_artifacts(self):
        failures = MODULE.collect(self.root, "3")
        report = self.root / "report.md"
        report.write_text("report\n", encoding="utf-8")
        archive = self.root / "bundle.tar.gz"
        MODULE.write_bundle(failures, report, archive)
        with MODULE.tarfile.open(archive) as bundle:
            names = set(bundle.getnames())
        self.assertIn("audit/report.md", names)
        self.assertIn(f"entries/{'a' * 64}/source/Example.java", names)

    def test_collects_fallback_source_from_frozen_run_without_cache(self):
        dataset = self.root / "dataset"
        dataset.mkdir()
        source = dataset / "Fallback.java"
        source.write_text("class Fallback { Missing value; }\n", encoding="utf-8")
        executions = dataset / "executions.csv"
        with executions.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=["pair_id", "left_path", "right_path"])
            writer.writeheader()
            writer.writerow({
                "pair_id": "pair-1", "left_path": "Fallback.java",
                "right_path": "Fallback.java",
            })
        results = self.root / "merged.jsonl"
        results.write_text(json.dumps({
            "pairId": "pair-1", "analysisMode": "SOURCE_ONLY_FALLBACK",
            "fallbackStage": "compile_left",
            "fallbackReason": "COMPILATION_FAILED",
            "stages": {"compile_left": {
                "detail": "L1 compiler.err.cant.resolve.location: cannot find symbol",
            }},
        }) + "\n", encoding="utf-8")

        failures = MODULE.collect_from_results(results, executions)
        self.assertEqual(1, len(failures))
        self.assertEqual(source.resolve(), failures[0]["source"])
        self.assertEqual("compiler.err.cant.resolve.location", failures[0]["primary_code"])


if __name__ == "__main__":
    unittest.main()
