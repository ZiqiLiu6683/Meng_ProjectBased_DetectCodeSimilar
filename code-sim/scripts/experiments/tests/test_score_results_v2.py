import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "score_results_v2.py"
SPEC = importlib.util.spec_from_file_location("score_results_v2", SCRIPT)
SCORER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = SCORER
SPEC.loader.exec_module(SCORER)


def region(candidate, clone_type, begin, end):
    return {
        "candidateId": candidate,
        "type": clone_type,
        "left": {"beginLine": begin, "endLine": end},
        "right": {"beginLine": begin, "endLine": end},
    }


class StrictScorerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.left = self.root / "Left.java"
        self.right = self.root / "Right.java"
        source = "\n".join(f"line{i};" for i in range(1, 31))
        self.left.write_text(source, encoding="utf-8")
        self.right.write_text(source, encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def manifest(self):
        return {
            "pair_id": "p1",
            "left_path": str(self.left),
            "right_path": str(self.right),
            "left_begin": "10",
            "left_end": "19",
            "right_begin": "10",
            "right_end": "19",
        }

    def label(self, expected="T3"):
        return {"pair_id": "p1", "expected_type": expected, "band": "", "cluster_id": "c1"}

    def result(self, regions):
        return {
            "pairId": "p1", "status": "ok", "analysisMode": "SOURCE_PLUS_WALA_SMT",
            "wallMs": 20, "report": {"regions": regions},
        }

    def test_primary_match_is_selected_by_boundary_not_expected_type(self):
        result = self.result([
            region("wrong-perfect", "T2", 10, 19),
            region("correct-loose", "T3", 8, 21),
        ])

        scored = SCORER.score_pair(self.label(), self.manifest(), result, False, 6)

        self.assertTrue(scored["detected"])
        self.assertEqual("wrong-perfect", scored["candidate_id"])
        self.assertEqual("T2", scored["primary_type"])
        self.assertFalse(scored["type_correct"])
        self.assertTrue(scored["any_correct_overlap"])

    def test_negative_reports_small_product_false_positive_separately(self):
        result = self.result([region("small", "T1", 12, 17)])

        scored = SCORER.score_pair(self.label("NON_CLONE"), self.manifest(), result, False, 6)

        self.assertFalse(scored["reference_range_fp"])
        self.assertTrue(scored["strict_product_fp"])

    def test_missing_result_remains_in_denominator(self):
        scored = SCORER.score_pair(self.label(), self.manifest(), None, False, 6)

        self.assertEqual("missing_result", scored["status"])
        self.assertFalse(scored["detected"])
        self.assertEqual("ERROR_OR_MISSING", scored["primary_type"])

    def test_missing_negative_is_not_counted_as_correct_rejection(self):
        scored = SCORER.score_pair(self.label("NON_CLONE"), self.manifest(), None, False, 6)

        summary = SCORER.build_summary([scored], 20, 42)

        self.assertIn("| NON_CLONE | 1 | 0.00%", summary)
        self.assertIn("| 1 | 0 |", summary)

    def test_summary_reports_analysis_modes_and_fallback_causes(self):
        semantic = SCORER.score_pair(
            self.label(), self.manifest(), self.result([]), False, 6)
        fallback_result = self.result([])
        fallback_result.update({
            "analysisMode": "SOURCE_ONLY_FALLBACK",
            "fallbackStage": "WALA_BUILD",
            "fallbackReason": "scope construction failed",
        })
        fallback = SCORER.score_pair(
            {**self.label(), "pair_id": "p2"},
            {**self.manifest(), "pair_id": "p2"}, fallback_result, False, 6)

        summary = SCORER.build_summary([semantic, fallback], 20, 42)

        self.assertIn("## Execution provenance", summary)
        self.assertIn("| SOURCE_PLUS_WALA_SMT | 1 | 50.00%", summary)
        self.assertIn("| SOURCE_ONLY_FALLBACK | 1 | 50.00%", summary)
        self.assertIn("| WALA_BUILD | scope construction failed | 1 |", summary)

    def test_v2_manifest_can_supply_labels_and_relative_paths(self):
        manifest = self.root / "manifest-v2.csv"
        results = self.root / "results.jsonl"
        out = self.root / "scored"
        with manifest.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow([
                "pair_id", "left_path", "right_path", "expected_type", "band",
                "cluster_id", "left_begin", "left_end", "right_begin", "right_end",
            ])
            writer.writerow([
                "p1", "Left.java", "Right.java", "NON_CLONE", "", "c1",
                "1", "30", "1", "30",
            ])
        results.write_text(json.dumps({
            "pairId": "p1", "status": "ok", "attempt": 1,
            "analysisMode": "SOURCE_PLUS_WALA_SMT", "wallMs": 1,
            "report": {"regions": []},
        }) + "\n", encoding="utf-8")

        scores = SCORER.run(Namespace(
            out=out,
            labels=None,
            manifest=manifest,
            results=results,
            attempt_policy="first",
            detection_policy="strict",
            min_region_lines=6,
            bootstrap_iterations=20,
            seed=42,
        ))

        self.assertEqual(1, len(scores))
        self.assertEqual("ok", scores[0]["status"])
        self.assertFalse(scores[0]["reference_range_fp"])


if __name__ == "__main__":
    unittest.main()
