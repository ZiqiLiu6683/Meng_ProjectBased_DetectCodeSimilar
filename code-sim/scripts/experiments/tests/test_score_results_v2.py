import csv
import importlib.util
import json
import sys
import tempfile
import unittest
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


if __name__ == "__main__":
    unittest.main()
