#!/usr/bin/env python3
"""Create a clean-room BCB pairwise-region benchmark from official inputs.

Unlike the legacy extractor, this generator never cuts, wraps, imports, copies,
or rewrites Java source.  The product receives the complete original files from
IJaDataset.  BCB method ranges are stored only in a separate reference table.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))
import execution_manifest


REFERENCE_SCHEMA_VERSION = "bcb-reference-1.0"
LOCK_SCHEMA_VERSION = "bcb-cleanroom-lock-1.0"
SIZE_FILTER = "c.MIN_SIZE >= 6 AND c.MIN_PRETTY_SIZE >= 6 AND c.MIN_TOKENS >= 50"
BANDS = [("VST3", 0.90, 1.00), ("ST3", 0.70, 0.90), ("MT3", 0.50, 0.70)]
REFERENCE_FIELDS = [
    "schema_version", "dataset_id", "reference_id", "execution_id",
    "expected_type", "band", "cluster_id", "functionality_id", "sim_both",
    "bcb_f1", "bcb_f2", "left_begin", "left_end", "right_begin", "right_end",
    "left_source_key", "right_source_key",
]


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def git_value(root: Path, *args: str) -> str:
    completed = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)
    if completed.returncode != 0:
        return "unknown"
    return completed.stdout.strip()


def portable_path(path: Path, manifest: Path) -> str:
    return Path(os.path.relpath(path.resolve(), manifest.parent.resolve())).as_posix()


def database_file(db_base: Path) -> Path:
    candidates = [db_base, Path(str(db_base) + ".mv.db"), Path(str(db_base) + ".h2.db")]
    files = [candidate.resolve() for candidate in candidates if candidate.is_file()]
    if len(files) != 1:
        raise FileNotFoundError(
            f"expected exactly one H2 database file for {db_base}; found {files}")
    return files[0]


def jdbc_database_base(db: Path) -> Path:
    text = str(db.resolve())
    for suffix in (".h2.db", ".mv.db"):
        if text.endswith(suffix):
            return Path(text[:-len(suffix)])
    return db.resolve()


def run_h2(db: Path, h2_jar: Path, sql: str) -> str:
    db_url = f"jdbc:h2:{str(jdbc_database_base(db)).replace(chr(92), '/')};IFEXISTS=TRUE"
    command = [
        "java", "-Xmx4g", "-cp", str(h2_jar.resolve()), "org.h2.tools.Shell",
        "-url", db_url, "-user", "sa", "-password", "", "-sql", sql,
    ]
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(f"H2 query failed: {detail[:3000]}")
    return completed.stdout


def csv_export(db: Path, h2_jar: Path, select_sql: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    target_text = str(target.resolve()).replace(chr(92), "/").replace("'", "''")
    inner = select_sql.replace("'", "''")
    run_h2(db, h2_jar, f"CALL CSVWRITE('{target_text}', '{inner}');")
    if not target.is_file():
        raise RuntimeError(f"H2 CSVWRITE produced no file: {target}")


def category_queries() -> list[tuple[str, str, str]]:
    categories = [
        ("T1", "T1", f"c.SYNTACTIC_TYPE = 1 AND {SIZE_FILTER}"),
        ("T2", "T2", f"c.SYNTACTIC_TYPE = 2 AND {SIZE_FILTER}"),
    ]
    for band, lower, upper in BANDS:
        categories.append((
            band,
            "T3",
            f"c.SYNTACTIC_TYPE = 3 AND {SIZE_FILTER} "
            f"AND LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) >= {lower} "
            f"AND LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) < {upper}",
        ))
    categories.append(("NEG", "NON_CLONE", "1 = 1"))
    return categories


def select_sql(table: str, where: str) -> str:
    return (
        "SELECT c.FUNCTION_ID_ONE, c.FUNCTION_ID_TWO, c.FUNCTIONALITY_ID, "
        "LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) AS SIM_BOTH, "
        "f1.NAME AS NAME1, f1.TYPE AS TYPE1, f1.STARTLINE AS S1, f1.ENDLINE AS E1, "
        "f2.NAME AS NAME2, f2.TYPE AS TYPE2, f2.STARTLINE AS S2, f2.ENDLINE AS E2 "
        f"FROM {table} c "
        "JOIN FUNCTIONS f1 ON c.FUNCTION_ID_ONE = f1.ID "
        "JOIN FUNCTIONS f2 ON c.FUNCTION_ID_TWO = f2.ID "
        f"WHERE {where} "
        "ORDER BY c.FUNCTIONALITY_ID, c.FUNCTION_ID_ONE, c.FUNCTION_ID_TWO"
    )


def read_query_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as stream:
        return [
            {key.upper(): (value or "").strip() for key, value in row.items()}
            for row in csv.DictReader(stream)
        ]


def balanced_sample(rows: list[dict[str, str]], wanted: int, seed: int,
                    stratum: str) -> list[dict[str, str]]:
    """Deterministic functionality-interleaved diagnostic sample; 0 means all."""
    if wanted < 0:
        raise ValueError("--per-stratum must be >= 0")
    if wanted == 0 or wanted >= len(rows):
        return sorted(rows, key=lambda row: (
            row["FUNCTIONALITY_ID"], int(row["FUNCTION_ID_ONE"]),
            int(row["FUNCTION_ID_TWO"])))
    groups: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        groups[row["FUNCTIONALITY_ID"]].append(row)
    for functionality, group in groups.items():
        group.sort(key=lambda row: (
            sha256_text(
                f"{seed}:{stratum}:{functionality}:{row['FUNCTION_ID_ONE']}:"
                f"{row['FUNCTION_ID_TWO']}"),
            int(row["FUNCTION_ID_ONE"]), int(row["FUNCTION_ID_TWO"])))
    functionality_order = sorted(
        groups,
        key=lambda value: (sha256_text(f"{seed}:{stratum}:{value}"), value),
    )
    selected: list[dict[str, str]] = []
    round_index = 0
    while len(selected) < wanted:
        added = False
        for functionality in functionality_order:
            group = groups[functionality]
            if round_index < len(group):
                selected.append(group[round_index])
                added = True
                if len(selected) == wanted:
                    break
        if not added:
            break
        round_index += 1
    return selected


def source_key(row: dict[str, str], side: int) -> str:
    return Path(row["FUNCTIONALITY_ID"], row[f"TYPE{side}"], row[f"NAME{side}"]).as_posix()


def resolve_source(bcb_root: Path, key: str) -> Path:
    root = bcb_root.resolve()
    source = (root / key).resolve()
    if source != root and root not in source.parents:
        raise ValueError(f"source path escapes IJaDataset root: {key}")
    if not source.is_file():
        raise FileNotFoundError(f"official source file is missing: {source}")
    return source


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8", errors="replace").splitlines())


def normalize_reference(row: dict[str, str], category: str, expected_type: str,
                        bcb_root: Path, line_count_cache: dict[Path, int] | None = None
                        ) -> tuple[dict[str, str], dict[str, Path]]:
    left_key = source_key(row, 1)
    right_key = source_key(row, 2)
    left_fid, right_fid = row["FUNCTION_ID_ONE"], row["FUNCTION_ID_TWO"]
    left_begin, left_end = int(row["S1"]), int(row["E1"])
    right_begin, right_end = int(row["S2"]), int(row["E2"])
    if right_key < left_key:
        left_key, right_key = right_key, left_key
        left_fid, right_fid = right_fid, left_fid
        left_begin, right_begin = right_begin, left_begin
        left_end, right_end = right_end, left_end

    left_path = resolve_source(bcb_root, left_key)
    right_path = resolve_source(bcb_root, right_key)
    if line_count_cache is None:
        line_count_cache = {}
    for side, begin, end, path in (
        ("left", left_begin, left_end, left_path),
        ("right", right_begin, right_end, right_path),
    ):
        if path not in line_count_cache:
            line_count_cache[path] = line_count(path)
        lines = line_count_cache[path]
        if begin < 1 or end < begin or end > lines:
            raise ValueError(
                f"{left_fid}/{right_fid}: invalid {side} range {begin}..{end} "
                f"for {path} ({lines} lines)")

    execution_id = "exec_" + sha256_text(left_key + "\0" + right_key)[:24]
    reference_material = (
        f"{category}\0{left_fid}\0{right_fid}\0{left_key}\0{right_key}\0"
        f"{left_begin}\0{left_end}\0{right_begin}\0{right_end}"
    )
    reference = {
        "schema_version": REFERENCE_SCHEMA_VERSION,
        "dataset_id": "",  # filled after dataset identity is frozen
        "reference_id": "ref_" + sha256_text(reference_material)[:24],
        "execution_id": execution_id,
        "expected_type": expected_type,
        "band": category if expected_type == "T3" else "",
        "cluster_id": row["FUNCTIONALITY_ID"],
        "functionality_id": row["FUNCTIONALITY_ID"],
        "sim_both": row.get("SIM_BOTH", ""),
        "bcb_f1": left_fid,
        "bcb_f2": right_fid,
        "left_begin": str(left_begin),
        "left_end": str(left_end),
        "right_begin": str(right_begin),
        "right_end": str(right_end),
        "left_source_key": left_key,
        "right_source_key": right_key,
    }
    return reference, {"left": left_path, "right": right_path}


def write_csv(path: Path, fields: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def ensure_new_output(out: Path) -> None:
    if out.exists() and any(out.iterdir()):
        raise ValueError(f"clean-room output already exists and is not empty: {out}")
    out.mkdir(parents=True, exist_ok=True)


def extract(args: argparse.Namespace) -> None:
    root = Path(__file__).resolve().parents[3]
    commit = git_value(root, "rev-parse", "HEAD")
    dirty = bool(git_value(root, "status", "--porcelain"))
    if commit == "unknown" or dirty:
        raise ValueError("clean-room generation requires a known commit and clean worktree")
    out = args.out.resolve()
    ensure_new_output(out)
    query_dir = out / "query_exports"
    query_dir.mkdir()
    raw_population: Counter[str] = Counter()
    selected_population: Counter[str] = Counter()
    source_hash_cache: dict[Path, str] = {}
    source_line_count_cache: dict[Path, int] = {}
    source_keys_by_path: dict[Path, set[str]] = defaultdict(set)
    references: list[dict[str, str]] = []
    execution_sources: dict[str, dict[str, Path]] = {}

    for category, expected_type, where in category_queries():
        table = "FALSE_POSITIVES" if category == "NEG" else "CLONES"
        query_path = query_dir / f"{category}.csv"
        print(f"[bcb-cleanroom] exporting deterministic {category} query")
        csv_export(args.db, args.h2, select_sql(table, where), query_path)
        population = read_query_rows(query_path)
        raw_population[category] = len(population)
        selected = balanced_sample(population, args.per_stratum, args.seed, category)
        selected_population[category] = len(selected)
        for row in selected:
            reference, sources = normalize_reference(
                row, category, expected_type, args.bcb.resolve(), source_line_count_cache)
            references.append(reference)
            source_keys_by_path[sources["left"]].add(reference["left_source_key"])
            source_keys_by_path[sources["right"]].add(reference["right_source_key"])
            previous = execution_sources.setdefault(reference["execution_id"], sources)
            if previous != sources:
                raise ValueError(f"execution ID collision: {reference['execution_id']}")

    if not references:
        raise ValueError("official queries produced no usable references")
    if len({row["reference_id"] for row in references}) != len(references):
        raise ValueError("duplicate official reference IDs were generated")

    dataset_material = json.dumps({
        "bcb_release": args.bcb_release,
        "ijadataset_release": args.ijadataset_release,
        "db_sha256": execution_manifest.sha256_file(database_file(args.db)),
        "selection_seed": args.seed,
        "per_stratum": args.per_stratum,
        "reference_ids": sorted(row["reference_id"] for row in references),
    }, sort_keys=True)
    dataset_id = "bcb-cleanroom-" + sha256_text(dataset_material)[:20]
    for reference in references:
        reference["dataset_id"] = dataset_id
    references.sort(key=lambda row: (
        row["band"] or row["expected_type"], row["functionality_id"],
        row["bcb_f1"], row["bcb_f2"], row["reference_id"]))

    executions_path = out / "executions.csv"
    references_path = out / "references.csv"
    executions: list[dict[str, str]] = []
    for execution_id in sorted(execution_sources):
        sources = execution_sources[execution_id]
        for path in sources.values():
            source_hash_cache.setdefault(path, execution_manifest.sha256_file(path))
        executions.append({
            "schema_version": execution_manifest.SCHEMA_VERSION,
            "dataset_id": dataset_id,
            "pair_id": execution_id,
            "left_path": portable_path(sources["left"], executions_path),
            "right_path": portable_path(sources["right"], executions_path),
            "left_sha256": source_hash_cache[sources["left"]],
            "right_sha256": source_hash_cache[sources["right"]],
        })
    write_csv(executions_path, execution_manifest.FIELDS, executions)
    write_csv(references_path, REFERENCE_FIELDS, references)
    execution_manifest.validate_manifest(executions_path, dataset_id)

    inventory = "\n".join(sorted(
        f"{source_key}:{source_hash_cache[path]}"
        for path, source_keys in source_keys_by_path.items()
        for source_key in source_keys))
    lock = {
        "schema_version": LOCK_SCHEMA_VERSION,
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_id": dataset_id,
        "bcb_release": args.bcb_release,
        "ijadataset_release": args.ijadataset_release,
        "h2_database_file": str(database_file(args.db)),
        "h2_database_sha256": execution_manifest.sha256_file(database_file(args.db)),
        "h2_jar_sha256": execution_manifest.sha256_file(args.h2.resolve()),
        "bcb_root": str(args.bcb.resolve()),
        "generator_commit": commit,
        "generator_dirty_worktree": dirty,
        "selection": {
            "mode": "all" if args.per_stratum == 0 else "diagnostic_functionality_interleaved",
            "seed": args.seed,
            "per_stratum": args.per_stratum,
        },
        "raw_population": dict(sorted(raw_population.items())),
        "selected_population": dict(sorted(selected_population.items())),
        "execution_count": len(executions),
        "reference_count": len(references),
        "functionality_count": len({row["functionality_id"] for row in references}),
        "source_file_count": len(source_hash_cache),
        "source_inventory_sha256": sha256_text(inventory),
        "executions_sha256": execution_manifest.sha256_file(executions_path),
        "references_sha256": execution_manifest.sha256_file(references_path),
        "query_export_sha256": {
            path.stem: execution_manifest.sha256_file(path)
            for path in sorted(query_dir.glob("*.csv"))
        },
        "source_policy": "complete_original_ijadataset_files_no_copy_no_rewrite",
        "reference_policy": "BCB method ranges are scoring-only and never product inputs",
    }
    (out / "dataset_lock.json").write_text(
        json.dumps(lock, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[bcb-cleanroom] dataset={dataset_id}")
    print(f"[bcb-cleanroom] {len(references)} references -> {len(executions)} file-pair executions")
    print(f"[bcb-cleanroom] lock={out / 'dataset_lock.json'}")


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True, type=Path,
                        help="H2 database base path (without .h2.db/.mv.db is accepted)")
    parser.add_argument("--h2", required=True, type=Path)
    parser.add_argument("--bcb", required=True, type=Path,
                        help="official IJaDataset bcb_reduced directory")
    parser.add_argument("--out", required=True, type=Path,
                        help="must be a new or empty directory")
    parser.add_argument("--bcb-release", required=True)
    parser.add_argument("--ijadataset-release", required=True)
    parser.add_argument("--per-stratum", type=int, default=0,
                        help="0 uses every eligible row; N creates a diagnostic smoke sample")
    parser.add_argument("--seed", type=int, default=20260721)
    return parser.parse_args(argv)


def main() -> None:
    try:
        extract(parse_args())
    except (OSError, RuntimeError, ValueError) as error:
        sys.exit(f"[bcb-cleanroom] ERROR: {error}")


if __name__ == "__main__":
    main()
