#!/bin/bash
# run-all-variants.sh - Runs all variants + original against opponent (+ champions)
#
# Usage: ./scripts/run-all-variants.sh <bot> <opponent> <map> [num_champions]
#
# Creates match files and databases for analysis
# Uses batchRun Gradle task for memory-efficient sequential execution

set -e

BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-MagicWood}"
NUM_CHAMPIONS="${4:-0}"
NUM_VARIANTS=16

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

# ─────────────────────────────────────────────────────────────────────────────
# Generate matchlist file
# ─────────────────────────────────────────────────────────────────────────────
MATCHLIST="$MATCHES_DIR/matchlist.txt"
> "$MATCHLIST"

MATCH_COUNT=0

# Original vs all opponents
for opp_idx in "${!ALL_OPPONENTS[@]}"; do
    opp="${ALL_OPPONENTS[$opp_idx]}"
    label="${OPPONENT_LABELS[$opp_idx]}"
    match_name="${BOT}_original_vs_${label}"
    echo "$BOT $opp $MAP $MATCHES_DIR/${match_name}.bc17" >> "$MATCHLIST"
    MATCH_COUNT=$((MATCH_COUNT + 1))
done

# Variants vs all opponents
for v in $(seq 1 $NUM_VARIANTS); do
    VARIANT="${BOT}_v${v}"
    if [[ -d "src/$VARIANT" ]]; then
        for opp_idx in "${!ALL_OPPONENTS[@]}"; do
            opp="${ALL_OPPONENTS[$opp_idx]}"
            label="${OPPONENT_LABELS[$opp_idx]}"
            match_name="${BOT}_v${v}_vs_${label}"
            echo "$VARIANT $opp $MAP $MATCHES_DIR/${match_name}.bc17" >> "$MATCHLIST"
            MATCH_COUNT=$((MATCH_COUNT + 1))
        done
    else
        echo "Skipping: v$v (folder not found)"
    fi
done

echo "Generated matchlist with $MATCH_COUNT matches"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Run all matches via single Gradle invocation
# ─────────────────────────────────────────────────────────────────────────────
echo "Running batch matches..."
./gradlew batchRun -PmatchList="$MATCHLIST"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Extract databases and clean up .bc17 files
# ─────────────────────────────────────────────────────────────────────────────
echo "Extracting databases from match files..."
EXTRACTED=0
FAILED=0

for bc17_file in "$MATCHES_DIR"/${BOT}*.bc17; do
    [[ -f "$bc17_file" ]] || continue

    db_file="${bc17_file%.bc17}.db"
    if python3 "$SCRIPT_DIR/bc17_query.py" extract "$bc17_file" "$db_file" > /dev/null 2>&1; then
        EXTRACTED=$((EXTRACTED + 1))
        # Delete .bc17 after successful extraction to save memory
        rm -f "$bc17_file"
    else
        echo "  Failed to extract: $bc17_file"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
if [[ $FAILED -gt 0 ]]; then
    echo "Warning: $FAILED extraction(s) failed"
else
    echo "All $EXTRACTED matches extracted successfully."
fi

# Clean up matchlist
rm -f "$MATCHLIST"

# List generated files
echo ""
echo "Database files:"
ls -la "$MATCHES_DIR"/${BOT}*.db 2>/dev/null | head -20 || echo "  (no .db files found)"
