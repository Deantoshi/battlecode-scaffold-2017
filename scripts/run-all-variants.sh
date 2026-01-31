#!/bin/bash
# run-all-variants.sh - Runs all variants + original against opponent (+ champions)
#
# Usage: ./scripts/run-all-variants.sh <bot> <opponent> <map> [num_champions]
#
# Creates match files and databases for analysis

set -e

BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-MagicWood}"
NUM_CHAMPIONS="${4:-0}"
NUM_VARIANTS=10

if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    echo "Usage: $0 <bot> <opponent> [map] [num_champions]"
    exit 1
fi

MATCHES_DIR="matches"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$MATCHES_DIR"

# Clean old match files for this bot
rm -f "$MATCHES_DIR"/${BOT}*.bc17 2>/dev/null || true
rm -f "$MATCHES_DIR"/${BOT}*.db 2>/dev/null || true
rm -f "$MATCHES_DIR"/${BOT}*.log 2>/dev/null || true

# Build opponent list: main opponent + all champions
ALL_OPPONENTS=("$OPPONENT")
OPPONENT_LABELS=("opponent")

for c in $(seq 0 $((NUM_CHAMPIONS - 1))); do
    ALL_OPPONENTS+=("${BOT}_champion_${c}")
    OPPONENT_LABELS+=("champ${c}")
done

echo "Running matches for $BOT and $NUM_VARIANTS variants against ${#ALL_OPPONENTS[@]} opponent(s) on $MAP..."
if [[ $NUM_CHAMPIONS -gt 0 ]]; then
    echo "  Opponents: $OPPONENT + $NUM_CHAMPIONS champion(s)"
fi
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

# Parallelism limit - only run this many matches at once
MAX_PARALLEL=${MAX_PARALLEL:-2}

# Arrays to track PIDs for parallel execution
declare -a PIDS=()
declare -a MATCH_NAMES=()
declare -a ALL_NAMES=()
FAILED=0

# Function to wait for a slot to become available
wait_for_slot() {
    while [[ ${#PIDS[@]} -ge $MAX_PARALLEL ]]; do
        # Wait for any job to finish
        for i in "${!PIDS[@]}"; do
            pid="${PIDS[$i]}"
            if ! kill -0 "$pid" 2>/dev/null; then
                # Process finished, check exit status
                if wait "$pid"; then
                    echo "  ✓ ${MATCH_NAMES[$i]} completed"
                else
                    echo "  ✗ ${MATCH_NAMES[$i]} failed"
                    FAILED=$((FAILED + 1))
                fi
                # Remove from arrays
                unset 'PIDS[i]'
                unset 'MATCH_NAMES[i]'
                # Reindex arrays
                PIDS=("${PIDS[@]}")
                MATCH_NAMES=("${MATCH_NAMES[@]}")
                return
            fi
        done
        sleep 0.5
    done
}

# Function to start a match with parallelism control
start_match() {
    local team_a="$1"
    local team_b="$2"
    local match_name="$3"
    local display_name="$4"

    wait_for_slot
    echo "Starting: $display_name ($team_a vs $team_b)"
    run_match "$team_a" "$team_b" "$MAP" "$match_name" &
    PIDS+=($!)
    MATCH_NAMES+=("$display_name")
    ALL_NAMES+=("$display_name")
}

# Run all competitors against all opponents
# Competitors: original + v1..v10
# Opponents: main opponent + champion_0..champion_N

# Original vs all opponents
for opp_idx in "${!ALL_OPPONENTS[@]}"; do
    opp="${ALL_OPPONENTS[$opp_idx]}"
    label="${OPPONENT_LABELS[$opp_idx]}"
    start_match "$BOT" "$opp" "${BOT}_original_vs_${label}" "original vs ${label}"
done

# Variants vs all opponents
for v in $(seq 1 $NUM_VARIANTS); do
    VARIANT="${BOT}_v${v}"
    if [[ -d "src/$VARIANT" ]]; then
        for opp_idx in "${!ALL_OPPONENTS[@]}"; do
            opp="${ALL_OPPONENTS[$opp_idx]}"
            label="${OPPONENT_LABELS[$opp_idx]}"
            start_match "$VARIANT" "$opp" "${BOT}_v${v}_vs_${label}" "v${v} vs ${label}"
        done
    else
        echo "Skipping: v$v (folder not found)"
    fi
done

echo ""
echo "Waiting for remaining matches to complete..."

# Wait for remaining matches
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
ls -la "$MATCHES_DIR"/${BOT}*.bc17 2>/dev/null | head -20 || echo "  (no .bc17 files found)"
echo ""
echo "Database files:"
ls -la "$MATCHES_DIR"/${BOT}*.db 2>/dev/null | head -20 || echo "  (no .db files found)"
