#!/bin/bash
# run-game-variant-matches.sh - Run original + all 5 variants in full game matches
# Usage: ./scripts/run-game-variant-matches.sh <bot_name> <opponent> [maps]

set -e

BOT_NAME="$1"
OPPONENT="${2:-examplefuncsplayer}"
MAPS="${3:-MagicWood}"

if [ -z "$BOT_NAME" ]; then
    echo "Usage: $0 <bot_name> [opponent] [maps]"
    echo "Example: $0 mybot examplefuncsplayer MagicWood"
    echo "Example: $0 mybot opponent_bot \"MagicWood,Arena\""
    exit 1
fi

echo "=== Running Full Game Variant Matches ==="
echo "Base Bot: $BOT_NAME"
echo "Opponent: $OPPONENT"
echo "Maps: $MAPS"
echo ""

# Create matches directory if needed
mkdir -p matches

# Clean old game match files
rm -f matches/*-game-*.bc17 matches/*-game-*.db 2>/dev/null || true
rm -f matches/*-game-*.log 2>/dev/null || true

# Convert comma-separated maps to array
IFS=',' read -ra MAP_ARRAY <<< "$MAPS"

PIDS=()
JOBS=()

echo "=== Starting matches in parallel ==="

# Run original bot
for MAP in "${MAP_ARRAY[@]}"; do
    MAP=$(echo "$MAP" | xargs)  # trim whitespace
    MATCH_FILE="matches/${BOT_NAME}-game-vs-${OPPONENT}-on-${MAP}.bc17"
    echo "Starting: $BOT_NAME vs $OPPONENT on $MAP"

    ./gradlew run \
        -PteamA="$BOT_NAME" \
        -PteamB="$OPPONENT" \
        -Pmaps="$MAP" > "matches/${BOT_NAME}-game-${MAP}.log" 2>&1 &

    PIDS+=($!)
    JOBS+=("$BOT_NAME on $MAP")
done

# Run all 5 variants
for i in 1 2 3 4 5; do
    VARIANT="${BOT_NAME}_v$i"

    if [ ! -d "src/$VARIANT" ]; then
        echo "Warning: Variant $VARIANT not found, skipping"
        continue
    fi

    for MAP in "${MAP_ARRAY[@]}"; do
        MAP=$(echo "$MAP" | xargs)  # trim whitespace
        MATCH_FILE="matches/${VARIANT}-game-vs-${OPPONENT}-on-${MAP}.bc17"
        echo "Starting: $VARIANT vs $OPPONENT on $MAP"

        ./gradlew run \
            -PteamA="$VARIANT" \
            -PteamB="$OPPONENT" \
            -Pmaps="$MAP" > "matches/${VARIANT}-game-${MAP}.log" 2>&1 &

        PIDS+=($!)
        JOBS+=("$VARIANT on $MAP")
    done
done

echo ""
echo "=== Waiting for ${#PIDS[@]} matches to complete ==="

# Wait for all matches and collect results
FAILED=0
for idx in "${!PIDS[@]}"; do
    PID=${PIDS[$idx]}
    JOB=${JOBS[$idx]}
    if wait $PID; then
        echo "✓ Completed: $JOB"
    else
        echo "✗ Failed: $JOB"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "=== Match Execution Complete ==="
echo "Total matches: ${#PIDS[@]}"
echo "Failed: $FAILED"
echo ""

# The match files are saved with the gradle default naming, let's rename them
echo "=== Locating match files ==="
for MAP in "${MAP_ARRAY[@]}"; do
    MAP=$(echo "$MAP" | xargs)

    # Original bot
    SRC_FILE="matches/${BOT_NAME}-vs-${OPPONENT}-on-${MAP}.bc17"
    DST_FILE="matches/${BOT_NAME}-game-vs-${OPPONENT}-on-${MAP}.bc17"
    if [ -f "$SRC_FILE" ]; then
        mv "$SRC_FILE" "$DST_FILE"
        echo "Renamed: $SRC_FILE -> $DST_FILE"
    fi

    # Variants
    for i in 1 2 3 4 5; do
        VARIANT="${BOT_NAME}_v$i"
        SRC_FILE="matches/${VARIANT}-vs-${OPPONENT}-on-${MAP}.bc17"
        DST_FILE="matches/${VARIANT}-game-vs-${OPPONENT}-on-${MAP}.bc17"
        if [ -f "$SRC_FILE" ]; then
            mv "$SRC_FILE" "$DST_FILE"
            echo "Renamed: $SRC_FILE -> $DST_FILE"
        fi
    done
done

# List generated match files
echo ""
echo "=== Generated match files ==="
ls -la matches/*-game-*.bc17 2>/dev/null || echo "No match files generated"

echo ""
echo "=== Extracting match data ==="
for match in matches/*-game-*.bc17; do
    if [ -f "$match" ]; then
        echo "Extracting: $match"
        python3 scripts/bc17_query.py extract "$match" 2>/dev/null || echo "  Warning: extraction failed for $match"
    fi
done

echo ""
echo "Done. Run ./scripts/analyze-game-variant-results.sh $BOT_NAME $OPPONENT $MAPS to see results."
