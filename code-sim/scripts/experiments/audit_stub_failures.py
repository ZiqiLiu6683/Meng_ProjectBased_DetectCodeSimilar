#!/usr/bin/env python3
"""Audit immutable Java compilation-cache failures without rerunning the product.

The compilation coordinator publishes the complete attempted source, generated
stub sources, and a negative ``compilation.properties`` manifest.  This tool
summarizes those artifacts and creates a private diagnostic bundle.  Benchmark
sources in the bundle must not be committed to the repository.
"""

from __future__ import annotations

import argparse
import collections
import csv
import json
import re
import tarfile
from pathlib import Path


DIAGNOSTIC_CODE = re.compile(r"compiler\.(?:err|warn|note)\.[A-Za-z0-9_.-]+")
DIAGNOSTIC_LINE = re.compile(r"(?:^|[^0-9A-Za-z])L(\d+)\s+compiler\.")


def unescape_property(value: str) -> str:
    """Decode the subset emitted by java.util.Properties.store."""
    def unicode_value(match: re.Match[str]) -> str:
        return chr(int(match.group(1), 16))

    value = re.sub(r"\\u([0-9A-Fa-f]{4})", unicode_value, value)
    replacements = {
        r"\t": "\t", r"\n": "\n", r"\r": "\r", r"\f": "\f",
        r"\=": "=", r"\:": ":", r"\ ": " ", r"\\": "\\",
    }
    for escaped, plain in replacements.items():
        value = value.replace(escaped, plain)
    return value


def read_properties(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            key, separator, value = line.partition(":")
        if separator:
            properties[unescape_property(key.strip())] = unescape_property(value.strip())
    return properties


def source_excerpt(path: Path, diagnostic: str, radius: int = 2) -> str:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    requested = sorted({int(value) for value in DIAGNOSTIC_LINE.findall(diagnostic)})
    if not requested:
        requested = [1]
    selected: set[int] = set()
    for line in requested[:5]:
        selected.update(range(max(1, line - radius), min(len(lines), line + radius) + 1))
    return "\n".join(f"{number:5d} | {lines[number - 1]}" for number in sorted(selected))


def collect(cache_root: Path, stub_version: str) -> list[dict[str, object]]:
    failures: list[dict[str, object]] = []
    entries = cache_root.resolve() / "entries"
    if not entries.is_dir():
        raise FileNotFoundError(f"compilation cache entries directory is missing: {entries}")
    for manifest in sorted(entries.glob("*/compilation.properties")):
        properties = read_properties(manifest)
        if properties.get("status") != "failed":
            continue
        if properties.get("stubGeneratorVersion") != stub_version:
            continue
        entry = manifest.parent
        sources = sorted((entry / "source").glob("*.java"))
        stubs = sorted((entry / "stub-sources").rglob("*.java"))
        diagnostic = properties.get("diagnosticSummary", "")
        code_match = DIAGNOSTIC_CODE.search(diagnostic)
        failures.append({
            "cache_key": entry.name,
            "entry": entry,
            "source": sources[0] if sources else None,
            "source_name": sources[0].name if sources else "(missing)",
            "stubs": stubs,
            "diagnostic": diagnostic,
            "primary_code": code_match.group(0) if code_match else "UNKNOWN",
        })
    return failures


def collect_from_results(results: Path, executions: Path) -> list[dict[str, object]]:
    """Resolve fallback sources from frozen run output when negative cache is unavailable."""
    with executions.resolve().open(newline="", encoding="utf-8-sig") as stream:
        manifest_rows = {row["pair_id"]: row for row in csv.DictReader(stream)}
    failures: list[dict[str, object]] = []
    with results.resolve().open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            row = json.loads(line)
            if row.get("analysisMode") != "SOURCE_ONLY_FALLBACK":
                continue
            pair_id = row.get("pairId", "")
            manifest = manifest_rows.get(pair_id)
            if manifest is None:
                raise ValueError(
                    f"results line {line_number} references unknown pairId: {pair_id}")
            stage = row.get("fallbackStage", "")
            if stage == "compile_left":
                side = "left"
            elif stage == "compile_right":
                side = "right"
            else:
                continue
            source = Path(manifest[f"{side}_path"])
            if not source.is_absolute():
                source = (executions.resolve().parent / source).resolve()
            if not source.is_file():
                raise FileNotFoundError(
                    f"official source for {pair_id}/{side} is missing: {source}")
            stage_outcome = row.get("stages", {}).get(stage, {})
            diagnostic = stage_outcome.get("detail", "") or row.get("fallbackReason", "")
            code_match = DIAGNOSTIC_CODE.search(diagnostic)
            failures.append({
                "cache_key": f"{pair_id}-{side}",
                "entry": None,
                "source": source,
                "source_name": source.name,
                "stubs": [],
                "diagnostic": diagnostic,
                "primary_code": code_match.group(0) if code_match else "UNKNOWN",
            })
    return failures


def render_report(failures: list[dict[str, object]], source_description: str,
                  stub_version: str, representatives_per_code: int) -> str:
    counts = collections.Counter(str(item["primary_code"]) for item in failures)
    lines = [
        "# Stub compilation failure audit",
        "",
        f"Audit source: `{source_description}`  ",
        f"Stub generator version: `{stub_version}`  ",
        f"Failed immutable entries: **{len(failures)}**",
        "",
        "## Primary final diagnostic distribution",
        "",
        "| Diagnostic | Entries |",
        "| --- | ---: |",
    ]
    lines.extend(f"| `{code}` | {count} |" for code, count in counts.most_common())
    lines.extend(["", "## All failed entries", "", "| Cache key | Source | Stubs | Primary diagnostic |",
                  "| --- | --- | ---: | --- |"])
    for item in failures:
        lines.append(
            f"| `{str(item['cache_key'])[:16]}` | `{item['source_name']}` | "
            f"{len(item['stubs'])} | `{item['primary_code']}` |")

    grouped: dict[str, list[dict[str, object]]] = collections.defaultdict(list)
    for failure in failures:
        grouped[str(failure["primary_code"])].append(failure)
    lines.extend(["", "## Representative exact failures"])
    for code, count in counts.most_common():
        for index, item in enumerate(grouped[code][:representatives_per_code], start=1):
            lines.extend([
                "", f"### `{code}` representative {index}/{min(count, representatives_per_code)}",
                "", f"Cache key: `{item['cache_key']}`  ",
                f"Source: `{item['source_name']}`  ",
                f"Generated stub source files: `{len(item['stubs'])}`",
                "", "Final diagnostics:", "", "```text", str(item["diagnostic"]), "```",
            ])
            source = item["source"]
            if isinstance(source, Path) and source.is_file():
                lines.extend(["", "Relevant source excerpt:", "", "```java",
                              source_excerpt(source, str(item["diagnostic"])), "```"])
            stubs = item["stubs"]
            if stubs:
                lines.extend(["", "Generated stub inventory:", ""])
                for stub in stubs:
                    entry = item["entry"]
                    lines.append(f"- `{stub.relative_to(entry) if isinstance(entry, Path) else stub}`")
    return "\n".join(lines) + "\n"


def write_bundle(failures: list[dict[str, object]], report: Path, archive: Path) -> None:
    archive.parent.mkdir(parents=True, exist_ok=True)
    index = [{
        "cache_key": item["cache_key"],
        "source_name": item["source_name"],
        "primary_code": item["primary_code"],
        "diagnostic": item["diagnostic"],
        "stub_count": len(item["stubs"]),
    } for item in failures]
    index_path = report.with_suffix(".json")
    index_path.write_text(json.dumps(index, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with tarfile.open(archive, "w:gz") as bundle:
        bundle.add(report, arcname="audit/report.md")
        bundle.add(index_path, arcname="audit/index.json")
        for item in failures:
            entry = item["entry"]
            prefix = Path("entries") / str(item["cache_key"])
            if isinstance(entry, Path):
                for relative in ("compilation.properties", "source", "stub-sources"):
                    artifact = entry / relative
                    if artifact.exists():
                        bundle.add(artifact, arcname=str(prefix / relative))
            else:
                source = item["source"]
                if isinstance(source, Path) and source.is_file():
                    bundle.add(source, arcname=str(prefix / "source" / source.name))


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--cache-root", type=Path)
    source.add_argument("--results", type=Path,
                        help="merged.jsonl from a frozen shard run")
    parser.add_argument("--executions", type=Path,
                        help="required with --results; frozen executions.csv")
    parser.add_argument("--stub-version", default="3")
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--representatives-per-code", type=int, default=1)
    return parser.parse_args(argv)


def main() -> None:
    args = parse_args()
    if args.results:
        if not args.executions:
            raise SystemExit("--executions is required with --results")
        failures = collect_from_results(args.results, args.executions)
        source_description = f"{args.results.resolve()} + {args.executions.resolve()}"
    else:
        failures = collect(args.cache_root, args.stub_version)
        source_description = str(args.cache_root.resolve())
    if not failures:
        raise SystemExit("no source-only compilation fallback entries were found")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(render_report(
        failures, source_description, args.stub_version, args.representatives_per_code),
        encoding="utf-8")
    if args.bundle:
        write_bundle(failures, args.report, args.bundle)
    print(f"[stub-audit] failures={len(failures)} report={args.report}")
    if args.bundle:
        print(f"[stub-audit] private_bundle={args.bundle}")


if __name__ == "__main__":
    main()
