#!/bin/bash
# run-all-variants.sh - Runs all variants + original against opponent
#
# Usage: ./scripts/run-all-variants.sh <bot> <opponent> <map>
#
# Creates match files and databases for analysis

set -e

BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-MagicWood}"
NUM_VARIANTS=10

if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    echo "Usage: $0 <bot> <opponent> [map]"
    exit 1
fi

MATCHES_DIR="matches"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$MATCHES_DIR"

# Clean old match files for this bot
rm -f "$MATCHES_DIR"/${BOT}*.bc17 2>/dev/null || true
rm -f "$MATCHES_DIR"/${BOT}*.db 2>/dev/null || true
rm -f "$MATCHES_DIR"/${BOT}*.log 2>/dev/null || true

echo "Running matches for $BOT and $NUM_VARIANTS variants against $OPPONENT on $MAP..."
echo ""

# Function to run a single match and extract DB
run_match() {
    local team_a="$1"
    local team_b="$2"
    local map="$3"
    local match_name="$4"

    local match_file="$MATCHES_DIR/${match_name}.bc17"
    local log_file="$MATCHES_DIR/${match_name}.log"
    local db_file="$MATCHES_DIR/${match_name}.db"

    # Run the match
    ./gradlew run -PteamA="$team_a" -PteamB="$team_b" -Pmaps="$map" \
        > "$log_file" 2>&1

    # Find the generated match file (gradle names it differently)
    local found_match=$(ls -t matches/*.bc17 2>/dev/null | head -1)
    if [[ -n "$found_match" && "$found_match" != "$match_file" ]]; then
        mv "$found_match" "$match_file" 2>/dev/null || true
    fi

    # Extract to database if match file exists
    if [[ -f "$match_file" ]]; then
        python3 "$SCRIPT_DIR/bc17_query.py" extract "$match_file" "$db_file" > /dev/null 2>&1 || true
    fi
}

# Array to track PIDs for parallel execution
declare -a PIDS=()
declare -a MATCH_NAMES=()

# Run original bot
echo "Starting: original ($BOT vs $OPPONENT)"
run_match "$BOT" "$OPPONENT" "$MAP" "${BOT}_original" &
PIDS+=($!)
MATCH_NAMES+=("original")

# Run all variants
for v in $(seq 1 $NUM_VARIANTS); do
    VARIANT="${BOT}_v${v}"
    if [[ -d "src/$VARIANT" ]]; then
        echo "Starting: v$v ($VARIANT vs $OPPONENT)"
        run_match "$VARIANT" "$OPPONENT" "$MAP" "${BOT}_v${v}" &
        PIDS+=($!)
        MATCH_NAMES+=("v$v")
    else
        echo "Skipping: v$v (folder not found)"
    fi
done

echo ""
echo "Waiting for ${#PIDS[@]} matches to complete..."

# Wait for all matches and report status
FAILED=0
for i in "${!PIDS[@]}"; do
    pid="${PIDS[$i]}"
    name="${MATCH_NAMES[$i]}"
    if wait "$pid"; then
        echo "  ✓ $name completed"
    else
        echo "  ✗ $name failed"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
if [[ $FAILED -gt 0 ]]; then
    echo "Warning: $FAILED match(es) failed"
else
    echo "All matches completed successfully."
fi

# List generated files
echo ""
echo "Match files:"
ls -la "$MATCHES_DIR"/${BOT}*.bc17 2>/dev/null | head -15 || echo "  (no .bc17 files found)"
echo ""
echo "Database files:"
ls -la "$MATCHES_DIR"/${BOT}*.db 2>/dev/null | head -15 || echo "  (no .db files found)"
