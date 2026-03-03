#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from opponent_utils import prepare_copy_bot, project_root_from_script


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create src/copy_bot from src/<src_folder> (or fallback src/examplefuncsplayer)")
    parser.add_argument("src_folder", help="source bot folder under src/")
    parser.add_argument("--json", action="store_true", help="print JSON payload")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_root = project_root_from_script(Path(__file__))

    try:
        meta = prepare_copy_bot(project_root, args.src_folder)
    except Exception as exc:  # noqa: BLE001
        print(f"copy_bot preparation failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)

    if args.json:
        print(json.dumps(meta, indent=2, sort_keys=True))
        return

    print(f"COPY_BOT_SOURCE: src/{meta['source_folder']}")
    print(f"COPY_BOT_SOURCE_KIND: {meta['source_kind']}")
    print(f"COPY_BOT_TARGET: src/{meta['target_folder']}")
    print(f"COPY_BOT_JAVA_FILES: {meta['java_file_count']}")
    print(f"COPY_BOT_REWRITTEN_FILES: {meta['java_files_rewritten']}")
    print(f"OPPONENTS: {','.join(meta['opponents'])}")


if __name__ == "__main__":
    main()
