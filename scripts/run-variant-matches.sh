#!/bin/bash
# run-variant-matches.sh - Run original + all 5 variants against an opponent
# Usage: ./scripts/run-variant-matches.sh <bot_name> <opponent> [maps]

set -e

BOT_NAME="$1"
OPPONENT="${2:-examplefuncsplayer}"
MAPS="${3:-Shrine}"

if [ -z "$BOT_NAME" ]; then
    echo "Usage: $0 <bot_name> [opponent] [maps]"
    echo "Example: $0 mybot examplefuncsplayer Shrine"
    echo "Example: $0 mybot opponent_bot \"Shrine,Arena\""
    exit 1
fi

echo "=== Running Variant Matches ==="
echo "Base Bot: $BOT_NAME"
echo "Opponent: $OPPONENT"
echo "Maps: $MAPS"
echo ""

# Create matches directory if needed
mkdir -p matches

# Clean old combat match files
rm -f matches/*-combat-*.bc17 matches/*-combat-*.db 2>/dev/null || true

# Convert comma-separated maps to array
IFS=',' read -ra MAP_ARRAY <<< "$MAPS"

PIDS=()
JOBS=()

echo "=== Starting matches in parallel ==="

# Run original bot
for MAP in "${MAP_ARRAY[@]}"; do
    MAP=$(echo "$MAP" | xargs)  # trim whitespace
    MATCH_FILE="matches/${BOT_NAME}-combat-vs-${OPPONENT}-on-${MAP}.bc17"
    echo "Starting: $BOT_NAME vs $OPPONENT on $MAP"

    ./gradlew combatSim \
        -PteamA="$BOT_NAME" \
        -PteamB="$OPPONENT" \
        -PsimMap="$MAP" \
        -PsimSave="$MATCH_FILE" > "matches/${BOT_NAME}-combat-${MAP}.log" 2>&1 &

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
        MATCH_FILE="matches/${VARIANT}-combat-vs-${OPPONENT}-on-${MAP}.bc17"
        echo "Starting: $VARIANT vs $OPPONENT on $MAP"

        ./gradlew combatSim \
            -PteamA="$VARIANT" \
            -PteamB="$OPPONENT" \
            -PsimMap="$MAP" \
            -PsimSave="$MATCH_FILE" > "matches/${VARIANT}-combat-${MAP}.log" 2>&1 &

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

# List generated match files
echo "=== Generated match files ==="
ls -la matches/*-combat-*.bc17 2>/dev/null || echo "No match files generated"

echo ""
echo "=== Extracting match data ==="
for match in matches/*-combat-*.bc17; do
    if [ -f "$match" ]; then
        echo "Extracting: $match"
        python3 scripts/bc17_query.py extract "$match" 2>/dev/null || echo "  Warning: extraction failed for $match"
    fi
done

echo ""
echo "Done. Run ./scripts/analyze-variant-results.sh $BOT_NAME $OPPONENT $MAPS to see results."
