---
description: Analyze match results and create improvement plan
mode: primary
temperature: 0.8
permission:
  read: allow
  unsafe-write: allow
  glob: allow
---

# Game Analyze

Analyze match results and decide whether goal is met or plan an improvement.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`
- `--opponent NAME` - **REQUIRED**: Opponent folder in `src/NAME/`
- `--maps MAP` - Single map (default: `MagicWood`)

---

## Identity

**Start with:**
```
=== GAME-ANALYZE STARTED ===
Bot: {BOT}
```

---

## Step 1: Read Match Results, Game Mechanics & Map Context

Read `TECHNICAL_DOCS.md` for game mechanics reference:
- Victory conditions (1000 VP or destroy all enemy units)
- Robot types, stats, and roles (Archon, Gardener, Soldier, Lumberjack, Scout, Tank)
- Tree clearing methods (lumberjack chop, tank trampling, soldier shooting, strike AoE)
- Economy system (bullets, tree income, shaking)
- Key API methods and combat micro tips

Read `src/{BOT}/.state/match-result.txt` to get the full match analysis.

Read `src/{BOT}/.state/match-summary.txt` to get:
- OUTCOME
- ROUNDS
- GOAL_MET

Read `src/{BOT}/.state/map-context.txt` for map spatial information:
- Map dimensions and boundaries
- Neutral tree positions, radii, and contents (bullets/robots)
- Tree density by quadrant (NW, NE, SW, SE)
- Initial unit positions for both teams

Read `src/{BOT}/.state/map-ascii.txt` for visual map layout:
- Shows tree positions (T=tree, $=tree+bullets, ?=tree+robot)
- Shows starting unit positions (UPPER=TeamA, lower=TeamB)

This spatial context helps identify movement/pathing issues.

---

## Step 2: Check Goal Status

**If GOAL_MET=YES:**
- Write `ACHIEVED` to `src/{BOT}/.state/goal-status.txt`
- Print:
  ```
  === GOAL ACHIEVED ===
  Won in {ROUNDS} rounds (target: ≤1500)
  ```
- Skip to Step 6 (Finish)

**If GOAL_MET=NO:**
- Continue to Step 3

---

## Step 3: Read History

Read `src/{BOT}/iteration-history.md` to see:
- Previous iterations
- What changes have already been tried
- What problems have been identified

This prevents repeating failed approaches.

---

## Step 4: Analyze Why We Didn't Meet Goal

Based on match results, determine the PRIMARY issue:

### If you LOST by elimination:
- Did you produce enough combat units? (check UNIT SUMMARY)
- Did your units die too fast? (check COMBAT TIMELINE - early deaths?)
- Are units stuck and not engaging? (check MOVEMENT ANALYSIS for ⚠ warnings)
- Is the opponent out-producing you?

### If you LOST by VP:
- Did opponent out-economy you? (compare cumulative gen/spent)
- Should you donate bullets to VP earlier?
- Should you rush to kill before they get VP?

### If you WON but >1500 rounds:
- What slowed you down? (check MOVEMENT ANALYSIS for stuck units)
- Can you be more aggressive earlier?
- Can you optimize build order?
- Are units wandering instead of attacking?

### Map-Aware Analysis (use map-context.txt):
- **Tree obstacles**: Are units getting stuck in dense tree clusters? Check quadrant tree counts.
- **Pathing issues**: Is there a clear path between spawn points or are trees blocking?
- **Resource trees**: Are $-trees (containing bullets) being prioritized by lumberjacks?
- **?-trees**: Are trees containing robots being chopped to gain unit advantage?
- **Spawn distance**: How far apart are Team A and Team B archons? Affects rush viability.
- **Quadrant strategy**: Which quadrant has fewest obstacles for flanking?

---

## Step 5: Create Improvement Plan

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

## Step 6: Finish

**If goal was achieved:**
```
=== GAME-ANALYZE COMPLETE ===
Status: GOAL ACHIEVED
```

**If continuing:**
```
=== GAME-ANALYZE COMPLETE ===
Status: CONTINUE
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
| Not finding enemies | Trees blocking line of sight | Move to open quadrant first |
| Missing resource trees | Not prioritizing $-trees | Target high-bullet trees early |
| Enemy has more units | Not chopping ?-trees | Prioritize robot-containing trees |
