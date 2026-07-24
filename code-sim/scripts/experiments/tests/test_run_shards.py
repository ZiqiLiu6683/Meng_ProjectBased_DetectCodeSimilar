import csv
import importlib.util
import json
import os
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


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

    def test_runtime_classpath_hash_changes_with_compiled_bytecode(self):
        classes = self.root / "target" / "classes"
        dependency = self.root / "deps" / "api.jar"
        classes.mkdir(parents=True)
        dependency.parent.mkdir()
        bytecode = classes / "Example.class"
        bytecode.write_bytes(b"first")
        dependency.write_bytes(b"dependency")
        classpath = os.pathsep.join([str(classes), str(dependency)])

        entries, first = RUNNER.runtime_classpath_lock(classpath)
        bytecode.write_bytes(b"second")
        _, second = RUNNER.runtime_classpath_lock(classpath)

        self.assertEqual(2, len(entries))
        self.assertNotEqual(first, second)

    def test_split_rebases_optional_project_and_classpath_context(self):
        context_manifest = self.manifest.parent / "context.csv"
        project = self.root / "projects" / "left"
        classes = project / "target" / "classes"
        dependency = self.root / "dependencies" / "api.jar"
        classes.mkdir(parents=True)
        dependency.parent.mkdir(parents=True)
        dependency.touch()
        row = {
            "pair_id": "context", "left_path": "pairs/p0/Left.java",
            "right_path": "pairs/p0/Right.java",
            "left_project_root": os.path.relpath(project, context_manifest.parent),
            "right_project_root": "",
            "left_classpath": os.pathsep.join([
                os.path.relpath(classes, context_manifest.parent),
                os.path.relpath(dependency, context_manifest.parent),
            ]),
            "right_classpath": "",
        }
        with context_manifest.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(row), lineterminator="\n")
            writer.writeheader()
            writer.writerow(row)
        out = self.root / "context-run"
        out.mkdir()

        shard = RUNNER.split_manifest(context_manifest, out, 1)[0]
        with shard.open(newline="", encoding="utf-8") as stream:
            rebased = next(csv.DictReader(stream))

        self.assertEqual(project.resolve(), (out / rebased["left_project_root"]).resolve())
        entries = [(out / value).resolve()
                   for value in rebased["left_classpath"].split(os.pathsep)]
        self.assertEqual([classes.resolve(), dependency.resolve()], entries)

    def test_primary_controls_reach_every_worker_jvm(self):
        shard = self.root / "shard.csv"
        shard.write_text("pair_id,left_path,right_path\n", encoding="utf-8")
        args = Namespace(java="java", xmx="6g", skip_dynamic=True,
                         disable_stubs=True, java_opt=[], max_attempts=1, limit=0)
        config = {
            "config_id": "primary", "dataset_id": "dataset",
            "code_commit": "abc", "dirty_worktree": "false",
            "manifest_sha256": "123",
            "runtime_classpath_sha256": "a" * 64,
        }
        with patch.object(RUNNER.subprocess, "run",
                          return_value=SimpleNamespace(returncode=0)) as run:
            RUNNER.run_shard(shard, args, self.root, "classes:deps", config)

        command = run.call_args.args[0]
        self.assertIn("-Dcodesim.skipDynamic=true", command)
        self.assertIn("-Dcodesim.disableStubs=true", command)
        self.assertIn(f"-Dcodesim.runtimeClasspathSha256={'a' * 64}", command)


if __name__ == "__main__":
    unittest.main()
