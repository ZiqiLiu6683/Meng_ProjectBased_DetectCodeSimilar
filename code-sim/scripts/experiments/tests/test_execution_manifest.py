import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "execution_manifest.py"
SPEC = importlib.util.spec_from_file_location("execution_manifest_tested", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ExecutionManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.left = self.root / "Left.java"
        self.right = self.root / "Right.java"
        self.left.write_text("class Left {}\n", encoding="utf-8")
        self.right.write_text("class Right {}\n", encoding="utf-8")
        self.manifest = self.root / "executions.csv"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, left_hash=None):
        row = {
            "schema_version": MODULE.SCHEMA_VERSION,
            "dataset_id": "official-test",
            "pair_id": "exec_1",
            "left_path": "Left.java",
            "right_path": "Right.java",
            "left_sha256": left_hash or MODULE.sha256_file(self.left),
            "right_sha256": MODULE.sha256_file(self.right),
        }
        with self.manifest.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=MODULE.FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerow(row)

    def test_validates_label_free_manifest_and_source_hashes(self):
        self.write()
        rows, dataset_id = MODULE.validate_manifest(self.manifest)
        self.assertEqual("official-test", dataset_id)
        self.assertNotIn("expected_type", rows[0])

    def test_rejects_source_hash_drift(self):
        self.write("0" * 64)
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            MODULE.validate_manifest(self.manifest)

    def write_with_roots(self, left_root, right_root):
        row = {
            "schema_version": MODULE.SCHEMA_VERSION,
            "dataset_id": "official-test",
            "pair_id": "exec_1",
            "left_path": "Left.java",
            "right_path": "Right.java",
            "left_sha256": MODULE.sha256_file(self.left),
            "right_sha256": MODULE.sha256_file(self.right),
            "left_project_root": left_root,
            "right_project_root": right_root,
        }
        fields = MODULE.FIELDS + MODULE.OPTIONAL_FIELDS
        with self.manifest.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            writer.writerow(row)

    def test_accepts_project_roots_that_contain_their_source(self):
        self.write_with_roots(".", ".")
        rows, _ = MODULE.validate_manifest(self.manifest)
        self.assertEqual(".", rows[0]["left_project_root"])

    def test_rejects_project_root_that_does_not_contain_its_source(self):
        elsewhere = self.root / "elsewhere"
        elsewhere.mkdir()
        self.write_with_roots("elsewhere", ".")
        with self.assertRaisesRegex(ValueError, "outside its project root"):
            MODULE.validate_manifest(self.manifest)

    def test_rejects_missing_project_root_directory(self):
        self.write_with_roots("no-such-dir", ".")
        with self.assertRaises(NotADirectoryError):
            MODULE.validate_manifest(self.manifest)

    def test_still_accepts_manifests_frozen_before_the_project_root_columns(self):
        self.write()
        with self.manifest.open(encoding="utf-8") as stream:
            text = stream.read().replace(MODULE.SCHEMA_VERSION, "execution-1.0")
        self.manifest.write_text(text, encoding="utf-8")
        rows, _ = MODULE.validate_manifest(self.manifest)
        self.assertEqual("execution-1.0", rows[0]["schema_version"])


if __name__ == "__main__":
    unittest.main()
