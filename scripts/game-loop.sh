#!/bin/bash
# game-loop.sh - Infinite bot improvement loop using modular agents
#
# Usage: ./scripts/game-loop.sh <bot> <opponent> [map] [max-iterations]
#
# This script orchestrates the bot improvement process by calling
# individual agents through Ralphy, each with a fresh context session.
#
# Agents called:
#   1. game-init       - Initialize (once)
#   2. game-run-match  - Run match
#   3. game-analyze    - Analyze results & plan
#   4. game-implement  - Implement changes
#   5. game-report     - Final report (when goal achieved)

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Arguments
BOT="${1:-}"
OPPONENT="${2:-}"
MAP="${3:-MagicWood}"
MAX_ITERS="${4:-20}"

# Ralphy path (adjust if needed)
RALPHY="./ralphy/ralphy.sh"

# AI Engine (default to opencode, can be changed)
AI_ENGINE="${AI_ENGINE:-opencode}"

# Validate arguments
if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    echo -e "${RED}Usage: $0 <bot> <opponent> [map] [max-iterations]${NC}"
    echo ""
    echo "Arguments:"
    echo "  bot            Your bot folder name (required)"
    echo "  opponent       Opponent bot folder name (required)"
    echo "  map            Map name (default: MagicWood)"
    echo "  max-iterations Maximum improvement cycles (default: 20)"
    echo ""
    echo "Environment variables:"
    echo "  AI_ENGINE      AI engine to use: opencode, claude, cursor (default: opencode)"
    echo ""
    echo "Example:"
    echo "  $0 grok_code_fast_1 examplefuncsplayer MagicWood 15"
    exit 1
fi

# Check Ralphy exists
if [[ ! -f "$RALPHY" ]]; then
    echo -e "${RED}Error: Ralphy not found at $RALPHY${NC}"
    exit 1
fi

# State directory
STATE_DIR="src/$BOT/.state"

# Print header
echo -e "${BOLD}${CYAN}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                         GAME IMPROVEMENT LOOP"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo -e "${NC}"
echo -e "${BLUE}Bot:${NC}        $BOT"
echo -e "${BLUE}Opponent:${NC}   $OPPONENT"
echo -e "${BLUE}Map:${NC}        $MAP"
echo -e "${BLUE}Max Iters:${NC}  $MAX_ITERS"
echo -e "${BLUE}AI Engine:${NC}  $AI_ENGINE"
echo ""

# Function to run an agent
run_agent() {
    local agent_name="$1"
    local args="$2"

    echo -e "${YELLOW}━━━ Running @${agent_name} ━━━${NC}"

    case "$AI_ENGINE" in
        opencode)
            "$RALPHY" "@${agent_name} ${args}" --opencode
            ;;
        claude)
            "$RALPHY" "@${agent_name} ${args}"
            ;;
        cursor)
            "$RALPHY" "@${agent_name} ${args}" --cursor
            ;;
        *)
            echo -e "${RED}Unknown AI engine: $AI_ENGINE${NC}"
            exit 1
            ;;
    esac
}

# Function to check goal status from state file
check_goal() {
    local status_file="$STATE_DIR/goal-status.txt"
    if [[ -f "$status_file" ]]; then
        cat "$status_file"
    else
        echo "UNKNOWN"
    fi
}

# Function to VALIDATE goal by actually running a match and checking output
# This ensures we don't falsely claim success
validate_goal() {
    echo -e "${YELLOW}━━━ Validating Goal (running verification match) ━━━${NC}"

    # Run the match and check for GOAL_MET=YES in output
    if ./scripts/run-match-with-analysis.sh "$BOT" "$OPPONENT" "$MAP" 2>&1 | grep -q 'GOAL_MET=YES'; then
        echo -e "${GREEN}✓ Validation PASSED: GOAL_MET=YES confirmed${NC}"
        return 0
    else
        echo -e "${RED}✗ Validation FAILED: GOAL_MET=YES not found${NC}"
        return 1
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# PHASE 0: Initialize (only if history doesn't exist)
# ═══════════════════════════════════════════════════════════════════════════════

echo -e "${BOLD}${GREEN}[PHASE 0] Initialization${NC}"

if [[ ! -f "src/$BOT/iteration-history.md" ]]; then
    echo "No history file found. Running initialization..."
    run_agent "game-init" "--bot $BOT --opponent $OPPONENT --maps $MAP"
else
    echo "History file exists. Skipping initialization."
    mkdir -p "$STATE_DIR"
fi

echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ═══════════════════════════════════════════════════════════════════════════════

for i in $(seq 1 "$MAX_ITERS"); do
    echo -e "${BOLD}${CYAN}"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "                            ITERATION $i / $MAX_ITERS"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo -e "${NC}"

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 1: Run Match (fresh context)
    # ─────────────────────────────────────────────────────────────────────────────
    echo -e "${BOLD}${GREEN}[STEP 1] Run Match${NC}"
    run_agent "game-run-match" "--bot $BOT --opponent $OPPONENT --maps $MAP"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 2: Analyze (fresh context)
    # ─────────────────────────────────────────────────────────────────────────────
    echo -e "${BOLD}${GREEN}[STEP 2] Analyze Results${NC}"
    run_agent "game-analyze" "--bot $BOT --opponent $OPPONENT --maps $MAP"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 3: Check Goal Status
    # ─────────────────────────────────────────────────────────────────────────────
    GOAL_STATUS=$(check_goal)

    if [[ "$GOAL_STATUS" == "ACHIEVED" ]]; then
        echo -e "${BOLD}${CYAN}[STEP 3] Goal Status: ACHIEVED (pending validation)${NC}"
        echo ""

        # VALIDATION: Actually run the match and confirm GOAL_MET=YES
        if validate_goal; then
            echo ""
            echo -e "${BOLD}${GREEN}"
            echo "┌─────────────────────────────────────────────────────────────────────────────┐"
            echo "│                           GOAL ACHIEVED!                                    │"
            echo "└─────────────────────────────────────────────────────────────────────────────┘"
            echo -e "${NC}"

            # Generate final report
            echo -e "${BOLD}${GREEN}[FINAL] Generating Report${NC}"
            run_agent "game-report" "--bot $BOT"

            echo ""
            echo -e "${GREEN}Successfully improved $BOT to beat $OPPONENT in ≤1500 rounds!${NC}"
            exit 0
        else
            # Validation failed - the LLM claimed success but match doesn't confirm it
            echo ""
            echo -e "${YELLOW}Validation failed. Analyze agent reported ACHIEVED but match shows otherwise.${NC}"
            echo -e "${YELLOW}Continuing to next iteration...${NC}"
            # Reset goal status so we continue
            echo "CONTINUE" > "$STATE_DIR/goal-status.txt"
        fi
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 4: Implement Improvement (fresh context)
    # ─────────────────────────────────────────────────────────────────────────────
    echo -e "${BOLD}${GREEN}[STEP 3] Implement Improvement${NC}"
    run_agent "game-implement" "--bot $BOT"
    echo ""

    echo -e "${BLUE}Iteration $i complete. Starting next iteration...${NC}"
    echo ""
done

# ═══════════════════════════════════════════════════════════════════════════════
# MAX ITERATIONS REACHED
# ═══════════════════════════════════════════════════════════════════════════════

echo -e "${BOLD}${YELLOW}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     MAX ITERATIONS REACHED ($MAX_ITERS)"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo -e "${NC}"

echo "Goal was NOT achieved within $MAX_ITERS iterations."
echo ""

# Still generate a report
echo -e "${BOLD}${GREEN}[FINAL] Generating Report${NC}"
run_agent "game-report" "--bot $BOT"

echo ""
echo -e "${YELLOW}Review src/$BOT/iteration-history.md for details on what was tried.${NC}"
exit 1
