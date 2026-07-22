import csv
import importlib.util
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "manifest_v2.py"
SPEC = importlib.util.spec_from_file_location("manifest_v2", SCRIPT)
MANIFEST = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MANIFEST
SPEC.loader.exec_module(MANIFEST)


class ManifestV2Test(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.pairs = self.root / "pairs"
        self.labels = self.root / "labels.csv"
        rows = [
            ("T1_1", "T1", "", "f1"),
            ("T1_2", "T1", "", "f2"),
            ("MT3_1", "T3", "MT3", "f3"),
            ("NEG_1", "NON_CLONE", "", "f4"),
        ]
        with self.labels.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow(["pair_id", "expected_type", "band", "functionality_id"])
            writer.writerows(rows)
        for pair_id, _, _, _ in rows:
            directory = self.pairs / pair_id
            directory.mkdir(parents=True)
            for side in ("Left", "Right"):
                (directory / f"{side}Input.java").write_text(
                    "import java.util.*;\n\n"
                    f"public class {side}Input {{\n"
                    "int value() { return 1; }\n"
                    "}\n",
                    encoding="utf-8",
                )

    def tearDown(self):
        self.temp.cleanup()

    def build(self, out: Path, fraction: float = 1.0):
        MANIFEST.build(Namespace(
            labels=self.labels,
            pairs_root=self.pairs,
            out=out,
            dataset_id="test-dataset",
            fraction=fraction,
            seed=42,
            minimum_per_stratum=1,
        ))

    def test_builds_portable_valid_manifest_with_ranges_and_hashes(self):
        out = self.root / "manifest.csv"
        self.build(out)

        rows = MANIFEST.read_csv(out)
        self.assertEqual(4, len(rows))
        self.assertFalse(Path(rows[0]["left_path"]).is_absolute())
        self.assertEqual("4", rows[0]["left_begin"])
        self.assertEqual("4", rows[0]["left_end"])
        self.assertEqual(64, len(rows[0]["left_sha256"]))
        MANIFEST.validate_manifest(out, "test-dataset")

    def test_sampling_is_deterministic_and_keeps_each_stratum(self):
        first = self.root / "first.csv"
        second = self.root / "second.csv"
        self.build(first, fraction=0.25)
        self.build(second, fraction=0.25)

        first_ids = [row["pair_id"] for row in MANIFEST.read_csv(first)]
        second_ids = [row["pair_id"] for row in MANIFEST.read_csv(second)]
        self.assertEqual(first_ids, second_ids)
        self.assertEqual(3, len(first_ids))

    def test_validation_rejects_source_tampering(self):
        out = self.root / "manifest.csv"
        self.build(out)
        target = self.pairs / "T1_1" / "LeftInput.java"
        target.write_text("tampered\n", encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            MANIFEST.validate_manifest(out, "test-dataset")


if __name__ == "__main__":
    unittest.main()
