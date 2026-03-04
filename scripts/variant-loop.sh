#!/bin/bash
# variant-loop.sh - Variant archetype optimization loop
#
# Usage: ./scripts/variant-loop.sh <bot> [opponent] [map] [max-iterations] [num-variants]
#
# This script orchestrates variant-based bot improvement:
#   1. A pi worker generates variant archetypes
#   2. Creates variant folders as copies of original
#   3. Parallel pi workers implement each archetype
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
OPPONENT="${2:-copy_bot}"
MAP="${3:-MagicWood}"
MAX_ITERS="${4:-20}"
NUM_VARIANTS="${5:-16}"

# AI runtime (pi workers)
AI_ENGINE="${AI_ENGINE:-pi}"
MODEL="${MODEL:-}"
PI_THINKING="${PI_THINKING:-}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)-$$}"

case "$AI_ENGINE" in
    pi|pi-coding-agent)
        AI_ENGINE="pi"
        ;;
    *)
        printf '%s\n' "${RED}Unsupported AI_ENGINE: $AI_ENGINE${NC}"
        printf '%s\n' "${RED}This script uses pi worker sessions only.${NC}"
        printf '%s\n' "${RED}Set AI_ENGINE=pi (or leave unset).${NC}"
        exit 1
        ;;
esac

if ! command -v pi >/dev/null 2>&1; then
    printf '%s\n' "${RED}Error: 'pi' CLI not found in PATH${NC}"
    exit 1
fi

# Validate arguments
if [[ -z "$BOT" ]]; then
    printf '%s\n' "${RED}Usage: $0 <bot> [opponent] [map] [max-iterations] [num-variants]${NC}"
    echo ""
    echo "Arguments:"
    echo "  bot            Your bot folder name (required)"
    echo "  opponent       Opponent bot folder name (default: copy_bot)"
    echo "  map            Map name (default: MagicWood)"
    echo "  max-iterations Maximum improvement cycles (default: 20)"
    echo "  num-variants   Variants generated per iteration (default: 16)"
    echo ""
    echo "Example:"
    echo "  $0 grok_code_fast_1"
    echo "  $0 grok_code_fast_1 copy_bot MagicWood 15 24"
    exit 1
fi

if [[ ! "$NUM_VARIANTS" =~ ^[0-9]+$ ]] || [[ "$NUM_VARIANTS" -lt 1 ]]; then
    printf '%s\n' "${RED}Error: num-variants must be a positive integer (got: $NUM_VARIANTS)${NC}"
    exit 1
fi

# Verify bot exists
if [[ ! -d "src/$BOT" ]]; then
    printf '%s\n' "${RED}Error: Bot folder not found: src/$BOT${NC}"
    exit 1
fi

# State directory
STATE_DIR="src/$BOT/.state"
STRATEGY_HISTORY="$STATE_DIR/strategy-history.json"
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
[[ -n "$PI_THINKING" ]] && printf '%s\n' "${BLUE}Thinking:${NC}   $PI_THINKING"
printf '%s\n' "${BLUE}Run ID:${NC}     $RUN_ID"
echo ""

# Function to run a pi worker session
run_agent() {
    local agent_name="$1"
    local args="$2"
    local context="$3"
    local exit_code=0
    local worker_prompt=""

    case "$agent_name" in
        archetype-creator)
            worker_prompt="scripts/pi-workers/archetype-creator.md"
            ;;
        archetype-implementer)
            worker_prompt="scripts/pi-workers/archetype-implementer.md"
            ;;
        *)
            printf '%s\n' "${RED}Unknown worker: $agent_name${NC}"
            return 1
            ;;
    esac

    if [[ ! -f "$worker_prompt" ]]; then
        printf '%s\n' "${RED}Worker prompt not found: $worker_prompt${NC}"
        return 1
    fi

    printf '%s\n' "${YELLOW}━━━ Running pi worker: ${agent_name} ━━━${NC}"

    local -a pi_cmd=(pi -p --no-session)
    [[ -n "$MODEL" ]] && pi_cmd+=(--model "$MODEL")
    [[ -n "$PI_THINKING" ]] && pi_cmd+=(--thinking "$PI_THINKING")

    local worker_message
    worker_message=$(cat <<EOF
You are running as worker "${agent_name}" for battlecode variant-loop.
Arguments: ${args}
Run metadata: run_id=${RUN_ID}, context=${context:-none}, bot=${BOT}, opponent=${OPPONENT}, map=${MAP}
Follow the attached worker spec exactly.
EOF
)

    "${pi_cmd[@]}" "@${worker_prompt}" "$worker_message" || exit_code=$?

    if [[ $exit_code -ne 0 ]]; then
        printf '%s\n' "${RED}Worker ${agent_name} failed with exit code: $exit_code${NC}"
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
    # (preserving strategy-history.json for feedback loop)
    # ─────────────────────────────────────────────────────────────────────────────
    if [[ -d "$STATE_DIR" ]]; then
        printf '%s\n' "${BLUE}Cleaning .state directory for fresh iteration...${NC}"
        # Back up strategy history before wiping
        STRATEGY_HISTORY_TMP=""
        if [[ -f "$STRATEGY_HISTORY" ]]; then
            STRATEGY_HISTORY_TMP="/tmp/${BOT}_strategy_history_$$"
            cp "$STRATEGY_HISTORY" "$STRATEGY_HISTORY_TMP"
        fi
        rm -rf "$STATE_DIR"
        mkdir -p "$STATE_DIR"
        # Restore strategy history
        if [[ -n "$STRATEGY_HISTORY_TMP" && -f "$STRATEGY_HISTORY_TMP" ]]; then
            mv "$STRATEGY_HISTORY_TMP" "$STRATEGY_HISTORY"
            printf '%s\n' "${BLUE}✓ Restored strategy history from previous iterations${NC}"
        fi
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 0: Generate fresh archetypes for this iteration
    # ─────────────────────────────────────────────────────────────────────────────
    mkdir -p "$STATE_DIR"
    printf '%s\n' "${BOLD}${GREEN}[STEP 0] Generating $NUM_VARIANTS Variant Archetypes${NC}"

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

    run_agent "archetype-creator" "--bot $BOT --opponent $OPPONENT --map $MAP --num-variants $NUM_VARIANTS" "iter:${iter}:phase0"

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
    ./scripts/create-16-variants.sh "$BOT" "$NUM_VARIANTS"

    # Copy bot-code-snapshot.txt to each variant's .state folder
    for v in $(seq 1 $NUM_VARIANTS); do
        VARIANT_STATE_DIR="src/${BOT}_v${v}/.state"
        mkdir -p "$VARIANT_STATE_DIR"
        cp "$STATE_DIR/bot-code-snapshot.txt" "$VARIANT_STATE_DIR/"
    done
    printf '%s\n' "${BLUE}✓ Copied bot-code-snapshot.txt to all variant .state folders${NC}"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 2: Implement each archetype (fresh pi worker per variant)
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 2] Implementing archetypes into variants (2 at a time)${NC}"

    PARALLEL=2
    for batch_start in $(seq 1 $PARALLEL $NUM_VARIANTS); do
        batch_end=$((batch_start + PARALLEL - 1))
        if [[ $batch_end -gt $NUM_VARIANTS ]]; then
            batch_end=$NUM_VARIANTS
        fi

        PIDS=()
        BATCH_VARIANTS=()

        for v in $(seq $batch_start $batch_end); do
            printf '%s\n' "${YELLOW}━━━ Launching Variant $v / $NUM_VARIANTS ━━━${NC}"

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

            # Save archetype to variant-specific state dir to avoid race conditions
            VARIANT_STATE_DIR="src/${BOT}_v${v}/.state"
            mkdir -p "$VARIANT_STATE_DIR"
            echo "$ARCHETYPE" > "$VARIANT_STATE_DIR/current-archetype.json"
            # Also keep a copy in the main state dir for the worker to find
            echo "$ARCHETYPE" > "$STATE_DIR/current-archetype-v${v}.json"

            # Run worker in background
            (
                run_agent "archetype-implementer" "--bot $BOT --variant $v --opponent $OPPONENT" "iter:${iter}:v${v}"
            ) &
            PIDS+=($!)
            BATCH_VARIANTS+=($v)
        done

        printf '%s\n' "${BLUE}Waiting for variants ${BATCH_VARIANTS[*]} to complete...${NC}"

        # Wait for all workers in this batch
        BATCH_FAILED=0
        for i in "${!PIDS[@]}"; do
            pid=${PIDS[$i]}
            v=${BATCH_VARIANTS[$i]}
            if ! wait "$pid"; then
                printf '%s\n' "${RED}Warning: Variant $v worker exited with error${NC}"
                BATCH_FAILED=$((BATCH_FAILED + 1))
            fi
        done

        # Verify compilation for this batch
        for v in "${BATCH_VARIANTS[@]}"; do
            printf '%s\n' "${BLUE}Verifying compilation for ${BOT}_v${v}...${NC}"
            if ! ./gradlew compileJava -q 2>&1 | tail -5; then
                printf '%s\n' "${RED}Warning: Compilation may have issues${NC}"
            fi
        done

        printf '%s\n' "${GREEN}Batch (variants ${BATCH_VARIANTS[*]}) complete${NC}"
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
    # Step 4b: Update strategy history with this iteration's results
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BLUE}Updating strategy history...${NC}"
    python3 << 'HISTORY_EOF' - "$STATE_DIR" "$iter"
import json
import os
import sys

state_dir = sys.argv[1]
iteration = int(sys.argv[2])

archetypes_file = os.path.join(state_dir, "archetypes.json")
results_file = os.path.join(state_dir, "variant-results.json")
history_file = os.path.join(state_dir, "strategy-history.json")

# Load archetypes
archetypes = []
if os.path.exists(archetypes_file):
    with open(archetypes_file) as f:
        data = json.load(f)
    archetypes = data.get("archetypes", data)
    if not isinstance(archetypes, list):
        archetypes = []

# Load results
results_data = {}
if os.path.exists(results_file):
    with open(results_file) as f:
        results_data = json.load(f)

# Build archetype lookup: v1 -> archetype[0], v2 -> archetype[1], etc.
arch_lookup = {}
for i, arch in enumerate(archetypes):
    arch_lookup[f"v{i+1}"] = arch


def generate_post_mortem(r):
    """Generate a concise tactical summary from match results."""
    primary_won = r.get("primary_won", False)
    rounds = r.get("primary_rounds", 3000)

    matches = r.get("matches", [])
    primary_match = next(
        (m for m in matches if m.get("opponent_label") == "opponent"), {}
    )
    kills = primary_match.get("enemy_kills", 0)
    vp = primary_match.get("victory_points", 0)

    # Build outcome phrase
    if primary_won:
        if rounds <= 1500:
            outcome = f"Won fast ({rounds} rounds)"
        elif rounds <= 2500:
            outcome = f"Won ({rounds} rounds)"
        else:
            outcome = f"Won slowly ({rounds} rounds)"
    else:
        outcome = f"Lost ({rounds} rounds)"

    # Add key stats
    stats = []
    if kills > 0:
        stats.append(f"{kills} kills")
    if vp > 0:
        stats.append(f"{vp} VP")
    if stats:
        outcome += f" [{', '.join(stats)}]"

    # Add tactical insight
    if primary_won and rounds <= 1500:
        insight = "Effective fast strategy."
    elif primary_won and rounds > 2500:
        insight = "Won but too slowly; needs faster execution."
    elif primary_won:
        insight = "Solid win; push for faster finish."
    elif not primary_won and kills >= 5:
        insight = "Good combat but couldn't close."
    elif not primary_won and vp >= 500:
        insight = "Strong VP progress but fell short."
    elif not primary_won and vp >= 200:
        insight = "Some VP progress; not enough to win."
    else:
        insight = "Strategy ineffective."

    return f"{outcome}. {insight}"


# Build history entry with archetype info + results
variants = []
for r in results_data.get("results", []):
    name = r["name"]
    arch = arch_lookup.get(name, {})

    entry = {
        "id": name,
        "score": r.get("total_score", 0),
        "primary_won": r.get("primary_won", False),
        "primary_rounds": r.get("primary_rounds", 3000),
        "post_mortem": generate_post_mortem(r),
    }

    if arch:
        entry["archetype_name"] = arch.get("name", "Unknown")
        entry["type"] = arch.get("type", "exploration")
        entry["win_condition"] = arch.get("win_condition", "unknown")
        entry["philosophy"] = arch.get("philosophy", "")
    elif name == "original":
        entry["archetype_name"] = "Original (baseline)"
        entry["type"] = "baseline"

    variants.append(entry)

history_entry = {
    "iteration": iteration,
    "winner": results_data.get("winner", "original"),
    "winner_score": results_data.get("winner_score", 0),
    "promoted": results_data.get("should_promote", False),
    "variants": variants,
}

# Load existing history or create new
history = {"iterations": []}
if os.path.exists(history_file):
    try:
        with open(history_file) as f:
            history = json.load(f)
    except (json.JSONDecodeError, IOError):
        history = {"iterations": []}

history["iterations"].append(history_entry)

with open(history_file, "w") as f:
    json.dump(history, f, indent=2)

n = len(history["iterations"])
print(f"Strategy history updated: {n} iteration(s) recorded")
HISTORY_EOF

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
