"""Unit tests for the negative-stratum scorer.

The failure mode this guards against is specific to negatives: every defect here moves specificity
UP, which is the direction nobody questions. Forget the minimum-size floor and small template
matches vanish; require 70 % coverage on one side instead of both and a pair that matched nothing on
the right still counts as clean; let `POSSIBLE_T4_CANDIDATE` fall into the syntactic bucket and the
headline changes meaning entirely.

Each test below pins one rule §4.1 states, so that changing the rule fails a test rather than
quietly improving the result.
"""

import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "score_negatives.py"
SPEC = importlib.util.spec_from_file_location("score_negatives", SCRIPT)
SCORER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = SCORER
SPEC.loader.exec_module(SCORER)


def region(kind, left, right, left_segments=None, right_segments=None):
    """One report region; `left`/`right` are (begin, end) boxes."""
    def side(box, segments):
        out = {"beginLine": box[0], "endLine": box[1]}
        if segments is not None:
            out["segments"] = [{"begin": b, "end": e} for b, e in segments]
        return out
    return {"type": kind, "left": side(left, left_segments), "right": side(right, right_segments)}


def record(pair_id="N00000", regions=()):
    return {"pairId": pair_id, "status": "ok", "analysisMode": "SOURCE_PLUS_WALA_SMT",
            "report": {"regions": list(regions)}}


class RegionLines(unittest.TestCase):
    def test_segments_win_over_the_bounding_box(self):
        """The box spans 1-100; the content is 6 lines. Scoring the box would call this huge."""
        endpoint = {"beginLine": 1, "endLine": 100,
                    "segments": [{"begin": 1, "end": 3}, {"begin": 90, "end": 92}]}
        self.assertEqual(len(SCORER.region_lines(endpoint)), 6)

    def test_box_is_the_fallback_when_there_are_no_segments(self):
        self.assertEqual(len(SCORER.region_lines({"beginLine": 10, "endLine": 14})), 5)

    def test_absent_bounds_are_empty_not_zero(self):
        self.assertEqual(SCORER.region_lines({}), set())


class StrictProductDefinition(unittest.TestCase):
    def test_region_below_the_minimum_is_not_a_false_positive(self):
        row = SCORER.classify(record(regions=[region("T1", (1, 4), (1, 4))]), 100, 100, 6)
        self.assertFalse(row["fp_strict_product"])

    def test_region_at_the_minimum_is_a_false_positive(self):
        row = SCORER.classify(record(regions=[region("T1", (1, 6), (1, 6))]), 100, 100, 6)
        self.assertTrue(row["fp_strict_product"])

    def test_the_smaller_side_decides(self):
        """20 lines on the left but 3 on the right is a 3-line correspondence, not a 20-line one."""
        row = SCORER.classify(record(regions=[region("T1", (1, 20), (1, 3))]), 100, 100, 6)
        self.assertFalse(row["fp_strict_product"])


class ReferenceRangeDefinition(unittest.TestCase):
    def test_needs_seventy_percent_of_BOTH_files(self):
        """80 % of a 10-line left, 30 % of a 100-line right: the pair does not correspond."""
        row = SCORER.classify(record(regions=[region("T1", (1, 8), (1, 30))]), 10, 100, 6)
        self.assertFalse(row["fp_reference_range"])

    def test_fires_when_both_sides_are_covered(self):
        row = SCORER.classify(record(regions=[region("T1", (1, 8), (1, 8))]), 10, 10, 6)
        self.assertTrue(row["fp_reference_range"])

    def test_is_independent_of_the_minimum_size_floor(self):
        """Two tiny files fully covered: reference-range fires, strict does not. Both are correct."""
        row = SCORER.classify(record(regions=[region("T1", (1, 4), (1, 4))]), 4, 4, 6)
        self.assertTrue(row["fp_reference_range"])
        self.assertFalse(row["fp_strict_product"])


class SyntacticClaim(unittest.TestCase):
    def test_possible_t4_candidate_is_not_a_syntactic_claim(self):
        row = SCORER.classify(
            record(regions=[region("POSSIBLE_T4_CANDIDATE", (1, 9), (1, 9))]), 100, 100, 6)
        self.assertTrue(row["fp_strict_product"])
        self.assertFalse(row["claims_syntactic_type"])

    def test_t3_is(self):
        row = SCORER.classify(record(regions=[region("T3", (1, 9), (1, 9))]), 100, 100, 6)
        self.assertTrue(row["claims_syntactic_type"])

    def test_a_syntactic_type_below_the_floor_does_not_count(self):
        """Otherwise the syntactic count could exceed the strict count it is a subset of."""
        row = SCORER.classify(record(regions=[region("T2", (1, 2), (1, 2))]), 100, 100, 6)
        self.assertFalse(row["claims_syntactic_type"])


class Clustering(unittest.TestCase):
    def test_problem_pair_key_is_order_independent(self):
        """(p1, p2) and (p2, p1) are the same cluster; treating them as two understates ICC."""
        groups = {}
        for left, right in (("p1", "p2"), ("p2", "p1")):
            groups.setdefault("|".join(sorted((left, right))), []).append(1.0)
        self.assertEqual(len(groups), 1)

    def test_icc_of_identical_clusters_is_zero_not_nan(self):
        rho, size = SCORER.icc_oneway({"a": [1.0, 1.0], "b": [1.0, 1.0]})
        self.assertEqual(rho, 0.0)
        self.assertEqual(size, 2.0)


class EndToEnd(unittest.TestCase):
    def test_missing_results_are_reported_not_silently_dropped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            left, right = root / "L.java", root / "R.java"
            left.write_text("\n".join(f"line {i}" for i in range(20)))
            right.write_text("\n".join(f"line {i}" for i in range(20)))
            manifest = root / "m.csv"
            with manifest.open("w", newline="") as fh:
                writer = csv.writer(fh)
                writer.writerow(["pair_id", "left_path", "right_path",
                                 "left_problem", "right_problem"])
                for i in range(3):
                    writer.writerow([f"N0000{i}", str(left), str(right), "p1", "p2"])
            results = root / "merged.jsonl"
            results.write_text(json.dumps({"schemaVersion": "1", **record("N00000")}) + "\n")

            out = root / "scored"
            sys.argv = ["score_negatives.py", "--results", str(results),
                        "--manifest", str(manifest), "--out", str(out)]
            SCORER.main()
            summary = (out / "SUMMARY.md").read_text()
            self.assertIn("2 manifest pairs missing", summary)
            scored = list(csv.DictReader((out / "scored_negatives.csv").open()))
            self.assertEqual(len(scored), 1)


if __name__ == "__main__":
    unittest.main()
