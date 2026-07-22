import csv
import importlib.util
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "bcb_cleanroom.py"
SPEC = importlib.util.spec_from_file_location("bcb_cleanroom_tested", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class BcbCleanroomTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.bcb = self.root / "bcb_reduced"
        for functionality in ("4", "5"):
            directory = self.bcb / functionality / "default"
            directory.mkdir(parents=True)
            (directory / "A.java").write_text(
                "\n".join(f"// A {line}" for line in range(1, 31)) + "\n",
                encoding="utf-8")
            (directory / "B.java").write_text(
                "\n".join(f"// B {line}" for line in range(1, 31)) + "\n",
                encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def query_row(self, functionality="4", one="10", two="20"):
        return {
            "FUNCTION_ID_ONE": one,
            "FUNCTION_ID_TWO": two,
            "FUNCTIONALITY_ID": functionality,
            "SIM_BOTH": "0.8",
            "NAME1": "B.java",
            "TYPE1": "default",
            "S1": "3",
            "E1": "12",
            "NAME2": "A.java",
            "TYPE2": "default",
            "S2": "5",
            "E2": "14",
        }

    def test_reference_orientation_swaps_ranges_but_never_rewrites_source(self):
        reference, sources = MODULE.normalize_reference(
            self.query_row(), "ST3", "T3", self.bcb)

        self.assertEqual("4/default/A.java", reference["left_source_key"])
        self.assertEqual("5", reference["left_begin"])
        self.assertEqual("20", reference["bcb_f1"])
        self.assertEqual("A.java", sources["left"].name)
        self.assertEqual("// A 1", sources["left"].read_text().splitlines()[0])

    def test_diagnostic_sampling_interleaves_functionalities_deterministically(self):
        rows = [self.query_row("4", str(index), str(index + 100)) for index in range(1, 5)]
        rows += [self.query_row("5", str(index), str(index + 200)) for index in range(5, 9)]

        first = MODULE.balanced_sample(rows, 2, 123, "T1")
        second = MODULE.balanced_sample(rows, 2, 123, "T1")

        self.assertEqual(first, second)
        self.assertEqual({"4", "5"}, {row["FUNCTIONALITY_ID"] for row in first})

    def test_h2_filename_is_normalized_to_jdbc_database_base(self):
        self.assertEqual(
            (self.root / "official").resolve(),
            MODULE.jdbc_database_base(self.root / "official.h2.db"),
        )

    def test_extract_writes_only_manifests_and_points_to_complete_official_files(self):
        db = self.root / "official.h2.db"
        h2 = self.root / "h2.jar"
        db.write_bytes(b"official-db")
        h2.write_bytes(b"official-h2")
        out = self.root / "cleanroom"

        def fake_export(_db, _h2, _sql, target):
            target.parent.mkdir(parents=True, exist_ok=True)
            row = self.query_row()
            with target.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(row), lineterminator="\n")
                writer.writeheader()
                writer.writerow(row)

        args = Namespace(
            db=db, h2=h2, bcb=self.bcb, out=out,
            bcb_release="BCB-v2", ijadataset_release="IJaDataset-BCEvalVersion",
            per_stratum=0, seed=20260721,
        )
        with patch.object(MODULE, "csv_export", side_effect=fake_export), \
             patch.object(MODULE, "git_value", side_effect=lambda _root, *git_args:
                          "a" * 40 if git_args[:2] == ("rev-parse", "HEAD") else ""):
            MODULE.extract(args)

        self.assertFalse(list(out.rglob("*.java")))
        with (out / "executions.csv").open(newline="", encoding="utf-8") as stream:
            execution = next(csv.DictReader(stream))
        left = (out / execution["left_path"]).resolve()
        self.assertEqual((self.bcb / "4/default/A.java").resolve(), left)
        with (out / "references.csv").open(newline="", encoding="utf-8") as stream:
            self.assertEqual(6, sum(1 for _ in csv.DictReader(stream)))


if __name__ == "__main__":
    unittest.main()
