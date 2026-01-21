---
description: Run a Battlecode match and analyze results to plan improvements
mode: primary
temperature: 0.6
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Game Run & Analyze

Run a match and analyze results in one step.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`
- `--opponent NAME` - **REQUIRED**: Opponent folder in `src/NAME/`
- `--maps MAP` - Single map (default: `MagicWood`)

---

## Identity

**Start with:**
```
=== GAME-RUN-ANALYZE STARTED ===
Bot: {BOT}
Opponent: {OPPONENT}
Map: {MAP}
```

---

## Step 1: Run Match

Execute:
```bash
./scripts/run-match-with-analysis.sh {BOT} {OPPONENT} {MAP}
```

**IMPORTANT:**
- Do NOT run any database queries yourself
- Do NOT run sqlite3 commands
- Do NOT run Python scripts to query .db files
- The script output is your ONLY data source

---

## Step 2: Parse & Save Match Output

From the script output, extract:

1. **RESULT section:**
   - `OUTCOME` (WIN or LOSS)
   - `ROUNDS` (number)
   - `GOAL_MET` (YES or NO)
   - Final bullets/VP for both teams

2. **UNIT SUMMARY table:**
   - Produced / Lost / Alive counts per unit type

3. **ECONOMY TIMELINE:**
   - Bullets/VP at intervals
   - Cumulative generated/spent

4. **COMBAT TIMELINE:**
   - Deaths by period

5. **MOVEMENT ANALYSIS:**
   - Unit distribution
   - Any stuck unit warnings

6. **VP ACTIVITY:**
   - Donation events if any

7. **BUILD ORDER:**
   - First 15 units per team

8. **COMBAT ANALYSIS:**
   - Shots, efficiency, friendly fire

9. **UNIT LIFESPAN:**
   - Avg/min/max lifespan by unit type
   - Early deaths (first 5 per team)

10. **TREE ECONOMY SNAPSHOT:**
    - Trees alive at key rounds

11. **ACTION SUMMARY:**
    - Non-combat actions (plant/water/shake/spawn/chop)

**Save files:**

Write the COMPLETE script output to `src/{BOT}/.state/match-result.txt`

Write a summary file `src/{BOT}/.state/match-summary.txt` with:
```
OUTCOME={WIN|LOSS}
ROUNDS={number}
GOAL_MET={YES|NO}
A_BULLETS={number}
A_VP={number}
B_BULLETS={number}
B_VP={number}
```

---

## Step 3: Check Goal Status

**If GOAL_MET=YES:**
- Write `ACHIEVED` to `src/{BOT}/.state/goal-status.txt`
- Print:
  ```
  === GOAL ACHIEVED ===
  Won in {ROUNDS} rounds (target: ≤1500)
  ```
- Skip to Step 7 (Finish)

**If GOAL_MET=NO:**
- Continue to Step 4

---

## Step 4: Read Context

Read `src/{BOT}/.state/analyze-context.md` which contains all context combined:
- Game mechanics reference (victory conditions, robot types, economy)
- Map spatial information
- Visual map layout
- Iteration history (previous attempts to avoid repeating)

This single file contains everything you need - do NOT read the individual files separately.

---

## Step 5: Analyze Why We Didn't Meet Goal

Based on match results, determine the PRIMARY issue:

### If you LOST by elimination:
- Did you produce enough combat units? (check UNIT SUMMARY)
- Did your units die too fast? (check COMBAT TIMELINE)
- Are units stuck and not engaging? (check MOVEMENT ANALYSIS)
- Is the opponent out-producing you?

### If you LOST by VP:
- Did opponent out-economy you?
- Should you donate bullets to VP earlier?
- Should you rush to kill before they get VP?

### If you WON but >1500 rounds:
- What slowed you down? (check MOVEMENT ANALYSIS for stuck units)
- Can you be more aggressive earlier?
- Can you optimize build order?
- Are units wandering instead of attacking?

### Map-Aware Analysis:
- **Tree obstacles**: Are units getting stuck in dense tree clusters?
- **Pathing issues**: Is there a clear path between spawn points?
- **Resource trees**: Are $-trees being prioritized by lumberjacks?
- **?-trees**: Are trees containing robots being chopped?
- **Quadrant strategy**: Which quadrant has fewest obstacles?

---

## Step 6: Create Improvement Plan & Implement Changes

Choose the **single most impactful** improvement.

**Write plan to `src/{BOT}/.state/improvement-plan.md`:**

```markdown
# Improvement Plan

## Iteration
{N} (based on history file)

## Match Result
- Outcome: {WIN/LOSS}
- Rounds: {number}
- Goal Met: {NO}

## Analysis

### Primary Problem
{What went wrong - be specific with data from match results}

### Root Cause
{Why this happened - reference specific unit counts, timelines, etc.}

### Previous Attempts
{What similar changes were tried before, if any}

## Proposed Solution

### Strategy Change
{High-level description of what to change}

### Implementation Details
- **File:** `src/{BOT}/{filename}.java`
- **Location:** {method or section to modify}
- **Change:** {specific code change description}

### Economy Projection

Calculate the expected bullet economy for the proposed build order:

**Unit Costs (bullets):**
- Archon: spawns free | Gardener: 100 | Scout: 80 | Soldier: 100 | Tank: 300 | Lumberjack: 100

**Income Sources:**
- Base: 1.0 bullet/round (passive)
- Per Archon: +2.0 bullets/round
- Per tree planted: +1.0 bullet/round (when fully grown)

**Projection Table:**
| Round | Build Action | Cost | Cumulative Spent | Income/Round | Balance |
|-------|--------------|------|------------------|--------------|---------|
| 1     | (start)      | 0    | 0                | 3.0          | 300     |
| {R}   | {Unit}       | {C}  | {total}          | {income}     | {bal}   |
| ...   | ...          | ...  | ...              | ...          | ...     |

**Key Milestones:**
- Round when first combat unit ready: {R}
- Round when army size reaches 5: {R}
- Projected bullets at R500: {N}

**Break-even Analysis:**
- If building Gardeners for trees: each Gardener costs 100, plants trees worth +1/round each
- Gardener + 1 tree breaks even at round ~100 after build

### Expected Impact
{How this should improve the outcome}

### Success Criteria
{How to know if this change worked}
```

**After writing the plan, IMPLEMENT the changes:**

1. Read the file(s) specified in "Implementation Details"
2. Make all code changes needed to achieve the proposed solution
3. Ensure the changes compile (no syntax errors)
4. Update `src/{BOT}/iteration-history.md` with a summary of what was changed

**Write `CONTINUE` to `src/{BOT}/.state/goal-status.txt`**

---

## Step 7: Finish

**If goal was achieved:**
```
=== GAME-RUN-ANALYZE COMPLETE ===
Status: GOAL ACHIEVED
Result: WIN in {ROUNDS} rounds
```

**If continuing:**
```
=== GAME-RUN-ANALYZE COMPLETE ===
Status: CONTINUE
Result: {OUTCOME} in {ROUNDS} rounds
Problem: {brief description}
Solution: {brief description}
Plan saved to: src/{BOT}/.state/improvement-plan.md
```

---

## Analysis Framework Quick Reference

| Symptom | Likely Cause | Typical Fix |
|---------|--------------|-------------|
| Lost by R500 | Out-produced early | Build more units early |
| Units all in one quadrant | Pathing/stuck issues | Add movement diversity |
| High death count early | Combat disadvantage | Improve targeting/kiting |
| Low bullet generation | Not enough trees | Prioritize tree planting |
| Enemy has more VP | They're donating | Either rush kill or out-donate |
| Won but slow (>1500) | Not aggressive enough | Seek enemies earlier |
| Units alive but not engaging | Target acquisition issue | Fix enemy detection range |
| Units stuck near spawn | Dense tree cluster blocking | Navigate around obstacle quadrant |
