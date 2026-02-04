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

# If using opencode, default to the TUI-selected model/variant when not explicitly set.
# This reads from opencode's state file: $XDG_STATE_HOME/opencode/model.json (fallback: ~/.local/state).
if [[ "$AI_ENGINE" == "opencode" ]]; then
    OPENCODE_STATE_HOME="${XDG_STATE_HOME:-$HOME/.local/state}"
    OPENCODE_MODEL_JSON="${OPENCODE_MODEL_JSON:-$OPENCODE_STATE_HOME/opencode/model.json}"
    if [[ -f "$OPENCODE_MODEL_JSON" ]]; then
        readarray -t __opencode_model_info < <(python3 - "$OPENCODE_MODEL_JSON" "${MODEL:-}" <<'PY'
import json
import sys

path = sys.argv[1]
requested = sys.argv[2] if len(sys.argv) > 2 else ""

def emit(model: str, variant: str) -> None:
    print(model or "")
    print(variant or "")

try:
    with open(path, "r") as f:
        data = json.load(f)
except Exception:
    emit("", "")
    sys.exit(0)

current = data.get("current") or {}
provider = current.get("providerID")
model_id = current.get("modelID")
current_model = f"{provider}/{model_id}" if provider and model_id else ""

if not current_model:
    recent = data.get("recent") or []
    if isinstance(recent, list) and recent:
        item = recent[0] or {}
        provider = item.get("providerID")
        model_id = item.get("modelID")
        if provider and model_id:
            current_model = f"{provider}/{model_id}"

model_for_variant = requested or current_model
variant_map = data.get("variant") or {}
variant = variant_map.get(model_for_variant, "") if model_for_variant else ""

emit(current_model, variant)
PY
        ) || true

        OPENCODE_SELECTED_MODEL="${__opencode_model_info[0]:-}"
        OPENCODE_SELECTED_VARIANT="${__opencode_model_info[1]:-}"

        if [[ -z "$MODEL" && -n "$OPENCODE_SELECTED_MODEL" ]]; then
            MODEL="$OPENCODE_SELECTED_MODEL"
        fi
        if [[ -z "$VARIANT" && -n "$OPENCODE_SELECTED_VARIANT" ]]; then
            VARIANT="$OPENCODE_SELECTED_VARIANT"
        fi
    fi
fi

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

# Count existing champions
NUM_CHAMPIONS=0
while [[ -d "src/${BOT}_champion_${NUM_CHAMPIONS}" ]]; do
    NUM_CHAMPIONS=$((NUM_CHAMPIONS + 1))
done

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
printf '%s\n' "${BLUE}Champions:${NC}  $NUM_CHAMPIONS"
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

ARCHETYPES_FILE="$STATE_DIR/archetypes.json"

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ═══════════════════════════════════════════════════════════════════════════════

for iter in $(seq 1 "$MAX_ITERS"); do
    printf '%s\n' "${BOLD}${CYAN}"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "                            ITERATION $iter / $MAX_ITERS"
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
    echo "                            Champions: $NUM_CHAMPIONS"
    fi
    echo "═══════════════════════════════════════════════════════════════════════════════"
    printf '%s\n' "${NC}"

    # ─────────────────────────────────────────────────────────────────────────────
    # Clean slate: remove .state directory from previous iteration
    # ─────────────────────────────────────────────────────────────────────────────
    if [[ -d "$STATE_DIR" ]]; then
        printf '%s\n' "${BLUE}Cleaning .state directory for fresh iteration...${NC}"
        rm -rf "$STATE_DIR"
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 0: Generate fresh archetypes for this iteration
    # ─────────────────────────────────────────────────────────────────────────────
    mkdir -p "$STATE_DIR"
    printf '%s\n' "${BOLD}${GREEN}[STEP 0] Generating 10 Variant Archetypes${NC}"

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

    run_agent "archetype-creator" "--bot $BOT --opponent $OPPONENT --map $MAP" "iter:${iter}:phase0"

    if [[ ! -f "$ARCHETYPES_FILE" ]]; then
        printf '%s\n' "${RED}Error: Archetypes file not created at $ARCHETYPES_FILE${NC}"
        exit 1
    fi
    printf '%s\n' "${GREEN}✓ Archetypes generated${NC}"
    echo ""

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
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
        printf '%s\n' "${BLUE}  (also playing against $NUM_CHAMPIONS champion(s))${NC}"
    fi
    ./scripts/run-all-variants.sh "$BOT" "$OPPONENT" "$MAP" "$NUM_CHAMPIONS"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 4: Rank results, promote winner, cleanup
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 4] Ranking results and promoting winner${NC}"
    ./scripts/rank-variants.sh "$BOT" "$OPPONENT" "$MAP" "$NUM_CHAMPIONS"

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

    # Recount champions (a new one may have been saved)
    NUM_CHAMPIONS=0
    while [[ -d "src/${BOT}_champion_${NUM_CHAMPIONS}" ]]; do
        NUM_CHAMPIONS=$((NUM_CHAMPIONS + 1))
    done

    echo ""
    printf '%s\n' "${CYAN}Winner: $WINNER (Score: $WINNER_SCORE)${NC}"
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
        printf '%s\n' "${CYAN}Champions: $NUM_CHAMPIONS${NC}"
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 5: Copy current bot to copy_bot for next iteration's opponent
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BLUE}Copying $BOT to copy_bot...${NC}"
    bash "$(cd "$(dirname "$0")" && pwd)/copy_bot.sh" "src/$BOT"

    # Stop Gradle daemon to free heap memory (~200-500MB) between iterations
    printf '%s\n' "${BLUE}Stopping Gradle daemon to free memory...${NC}"
    ./gradlew --stop 2>/dev/null || true

    printf '%s\n' "${BLUE}Iteration $iter complete. Continuing...${NC}"
    echo ""
done

# ═══════════════════════════════════════════════════════════════════════════════
# ALL ITERATIONS COMPLETE
# ═══════════════════════════════════════════════════════════════════════════════

printf '%s\n' "${BOLD}${GREEN}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                  ALL $MAX_ITERS ITERATIONS COMPLETE"
echo "═══════════════════════════════════════════════════════════════════════════════"
printf '%s\n' "${NC}"

echo "Best result saved in src/$BOT/"
exit 0
