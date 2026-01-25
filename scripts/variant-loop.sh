#!/bin/bash
# variant-loop.sh - Variant archetype optimization loop
#
# Usage: ./scripts/variant-loop.sh <bot> <opponent> [map] [max-iterations]
#
# This script orchestrates variant-based bot improvement:
#   1. Archetype creator agent generates 10 variant archetypes (once)
#   2. Creates 10 variant folders as copies of original
#   3. For each variant, an agent implements its archetype
#   4. All variants + original run against opponent
#   5. Best performer is promoted if better than original
#   6. Loop until goal achieved or max iterations

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
MAX_ITERS="${4:-20}"
NUM_VARIANTS=10

# AI Engine
AI_ENGINE="${AI_ENGINE:-opencode}"

# Model override (e.g., google/antigravity-claude-opus-4-5-thinking)
MODEL="${MODEL:-}"
VARIANT="${VARIANT:-}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)-$$}"

# Validate arguments
if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    printf '%s\n' "${RED}Usage: $0 <bot> <opponent> [map] [max-iterations]${NC}"
    echo ""
    echo "Arguments:"
    echo "  bot            Your bot folder name (required)"
    echo "  opponent       Opponent bot folder name (required)"
    echo "  map            Map name (default: MagicWood)"
    echo "  max-iterations Maximum improvement cycles (default: 20)"
    echo ""
    echo "Example:"
    echo "  $0 grok_code_fast_1 copy_bot MagicWood 15"
    exit 1
fi

# Verify bot exists
if [[ ! -d "src/$BOT" ]]; then
    printf '%s\n' "${RED}Error: Bot folder not found: src/$BOT${NC}"
    exit 1
fi

# State directory
STATE_DIR="src/$BOT/.state"
mkdir -p "$STATE_DIR"

# Print header
printf '%s\n' "${BOLD}${CYAN}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                      VARIANT ARCHETYPE OPTIMIZER"
echo "═══════════════════════════════════════════════════════════════════════════════"
printf '%s\n' "${NC}"
printf '%s\n' "${BLUE}Bot:${NC}        $BOT"
printf '%s\n' "${BLUE}Opponent:${NC}   $OPPONENT"
printf '%s\n' "${BLUE}Map:${NC}        $MAP"
printf '%s\n' "${BLUE}Max Iters:${NC}  $MAX_ITERS"
printf '%s\n' "${BLUE}Variants:${NC}   $NUM_VARIANTS"
printf '%s\n' "${BLUE}AI Engine:${NC}  $AI_ENGINE"
[[ -n "$MODEL" ]] && printf '%s\n' "${BLUE}Model:${NC}      $MODEL"
[[ -n "$VARIANT" ]] && printf '%s\n' "${BLUE}Variant:${NC}    $VARIANT"
echo ""

# Function to run an agent
run_agent() {
    local agent_name="$1"
    local args="$2"
    local context="$3"
    local exit_code=0

    printf '%s\n' "${YELLOW}━━━ Running @${agent_name} ━━━${NC}"

    case "$AI_ENGINE" in
        opencode)
            local model_args=""
            [[ -n "$MODEL" ]] && model_args="--model $MODEL"
            [[ -n "$VARIANT" ]] && model_args="$model_args --variant $VARIANT"
            local title="variant-loop:${RUN_ID}:${BOT}:${OPPONENT}:${MAP}:${agent_name}"
            [[ -n "$context" ]] && title="${title}:${context}"
            opencode run --agent "${agent_name}" --title "${title}" $model_args --format default -- "${args}" || exit_code=$?
            ;;
        claude)
            ./ralphy/ralphy.sh "@${agent_name} ${args}" || exit_code=$?
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

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 0: Generate Archetypes (only once)
# ═══════════════════════════════════════════════════════════════════════════════

ARCHETYPES_FILE="$STATE_DIR/archetypes.json"

if [[ ! -f "$ARCHETYPES_FILE" ]]; then
    printf '%s\n' "${BOLD}${GREEN}[PHASE 0] Generating 10 Variant Archetypes${NC}"

    # Prepare context for archetype creator
    {
        echo "# Bot Code"
        echo ""
        for f in src/"$BOT"/*.java; do
            if [[ -f "$f" ]]; then
                echo "=== FILE: $(basename "$f") ==="
                cat "$f"
                echo ""
            fi
        done
    } > "$STATE_DIR/bot-code-snapshot.txt"

    run_agent "archetype-creator" "--bot $BOT --opponent $OPPONENT --map $MAP" "phase0"

    if [[ ! -f "$ARCHETYPES_FILE" ]]; then
        printf '%s\n' "${RED}Error: Archetypes file not created at $ARCHETYPES_FILE${NC}"
        exit 1
    fi
    printf '%s\n' "${GREEN}✓ Archetypes generated${NC}"
else
    printf '%s\n' "${BLUE}[PHASE 0] Archetypes already exist, skipping generation${NC}"
fi

echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ═══════════════════════════════════════════════════════════════════════════════

for iter in $(seq 1 "$MAX_ITERS"); do
    printf '%s\n' "${BOLD}${CYAN}"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "                            ITERATION $iter / $MAX_ITERS"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    printf '%s\n' "${NC}"

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 1: Create variant folders
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 1] Creating $NUM_VARIANTS variant folders${NC}"
    ./scripts/create-10-variants.sh "$BOT"

    # Copy bot-code-snapshot.txt to each variant's .state folder
    for v in $(seq 1 $NUM_VARIANTS); do
        VARIANT_STATE_DIR="src/${BOT}_v${v}/.state"
        mkdir -p "$VARIANT_STATE_DIR"
        cp "$STATE_DIR/bot-code-snapshot.txt" "$VARIANT_STATE_DIR/"
    done
    printf '%s\n' "${BLUE}✓ Copied bot-code-snapshot.txt to all variant .state folders${NC}"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 2: Implement each archetype (fresh agent per variant)
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 2] Implementing archetypes into variants${NC}"

    for v in $(seq 1 $NUM_VARIANTS); do
        printf '%s\n' "${YELLOW}━━━ Implementing Variant $v / $NUM_VARIANTS ━━━${NC}"

        # Extract this variant's archetype from JSON
        ARCHETYPE=$(python3 -c "
import json
with open('$ARCHETYPES_FILE', 'r') as f:
    data = json.load(f)
archetypes = data.get('archetypes', data)
if isinstance(archetypes, list):
    print(json.dumps(archetypes[$v - 1]))
else:
    print(json.dumps(archetypes.get('v$v', {})))
" 2>/dev/null || echo "{}")

        # Save archetype for this variant
        echo "$ARCHETYPE" > "$STATE_DIR/current-archetype.json"

        run_agent "archetype-implementer" "--bot $BOT --variant $v --opponent $OPPONENT" "iter:${iter}:v${v}"

        # Verify compilation
        printf '%s\n' "${BLUE}Verifying compilation for ${BOT}_v${v}...${NC}"
        if ! ./gradlew compileJava -q 2>&1 | tail -5; then
            printf '%s\n' "${RED}Warning: Compilation may have issues${NC}"
        fi
    done
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 3: Run all variants against opponent
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 3] Running all variants against $OPPONENT${NC}"
    ./scripts/run-all-variants.sh "$BOT" "$OPPONENT" "$MAP"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 4: Rank results, promote winner, cleanup
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 4] Ranking results and promoting winner${NC}"
    ./scripts/rank-variants.sh "$BOT" "$OPPONENT" "$MAP"

    # Read results
    RESULTS_FILE="$STATE_DIR/variant-results.json"
    if [[ ! -f "$RESULTS_FILE" ]]; then
        printf '%s\n' "${RED}Error: Results file not found${NC}"
        exit 1
    fi

    # Parse winner info
    WINNER=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('winner', 'original'))
")

    WINNER_SCORE=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('winner_score', 0))
")

    GOAL_MET=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('goal_met', 'NO'))
")

    echo ""
    printf '%s\n' "${CYAN}Winner: $WINNER (Score: $WINNER_SCORE)${NC}"

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 5: Check if goal achieved
    # ─────────────────────────────────────────────────────────────────────────────
    if [[ "$GOAL_MET" == "YES" ]]; then
        printf '%s\n' "${BOLD}${GREEN}"
        echo "┌─────────────────────────────────────────────────────────────────────────────┐"
        echo "│                           GOAL ACHIEVED!                                    │"
        echo "└─────────────────────────────────────────────────────────────────────────────┘"
        printf '%s\n' "${NC}"
        echo ""
        echo "Bot $BOT has achieved victory in ≤1500 rounds!"
        echo "Winner: $WINNER"
        echo "Iterations: $iter"
        exit 0
    fi

    printf '%s\n' "${BLUE}Iteration $iter complete. Continuing...${NC}"
    echo ""
done

# ═══════════════════════════════════════════════════════════════════════════════
# MAX ITERATIONS REACHED
# ═══════════════════════════════════════════════════════════════════════════════

printf '%s\n' "${BOLD}${YELLOW}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     MAX ITERATIONS REACHED ($MAX_ITERS)"
echo "═══════════════════════════════════════════════════════════════════════════════"
printf '%s\n' "${NC}"

echo "Goal was NOT achieved within $MAX_ITERS iterations."
echo "Best result saved in src/$BOT/"
exit 1
