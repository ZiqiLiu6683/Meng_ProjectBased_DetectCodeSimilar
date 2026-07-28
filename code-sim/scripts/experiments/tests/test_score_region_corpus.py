"""Unit tests for the region-corpus scorer.

Protocol §12 lists passing coverage/type/localization scorer tests as an execution gate, and the
reason is specific: a scorer defect is invisible. It does not crash, it returns a number, and the
number is wrong in a direction nobody notices. Two such defects have already been found in this
project by accident rather than by test -- a bounding box scored as if it were dense content, which
inflated detection and deflated IoU simultaneously, and a reference range that collapsed to a single
line because only identical lines were mapped.

The cases below therefore pin the behaviours that would silently change a headline number: the
enclosing-prediction artifact, one-sided references, and the rule that the primary match is chosen
by boundary quality and never by type.
"""

import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "score_region_corpus.py"
SPEC = importlib.util.spec_from_file_location("score_region_corpus", SCRIPT)
SCORER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = SCORER
SPEC.loader.exec_module(SCORER)


def lines(*pairs):
    out = set()
    for begin, end in pairs:
        out.update(range(begin, end + 1))
    return out


class GeometryTest(unittest.TestCase):

    def test_perfect_match_scores_one_on_every_metric(self):
        coverage, precision, iou = SCORER.quality(
            (lines((10, 20)), lines((10, 20))),
            (lines((10, 20)), lines((10, 20))))
        self.assertEqual((coverage, precision, iou), (1.0, 1.0, 1.0))

    def test_prediction_that_merely_encloses_the_reference_has_full_coverage_but_poor_precision(self):
        # The artifact the bounding box produced for a whole year of runs: a prediction spanning
        # 12-100 "found" every small reference inside it while having examined almost none of it.
        coverage, precision, iou = SCORER.quality(
            (lines((10, 15)), lines((10, 15))),
            (lines((1, 100)), lines((1, 100))))
        self.assertEqual(coverage, 1.0)
        self.assertLess(precision, 0.1)
        self.assertLess(iou, 0.1)

    def test_deletion_is_scored_on_the_left_side_only(self):
        # A deleted statement has no right-hand extent. Scoring the empty side would make every
        # deletion unmatchable no matter what the system reported.
        coverage, _, iou = SCORER.quality(
            (lines((10, 12)), set()),
            (lines((10, 12)), set()))
        self.assertEqual(coverage, 1.0)
        self.assertEqual(iou, 1.0)

    def test_insertion_is_scored_on_the_right_side_only(self):
        coverage, _, _ = SCORER.quality(
            (set(), lines((30, 30))),
            (set(), lines((28, 32))))
        self.assertEqual(coverage, 1.0)

    def test_disjoint_prediction_scores_zero(self):
        self.assertEqual(SCORER.quality((lines((1, 5)), lines((1, 5))),
                                        (lines((50, 60)), lines((50, 60)))),
                         (0.0, 0.0, 0.0))

    def test_reference_with_no_extent_at_all_scores_zero_rather_than_dividing_by_zero(self):
        self.assertEqual(SCORER.quality((set(), set()), (lines((1, 5)), lines((1, 5)))),
                         (0.0, 0.0, 0.0))


class RegionLinesTest(unittest.TestCase):

    def test_segments_are_preferred_over_the_bounding_box(self):
        endpoint = {"beginLine": 12, "endLine": 100,
                    "segments": [{"begin": 12, "end": 17}, {"begin": 96, "end": 100}]}
        self.assertEqual(SCORER.region_lines(endpoint), lines((12, 17), (96, 100)))

    def test_bounding_box_is_used_only_when_no_segments_are_present(self):
        self.assertEqual(SCORER.region_lines({"beginLine": 3, "endLine": 5}), lines((3, 5)))

    def test_empty_segment_list_falls_back_rather_than_returning_nothing(self):
        self.assertEqual(SCORER.region_lines({"beginLine": 3, "endLine": 5, "segments": []}),
                         lines((3, 5)))


def result(pair_id, regions):
    return json.dumps({"schemaVersion": "4.1", "pairId": pair_id, "status": "ok",
                       "report": {"regions": regions}})


def region(candidate, clone_type, begin, end, subs=None):
    endpoint = {"beginLine": begin, "endLine": end,
                "segments": [{"begin": begin, "end": end}]}
    return {"candidateId": candidate, "type": clone_type,
            "left": dict(endpoint), "right": dict(endpoint),
            "subRegions": subs or []}


def sub(clone_type, begin, end):
    return {"type": clone_type,
            "left": [{"begin": begin, "end": end}],
            "right": [{"begin": begin, "end": end}],
            "reason": ""}


def reference(pair_id, index, kind, clone_type, operator, lb, le, rb, re):
    return {"pair_id": pair_id, "ref_index": str(index), "kind": kind,
            "clone_type": clone_type, "operator": operator,
            "left_begin": str(lb), "left_end": str(le),
            "right_begin": str(rb), "right_end": str(re)}


class ScoringTest(unittest.TestCase):

    def run_scorer(self, results, references):
        workspace = Path(tempfile.mkdtemp())
        (workspace / "merged.jsonl").write_text("\n".join(results) + "\n", encoding="utf-8")
        with (workspace / "refs.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(references[0].keys()),
                                    lineterminator="\n")
            writer.writeheader()
            writer.writerows(references)
        (workspace / "manifest.csv").write_text("pair_id,left_path,right_path\n", encoding="utf-8")
        out = workspace / "scored"
        argv = sys.argv
        sys.argv = ["score_region_corpus.py",
                    "--results", str(workspace / "merged.jsonl"),
                    "--references", str(workspace / "refs.csv"),
                    "--manifest", str(workspace / "manifest.csv"),
                    "--out", str(out)]
        try:
            SCORER.main()
        finally:
            sys.argv = argv
        with (out / "scored_references.csv").open(encoding="utf-8") as stream:
            return list(csv.DictReader(stream))

    def test_clone_interval_needs_seventy_percent_coverage_but_a_mutation_only_needs_overlap(self):
        # One prediction, covering a third of each reference. The clone interval must fail and the
        # mutation must pass: they answer different questions and §6 gives them different rules.
        results = [result("P1", [region("AR1", "T3", 10, 12,
                                        subs=[sub("T3", 10, 12)])])]
        references = [
            reference("P1", 0, "CLONE_INTERVAL", "T3", "t3_insert_statement", 10, 18, 10, 18),
            reference("P1", 1, "MUTATION", "T3", "t3_insert_statement", 10, 18, 10, 18),
        ]
        rows = self.run_scorer(results, references)
        by_kind = {row["kind"]: row for row in rows}
        self.assertEqual(by_kind["CLONE_INTERVAL"]["matched"], "False")
        self.assertEqual(by_kind["MUTATION"]["matched"], "True")

    def test_primary_match_is_chosen_by_boundary_quality_not_by_the_expected_type(self):
        # A tiny sub-region carrying the expected label sits beside a well-aligned one that does
        # not. Choosing by type would report a correct verdict the system never gave.
        results = [result("P1", [region("AR1", "T3", 10, 20, subs=[
            sub("T1", 10, 20),   # aligns with the reference
            sub("T2", 10, 10),   # carries the expected label, but barely overlaps
        ])])]
        references = [reference("P1", 0, "MUTATION", "T2", "t2_rename_local", 10, 20, 10, 20)]
        row = self.run_scorer(results, references)[0]
        self.assertEqual(row["predicted_type"], "T1")
        self.assertEqual(row["type_correct"], "False")

    def test_untouched_run_matched_by_a_t1_sub_region_counts_as_correct(self):
        results = [result("P1", [region("AR1", "T3", 1, 40, subs=[sub("T1", 20, 30)])])]
        references = [reference("P1", 0, "UNTOUCHED", "T1", "none", 20, 30, 20, 30)]
        row = self.run_scorer(results, references)[0]
        self.assertEqual(row["matched"], "True")
        self.assertEqual(row["type_correct"], "True")
        self.assertEqual(row["iou"], "1.0")

    def test_a_pair_with_no_result_stays_in_the_denominator_as_unmatched(self):
        references = [reference("MISSING", 0, "MUTATION", "T2", "t2_rename_local", 5, 9, 5, 9)]
        row = self.run_scorer([result("OTHER", [])], references)[0]
        self.assertEqual(row["matched"], "False")
        self.assertEqual(row["status"], "missing")

    def test_clone_intervals_are_scored_against_regions_and_never_against_sub_regions(self):
        # The region itself is far too wide to c-match; only a sub-region fits. If clone intervals
        # were allowed to match sub-regions the scale separation would be lost and the number would
        # silently become a different measurement.
        results = [result("P1", [region("AR1", "T2", 1, 200, subs=[sub("T2", 10, 20)])])]
        references = [reference("P1", 0, "CLONE_INTERVAL", "T2", "t2_rename_local", 10, 20, 10, 20)]
        row = self.run_scorer(results, references)[0]
        self.assertEqual(row["matched"], "True")      # the wide region does cover it
        self.assertLess(float(row["iou"]), 0.1)       # and the poor localisation is visible


if __name__ == "__main__":
    unittest.main()
