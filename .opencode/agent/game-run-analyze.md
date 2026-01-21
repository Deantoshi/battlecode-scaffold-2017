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

Run a match, analyze results, and create an improvement plan (no implementation).

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
- The JSON output file is your ONLY data source (do NOT parse stdout)

---

## Step 2: Parse JSON Match Output

Read `src/{BOT}/.state/match-result.json` (created by `run-match-with-analysis.sh`).

**If the JSON file is missing or empty:**
- Print an error and exit without writing any other files.

From the JSON, extract:

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

12. **SHOTS BY UNIT TYPE:**
    - Shots and bullet cost by robot type

13. **ERROR / SELF-DESTRUCT ANALYSIS:**
    - DIE_EXCEPTION / DIE_SUICIDE counts and sample incidents
    - Error/exception log counts and sample logs

**Save files:**

The JSON file already contains the complete match output:
`src/{BOT}/.state/match-result.json`

Write a summary file `src/{BOT}/.state/match-summary.txt` with:
```
OUTCOME={WIN|LOSS}
ROUNDS={number}
GOAL_MET={YES|NO}
A_BULLETS={number}
A_VP={number}
B_BULLETS={number}
B_VP={number}
FIRST_SOLDIER_A={round or N/A}
FIRST_SOLDIER_B={round or N/A}
KD_RATIO_A={number}
KD_RATIO_B={number}
BULLETS_PER_KILL_A={number}
BULLETS_PER_KILL_B={number}
A_UNITS_PRODUCED={number}
B_UNITS_PRODUCED={number}
```

---

## Step 3: Check Goal Status

Use `GOAL_MET` from the JSON.

**If GOAL_MET=YES:**
- Write `ACHIEVED` to `src/{BOT}/.state/goal-status.txt`
- Print:
  ```
  === GOAL ACHIEVED ===
  Won in {ROUNDS} rounds (target: ≤1500)
  ```
- Skip to Step 8 (Finish)

**If GOAL_MET=NO:**
- Continue to Step 4

---

## Step 4: Read Context

Read `src/{BOT}/.state/analyze-context.md` which contains all context combined:
- Game mechanics reference (victory conditions, robot types, economy)
- Map spatial information
- Visual map layout
- Iteration history (including **Exhausted Strategies** and **Metrics Over Time**)
- Recent code changes (git diff)

This single file contains everything you need - do NOT read the individual files separately.

---

## Step 5: Check for Stagnation

**Count recent iterations with similar results:**

Look at the last 5 iterations in the Iterations table:
1. Count how many have rounds within ±200 of current result
2. Count how many have the same OUTCOME (WIN/LOSS)

**STAGNATION DETECTED if:**
- Last 5+ iterations all have same outcome AND
- Round count hasn't improved by >300 rounds in last 5 iterations

**If stagnation detected:**
- Print: `⚠ STAGNATION DETECTED: No significant improvement in last 5 iterations`
- You MUST propose a RADICAL strategy change (see Step 6b)
- Do NOT tune the same parameters again

---

## Step 6: Analyze Why We Didn't Meet Goal

### Step 6a: Check Exhausted Strategies

**CRITICAL:** Before proposing ANY change, review the "Exhausted Strategies" table in iteration history.

If your proposed approach matches or is similar to an exhausted strategy:
- DO NOT propose it
- Choose a different approach
- If all obvious approaches are exhausted, propose something fundamentally new

### Step 6b: Analysis Based on Match Results

**If you LOST by elimination:**
- Did you produce enough combat units? (check UNIT SUMMARY)
- Did your units die too fast? (check COMBAT TIMELINE)
- Are units stuck and not engaging? (check MOVEMENT ANALYSIS)
- Is the opponent out-producing you?

**If you LOST by VP:**
- Did opponent out-economy you?
- Should you donate bullets to VP earlier?
- Should you rush to kill before they get VP?

**If you WON but >1500 rounds:**
- What slowed you down? (check MOVEMENT ANALYSIS for stuck units)
- Can you be more aggressive earlier?
- Can you optimize build order?
- Are units wandering instead of attacking?

**If STAGNATION DETECTED:**
Consider these RADICAL changes (pick one you haven't tried):
1. **VP Rush**: Focus on bullet generation and early VP donation
2. **Scout Harassment**: Build scouts for early pressure and vision
3. **Tank Heavy**: Switch to tank-based army composition
4. **Lumberjack Rush**: All-in early lumberjacks for melee pressure
5. **Gardener Spam**: Max economy with many gardeners and trees
6. **Completely different navigation**: If stuck issues persist, try simpler wander-based movement

### Map-Aware Analysis:
- **Tree obstacles**: Are units getting stuck in dense tree clusters?
- **Pathing issues**: Is there a clear path between spawn points?
- **Resource trees**: Are $-trees being prioritized by lumberjacks?
- **?-trees**: Are trees containing robots being chopped?
- **Quadrant strategy**: Which quadrant has fewest obstacles?

### Economy-Based Build Analysis

**Unit Costs Reference:**
| Unit | Bullet Cost | Build Time | Notes |
|------|-------------|------------|-------|
| Scout | 80 | 20 rounds | Fast, cheap harass/vision |
| Soldier | 100 | 20 rounds | Core combat unit |
| Lumberjack | 100 | 20 rounds | Melee, good vs trees/clumps |
| Tank | 300 | 30 rounds | High damage, slow, expensive |
| Gardener | 100 | 20 rounds | Economy unit, plants trees |

**Analyze Economy Timeline:**

From the ECONOMY TIMELINE in match results, check these checkpoints:

| Round | Healthy Economy Signs | Warning Signs |
|-------|----------------------|---------------|
| R100 | 1+ gardener, planting started | No gardeners, floating 200+ bullets |
| R300 | 3+ trees, 50+ bullets/round income | <2 trees, still on starting income |
| R500 | Steady unit production, not floating | Floating 500+ bullets OR starved at 0 |
| R1000 | Army built, bullets going to VP or units | Large float with no army |

**Diagnose Economic State:**

1. **Floating Bullets** (spending << generating):
   - Symptom: Bullets accumulating over time, army size not growing
   - Cause: Build logic too conservative, or stuck waiting for conditions
   - Fix: Lower thresholds for unit production, build more aggressively

2. **Bullet Starved** (want to build but can't):
   - Symptom: Bullets near 0, fewer units than opponent
   - Cause: Too many gardeners, or not enough trees, or over-building early
   - Fix: Plant more trees early, delay expensive units (tanks)

3. **Income Gap** (opponent generating more):
   - Symptom: Opponent's bullet count growing faster
   - Cause: Fewer trees, gardeners killed, trees not watered
   - Fix: Protect gardeners, prioritize tree economy

**Sustainable Production Rates:**

Calculate: `income_per_round = bullets_generated / rounds_elapsed`

| Income/Round | Can Sustain |
|--------------|-------------|
| 5 | 1 soldier per 20 rounds |
| 10 | 1 soldier per 10 rounds |
| 20 | 1 soldier per 5 rounds OR save for tank |
| 30+ | Aggressive production OR VP donation viable |

**Economy-Aware Build Recommendations:**

- **Early game (R1-R200):** Prioritize 1-2 gardeners and tree planting. Don't overspend on army.
- **Mid game (R200-R800):** Match unit production to income. If floating >300 bullets, build faster.
- **Late game (R800+):** If winning, convert excess bullets to VP. If losing, all-in on army.

**Questions to Answer:**
1. At R500, were we floating or starved? (check ECONOMY TIMELINE)
2. Did we out-produce or under-produce vs opponent? (check UNIT SUMMARY)
3. Is our tree count growing? (check TREE ECONOMY SNAPSHOT)
4. Are we spending bullets efficiently? (cumulative spent vs units produced)

---

## Step 7: Create Improvement Plan (NO IMPLEMENTATION)

**Ownership note:** This agent is the ONLY writer for "Metrics Over Time" and
"Exhausted Strategies" in `iteration-history.md`. The game-implement agent must
not modify those sections.

Choose the **single most impactful** improvement that is NOT in the Exhausted Strategies table.

**Write plan to `src/{BOT}/.state/improvement-plan.md`:**

```markdown
# Improvement Plan

## Iteration
{N} (based on history file)

## Match Result
- Outcome: {WIN/LOSS}
- Rounds: {number}
- Goal Met: {NO}

## Key Metrics This Match
- First soldier: R{N} (opponent: R{N})
- K/D ratio: {N} (opponent: {N})
- Bullets/kill: {N} (opponent: {N})
- Units produced: {N} (opponent: {N})

## Stagnation Check
- Status: {STAGNATING / PROGRESSING}
- Rounds improved from 5 iterations ago: {N}

## Analysis

### Primary Problem
{What went wrong - be specific with data from match results}

### Root Cause
{Why this happened - reference specific unit counts, timelines, etc.}

### Previous Attempts (from Exhausted Strategies)
{List similar strategies that were already tried and failed}

### Why This Approach Is Different
{Explain how your proposed solution differs from exhausted strategies}

## Proposed Solution

### Strategy Change
{High-level description of what to change}

### Is This Radical? (required if stagnating)
{YES/NO - if stagnating, this MUST be YES}

### Implementation Details
- **File:** `src/{BOT}/{filename}.java`
- **Location:** {method or section to modify}
- **Change:** {specific code change description}

{Repeat for each file if multiple changes needed}

### Expected Impact
{How this should improve the outcome}

### Success Criteria
{How to know if this change worked}

### When to Mark as Exhausted
{After how many iterations of similar results should this be added to Exhausted Strategies}
```

**Update Exhausted Strategies (if needed):**

If the SAME strategy category has been tried 3+ times with no improvement:
- Add it to the "Exhausted Strategies" table in `src/{BOT}/iteration-history.md`
- Format: `| {Category} | {Specific approach} | {iteration numbers} | {why it failed} |`

**Update Metrics Over Time:**

Add a new row to the "Metrics Over Time" table:
```
| {iter} | {WIN/LOSS} | {rounds} | R{first_soldier} | {kd_ratio} | {bullets_per_kill} | {a_units} | {b_units} |
```

**Write `CONTINUE` to `src/{BOT}/.state/goal-status.txt`**

**IMPORTANT:** Do NOT implement the changes. Only write the plan. The game-implement agent will do the implementation.

---

## Step 8: Finish

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
Stagnation: {YES/NO}
Problem: {brief description}
Solution: {brief description}
Plan saved to: src/{BOT}/.state/improvement-plan.md
Ready for game-implement agent.
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
| Same result 5+ iterations | Strategy exhausted | Try RADICAL change |
| Floating 300+ bullets by R500 | Build logic too conservative | Lower bullet thresholds for spawning units |
| Bullets near 0, fewer units than opponent | Over-built early OR poor tree economy | Delay early army, prioritize gardener + trees |
| Opponent bullet count growing faster | Income gap (fewer trees/gardeners) | Protect gardeners, plant more trees, water consistently |
| Army size plateaus mid-game | Income can't sustain production rate | Add gardeners, or switch to cheaper units (scouts) |
| Have income but no army | Build conditions never triggering | Debug spawn logic, check if thresholds are too high |
| Lost despite good economy | Not converting bullets to units/VP | Spend more aggressively, donate to VP if ahead |
| Tanks never built | Never accumulated 300 bullets | Either float more before building, or skip tanks entirely |
| Early gardener death | Unprotected economy | Build soldier before/with gardener, or position gardener safer |

---

## Stagnation Escape Strategies (pick one NOT in Exhausted Strategies)

1. **VP Rush**: Donate aggressively at 50% bullet threshold
2. **Scout Swarm**: Build 5+ scouts for harassment and tree shaking
3. **Tank Push**: Save for tanks instead of soldiers
4. **Gardener Economy**: 4+ gardeners, tree farm, late-game army
5. **No Trees**: Zero tree planting, all resources to units
6. **Archon Aggression**: Move archon toward enemy for faster gardener deployment
7. **Lumberjack Only**: No ranged units, pure melee
8. **Random Wander**: Abandon pathfinding, use random movement to unstick
9. **Greedy Economy**: 3+ gardeners before any army, max tree farm, overwhelm late-game
10. **Bullet Threshold Tuning**: If floating, halve all spawn thresholds; if starved, double them
11. **Scout Economy**: Scouts cost 80 (not 100), spam scouts instead of soldiers for same income
12. **Tank Timing**: Save to 350 bullets before any combat units, rush single tank
13. **VP Conversion Rush**: Once 500+ bullets floating, donate 50% every round to VP
14. **No Float Policy**: Spend bullets the moment you can afford any unit, never float >100
15. **Gardener Shield**: First soldier stands guard near gardener until 3+ trees planted
16. **Adaptive Spend**: Check bullet count each round - if >200, force spawn; if <50, pause building
