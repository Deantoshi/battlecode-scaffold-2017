#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: open_pi_tui_for_delegation.sh <agent> <src_folder>" >&2
  exit 1
fi

agent="$1"
src_folder="$2"

case "$agent" in
  claude|opencode|codex|pi)
    ;;
  *)
    echo "agent must be one of: claude, opencode, codex, pi" >&2
    exit 1
    ;;
esac

if [[ ! "$src_folder" =~ ^[a-z][a-z0-9_]*$ ]]; then
  echo "src_folder must match ^[a-z][a-z0-9_]*$ (lowercase Java package-style folder name)" >&2
  exit 1
fi

if ! command -v pi >/dev/null 2>&1; then
  echo "Error: 'pi' CLI not found in PATH" >&2
  exit 1
fi

mkdir -p "src/${src_folder}"

echo "Opening Pi TUI and starting skill workflow..."
echo "Skill: /skill:battlecode-cli-delegator ${agent} ${src_folder}"

exec pi "/skill:battlecode-cli-delegator ${agent} ${src_folder}"
