#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF' >&2
Usage: run_delegation_with_watch.sh <agent> <src_folder> [extra_feedback_file]

Runs delegated CLI command and polls output for completion marker.
Also extracts token usage metrics (if present) from the run log.

Environment variables:
  POLL_SECONDS   Poll interval in seconds (default: 5)
  MAX_SECONDS    Max runtime before timeout/kill; 0 = unlimited (default: 0)
  TAIL_LINES     Number of tail lines shown each poll (default: 2)

Completion marker:
  FINAL_STATUS: SUCCESS
EOF
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 1
fi

agent="$1"
src_folder="$2"
extra_file="${3:-}"

poll_seconds="${POLL_SECONDS:-5}"
max_seconds="${MAX_SECONDS:-0}"
tail_lines="${TAIL_LINES:-2}"

if [[ ! "$poll_seconds" =~ ^[0-9]+$ ]] || [[ "$poll_seconds" -lt 1 ]]; then
  echo "POLL_SECONDS must be an integer >= 1" >&2
  exit 1
fi

if [[ ! "$max_seconds" =~ ^[0-9]+$ ]] || [[ "$max_seconds" -lt 0 ]]; then
  echo "MAX_SECONDS must be an integer >= 0" >&2
  exit 1
fi

if [[ ! "$tail_lines" =~ ^[0-9]+$ ]] || [[ "$tail_lines" -lt 1 ]]; then
  echo "TAIL_LINES must be an integer >= 1" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime_dir="${script_dir}/../runtime"
mkdir -p "$runtime_dir"

timestamp="$(date +%Y%m%d_%H%M%S)"
log_file="${runtime_dir}/delegation_${agent}_${src_folder}_${timestamp}.log"
token_file="${runtime_dir}/tokens_${agent}_${src_folder}_${timestamp}.json"

echo "[watch] preparing src/copy_bot for this run..."
python3 "${script_dir}/prepare_copy_bot.py" "$src_folder"

if [[ -n "$extra_file" ]]; then
  delegate_cmd="$(python3 "${script_dir}/build_delegation_command.py" "$agent" "$src_folder" --extra-file "$extra_file")"
else
  delegate_cmd="$(python3 "${script_dir}/build_delegation_command.py" "$agent" "$src_folder")"
fi

extract_tokens() {
  local extractor
  extractor="${script_dir}/extract_token_usage.py"

  if [[ ! -f "$extractor" ]]; then
    echo "TOKEN_USAGE_FOUND: unknown (extract_token_usage.py missing)"
    return 0
  fi

  local tmp_out tmp_err
  tmp_out="${token_file}.extract.stdout"
  tmp_err="${token_file}.extract.stderr"

  if python3 "$extractor" "$log_file" --agent "$agent" --json-out "$token_file" --quiet >"$tmp_out" 2>"$tmp_err"; then
    echo "TOKEN_FILE: ${token_file}"
    python3 - "$token_file" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.exists():
    print("TOKEN_USAGE_FOUND: no")
    raise SystemExit(0)

payload = json.loads(path.read_text(encoding="utf-8", errors="replace"))
metrics = payload.get("metrics") or {}
if metrics:
    print("TOKEN_USAGE_FOUND: yes")
    for key in sorted(metrics):
        print(f"TOKEN_{key.upper()}: {metrics[key]}")
else:
    print("TOKEN_USAGE_FOUND: no")
PY
  else
    echo "TOKEN_USAGE_FOUND: unknown (extraction failed)"
    if [[ -s "$tmp_err" ]]; then
      echo "TOKEN_EXTRACT_ERROR_FILE: $tmp_err"
    fi
  fi

  rm -f "$tmp_out" "$tmp_err"
}

echo "[watch] starting delegated run"
echo "[watch] poll interval: ${poll_seconds}s"
echo "[watch] max seconds: ${max_seconds} (0 = unlimited)"
echo "[watch] completion marker: FINAL_STATUS: SUCCESS"
echo "[watch] log: ${log_file}"

bash -lc "$delegate_cmd" >"$log_file" 2>&1 &
pid=$!

start_epoch="$(date +%s)"
marker_seen=0
timed_out=0

while kill -0 "$pid" 2>/dev/null; do
  if grep -q "FINAL_STATUS: SUCCESS" "$log_file"; then
    marker_seen=1
    echo "[watch] completion marker detected; waiting for delegate process to exit..."
    break
  fi

  now="$(date +%s)"
  elapsed=$((now - start_epoch))

  if [[ "$max_seconds" -gt 0 && "$elapsed" -ge "$max_seconds" ]]; then
    echo "[watch] timeout after ${elapsed}s. stopping process ${pid}."
    kill "$pid" 2>/dev/null || true
    timed_out=1
    break
  fi

  latest="$(tail -n "$tail_lines" "$log_file" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | sed 's/^ //; s/ $//')"
  if [[ -z "$latest" ]]; then
    latest="<no output yet>"
  fi

  echo "[watch] elapsed=${elapsed}s marker=no tail=${latest}"
  sleep "$poll_seconds"
done

set +e
wait "$pid"
wait_code=$?
set -e

if [[ "$timed_out" -eq 1 ]]; then
  exit_code=124
else
  exit_code="$wait_code"
fi

if grep -q "FINAL_STATUS: SUCCESS" "$log_file"; then
  marker_seen=1
fi

extract_tokens

if [[ "$timed_out" -eq 1 ]]; then
  echo "RESULT: TIMEOUT"
  echo "LOG_FILE: ${log_file}"
  echo "MARKER_SEEN: ${marker_seen}"
  echo "---- tail (40) ----"
  tail -n 40 "$log_file"
  exit 124
fi

if [[ "$exit_code" -eq 0 && "$marker_seen" -eq 1 ]]; then
  echo "RESULT: SUCCESS"
  echo "LOG_FILE: ${log_file}"
  echo "---- tail (${tail_lines}) ----"
  tail -n "$tail_lines" "$log_file"
  exit 0
fi

echo "RESULT: FAILED"
echo "EXIT_CODE: ${exit_code}"
echo "MARKER_SEEN: ${marker_seen}"
echo "LOG_FILE: ${log_file}"
echo "---- tail (40) ----"
tail -n 40 "$log_file"

if [[ "$exit_code" -ne 0 ]]; then
  exit "$exit_code"
fi

exit 1
