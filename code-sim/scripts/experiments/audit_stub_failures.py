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
import json
import re
import tarfile
from pathlib import Path


DIAGNOSTIC_CODE = re.compile(r"compiler\.(?:err|warn|note)\.[A-Za-z0-9_.]+")
DIAGNOSTIC_LINE = re.compile(r"(?:^|\|\s*)L(\d+)\s+compiler\.")


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


def render_report(failures: list[dict[str, object]], cache_root: Path,
                  stub_version: str, representatives_per_code: int) -> str:
    counts = collections.Counter(str(item["primary_code"]) for item in failures)
    lines = [
        "# Stub compilation failure audit",
        "",
        f"Cache root: `{cache_root.resolve()}`  ",
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
                entry = item["entry"]
                assert isinstance(entry, Path)
                for stub in stubs:
                    lines.append(f"- `{stub.relative_to(entry)}`")
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
            assert isinstance(entry, Path)
            prefix = Path("entries") / entry.name
            for relative in ("compilation.properties", "source", "stub-sources"):
                artifact = entry / relative
                if artifact.exists():
                    bundle.add(artifact, arcname=str(prefix / relative))


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache-root", required=True, type=Path)
    parser.add_argument("--stub-version", default="3")
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--representatives-per-code", type=int, default=1)
    return parser.parse_args(argv)


def main() -> None:
    args = parse_args()
    failures = collect(args.cache_root, args.stub_version)
    if not failures:
        raise SystemExit(
            f"no failed cache entries found for stub generator version {args.stub_version}")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(render_report(
        failures, args.cache_root, args.stub_version, args.representatives_per_code),
        encoding="utf-8")
    if args.bundle:
        write_bundle(failures, args.report, args.bundle)
    print(f"[stub-audit] failures={len(failures)} report={args.report}")
    if args.bundle:
        print(f"[stub-audit] private_bundle={args.bundle}")


if __name__ == "__main__":
    main()
