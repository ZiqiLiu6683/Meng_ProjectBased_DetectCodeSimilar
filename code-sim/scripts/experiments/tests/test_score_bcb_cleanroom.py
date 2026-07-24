import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "score_bcb_cleanroom.py"
SPEC = importlib.util.spec_from_file_location("score_bcb_cleanroom_tested", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CleanroomScorerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.left = self.root / "Left.java"
        self.right = self.root / "Right.java"
        source = "\n".join(f"line{line};" for line in range(1, 31)) + "\n"
        self.left.write_text(source, encoding="utf-8")
        self.right.write_text(source, encoding="utf-8")
        self.executions = self.root / "executions.csv"
        self.references = self.root / "references.csv"
        self.lock = self.root / "dataset_lock.json"
        self.run_config = self.root / "run_config.json"
        self.results = self.root / "merged.jsonl"
        self.machine = self.root / "machine.json"
        self.dataset_id = "bcb-cleanroom-test"
        self.commit = "a" * 40
        self.write_inputs()

    def tearDown(self):
        self.temp.cleanup()

    def write_inputs(self):
        execution = {
            "schema_version": MODULE.execution_manifest.SCHEMA_VERSION,
            "dataset_id": self.dataset_id,
            "pair_id": "exec_1",
            "left_path": "Left.java",
            "right_path": "Right.java",
            "left_sha256": MODULE.execution_manifest.sha256_file(self.left),
            "right_sha256": MODULE.execution_manifest.sha256_file(self.right),
        }
        with self.executions.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(
                stream, fieldnames=MODULE.execution_manifest.FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerow(execution)

        references = []
        for index, expected in enumerate(("T1", "T2"), start=1):
            references.append({
                "schema_version": MODULE.bcb_cleanroom.REFERENCE_SCHEMA_VERSION,
                "dataset_id": self.dataset_id,
                "reference_id": f"ref_{index}",
                "execution_id": "exec_1",
                "expected_type": expected,
                "band": "",
                "cluster_id": "4",
                "functionality_id": "4",
                "sim_both": "1.0",
                "bcb_f1": str(index),
                "bcb_f2": str(index + 10),
                "left_begin": "10", "left_end": "19",
                "right_begin": "10", "right_end": "19",
                "left_source_key": "4/default/Left.java",
                "right_source_key": "4/default/Right.java",
            })
        with self.references.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(
                stream, fieldnames=MODULE.bcb_cleanroom.REFERENCE_FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerows(references)

        execution_hash = MODULE.execution_manifest.sha256_file(self.executions)
        self.lock.write_text(json.dumps({
            "schema_version": MODULE.bcb_cleanroom.LOCK_SCHEMA_VERSION,
            "dataset_id": self.dataset_id,
            "generator_commit": self.commit,
            "generator_dirty_worktree": False,
            "executions_sha256": execution_hash,
            "references_sha256": MODULE.execution_manifest.sha256_file(self.references),
        }), encoding="utf-8")
        self.run_config.write_text(json.dumps({
            "dataset_id": self.dataset_id,
            "manifest_sha256": execution_hash,
            "dirty_worktree": "false",
            "execution_manifest_schema": MODULE.execution_manifest.SCHEMA_VERSION,
            "config_id": "strict-bcb",
            "code_commit": self.commit,
            "skip_dynamic": True,
            "disable_stubs": True,
            "runtime_classpath_sha256": "a" * 64,
            "runtime_classpath_entries": [
                {"path": "/frozen/classes", "kind": "directory", "sha256": "b" * 64}
            ],
        }), encoding="utf-8")
        self.result = {
            "schemaVersion": MODULE.RESULT_SCHEMA_VERSION,
            "pairId": "exec_1", "status": "ok", "attempt": 1,
            "datasetId": self.dataset_id, "configId": "strict-bcb",
            "codeCommit": self.commit, "dirtyWorktree": "false",
            "manifestSha256": execution_hash,
            "runtimeClasspathSha256": "a" * 64,
            "leftSha256": execution["left_sha256"],
            "rightSha256": execution["right_sha256"],
            "analysisMode": "SOURCE_PLUS_WALA_SMT",
            "stages": {"compile_left": {"status": "SUCCESS", "durationMs": 1}},
            "compilations": {
                "left": {"mode": "STANDALONE"},
                "right": {"mode": "STANDALONE"},
            },
            "fallbackStage": "", "fallbackReason": "", "wallMs": 10,
            "report": {"regions": [{
                "candidateId": "region-1", "type": "T1",
                "left": {"beginLine": 10, "endLine": 19},
                "right": {"beginLine": 10, "endLine": 19},
            }]},
        }
        self.results.write_text(json.dumps(self.result) + "\n", encoding="utf-8")
        self.machine.write_text(json.dumps({
            "logical_cpu_count": 32, "memory_kib": 250000000,
            "cpu_model": "synthetic-test-cpu", "os_release": {"PRETTY_NAME": "test"},
        }), encoding="utf-8")

    def args(self, out):
        return Namespace(
            executions=self.executions, references=self.references,
            dataset_lock=self.lock, run_config=self.run_config, results=self.results,
            machine=self.machine,
            out=out, attempt_policy="first", min_region_lines=6,
            bootstrap_iterations=20, seed=42, stub_policy="forbid",
        )

    def test_one_original_file_pair_scores_multiple_bcb_references_once(self):
        out = self.root / "score"
        with patch.object(MODULE, "current_commit", return_value=(self.commit, False)):
            records = MODULE.run(self.args(out))
        self.assertEqual(2, len(records))
        summary = (out / "summary.md").read_text(encoding="utf-8")
        self.assertIn("Unique file-pair execution provenance", summary)
        self.assertIn("| SOURCE_PLUS_WALA_SMT | 1 | 100.00%", summary)
        self.assertIn("BCB-derived pairwise region recall", summary)
        self.assertIn("stages_json", (out / "execution_provenance.csv").read_text())

    def test_rejects_mixed_or_old_result_schema(self):
        broken = dict(self.result)
        broken["schemaVersion"] = "2.0-dev"
        self.results.write_text(json.dumps(broken) + "\n", encoding="utf-8")
        with patch.object(MODULE, "current_commit", return_value=(self.commit, False)):
            with self.assertRaisesRegex(ValueError, "schemaVersion"):
                MODULE.run(self.args(self.root / "broken-score"))

    def test_rejects_stub_assisted_rows_from_no_stub_ablation(self):
        broken = dict(self.result)
        broken["compilations"] = {
            "left": {"mode": "STUBBED"},
            "right": {"mode": "STANDALONE"},
        }
        self.results.write_text(json.dumps(broken) + "\n", encoding="utf-8")
        with patch.object(MODULE, "current_commit", return_value=(self.commit, False)):
            with self.assertRaisesRegex(ValueError, "no-stub BCB ablation"):
                MODULE.run(self.args(self.root / "stubbed-score"))

    def test_accepts_stub_assisted_rows_in_product_primary(self):
        run_config = json.loads(self.run_config.read_text(encoding="utf-8"))
        run_config["disable_stubs"] = False
        self.run_config.write_text(json.dumps(run_config), encoding="utf-8")
        stubbed = dict(self.result)
        stubbed["analysisMode"] = "SOURCE_PLUS_STUBBED_WALA_SMT"
        stubbed["compilations"] = {
            "left": {"mode": "STUBBED"},
            "right": {"mode": "STANDALONE"},
        }
        self.results.write_text(json.dumps(stubbed) + "\n", encoding="utf-8")
        args = self.args(self.root / "stubbed-primary-score")
        args.stub_policy = "allow"

        with patch.object(MODULE, "current_commit", return_value=(self.commit, False)):
            records = MODULE.run(args)

        self.assertEqual(2, len(records))
        summary = (args.out / "summary.md").read_text(encoding="utf-8")
        self.assertIn("SOURCE_PLUS_STUBBED_WALA_SMT", summary)

    def test_rejects_t4_claim_from_stub_assisted_primary_row(self):
        run_config = json.loads(self.run_config.read_text(encoding="utf-8"))
        run_config["disable_stubs"] = False
        self.run_config.write_text(json.dumps(run_config), encoding="utf-8")
        stubbed = dict(self.result)
        stubbed["analysisMode"] = "SOURCE_PLUS_STUBBED_WALA_SMT"
        stubbed["compilations"] = {
            "left": {"mode": "STUBBED"},
            "right": {"mode": "STANDALONE"},
        }
        stubbed["report"] = {"regions": [{
            "candidateId": "region-t4", "type": "T4_CONFIRMED",
            "left": {"beginLine": 10, "endLine": 19},
            "right": {"beginLine": 10, "endLine": 19},
        }]}
        self.results.write_text(json.dumps(stubbed) + "\n", encoding="utf-8")
        args = self.args(self.root / "stubbed-t4-score")
        args.stub_policy = "allow"

        with patch.object(MODULE, "current_commit", return_value=(self.commit, False)):
            with self.assertRaisesRegex(ValueError, "forbidden T4 evidence"):
                MODULE.run(args)

    def test_accepts_partial_stub_provenance_when_other_side_forces_fallback(self):
        run_config = json.loads(self.run_config.read_text(encoding="utf-8"))
        run_config["disable_stubs"] = False
        self.run_config.write_text(json.dumps(run_config), encoding="utf-8")
        fallback = dict(self.result)
        fallback["analysisMode"] = "SOURCE_ONLY_FALLBACK"
        fallback["compilations"] = {"left": {"mode": "STUBBED"}}
        fallback["fallbackStage"] = "compile_right"
        fallback["fallbackReason"] = "COMPILATION_FAILED"
        fallback["stages"] = {
            "compile_left": {"status": "SUCCESS", "durationMs": 1},
            "compile_right": {"status": "FAILED", "durationMs": 1},
            "fallback": {"status": "SUCCESS", "durationMs": 1},
        }
        self.results.write_text(json.dumps(fallback) + "\n", encoding="utf-8")
        args = self.args(self.root / "partial-stub-fallback-score")
        args.stub_policy = "allow"

        with patch.object(MODULE, "current_commit", return_value=("b" * 40, False)):
            records = MODULE.run(args)

        self.assertEqual(2, len(records))
        summary = (args.out / "summary.md").read_text(encoding="utf-8")
        self.assertIn("SOURCE_ONLY_FALLBACK", summary)


if __name__ == "__main__":
    unittest.main()
