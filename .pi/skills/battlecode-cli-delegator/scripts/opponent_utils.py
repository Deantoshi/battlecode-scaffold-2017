#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
from pathlib import Path

COPY_BOT_FOLDER = "copy_bot"
FALLBACK_FOLDER = "examplefuncsplayer"


def project_root_from_script(script_file: Path) -> Path:
    # <project>/.pi/skills/battlecode-cli-delegator/scripts/<file>
    return script_file.resolve().parent.parent.parent.parent.parent


def src_root(project_root: Path) -> Path:
    return project_root / "src"


def folder_has_java(folder: Path) -> bool:
    return folder.is_dir() and any(folder.rglob("*.java"))


def detect_champion_variants(project_root: Path, src_folder: str) -> list[str]:
    src_dir = src_root(project_root)
    if not src_dir.is_dir():
        return []

    pattern = re.compile(rf"^{re.escape(src_folder)}_champion_(\d+)$")
    champions: list[tuple[int, str]] = []

    for child in src_dir.iterdir():
        if not child.is_dir():
            continue
        match = pattern.fullmatch(child.name)
        if not match:
            continue
        champions.append((int(match.group(1)), child.name))

    champions.sort(key=lambda item: item[0])
    return [name for _, name in champions]


def detect_opponents(project_root: Path, src_folder: str) -> list[str]:
    return [COPY_BOT_FOLDER] + detect_champion_variants(project_root, src_folder)


def discover_java_packages(source_dir: Path) -> list[str]:
    packages: set[str] = set()
    package_re = re.compile(r"(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*)\s*;")

    for java_file in source_dir.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8", errors="replace")
        match = package_re.search(text)
        if match:
            packages.add(match.group(1))

    return sorted(packages)


def _rewrite_java_package(java_file: Path, source_packages: list[str], dest_pkg: str) -> bool:
    text = java_file.read_text(encoding="utf-8", errors="replace")
    updated = text

    # Force package declaration to copy_bot regardless of source package name.
    updated = re.sub(
        r"(?m)^(\s*package\s+)[A-Za-z_][A-Za-z0-9_]*(\s*;)",
        rf"\1{dest_pkg}\2",
        updated,
    )

    for source_pkg in source_packages:
        if source_pkg == dest_pkg:
            continue
        source_pkg_escaped = re.escape(source_pkg)

        # imports
        updated = re.sub(
            rf"(?m)^(\s*import\s+){source_pkg_escaped}\\.",
            rf"\1{dest_pkg}.",
            updated,
        )

        # fully-qualified references
        updated = re.sub(
            rf"(?<![A-Za-z0-9_]){source_pkg_escaped}\\.",
            f"{dest_pkg}.",
            updated,
        )

    if updated != text:
        java_file.write_text(updated, encoding="utf-8")
        return True

    return False


def prepare_copy_bot(project_root: Path, src_folder: str) -> dict:
    src_dir = src_root(project_root)
    if not src_dir.is_dir():
        raise RuntimeError(f"src directory not found: {src_dir}")

    preferred_dir = src_dir / src_folder
    fallback_dir = src_dir / FALLBACK_FOLDER

    if src_folder != COPY_BOT_FOLDER and folder_has_java(preferred_dir):
        source_dir = preferred_dir
        source_kind = "existing_src_folder"
    elif folder_has_java(fallback_dir):
        source_dir = fallback_dir
        source_kind = "fallback_examplefuncsplayer"
    else:
        raise RuntimeError(
            f"Neither src/{src_folder} nor src/{FALLBACK_FOLDER} contains Java files; cannot build src/{COPY_BOT_FOLDER}."
        )

    source_packages = discover_java_packages(source_dir)
    if source_dir.name not in source_packages:
        source_packages.append(source_dir.name)
    source_packages = sorted(set(source_packages))

    target_dir = src_dir / COPY_BOT_FOLDER
    if target_dir.exists():
        shutil.rmtree(target_dir)
    shutil.copytree(source_dir, target_dir)

    java_files = sorted(target_dir.rglob("*.java"))
    changed_files = 0
    for java_file in java_files:
        if _rewrite_java_package(java_file, source_packages, COPY_BOT_FOLDER):
            changed_files += 1

    return {
        "target_folder": COPY_BOT_FOLDER,
        "target_dir": str(target_dir),
        "source_folder": source_dir.name,
        "source_dir": str(source_dir),
        "source_kind": source_kind,
        "source_packages": source_packages,
        "java_file_count": len(java_files),
        "java_files_rewritten": changed_files,
        "opponents": detect_opponents(project_root, src_folder),
    }
