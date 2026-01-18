#!/bin/bash
# analyze-game-variant-results.sh - Analyze results from full game variant matches
# Scoring: Only rounds matter - fewer rounds to win = better
# Usage: ./scripts/analyze-game-variant-results.sh <bot_name> <opponent> [maps] [--finalize]

BOT_NAME="$1"
OPPONENT="${2:-examplefuncsplayer}"
MAPS="${3:-MagicWood}"

if [ -z "$BOT_NAME" ]; then
    echo "Usage: $0 <bot_name> [opponent] [maps] [--finalize]"
    echo "Example: $0 mybot examplefuncsplayer MagicWood"
    exit 1
fi

# Convert comma-separated maps to array
IFS=',' read -ra MAP_ARRAY <<< "$MAPS"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "GAME VARIANT PERFORMANCE ANALYSIS"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Base Bot: $BOT_NAME"
echo "Opponent: $OPPONENT"
echo "Maps: $MAPS"
echo ""
echo "Scoring: Win = 10000 - rounds | Loss = -rounds"
echo ""

# Function to analyze a single match
analyze_match() {
    local VARIANT="$1"
    local MAP="$2"
    local DB_FILE="matches/${VARIANT}-game-vs-${OPPONENT}-on-${MAP}.db"
    local LOG_FILE="matches/${VARIANT}-game-${MAP}.log"

    if [ ! -f "$DB_FILE" ]; then
        echo "SKIP|$VARIANT|$MAP|NO_DB"
        return
    fi

    # Get total rounds
    local TOTAL_ROUNDS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
        "SELECT MAX(round_id) as total_rounds FROM rounds" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "3000")

    # Determine winner from database or log
    local WON="NO"

    # Check log file for winner info (more reliable for full games)
    if [ -f "$LOG_FILE" ]; then
        if grep -q "winner=A" "$LOG_FILE" 2>/dev/null; then
            WON="YES"
        elif grep -q "Team A wins" "$LOG_FILE" 2>/dev/null; then
            WON="YES"
        elif grep -q "winner=B" "$LOG_FILE" 2>/dev/null; then
            WON="NO"
        elif grep -q "Team B wins" "$LOG_FILE" 2>/dev/null; then
            WON="NO"
        fi
    fi

    # Fallback: check database for survivors
    if [ "$WON" = "NO" ]; then
        local TEAM_A_SURVIVORS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
            "SELECT COUNT(*) FROM robots WHERE team='A' AND death_round IS NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")
        local TEAM_B_SURVIVORS=$(python3 scripts/bc17_query.py sql "$DB_FILE" \
            "SELECT COUNT(*) FROM robots WHERE team='B' AND death_round IS NULL" 2>/dev/null | grep -E "^[0-9]+" | head -1 || echo "0")

        if [ "$TEAM_B_SURVIVORS" = "0" ] && [ "$TEAM_A_SURVIVORS" != "0" ]; then
            WON="YES"
        fi
    fi

    echo "DATA|$VARIANT|$MAP|$WON|$TOTAL_ROUNDS"
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
printf "┌──────────┬───────┬────────┬────────┐\n"
printf "│ %-8s │ %-5s │ %-6s │ %-6s │\n" "Variant" "Won" "Rounds" "SCORE"
printf "├──────────┼───────┼────────┼────────┤\n"

BEST_VARIANT=""
BEST_SCORE=-999999

# Calculate scores and print table
for VARIANT_KEY in "original" "v1" "v2" "v3" "v4" "v5"; do
    TOTAL_SCORE=0
    TOTAL_ROUNDS=0
    TOTAL_WON=0
    MATCHES=0

    for MAP in "${MAP_ARRAY[@]}"; do
        MAP=$(echo "$MAP" | xargs)
        KEY="${VARIANT_KEY}|${MAP}"
        DATA="${RESULTS[$KEY]}"

        if [ -z "$DATA" ] || [[ "$DATA" == SKIP* ]]; then
            continue
        fi

        # Parse: DATA|variant|map|won|rounds
        IFS='|' read -ra FIELDS <<< "$DATA"
        WON="${FIELDS[3]}"
        ROUNDS="${FIELDS[4]}"

        # Handle empty/invalid values
        ROUNDS=${ROUNDS:-3000}

        MATCHES=$((MATCHES + 1))
        TOTAL_ROUNDS=$((TOTAL_ROUNDS + ROUNDS))

        # Calculate score: only rounds matter
        if [ "$WON" = "YES" ]; then
            TOTAL_WON=$((TOTAL_WON + 1))
            MATCH_SCORE=$((10000 - ROUNDS))
        else
            # Losses get negative score
            MATCH_SCORE=$((-ROUNDS))
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

    printf "│ %-8s │ %-5s │ %-6s │ %-6s │\n" \
        "$VARIANT_KEY" "$WON_DISPLAY" "$AVG_ROUNDS" "$TOTAL_SCORE"

    # Track best
    if [ "$TOTAL_SCORE" -gt "$BEST_SCORE" ]; then
        BEST_SCORE=$TOTAL_SCORE
        BEST_VARIANT=$VARIANT_KEY
    fi
done

printf "└──────────┴───────┴────────┴────────┘\n"

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
else
    echo ""
    echo "Variant $BEST_VARIANT performed best."
fi

# Check for --finalize flag
if [[ "$*" == *"--finalize"* ]]; then
    echo ""
    echo "=== Auto-finalizing with winner: $BEST_VARIANT ==="
    echo ""

    # Inline finalization (adapted from finalize-variant.sh but for game files)
    if [ "$BEST_VARIANT" = "original" ]; then
        echo "Original bot performed best - no code changes needed."
        echo ""
        echo "=== Cleaning up variant folders ==="

        for i in 1 2 3 4 5; do
            VARIANT_DIR="src/${BOT_NAME}_v$i"
            if [ -d "$VARIANT_DIR" ]; then
                echo "Removing: $VARIANT_DIR"
                rm -rf "$VARIANT_DIR"
            fi
        done
    else
        # Extract variant number
        VARIANT_NUM="${BEST_VARIANT#v}"
        WINNER_DIR="src/${BOT_NAME}_v${VARIANT_NUM}"

        if [ ! -d "$WINNER_DIR" ]; then
            echo "ERROR: Winning variant not found at $WINNER_DIR"
            exit 1
        fi

        echo "Winner: ${BOT_NAME}_v${VARIANT_NUM}"
        echo ""

        echo "=== Removing losing variants ==="
        for i in 1 2 3 4 5; do
            if [ "$i" != "$VARIANT_NUM" ]; then
                VARIANT_DIR="src/${BOT_NAME}_v$i"
                if [ -d "$VARIANT_DIR" ]; then
                    echo "Removing: $VARIANT_DIR"
                    rm -rf "$VARIANT_DIR"
                fi
            fi
        done

        echo ""
        echo "=== Replacing original bot with winning variant ==="

        # Remove original bot
        if [ -d "src/$BOT_NAME" ]; then
            echo "Removing original: src/$BOT_NAME"
            rm -rf "src/$BOT_NAME"
        fi

        # Rename winner to original name
        echo "Renaming: $WINNER_DIR -> src/$BOT_NAME"
        mv "$WINNER_DIR" "src/$BOT_NAME"

        # Update package declarations back to original name
        echo ""
        echo "=== Updating package declarations ==="
        for f in src/$BOT_NAME/*.java; do
            if [ -f "$f" ]; then
                echo "Updating: $f"
                perl -i -pe "s/package ${BOT_NAME}_v${VARIANT_NUM};/package $BOT_NAME;/g" "$f"
            fi
        done
    fi

    echo ""
    echo "=== Cleaning up match files ==="
    rm -f matches/*-game-*.bc17 matches/*-game-*.db 2>/dev/null || true
    rm -f matches/*-game-*.log 2>/dev/null || true

    echo ""
    echo "=== Verifying final bot ==="
    if [ ! -f "src/$BOT_NAME/RobotPlayer.java" ]; then
        echo "ERROR: RobotPlayer.java not found after finalization!"
        exit 1
    fi

    echo "Package declarations:"
    grep "^package" src/$BOT_NAME/*.java 2>/dev/null || echo "(no package statements found)"

    echo ""
    echo "=== Compiling to verify ==="
    ./gradlew compileJava 2>&1 | tail -20

    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "FINALIZATION COMPLETE"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo ""
    echo "Final bot: src/$BOT_NAME/"
    echo ""

    if [ "$BEST_VARIANT" != "original" ]; then
        echo "The winning variant ($BEST_VARIANT) has been merged into the original bot."
    fi

    echo ""
    echo "To run a validation match:"
    echo "  ./gradlew run -PteamA=$BOT_NAME -PteamB=$OPPONENT -Pmaps=MagicWood"
else
    echo ""
    echo "To finalize the winner, run:"
    echo "  ./scripts/analyze-game-variant-results.sh $BOT_NAME $OPPONENT $MAPS --finalize"
fi
