#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from token_usage_utils import extract_token_usage_from_text


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extract token usage metrics from a delegate log file")
    parser.add_argument("log_file", help="Path to delegate log file")
    parser.add_argument("--agent", help="Agent name (claude|codex|opencode|pi)")
    parser.add_argument("--json-out", help="Optional output path for JSON result")
    parser.add_argument("--quiet", action="store_true", help="Do not print summary text")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    log_path = Path(args.log_file)

    if not log_path.exists():
        print(f"log file does not exist: {log_path}", file=sys.stderr)
        sys.exit(1)

    text = log_path.read_text(encoding="utf-8", errors="replace")
    metrics, evidence = extract_token_usage_from_text(text)

    payload = {
        "agent": args.agent,
        "log_file": str(log_path),
        "metrics": metrics,
        "found_any": bool(metrics),
        "evidence": evidence,
    }

    if args.json_out:
        out_path = Path(args.json_out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

    if not args.quiet:
        if metrics:
            print("TOKEN_USAGE_FOUND: yes")
            for key in sorted(metrics):
                print(f"TOKEN_{key.upper()}: {metrics[key]}")
        else:
            print("TOKEN_USAGE_FOUND: no")

    print(json.dumps(payload, sort_keys=True))


if __name__ == "__main__":
    main()
