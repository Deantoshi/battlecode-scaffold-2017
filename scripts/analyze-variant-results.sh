#!/bin/bash
# analyze-variant-results.sh - Analyze results from all variant matches and output scoring
# Usage: ./scripts/analyze-variant-results.sh <bot_name> <opponent> [maps]

BOT_NAME="$1"
OPPONENT="${2:-examplefuncsplayer}"
MAPS="${3:-Shrine}"

if [ -z "$BOT_NAME" ]; then
    echo "Usage: $0 <bot_name> [opponent] [maps]"
    echo "Example: $0 mybot examplefuncsplayer Shrine"
    exit 1
fi

# Convert comma-separated maps to array
IFS=',' read -ra MAP_ARRAY <<< "$MAPS"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "VARIANT PERFORMANCE ANALYSIS"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Base Bot: $BOT_NAME"
echo "Opponent: $OPPONENT"
echo "Maps: $MAPS"
echo ""

# Function to analyze a single match
analyze_match() {
    local VARIANT="$1"
    local MAP="$2"
    local DB_FILE="matches/${VARIANT}-combat-vs-${OPPONENT}-on-${MAP}.db"
    local LOG_FILE="matches/${VARIANT}-combat-${MAP}.log"

    if [ ! -f "$DB_FILE" ]; then
        echo "SKIP|$VARIANT|$MAP|NO_DB"
        return
    fi

    # Get total rounds
    local TOTAL_ROUNDS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT MAX(round_id) as total_rounds FROM rounds" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "500")

    # Get Team A (our bot) deaths
    local TEAM_A_DEATHS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT COUNT(*) FROM robots WHERE team='A' AND death_round IS NOT NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")

    # Get Team B (enemy) deaths
    local TEAM_B_DEATHS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT COUNT(*) FROM robots WHERE team='B' AND death_round IS NOT NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")

    # Get Team A survivors
    local TEAM_A_SURVIVORS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT COUNT(*) FROM robots WHERE team='A' AND death_round IS NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")

    # Get Team B survivors
    local TEAM_B_SURVIVORS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT COUNT(*) FROM robots WHERE team='B' AND death_round IS NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")

    # Determine winner: if Team B has no survivors, Team A won
    local WON="NO"
    if [ "$TEAM_B_SURVIVORS" = "0" ] && [ "$TEAM_A_SURVIVORS" != "0" ]; then
        WON="YES"
    fi

    # Also check log file for winner info
    if [ -f "$LOG_FILE" ]; then
        if grep -q "winner=A" "$LOG_FILE" 2>/dev/null; then
            WON="YES"
        elif grep -q "winner=B" "$LOG_FILE" 2>/dev/null; then
            WON="NO"
        fi
    fi

    echo "DATA|$VARIANT|$MAP|$WON|$TOTAL_ROUNDS|$TEAM_A_DEATHS|$TEAM_B_DEATHS|$TEAM_A_SURVIVORS"
}

# Collect results for all variants
declare -A RESULTS

echo "=== Analyzing match databases ==="
echo ""

# Analyze original bot
for MAP in "${MAP_ARRAY[@]}"; do
    MAP=$(echo "$MAP" | xargs)
    RESULT=$(analyze_match "$BOT_NAME" "$MAP")
    echo "  $RESULT"
    RESULTS["original|$MAP"]="$RESULT"
done

# Analyze variants v1-v5
for i in 1 2 3 4 5; do
    VARIANT="${BOT_NAME}_v$i"
    for MAP in "${MAP_ARRAY[@]}"; do
        MAP=$(echo "$MAP" | xargs)
        RESULT=$(analyze_match "$VARIANT" "$MAP")
        echo "  $RESULT"
        RESULTS["v$i|$MAP"]="$RESULT"
    done
done

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "RESULTS TABLE"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""

# Print header
printf "┌──────────┬───────┬────────┬──────────┬────────────┬───────────┬───────┐\n"
printf "│ %-8s │ %-5s │ %-6s │ %-8s │ %-10s │ %-9s │ %-5s │\n" "Variant" "Won" "Rounds" "Our Dead" "Enemy Dead" "Survivors" "SCORE"
printf "├──────────┼───────┼────────┼──────────┼────────────┼───────────┼───────┤\n"

BEST_VARIANT=""
BEST_SCORE=-999999

# Calculate scores and print table
for VARIANT_KEY in "original" "v1" "v2" "v3" "v4" "v5"; do
    TOTAL_SCORE=0
    TOTAL_ROUNDS=0
    TOTAL_WON=0
    TOTAL_OUR_DEAD=0
    TOTAL_ENEMY_DEAD=0
    TOTAL_SURVIVORS=0
    MATCHES=0

    for MAP in "${MAP_ARRAY[@]}"; do
        MAP=$(echo "$MAP" | xargs)
        KEY="${VARIANT_KEY}|${MAP}"
        DATA="${RESULTS[$KEY]}"

        if [ -z "$DATA" ] || [[ "$DATA" == SKIP* ]]; then
            continue
        fi

        # Parse: DATA|variant|map|won|rounds|our_dead|enemy_dead|survivors
        IFS='|' read -ra FIELDS <<< "$DATA"
        WON="${FIELDS[3]}"
        ROUNDS="${FIELDS[4]}"
        OUR_DEAD="${FIELDS[5]}"
        ENEMY_DEAD="${FIELDS[6]}"
        SURVIVORS="${FIELDS[7]}"

        # Handle empty/invalid values
        ROUNDS=${ROUNDS:-500}
        OUR_DEAD=${OUR_DEAD:-0}
        ENEMY_DEAD=${ENEMY_DEAD:-0}
        SURVIVORS=${SURVIVORS:-0}

        MATCHES=$((MATCHES + 1))
        TOTAL_ROUNDS=$((TOTAL_ROUNDS + ROUNDS))
        TOTAL_OUR_DEAD=$((TOTAL_OUR_DEAD + OUR_DEAD))
        TOTAL_ENEMY_DEAD=$((TOTAL_ENEMY_DEAD + ENEMY_DEAD))
        TOTAL_SURVIVORS=$((TOTAL_SURVIVORS + SURVIVORS))

        # Calculate score per match
        if [ "$WON" = "YES" ]; then
            TOTAL_WON=$((TOTAL_WON + 1))
            # Score: prioritize wins, then fewer rounds, then more kills
            MATCH_SCORE=$((10000 - ROUNDS + ENEMY_DEAD * 10 + SURVIVORS * 5))
        else
            # Lost: score based on damage dealt
            MATCH_SCORE=$((ENEMY_DEAD * 10 - ROUNDS / 10))
        fi
        TOTAL_SCORE=$((TOTAL_SCORE + MATCH_SCORE))
    done

    if [ "$MATCHES" -eq 0 ]; then
        continue
    fi

    # Average values for display
    AVG_ROUNDS=$((TOTAL_ROUNDS / MATCHES))
    WON_DISPLAY="NO"
    if [ "$TOTAL_WON" -eq "$MATCHES" ]; then
        WON_DISPLAY="YES"
    elif [ "$TOTAL_WON" -gt 0 ]; then
        WON_DISPLAY="${TOTAL_WON}/${MATCHES}"
    fi

    printf "│ %-8s │ %-5s │ %-6s │ %-8s │ %-10s │ %-9s │ %-5s │\n" \
        "$VARIANT_KEY" "$WON_DISPLAY" "$AVG_ROUNDS" "$TOTAL_OUR_DEAD" "$TOTAL_ENEMY_DEAD" "$TOTAL_SURVIVORS" "$TOTAL_SCORE"

    # Track best
    if [ "$TOTAL_SCORE" -gt "$BEST_SCORE" ]; then
        BEST_SCORE=$TOTAL_SCORE
        BEST_VARIANT=$VARIANT_KEY
    fi
done

printf "└──────────┴───────┴────────┴──────────┴────────────┴───────────┴───────┘\n"

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "WINNER: $BEST_VARIANT (Score: $BEST_SCORE)"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""

# Output machine-readable result
echo "BEST_VARIANT=$BEST_VARIANT"
echo "BEST_SCORE=$BEST_SCORE"

if [ "$BEST_VARIANT" = "original" ]; then
    echo ""
    echo "The original bot performed best. No code changes needed."
    echo "Run: ./scripts/finalize-variant.sh $BOT_NAME original"
else
    echo ""
    echo "Variant $BEST_VARIANT performed best."
    echo "Run: ./scripts/finalize-variant.sh $BOT_NAME $BEST_VARIANT"
fi
