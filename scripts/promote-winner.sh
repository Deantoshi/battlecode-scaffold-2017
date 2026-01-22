#!/bin/bash
# promote-winner.sh - Replaces original bot with winning variant if applicable
#
# Usage: ./scripts/promote-winner.sh <bot>
#
# Reads from: src/<bot>/.state/variant-results.json
# If should_promote is true, replaces original bot code with winner

set -e

BOT="${1:-}"

if [[ -z "$BOT" ]]; then
    echo "Usage: $0 <bot>"
    exit 1
fi

STATE_DIR="src/$BOT/.state"
RESULTS_FILE="$STATE_DIR/variant-results.json"

if [[ ! -f "$RESULTS_FILE" ]]; then
    echo "Error: Results file not found: $RESULTS_FILE"
    exit 1
fi

# Parse results
SHOULD_PROMOTE=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print('YES' if data.get('should_promote', False) else 'NO')
")

WINNER=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('winner', 'original'))
")

if [[ "$SHOULD_PROMOTE" == "YES" && "$WINNER" != "original" ]]; then
    echo "Promoting $WINNER to original..."

    WINNER_DIR="src/${BOT}_${WINNER}"
    if [[ ! -d "$WINNER_DIR" ]]; then
        echo "Error: Winner folder not found: $WINNER_DIR"
        exit 1
    fi

    # Backup state directory
    if [[ -d "$STATE_DIR" ]]; then
        mv "$STATE_DIR" "/tmp/${BOT}_state_backup_$$"
    fi

    # Copy winner Java files to original (excluding .state)
    for java_file in "$WINNER_DIR"/*.java; do
        if [[ -f "$java_file" ]]; then
            filename=$(basename "$java_file")
            # Update package declaration back to original
            sed "s/^package ${BOT}_${WINNER};/package ${BOT};/" "$java_file" > "src/$BOT/$filename"
        fi
    done

    # Restore state directory
    if [[ -d "/tmp/${BOT}_state_backup_$$" ]]; then
        mv "/tmp/${BOT}_state_backup_$$" "$STATE_DIR"
    fi

    echo "✓ Promoted $WINNER to $BOT"

    # Record promotion in history
    HISTORY_FILE="$STATE_DIR/promotion-history.txt"
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Promoted $WINNER to original" >> "$HISTORY_FILE"
else
    echo "No promotion needed (original is best or tied)"
fi

# Clean up variant folders
echo "Cleaning up variant folders..."
for v in $(seq 1 10); do
    if [[ -d "src/${BOT}_v${v}" ]]; then
        rm -rf "src/${BOT}_v${v}"
    fi
done

# Clean up match files
rm -f matches/${BOT}*.bc17 2>/dev/null || true
rm -f matches/${BOT}*.log 2>/dev/null || true

echo "✓ Cleanup complete"
