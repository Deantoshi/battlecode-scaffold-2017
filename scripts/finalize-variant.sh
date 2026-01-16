#!/bin/bash
# finalize-variant.sh - Finalize the winning variant and clean up
# Usage: ./scripts/finalize-variant.sh <bot_name> <winner>
#   winner can be: "original", "v1", "v2", "v3", "v4", or "v5"

set -e

BOT_NAME="$1"
WINNER="$2"

if [ -z "$BOT_NAME" ] || [ -z "$WINNER" ]; then
    echo "Usage: $0 <bot_name> <winner>"
    echo "  winner: 'original', 'v1', 'v2', 'v3', 'v4', or 'v5'"
    echo ""
    echo "Example: $0 mybot v3"
    echo "Example: $0 mybot original"
    exit 1
fi

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "FINALIZING VARIANT: $WINNER"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""

# Validate winner argument
if [[ ! "$WINNER" =~ ^(original|v[1-5])$ ]]; then
    echo "ERROR: Invalid winner '$WINNER'. Must be 'original' or 'v1'-'v5'"
    exit 1
fi

if [ "$WINNER" = "original" ]; then
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
    VARIANT_NUM="${WINNER#v}"
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
rm -f matches/*-variant-*.bc17 matches/*-variant-*.db 2>/dev/null || true
rm -f matches/*-variant-*.log 2>/dev/null || true

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

if [ "$WINNER" != "original" ]; then
    echo "The winning variant ($WINNER) has been merged into the original bot."
    echo "Key changes were applied from the variant's Soldier.java and Nav.java."
fi

echo ""
echo "To run a validation match:"
echo "  ./gradlew runWithSummary -PteamA=$BOT_NAME -PteamB=examplefuncsplayer -Pmaps=Shrine"
