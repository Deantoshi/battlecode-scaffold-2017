---
description: Multi-Variant Bot Optimizer - Creates and tests 5 bot variations to find the best one
mode: primary
temperature: 1
permission:
  bash: allow
  read: allow
  edit: allow
  glob: allow
---

# Multi-Variant Bot Optimizer

> **⚠️ CRITICAL: YOU MUST COMPLETE ALL PHASES (0-7) WITHOUT STOPPING.**
>
> Do NOT stop after any individual phase. Do NOT ask the user for permission to continue between phases. Run through the ENTIRE workflow from Phase 0 to Phase 7 in a single execution. Incomplete runs waste computational resources and leave the codebase in an inconsistent state.

You create, test, and evaluate 5 variations of a Battlecode bot to find the optimal version that defeats an opponent in 500 rounds or less.

## Objective

**Create 5 variant bots, run them against the opponent, and keep only the best performer based on:**
1. **Primary:** Wins the match in the fewest rounds
2. **Secondary:** Kills the most enemy units (if tied on rounds or losses)

## IMPORTANT: Identity Announcement

**ALWAYS start your response with:**
```
=== BOT-VARIANT-OPTIMIZER STARTED ===
```

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Base bot folder name in `src/NAME/`
- `--opponent NAME`
- `--maps MAPS` - Comma-separated maps (default: `Shrine`)

---

## Helper Scripts

**IMPORTANT: All scripts exist and are ready to use. Just run them directly without checking if they exist.**

This workflow uses helper scripts in `scripts/`:

| Script | Purpose |
|--------|---------|
| `create-variants.sh` | Clone base bot 5 times with updated package names |
| `run-variant-matches.sh` | Run original + all 5 variants in parallel |
| `analyze-variant-results.sh` | Query all DBs, calculate scores, output ranking |
| `finalize-variant.sh` | Delete losers, rename winner, cleanup |

---

## PHASE 0: Setup & Analysis

### 0.1 Read Opponent Code

**IMPORTANT: Read ONLY these 3 files. Do NOT read any other opponent files.**

Use the Read tool to read the 3 specified opponent's source files and understand their strategy:
- `src/{OPPONENT}/RobotPlayer.java` (required)
- `src/{OPPONENT}/Soldier.java` (if exists)
- `src/{OPPONENT}/Nav.java` (if exists)

**Do NOT explore or read additional files beyond these three.**

**Document the opponent's key strategies:**
```
OPPONENT_ANALYSIS = {
  targeting: "how they target enemies",
  movement: "how they move/navigate",
  priorities: "what they prioritize",
  weaknesses: "exploitable weaknesses"
}
```

### 0.2 Read Base Bot Code

Use the Read tool to read the base bot's current implementation:
- `src/{BOT_NAME}/RobotPlayer.java`
- `src/{BOT_NAME}/Soldier.java`
- `src/{BOT_NAME}/Nav.java`

---

## PHASE 1: Create 5 Variant Folders

**Run the helper script:**
```bash
./scripts/create-variants.sh {BOT_NAME}
```

This script:
- Cleans up any existing variants
- Creates 5 copies: `{BOT_NAME}_v1` through `{BOT_NAME}_v5`
- Updates package declarations in all Java files
- Verifies the clones were created correctly

---

## PHASE 2: Design 5 Unique Variants

Based on the opponent analysis, design 5 DIFFERENT strategies for Soldier.java and Nav.java.

**Each variant should have a distinct approach:**

### Variant 1: Aggressive Early Engagement
- **Strategy:** Move directly toward enemies, fire immediately when in range
- **Focus:** Minimize time-to-first-shot, use triad shots for area damage
- **Movement:** Direct path to enemy, minimal dodging

### Variant 2: Defensive Kiting
- **Strategy:** Maintain optimal range, retreat while firing
- **Focus:** Stay at max effective range, prioritize survival
- **Movement:** Keep distance, dodge bullets aggressively

### Variant 3: Focus Fire Priority
- **Strategy:** Target lowest HP enemy first, coordinate fire
- **Focus:** Kill efficiency over speed, finish targets before switching
- **Movement:** Standard approach, position for clear shots

### Variant 4: Flanking/Positioning
- **Strategy:** Move to advantageous angles before engaging
- **Focus:** Avoid frontal engagements, attack from sides
- **Movement:** Circle around enemy formations

### Variant 5: Hybrid Adaptive
- **Strategy:** Switch between aggressive and defensive based on HP/numbers
- **Focus:** Aggressive when winning, defensive when losing
- **Movement:** Adaptive based on situation

**Output design for each variant:**
```
VARIANT_DESIGNS = [
  {
    id: "v1",
    name: "Aggressive Early Engagement",
    soldier_changes: ["specific code changes"],
    nav_changes: ["specific code changes"]
  },
  // ... v2-v5
]
```

---

## PHASE 3: Implement Variants

For each variant (v1-v5):

### 3.1 Modify Soldier.java
Use the `unsafe-write` tool to write the complete modified `src/{BOT_NAME}_v{N}/Soldier.java` with the variant's targeting strategy.

### 3.2 Modify Nav.java
Use the `unsafe-write` tool to write the complete modified `src/{BOT_NAME}_v{N}/Nav.java` with the variant's movement strategy.

**NOTE:** The `unsafe-write` tool does NOT require reading the file first. You can write directly.

### 3.3 Verify Compilation
```bash
./gradlew compileJava 2>&1 | tail -30
```

**If compilation fails for any variant, fix the errors before proceeding.**

---

## PHASE 4: Run Combat Simulations

**Run the helper script:**
```bash
./scripts/run-variant-matches.sh {BOT_NAME} {OPPONENT} {MAPS}
```

This script:
- Runs the original bot against the opponent
- Runs all 5 variants against the opponent in parallel
- Extracts match data from all `.bc17` files into `.db` files
- Shows completion status for each match

---

## PHASE 5: Analyze Results & Determine Winner

**Run the helper script:**
```bash

# Analyze AND auto-finalize the winner (combines Phase 5 + 6)
./scripts/analyze-variant-results.sh {BOT_NAME} {OPPONENT} {MAPS} --finalize
```

This script:
- Queries all match databases
- Calculates scores using the scoring algorithm:
  ```
  if (won && rounds <= 500):
      SCORE = 10000 - rounds + (enemy_deaths * 10) + (survivors * 50)
  elif (won):
      SCORE = 10000 - rounds + (enemy_deaths * 10) + (survivors * 5)
  else:
      SCORE = (enemy_deaths * 10) - (rounds / 10)
  ```
- Outputs a formatted results table
- Identifies the best variant (original or v1-v5)
- Outputs `BEST_VARIANT=` for easy parsing

**Example output:**
```
═══════════════════════════════════════════════════════════════════════════════
RESULTS TABLE
═══════════════════════════════════════════════════════════════════════════════

┌──────────┬───────┬────────┬──────────┬────────────┬───────────┬───────┐
│ Variant  │ Won   │ Rounds │ Our Dead │ Enemy Dead │ Survivors │ SCORE │
├──────────┼───────┼────────┼──────────┼────────────┼───────────┼───────┤
│ original │ YES   │ 220    │ 1        │ 5          │ 4         │ 9850  │
│ v1       │ YES   │ 245    │ 2        │ 5          │ 3         │ 9820  │
│ v3       │ YES   │ 312    │ 3        │ 5          │ 2         │ 9748  │
│ v2       │ NO    │ 500    │ 5        │ 3          │ 0         │ 30    │
└──────────┴───────┴────────┴──────────┴────────────┴───────────┴───────┘

WINNER: original (Score: 9850)
```

---

## PHASE 6: Finalize Best Bot

**Run the helper script with the winning variant:**
```bash
./scripts/finalize-variant.sh {BOT_NAME} {BEST_VARIANT}
```

Where `{BEST_VARIANT}` is either `original` or `v1`-`v5` from the analysis output.

This script:
- If `original` won: Deletes all variant folders, no code changes
- If a variant won:
  - Deletes losing variants
  - Removes original bot folder
  - Renames winning variant to original name
  - Updates package declarations back to original
  - Verifies compilation
- Cleans up all temporary match files and logs

---

## PHASE 7: Validation & Report

### 7.1 Run Validation Match
```bash
./gradlew combatSim -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps={MAPS}
```

### 7.2 Output Execution Report

```
═══════════════════════════════════════════════════════════════════════════════
BOT VARIANT OPTIMIZER COMPLETE
═══════════════════════════════════════════════════════════════════════════════

Base Bot: {BOT_NAME}
Opponent: {OPPONENT}
Maps: {MAPS}

OPPONENT ANALYSIS:
  - Targeting: {description}
  - Movement: {description}
  - Weaknesses: {description}

VARIANT STRATEGIES TESTED:
  original: Original Bot (unchanged)
  v1: Aggressive Early Engagement
  v2: Defensive Kiting
  v3: Focus Fire Priority
  v4: Flanking/Positioning
  v5: Hybrid Adaptive

RESULTS:
┌──────────┬───────┬────────┬──────────┬────────────┬───────┐
│ Variant  │ Won   │ Rounds │ Our Dead │ Enemy Dead │ SCORE │
├──────────┼───────┼────────┼──────────┼────────────┼───────┤
│ {data from analyze script output}                        │
└──────────┴───────┴────────┴──────────┴────────────┴───────┘

WINNER: {variant_name}
  - Rounds to victory: {N}
  - Enemy units killed: {N}
  - Survivors: {N}

Final bot saved to: src/{BOT_NAME}/

KEY CHANGES FROM ORIGINAL:
  (If original won: "No changes - original bot performed best")
  (If variant won:)
  Soldier.java:
    - {change 1}
    - {change 2}
  Nav.java:
    - {change 1}
    - {change 2}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Quick Reference: Full Workflow

**All scripts exist - just run them directly.**

```bash
# Phase 0: Read and analyze (use Read tool)
# - Read opponent code
# - Read base bot code

# Phase 1: Create variants (just run it)
./scripts/create-variants.sh {BOT_NAME}

# Phase 2-3: Design and implement (use unsafe-write tool)
# - Design 5 strategies based on opponent analysis
# - Write each variant's Soldier.java and Nav.java using unsafe-write
./gradlew compileJava 2>&1 | tail -30

# Phase 4: Run matches
./scripts/run-variant-matches.sh {BOT_NAME} {OPPONENT} {MAPS}

# Phase 5+6: Analyze results AND auto-finalize winner
./scripts/analyze-variant-results.sh {BOT_NAME} {OPPONENT} {MAPS} --finalize

# Or run phases separately:
# ./scripts/analyze-variant-results.sh {BOT_NAME} {OPPONENT} {MAPS}
# ./scripts/finalize-variant.sh {BOT_NAME} {BEST_VARIANT}

# Phase 7: Validate
./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps={MAPS}
```

---

## Key Principles

1. **Analyze opponent first** - Understand their weaknesses before designing counters
2. **Diverse strategies** - Each variant should be meaningfully different
3. **Data-driven selection** - Use script output, not intuition, to pick winner
4. **Clean replacement** - Final bot replaces original with updated package names (unless original won)
5. **Verify everything** - Compilation checks after every modification
6. **Use unsafe-write** - Use the `unsafe-write` tool to write variant files. Do NOT use `sed` or `awk`.

---

## Error Recovery

### If compilation fails:
1. Identify which variant(s) failed from error output
2. Fix syntax errors in those variants
3. Re-run compilation
4. If unfixable, exclude that variant from testing

### If all variants lose (including original):
1. The analyze script will still rank by score (damage dealt)
2. If original performed best among losers, keep it unchanged
3. If a variant performed best among losers, use that variant as the new base bot
4. Suggest running the optimizer again with different strategies

### If a script fails:
1. Check the script output for specific error messages
2. Verify variants were created: `ls src/{BOT_NAME}_v*/`
3. Check match files exist: `ls matches/*-variant-*.bc17`
4. Re-run the failed script to see detailed output
