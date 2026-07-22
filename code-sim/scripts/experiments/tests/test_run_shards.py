import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "run_shards.py"
SPEC = importlib.util.spec_from_file_location("run_shards", SCRIPT)
RUNNER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUNNER
SPEC.loader.exec_module(RUNNER)


class RunShardsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.manifest = self.root / "dataset" / "manifest.csv"
        self.pairs = self.manifest.parent / "pairs"
        self.pairs.mkdir(parents=True)
        rows = []
        for index in range(3):
            directory = self.pairs / f"p{index}"
            directory.mkdir()
            left = directory / "Left.java"
            right = directory / "Right.java"
            left.write_text("left\n", encoding="utf-8")
            right.write_text("right\n", encoding="utf-8")
            rows.append({
                "pair_id": f"p{index}",
                "left_path": f"pairs/p{index}/Left.java",
                "right_path": f"pairs/p{index}/Right.java",
            })
        with self.manifest.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)

    def tearDown(self):
        self.temp.cleanup()

    def test_split_rebases_relative_paths_and_preserves_all_rows(self):
        out = self.root / "run"
        out.mkdir()
        shards = RUNNER.split_manifest(self.manifest, out, 2)

        pair_ids = []
        for shard in shards:
            with shard.open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            for row in rows:
                pair_ids.append(row["pair_id"])
                self.assertTrue((shard.parent / row["left_path"]).resolve().is_file())
                self.assertTrue((shard.parent / row["right_path"]).resolve().is_file())
        self.assertEqual(["p0", "p1", "p2"], sorted(pair_ids))

    def test_existing_output_directory_rejects_config_drift(self):
        path = self.root / "run_config.json"
        RUNNER.lock_config(path, {"manifest_sha256": "one", "shards": 2})
        RUNNER.lock_config(path, {"manifest_sha256": "one", "shards": 2})
        self.assertEqual("one", json.loads(path.read_text())["manifest_sha256"])

        with self.assertRaisesRegex(ValueError, "configuration differs"):
            RUNNER.lock_config(path, {"manifest_sha256": "two", "shards": 2})


if __name__ == "__main__":
    unittest.main()
