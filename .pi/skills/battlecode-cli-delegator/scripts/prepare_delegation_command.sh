#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: prepare_delegation_command.sh <agent> <src_folder> [extra_feedback_file]" >&2
  exit 1
fi

agent="$1"
src_folder="$2"
extra_file="${3:-}"

if [[ ! "$src_folder" =~ ^[a-z][a-z0-9_]*$ ]]; then
  echo "src_folder must match ^[a-z][a-z0-9_]*$ (lowercase Java package-style folder name)" >&2
  exit 1
fi

mkdir -p "src/${src_folder}"

if [[ -n "$extra_file" ]]; then
  python3 "$(dirname "$0")/build_delegation_command.py" "$agent" "$src_folder" --extra-file "$extra_file"
else
  python3 "$(dirname "$0")/build_delegation_command.py" "$agent" "$src_folder"
fi
