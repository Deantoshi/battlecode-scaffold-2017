#!/usr/bin/env bash
# validate-objective.sh - Validate a sub-objective by running a match and checking metrics
#
# Usage: validate-objective.sh <bot> <opponent> <map> <metric_path> <operator> <threshold>
#
# Arguments:
#   bot          Your bot folder name
#   opponent     Opponent bot folder name
#   map          Map name
#   metric_path  JSON path to metric (e.g., "unit_summary.A.SOLDIER.alive" or "result.rounds")
#   operator     Comparison operator: >=, >, ==, <=, <
#   threshold    Numeric threshold value
#
# Exit codes:
#   0 = Objective MET
#   1 = Objective NOT MET
#   2 = Error (missing args, bad metric, etc.)
#
# Examples:
#   # Check if we have 3+ soldiers alive at end of match
#   ./scripts/validate-objective.sh mybot opponent Map "unit_count.A.SOLDIER.alive" ">=" 3
#
#   # Check if we won
#   ./scripts/validate-objective.sh mybot opponent Map "result.won" "==" "YES"
#
#   # Check if we have 5+ trees at round 300
#   ./scripts/validate-objective.sh mybot opponent Map "tree_count_at_round.300.A" ">=" 5

set -euo pipefail

# Colors
RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
NC=$'\033[0m'

# Arguments
BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-}"
METRIC_PATH="${4:-}"
OPERATOR="${5:-}"
THRESHOLD="${6:-}"

if [[ -z "$BOT" || -z "$OPPONENT" || -z "$MAP" || -z "$METRIC_PATH" || -z "$OPERATOR" || -z "$THRESHOLD" ]]; then
    echo "${RED}Usage: $0 <bot> <opponent> <map> <metric_path> <operator> <threshold>${NC}"
    echo ""
    echo "Arguments:"
    echo "  bot          Your bot folder name"
    echo "  opponent     Opponent bot folder name"
    echo "  map          Map name"
    echo "  metric_path  Metric to check (see available metrics below)"
    echo "  operator     Comparison: >=, >, ==, <=, <"
    echo "  threshold    Target value"
    echo ""
    echo "Common Metrics:"
    echo "  result.won                     - 'YES' or 'NO'"
    echo "  result.rounds                  - Total rounds played"
    echo "  result.goal_met                - 'YES' if won in <=1500 rounds"
    echo "  unit_alive.A.SOLDIER           - Soldiers alive at end"
    echo "  unit_alive.A.GARDENER          - Gardeners alive at end"
    echo "  unit_alive.A.TREE              - Trees alive at end"
    echo "  unit_produced.A.SOLDIER        - Total soldiers produced"
    echo "  economy.A.bullets_at_500       - Bullets at round 500"
    echo "  trees_at_round.300.A           - Trees at round 300"
    echo "  first_unit.A.SOLDIER           - Round first soldier built"
    echo "  damage.A.enemy_kills           - Enemy units killed"
    echo ""
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
STATE_DIR="$PROJECT_DIR/src/$BOT/.state"
JSON_FILE="$STATE_DIR/match-result.json"

# Run the match
echo "${YELLOW}Running match: $BOT vs $OPPONENT on $MAP${NC}"
"$SCRIPT_DIR/run-match-with-analysis.sh" "$BOT" "$OPPONENT" "$MAP" > /dev/null 2>&1

if [[ ! -s "$JSON_FILE" ]]; then
    echo "${RED}ERROR: Match result JSON not found at $JSON_FILE${NC}"
    exit 2
fi

# Extract metric and compare using Python
# This handles all the metric path parsing and comparison logic
RESULT=$(python3 - "$JSON_FILE" "$METRIC_PATH" "$OPERATOR" "$THRESHOLD" <<'PYEOF'
import json
import sys

def get_metric(data, path):
    """
    Extract a metric from match JSON using a dot-separated path.

    Supported paths:
    - result.won, result.rounds, result.goal_met
    - unit_alive.{team}.{type} - units alive at end
    - unit_produced.{team}.{type} - units produced
    - unit_lost.{team}.{type} - units lost
    - trees_at_round.{round}.{team} - trees alive at specific round
    - economy.{team}.bullets_at_{round} - bullets at specific round
    - economy.{team}.generated_at_{round} - cumulative generated at round
    - first_unit.{team}.{type} - round first unit of type was built
    - damage.{team}.enemy_kills - kills by team
    - damage.{team}.kd_ratio - kill/death ratio
    - combat.first_round - first combat round
    """
    parts = path.split('.')

    # Direct result fields
    if parts[0] == 'result':
        return data.get('result', {}).get(parts[1])

    # Unit counts (alive/produced/lost)
    if parts[0] in ('unit_alive', 'unit_produced', 'unit_lost'):
        team = parts[1]  # A or B
        unit_type = parts[2]  # SOLDIER, GARDENER, etc.
        field_map = {'unit_alive': 'alive', 'unit_produced': 'produced', 'unit_lost': 'lost'}
        field = field_map[parts[0]]

        for unit in data.get('unit_summary', []):
            if unit.get('team') == team and unit.get('unit') == unit_type:
                return unit.get(field, 0)
        return 0

    # Trees at specific round
    if parts[0] == 'trees_at_round':
        target_round = int(parts[1])
        team = parts[2]  # A or B
        team_key = f'team_{team.lower()}_trees'

        # Find the closest round in tree_economy
        tree_data = data.get('tree_economy', [])
        for entry in tree_data:
            if entry.get('round', 0) >= target_round:
                return entry.get(team_key, 0)
        # If no round >= target, return last known value
        if tree_data:
            return tree_data[-1].get(team_key, 0)
        return 0

    # Economy at specific round
    if parts[0] == 'economy':
        team = parts[1]  # A or B
        metric = parts[2]  # bullets_at_500, generated_at_500, etc.

        if metric.startswith('bullets_at_'):
            target_round = int(metric.split('_')[-1])
            team_key = f'team_{team.lower()}'
            for entry in data.get('economy_timeline', []):
                if entry.get('round', 0) >= target_round:
                    return entry.get(team_key, {}).get('bullets', 0)
            return 0

        if metric.startswith('generated_at_'):
            target_round = int(metric.split('_')[-1])
            team_key = f'team_{team.lower()}'
            for entry in data.get('economy_timeline', []):
                if entry.get('round', 0) >= target_round:
                    return entry.get(team_key, {}).get('bullets_generated', 0)
            return 0

    # First unit of type
    if parts[0] == 'first_unit':
        team = parts[1]  # A or B
        unit_type = parts[2]  # SOLDIER, GARDENER, etc.

        build_data = data.get('build_order', {}).get('teams', {}).get(team, {})
        for unit in build_data.get('units', []):
            if unit.get('unit') == unit_type:
                return unit.get('round', 9999)
        return 9999  # Never built

    # Damage/combat stats
    if parts[0] == 'damage':
        team = parts[1]  # A or B
        metric = parts[2]  # enemy_kills, kd_ratio, etc.

        combat = data.get('combat_analysis', {}).get('teams', {}).get(team, {})
        return combat.get(metric, 0)

    # Combat timing
    if parts[0] == 'combat':
        if parts[1] == 'first_round':
            return int(data.get('combat_analysis', {}).get('combat_duration', {}).get('first_round', 9999))

    # Fallback: try direct nested access
    current = data
    for part in parts:
        if isinstance(current, dict):
            current = current.get(part)
        elif isinstance(current, list) and part.isdigit():
            idx = int(part)
            current = current[idx] if idx < len(current) else None
        else:
            return None
        if current is None:
            return None
    return current


def compare(value, operator, threshold):
    """Compare value against threshold using operator."""
    # Handle string comparisons
    if isinstance(value, str) or isinstance(threshold, str):
        str_val = str(value).upper()
        str_thresh = str(threshold).upper()
        if operator == '==':
            return str_val == str_thresh
        elif operator == '!=':
            return str_val != str_thresh
        else:
            # Try numeric comparison if possible
            try:
                value = float(value)
                threshold = float(threshold)
            except (ValueError, TypeError):
                return False

    # Numeric comparisons
    try:
        value = float(value) if value is not None else 0
        threshold = float(threshold)
    except (ValueError, TypeError):
        return False

    if operator == '>=':
        return value >= threshold
    elif operator == '>':
        return value > threshold
    elif operator == '==':
        return value == threshold
    elif operator == '<=':
        return value <= threshold
    elif operator == '<':
        return value < threshold
    elif operator == '!=':
        return value != threshold
    else:
        return False


# Main
json_file = sys.argv[1]
metric_path = sys.argv[2]
operator = sys.argv[3]
threshold = sys.argv[4]

try:
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
except Exception as e:
    print(f"ERROR: Failed to read JSON: {e}")
    sys.exit(2)

value = get_metric(data, metric_path)

if value is None:
    print(f"ERROR: Metric '{metric_path}' not found in match data")
    sys.exit(2)

success = compare(value, operator, threshold)

# Output result
print(f"METRIC: {metric_path}")
print(f"VALUE: {value}")
print(f"TARGET: {operator} {threshold}")
print(f"RESULT: {'PASS' if success else 'FAIL'}")

sys.exit(0 if success else 1)
PYEOF
)

EXIT_CODE=$?
echo "$RESULT"

if [[ $EXIT_CODE -eq 0 ]]; then
    echo ""
    echo "${GREEN}OBJECTIVE MET${NC}"
else
    echo ""
    echo "${RED}OBJECTIVE NOT MET${NC}"
fi

exit $EXIT_CODE
