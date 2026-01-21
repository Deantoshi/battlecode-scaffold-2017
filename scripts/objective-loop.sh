#!/bin/bash
# objective-loop.sh - Sub-objective focused bot improvement loop
#
# Usage: ./scripts/objective-loop.sh <bot> <opponent> [map] [max-iterations]
#
# This framework breaks down the primary goal (win in ≤1500 rounds) into
# measurable sub-objectives that are worked on one at a time until achieved.
#
# Key Features:
# - LLM proposes sub-objectives with measurable success criteria
# - Framework VALIDATES objectives using actual match results
# - No flip-flopping: committed to one objective until achieved or abandoned
# - Regression protection: completed objectives are checked periodically
#
# Flow:
#   1. Check primary goal (win in ≤1500) - if met, done!
#   2. If no active sub-objective, LLM proposes one
#   3. Run match and validate sub-objective metric
#   4. If met: lock it, move to next. If not: LLM works on it.
#   5. Repeat

set -e

# Colors
RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
BLUE=$'\033[0;34m'
CYAN=$'\033[0;36m'
BOLD=$'\033[1m'
NC=$'\033[0m'

# Arguments
BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-MagicWood}"
MAX_ITERS="${4:-30}"

# Paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RALPHY="$PROJECT_DIR/ralphy/ralphy.sh"
AI_ENGINE="${AI_ENGINE:-opencode}"

# State files
STATE_DIR="$PROJECT_DIR/src/$BOT/.state"
CURRENT_OBJ="$STATE_DIR/current-objective.json"
COMPLETED_OBJ="$STATE_DIR/completed-objectives.json"
OBJ_HISTORY="$STATE_DIR/objective-history.md"
MATCH_JSON="$STATE_DIR/match-result.json"

# Validate arguments
if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    printf '%s\n' "${RED}Usage: $0 <bot> <opponent> [map] [max-iterations]${NC}"
    echo ""
    echo "Arguments:"
    echo "  bot            Your bot folder name (required)"
    echo "  opponent       Opponent bot folder name (required)"
    echo "  map            Map name (default: MagicWood)"
    echo "  max-iterations Maximum improvement cycles (default: 30)"
    echo ""
    echo "Environment variables:"
    echo "  AI_ENGINE      AI engine: opencode, claude (default: opencode)"
    echo ""
    echo "Example:"
    echo "  $0 grok_code_fast_1 examplefuncsplayer MagicWood 20"
    exit 1
fi

# Check Ralphy exists (for claude mode)
if [[ "$AI_ENGINE" == "claude" && ! -f "$RALPHY" ]]; then
    printf '%s\n' "${RED}Error: Ralphy not found at $RALPHY${NC}"
    exit 1
fi

# Initialize state directory
mkdir -p "$STATE_DIR"

# Initialize completed objectives if not exists
if [[ ! -f "$COMPLETED_OBJ" ]]; then
    echo '[]' > "$COMPLETED_OBJ"
fi

# Initialize objective history if not exists
if [[ ! -f "$OBJ_HISTORY" ]]; then
    cat > "$OBJ_HISTORY" << 'EOF'
# Objective History

## Completed Objectives

| # | Objective | Metric | Target | Achieved | Iteration | Locked |
|---|-----------|--------|--------|----------|-----------|--------|

## Failed/Abandoned Objectives

| # | Objective | Metric | Target | Best Result | Reason | Iteration |
|---|-----------|--------|--------|-------------|--------|-----------|

## Current Session Log

EOF
fi

# Print header
printf '%s\n' "${BOLD}${CYAN}"
echo "==============================================================================="
echo "                    OBJECTIVE-FOCUSED IMPROVEMENT LOOP"
echo "==============================================================================="
printf '%s\n' "${NC}"
printf '%s\n' "${BLUE}Bot:${NC}        $BOT"
printf '%s\n' "${BLUE}Opponent:${NC}   $OPPONENT"
printf '%s\n' "${BLUE}Map:${NC}        $MAP"
printf '%s\n' "${BLUE}Max Iters:${NC}  $MAX_ITERS"
printf '%s\n' "${BLUE}AI Engine:${NC}  $AI_ENGINE"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# HELPER FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

run_agent() {
    local agent_name="$1"
    local args="$2"
    local exit_code=0

    printf '%s\n' "${YELLOW}━━━ Running @${agent_name} ━━━${NC}"

    case "$AI_ENGINE" in
        opencode)
            opencode run --agent "${agent_name}" --format default -- "${args}" || exit_code=$?
            ;;
        claude)
            "$RALPHY" "@${agent_name} ${args}" || exit_code=$?
            ;;
        *)
            printf '%s\n' "${RED}Unknown AI engine: $AI_ENGINE${NC}"
            exit 1
            ;;
    esac

    if [[ $exit_code -ne 0 ]]; then
        printf '%s\n' "${RED}Agent @${agent_name} failed with exit code: $exit_code${NC}"
        return $exit_code
    fi
}

run_match() {
    printf '%s\n' "${BLUE}Running match...${NC}"
    "$SCRIPT_DIR/run-match-with-analysis.sh" "$BOT" "$OPPONENT" "$MAP"
}

validate_primary_goal() {
    # Check if we won in ≤1500 rounds
    if [[ ! -f "$MATCH_JSON" ]]; then
        return 1
    fi

    local goal_met
    goal_met=$(python3 -c "
import json
with open('$MATCH_JSON') as f:
    data = json.load(f)
print(data.get('result', {}).get('goal_met', 'NO'))
")

    if [[ "$goal_met" == "YES" ]]; then
        return 0
    fi
    return 1
}

validate_current_objective() {
    # Validate the current sub-objective using the validator script
    if [[ ! -f "$CURRENT_OBJ" ]]; then
        return 1
    fi

    local metric operator threshold
    metric=$(jq -r '.metric_path' "$CURRENT_OBJ")
    operator=$(jq -r '.operator' "$CURRENT_OBJ")
    threshold=$(jq -r '.threshold' "$CURRENT_OBJ")

    if [[ -z "$metric" || "$metric" == "null" ]]; then
        printf '%s\n' "${RED}ERROR: Invalid objective - missing metric_path${NC}"
        return 1
    fi

    # Use the validator (it will run a match internally)
    printf '%s\n' "${BLUE}Validating objective: $metric $operator $threshold${NC}"

    if "$SCRIPT_DIR/validate-objective.sh" "$BOT" "$OPPONENT" "$MAP" "$metric" "$operator" "$threshold"; then
        return 0
    else
        return 1
    fi
}

get_current_metric_value() {
    # Get the current value of the objective metric from latest match
    if [[ ! -f "$CURRENT_OBJ" || ! -f "$MATCH_JSON" ]]; then
        echo "0"
        return
    fi

    local metric
    metric=$(jq -r '.metric_path' "$CURRENT_OBJ")

    python3 - "$MATCH_JSON" "$metric" <<'PYEOF'
import json
import sys
exec(open('/home/ddean/battlecode-scaffold-2017/scripts/validate-objective.sh').read().split("PYEOF")[1].split("PYEOF")[0]) if False else None

# Inline the get_metric function
def get_metric(data, path):
    parts = path.split('.')
    if parts[0] == 'result':
        return data.get('result', {}).get(parts[1])
    if parts[0] in ('unit_alive', 'unit_produced', 'unit_lost'):
        team = parts[1]
        unit_type = parts[2]
        field_map = {'unit_alive': 'alive', 'unit_produced': 'produced', 'unit_lost': 'lost'}
        field = field_map[parts[0]]
        for unit in data.get('unit_summary', []):
            if unit.get('team') == team and unit.get('unit') == unit_type:
                return unit.get(field, 0)
        return 0
    if parts[0] == 'trees_at_round':
        target_round = int(parts[1])
        team = parts[2]
        team_key = f'team_{team.lower()}_trees'
        for entry in data.get('tree_economy', []):
            if entry.get('round', 0) >= target_round:
                return entry.get(team_key, 0)
        if data.get('tree_economy'):
            return data['tree_economy'][-1].get(team_key, 0)
        return 0
    if parts[0] == 'first_unit':
        team = parts[1]
        unit_type = parts[2]
        build_data = data.get('build_order', {}).get('teams', {}).get(team, {})
        for unit in build_data.get('units', []):
            if unit.get('unit') == unit_type:
                return unit.get('round', 9999)
        return 9999
    if parts[0] == 'damage':
        team = parts[1]
        metric = parts[2]
        return data.get('combat_analysis', {}).get('teams', {}).get(team, {}).get(metric, 0)
    return 0

with open(sys.argv[1]) as f:
    data = json.load(f)
value = get_metric(data, sys.argv[2])
print(value if value is not None else 0)
PYEOF
}

lock_objective() {
    # Mark the current objective as completed and lock it
    if [[ ! -f "$CURRENT_OBJ" ]]; then
        return
    fi

    local obj_name iteration
    obj_name=$(jq -r '.name' "$CURRENT_OBJ")
    iteration=$1

    printf '%s\n' "${GREEN}LOCKING objective: $obj_name${NC}"

    # Add to completed objectives
    local obj_data
    obj_data=$(jq --arg iter "$iteration" '. + {completed_iteration: ($iter | tonumber), locked: true}' "$CURRENT_OBJ")

    # Append to completed array
    jq --argjson obj "$obj_data" '. += [$obj]' "$COMPLETED_OBJ" > "${COMPLETED_OBJ}.tmp"
    mv "${COMPLETED_OBJ}.tmp" "$COMPLETED_OBJ"

    # Update history file
    local metric operator threshold
    metric=$(jq -r '.metric_path' "$CURRENT_OBJ")
    operator=$(jq -r '.operator' "$CURRENT_OBJ")
    threshold=$(jq -r '.threshold' "$CURRENT_OBJ")

    # Add to completed table in history
    local count
    count=$(jq 'length' "$COMPLETED_OBJ")

    # Append to objective history
    sed -i "/^## Completed Objectives/,/^## /{/^|.*|.*|.*|.*|.*|.*|$/a\\
| $count | $obj_name | $metric | $operator $threshold | YES | $iteration | $(date +%Y-%m-%d) |
}" "$OBJ_HISTORY" 2>/dev/null || true

    # Clear current objective
    rm -f "$CURRENT_OBJ"
}

increment_attempts() {
    if [[ -f "$CURRENT_OBJ" ]]; then
        jq '.attempts += 1' "$CURRENT_OBJ" > "${CURRENT_OBJ}.tmp"
        mv "${CURRENT_OBJ}.tmp" "$CURRENT_OBJ"
    fi
}

update_best_result() {
    local current_value="$1"
    if [[ -f "$CURRENT_OBJ" ]]; then
        local current_best
        current_best=$(jq -r '.best_result // 0' "$CURRENT_OBJ")

        # For most metrics, higher is better (we can add logic for "lower is better" later)
        if (( $(echo "$current_value > $current_best" | bc -l 2>/dev/null || echo 0) )); then
            jq --arg val "$current_value" '.best_result = ($val | tonumber)' "$CURRENT_OBJ" > "${CURRENT_OBJ}.tmp"
            mv "${CURRENT_OBJ}.tmp" "$CURRENT_OBJ"
        fi
    fi
}

check_max_attempts() {
    if [[ ! -f "$CURRENT_OBJ" ]]; then
        return 1
    fi

    local attempts max_attempts
    attempts=$(jq -r '.attempts // 0' "$CURRENT_OBJ")
    max_attempts=$(jq -r '.max_attempts // 5' "$CURRENT_OBJ")

    if [[ "$attempts" -ge "$max_attempts" ]]; then
        return 0  # Max attempts reached
    fi
    return 1
}

generate_objective_context() {
    # Create context file for objective-propose agent
    local output_file="$STATE_DIR/objective-context.md"
    printf '%s\n' "${BLUE}Generating objective context...${NC}"
    {
        echo "# Objective Selection Context"
        echo ""
        echo "## Primary Goal"
        echo "Win the match in ≤1500 rounds"
        echo ""
        echo "## Current Match Result"
        if [[ -f "$MATCH_JSON" ]]; then
            python3 -c "
import json
with open('$MATCH_JSON') as f:
    data = json.load(f)
r = data.get('result', {})
print(f\"- Outcome: {r.get('outcome', 'UNKNOWN')}\")
print(f\"- Rounds: {r.get('rounds', 'N/A')}\")
print(f\"- Goal Met: {r.get('goal_met', 'NO')}\")
"
        else
            echo "- No match data yet"
        fi
        echo ""
        echo "## Completed Objectives (DO NOT RE-SELECT)"
        if [[ -f "$COMPLETED_OBJ" ]]; then
            jq -r '.[] | "- \(.name): \(.metric_path) \(.operator) \(.threshold)"' "$COMPLETED_OBJ" 2>/dev/null || echo "- None yet"
        else
            echo "- None yet"
        fi
        echo ""
        echo "## Match Metrics (from last match)"
        if [[ -f "$MATCH_JSON" ]]; then
            python3 -c "
import json
with open('$MATCH_JSON') as f:
    data = json.load(f)

print('### Unit Summary (Team A = us)')
for u in data.get('unit_summary', []):
    if u.get('team') == 'A':
        print(f\"- {u.get('unit')}: produced={u.get('produced',0)}, alive={u.get('alive',0)}, lost={u.get('lost',0)}\")

print('')
print('### Economy')
for e in data.get('economy_timeline', [])[-2:]:
    r = e.get('round', 0)
    a = e.get('team_a', {})
    print(f\"- R{r}: bullets={a.get('bullets',0)}, generated={a.get('bullets_generated',0)}, spent={a.get('bullets_spent',0)}\")

print('')
print('### Trees')
for t in data.get('tree_economy', []):
    print(f\"- R{t.get('round',0)}: A={t.get('team_a_trees',0)}, B={t.get('team_b_trees',0)}\")

print('')
print('### Combat')
ca = data.get('combat_analysis', {}).get('teams', {}).get('A', {})
print(f\"- Enemy kills: {ca.get('enemy_kills', 0)}\")
print(f\"- Our deaths: {ca.get('total_deaths', 0)}\")
print(f\"- K/D ratio: {ca.get('kd_ratio', 0)}\")
"
        fi
        echo ""
        echo "## Bot Code Files"
        for f in "$PROJECT_DIR/src/$BOT"/*.java; do
            if [[ -f "$f" ]]; then
                echo "- $(basename "$f")"
            fi
        done
    } > "$output_file"
    printf '%s\n' "${GREEN}Context saved to $output_file${NC}"
}

generate_work_context() {
    # Create context file for objective-work agent
    local output_file="$STATE_DIR/work-context.md"
    printf '%s\n' "${BLUE}Generating work context...${NC}"
    {
        echo "# Objective Work Context"
        echo ""
        echo "## YOUR CURRENT OBJECTIVE"
        if [[ -f "$CURRENT_OBJ" ]]; then
            cat "$CURRENT_OBJ"
        else
            echo "ERROR: No current objective"
        fi
        echo ""
        echo "## Latest Match Metrics"
        if [[ -f "$MATCH_JSON" ]]; then
            python3 -c "
import json
with open('$MATCH_JSON') as f:
    data = json.load(f)
print(json.dumps(data.get('result', {}), indent=2))
print('')
print('Unit Summary:')
print(json.dumps(data.get('unit_summary', []), indent=2))
"
        fi
        echo ""
        echo "## Objective History"
        if [[ -f "$OBJ_HISTORY" ]]; then
            cat "$OBJ_HISTORY"
        fi
        echo ""
        echo "## Bot Code"
        for f in "$PROJECT_DIR/src/$BOT"/*.java; do
            if [[ -f "$f" ]]; then
                echo ""
                echo "### $(basename "$f")"
                echo '```java'
                cat "$f"
                echo '```'
            fi
        done
    } > "$output_file"
    printf '%s\n' "${GREEN}Context saved to $output_file${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 0: Initialize
# ═══════════════════════════════════════════════════════════════════════════════

printf '%s\n' "${BOLD}${GREEN}[PHASE 0] Initialization${NC}"

# Run initial match to get baseline metrics
if [[ ! -f "$MATCH_JSON" ]]; then
    echo "Running initial match to establish baseline..."
    run_match
fi

echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ═══════════════════════════════════════════════════════════════════════════════

for i in $(seq 1 "$MAX_ITERS"); do
    printf '%s\n' "${BOLD}${CYAN}"
    echo "==============================================================================="
    echo "                            ITERATION $i / $MAX_ITERS"
    echo "==============================================================================="
    printf '%s\n' "${NC}"

    # ─────────────────────────────────────────────────────────────────────────
    # Step 1: Run match and check PRIMARY GOAL first
    # ─────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 1] Run Match & Check Primary Goal${NC}"
    run_match

    if validate_primary_goal; then
        printf '%s\n' "${BOLD}${GREEN}"
        echo "┌─────────────────────────────────────────────────────────────────────────────┐"
        echo "│                        PRIMARY GOAL ACHIEVED!                               │"
        echo "│                        Won in ≤1500 rounds!                                 │"
        echo "└─────────────────────────────────────────────────────────────────────────────┘"
        printf '%s\n' "${NC}"

        # Generate final report
        run_agent "game-report" "--bot $BOT" || true
        exit 0
    fi

    echo "Primary goal not yet achieved. Working on sub-objectives..."
    echo ""

    # ─────────────────────────────────────────────────────────────────────────
    # Step 2: Check if we have an active sub-objective
    # ─────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 2] Sub-Objective Management${NC}"

    if [[ ! -f "$CURRENT_OBJ" ]]; then
        echo "No active sub-objective. Proposing a new one..."
        generate_objective_context
        run_agent "objective-propose" "--bot $BOT --opponent $OPPONENT --map $MAP"

        if [[ ! -f "$CURRENT_OBJ" ]]; then
            printf '%s\n' "${RED}ERROR: objective-propose agent did not create $CURRENT_OBJ${NC}"
            exit 1
        fi

        printf '%s\n' "${GREEN}New objective created:${NC}"
        jq '.' "$CURRENT_OBJ"
        echo ""
    fi

    # Display current objective
    obj_name=$(jq -r '.name' "$CURRENT_OBJ")
    obj_metric=$(jq -r '.metric_path' "$CURRENT_OBJ")
    obj_op=$(jq -r '.operator' "$CURRENT_OBJ")
    obj_thresh=$(jq -r '.threshold' "$CURRENT_OBJ")
    obj_attempts=$(jq -r '.attempts // 0' "$CURRENT_OBJ")
    obj_max=$(jq -r '.max_attempts // 5' "$CURRENT_OBJ")

    printf '%s\n' "${CYAN}Current Objective: $obj_name${NC}"
    printf '%s\n' "${CYAN}  Metric: $obj_metric $obj_op $obj_thresh${NC}"
    printf '%s\n' "${CYAN}  Attempts: $obj_attempts / $obj_max${NC}"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────
    # Step 3: Check if current objective is MET
    # ─────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 3] Validate Current Objective${NC}"

    # Run validation (this runs another match internally)
    if "$SCRIPT_DIR/validate-objective.sh" "$BOT" "$OPPONENT" "$MAP" "$obj_metric" "$obj_op" "$obj_thresh"; then
        printf '%s\n' "${GREEN}"
        echo "┌─────────────────────────────────────────────────────────────────────────────┐"
        echo "│                      SUB-OBJECTIVE ACHIEVED!                                │"
        echo "│                      $obj_name"
        echo "└─────────────────────────────────────────────────────────────────────────────┘"
        printf '%s\n' "${NC}"

        lock_objective "$i"
        echo "Objective locked. Next iteration will select a new objective."
        echo ""
        continue
    fi

    echo "Objective not yet met."

    # ─────────────────────────────────────────────────────────────────────────
    # Step 4: Check if max attempts reached
    # ─────────────────────────────────────────────────────────────────────────
    if check_max_attempts; then
        printf '%s\n' "${YELLOW}Max attempts reached for objective: $obj_name${NC}"
        printf '%s\n' "${YELLOW}Running objective-reassess agent...${NC}"

        generate_work_context
        run_agent "objective-reassess" "--bot $BOT"

        # Agent should either:
        # - Adjust the objective (lower threshold, extend rounds)
        # - Abandon it (delete current-objective.json)
        # - Retry with new approach (reset attempts)

        if [[ ! -f "$CURRENT_OBJ" ]]; then
            echo "Objective abandoned. Next iteration will propose new objective."
        fi
        continue
    fi

    # ─────────────────────────────────────────────────────────────────────────
    # Step 5: Work on the objective
    # ─────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 5] Work on Objective${NC}"

    increment_attempts
    generate_work_context
    run_agent "objective-work" "--bot $BOT"

    # Verify compilation
    printf '%s\n' "${BLUE}Verifying compilation...${NC}"
    if ! ./gradlew compileJava --quiet 2>&1 | tail -5; then
        printf '%s\n' "${RED}Compilation failed! Agent needs to fix errors.${NC}"
    else
        printf '%s\n' "${GREEN}Compilation successful.${NC}"
    fi

    echo ""
    printf '%s\n' "${BLUE}Iteration $i complete. Continuing to next iteration...${NC}"
    echo ""

done

# ═══════════════════════════════════════════════════════════════════════════════
# MAX ITERATIONS REACHED
# ═══════════════════════════════════════════════════════════════════════════════

printf '%s\n' "${BOLD}${YELLOW}"
echo "==============================================================================="
echo "                     MAX ITERATIONS REACHED ($MAX_ITERS)"
echo "==============================================================================="
printf '%s\n' "${NC}"

echo "Primary goal was NOT achieved within $MAX_ITERS iterations."
echo ""
echo "Completed sub-objectives:"
jq -r '.[] | "  - \(.name)"' "$COMPLETED_OBJ" 2>/dev/null || echo "  (none)"
echo ""

run_agent "game-report" "--bot $BOT" || true

exit 1
