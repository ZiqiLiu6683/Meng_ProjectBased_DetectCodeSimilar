#!/usr/bin/env python3
import csv
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "target" / "robustness" / "medium" / "sources"
CLASS_ROOT = ROOT / "target" / "robustness" / "medium" / "classes"
REPORT = ROOT / "target" / "robustness" / "medium" / "build_report.csv"


def supported_release(release):
    result = subprocess.run(
        ["javac", "--release", str(release), "-version"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def variants():
    items = [
        ("javac-g", ["javac", "-g"]),
        ("javac-g-none", ["javac", "-g:none"]),
    ]
    for release in (8, 11, 17):
        if supported_release(release):
            items.append((f"javac-release-{release}", ["javac", "--release", str(release)]))
    return items


def source_files(project_dir):
    files = []
    for src_dir in sorted(project_dir.glob("**/src/main/java")):
        if not src_dir.is_dir():
            continue
        for path in sorted(src_dir.rglob("*.java")):
            if path.name == "module-info.java":
                continue
            files.append(path)
    return files


def compile_variant(project, files, variant_name, javac_prefix):
    output_dir = CLASS_ROOT / project / variant_name
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    argfile = output_dir / "sources.argfile"
    argfile.write_text("\n".join(str(path) for path in files), encoding="utf-8")
    command = javac_prefix + ["-d", str(output_dir), f"@{argfile}"]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    status = "success" if result.returncode == 0 else "failed"
    (output_dir / "javac.stdout.log").write_text(result.stdout, encoding="utf-8")
    (output_dir / "javac.stderr.log").write_text(result.stderr, encoding="utf-8")
    return {
        "project": project,
        "variant": variant_name,
        "status": status,
        "source_files": len(files),
        "output_dir": str(output_dir),
        "error_log": str(output_dir / "javac.stderr.log"),
    }


def main():
    if not SOURCE_ROOT.exists():
        print(f"Missing source root: {SOURCE_ROOT}", file=sys.stderr)
        return 2
    CLASS_ROOT.mkdir(parents=True, exist_ok=True)
    rows = []
    for project_dir in sorted(path for path in SOURCE_ROOT.iterdir() if path.is_dir()):
        files = source_files(project_dir)
        if not files:
            rows.append({
                "project": project_dir.name,
                "variant": "",
                "status": "no_sources",
                "source_files": 0,
                "output_dir": "",
                "error_log": "",
            })
            continue
        for variant_name, javac_prefix in variants():
            rows.append(compile_variant(project_dir.name, files, variant_name, javac_prefix))
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    with REPORT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["project", "variant", "status", "source_files", "output_dir", "error_log"],
        )
        writer.writeheader()
        writer.writerows(rows)
    success_count = sum(1 for row in rows if row["status"] == "success")
    print(f"Wrote build report to {REPORT}")
    print(f"Successful project variants: {success_count}/{len(rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
