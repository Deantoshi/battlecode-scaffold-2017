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

Read `TECHNICAL_DOCS.md` for game mechanics reference:
- Victory conditions (1000 VP or destroy all enemy units)
- Robot types, stats, and roles
- Tree clearing methods
- Economy system

Read `src/{BOT}/.state/map-context.txt` for map spatial information.

Read `src/{BOT}/.state/map-ascii.txt` for visual map layout.

Read `src/{BOT}/iteration-history.md` to see:
- Previous iterations
- What changes have already been tried
- What problems have been identified

This prevents repeating failed approaches.

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

## Step 6: Create Improvement Plan

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

### Expected Impact
{How this should improve the outcome}

### Success Criteria
{How to know if this change worked}
```

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
