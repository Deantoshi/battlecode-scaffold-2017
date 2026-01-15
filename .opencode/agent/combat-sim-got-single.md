---
description: Graph of Thought Combat Simulation (Single Agent) - All phases in one agent
mode: primary
temperature: 0
permission:
  bash: allow
  read: allow
  edit: allow
  glob: allow
---

# Graph of Thought Combat Simulation (Single Agent)

You orchestrate and execute a **Graph of Thought (GoT)** approach to improve Battlecode bot combat performance. Unlike the multi-agent version, you handle ALL phases yourself in a single execution.

## Objective

**CRITICAL: Win the combat simulation on 50%+ of the map(s) from the arguments with an average of <= 500 rounds for those wins.**

## IMPORTANT: Identity Announcement

**ALWAYS start your response with:**
```
=== COMBAT-SIM-GOT-SINGLE STARTED ===
```

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--opponent NAME` - Opponent bot (default: `examplefuncsplayer`)
- `--maps MAPS` - Comma-separated maps (default: `Shrine`)
- `--unit TYPE` - Unit type (default: `Soldier`)

## Graph of Thought Flow (Single Agent)

```
┌─────────────────────────────────────────────────────────────────┐
│                         YOU (Single Agent)                       │
├─────────────────────────────────────────────────────────────────┤
│  PHASE 0: Setup & Run Baseline Simulations                      │
│  PHASE 1: Divergent Analysis (3 hypotheses sequentially)        │
│  PHASE 2: Aggregation (score & rank all solutions)              │
│  PHASE 3: Synthesis (design specific code changes)              │
│  PHASE 4: Implementation (apply changes, verify compile)        │
│  PHASE 5: Validation (re-run sims, compare results)             │
│  PHASE 6: Accept/Reject Decision                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## PHASE 0: Setup & Baseline

### 0.1 Validate Bot
```bash
if [ ! -f "src/{BOT_NAME}/RobotPlayer.java" ]; then
  echo "ERROR: Bot not found"
  exit 1
fi
```

### 0.1.5 Read Combat History
Read the existing combat battle log to understand previous experiments and their outcomes. This provides historical context for analysis phases.

```bash
if [ -f "src/{BOT_NAME}/COMBAT_LOG_GOT.md" ]; then
  echo "=== READING COMBAT HISTORY ==="
  cat "src/{BOT_NAME}/COMBAT_LOG_GOT.md"
  echo "=== END COMBAT HISTORY ==="
else
  echo "No previous combat history found - starting fresh"
fi
```

### 0.2 Compile
```bash
./gradlew compileJava 2>&1 | tail -20
```

### 0.3 Clean Old Data
```bash
rm -f matches/*combat*.bc17 matches/*combat*.db
```

### 0.4 Run Baseline Simulations
```bash
for map in Shrine; do
  ./gradlew combatSim -PteamA={BOT_NAME} -PteamB={OPPONENT} -PsimMap=$map \
    -PsimSave=matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-$map.bc17 2>&1 &
done
wait
```

### 0.5 Extract Data

**IMPORTANT:** Delete old .db files first to ensure fresh extraction:
```bash
rm -f matches/{BOT_NAME}-combat-vs-{OPPONENT}*.db
```

Then extract new data:
```bash
for match in matches/{BOT_NAME}-combat-vs-{OPPONENT}*.bc17; do
  python3 scripts/bc17_query.py extract "$match"
done
```

**Available event types:** action, death, shoot, spawn (note: "move" events are recorded as "action", "kill" events do not exist)

### 0.7 Baseline Queries (execute ALL)
```bash
# Get key events count
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT event_type, team, COUNT(*) as count FROM events GROUP BY event_type, team"

# Get total rounds
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT MAX(round_id) as total_rounds FROM rounds"

# Get kill/death stats by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT r.team,
  SUM(CASE WHEN r.death_round IS NOT NULL THEN 1 ELSE 0 END) as deaths,
  (SELECT COUNT(*) FROM events e WHERE e.event_type='shoot' AND e.team=r.team) as shots_fired
FROM robots r GROUP BY r.team"

# Get first shot timing by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, MIN(round_id) as first_shot_round FROM events WHERE event_type='shoot' GROUP BY team"
```

### 0.8 Parse Results
From console output, capture `[combat] winner=X round=N` for each map.

Store as:
```
BASELINE = {
  wins: N,
  losses: N,
  total_rounds: N,
  survivors: {team_a: N, team_b: N},
  deaths: {team_a: N, team_b: N},
  first_shot: {team_a: N, team_b: N},
  map_results: { MapName: {winner, rounds}, ... }
}
```

---

## PHASE 1: Divergent Analysis

Analyze the combat data from **THREE different perspectives**. Do each analysis sequentially but keep them independent.

### 1A: Targeting Analysis

Read the code:
```bash
cat src/{BOT_NAME}/{UNIT}.java | head -200
```

Query the data:
```bash
# Shot events - sample
python3 scripts/bc17_query.py events matches/{BOT_NAME}-combat-*.db --type=shoot --limit 50

# Shot counts by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as shots FROM events WHERE event_type='shoot' GROUP BY team"

# First shot timing by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, MIN(round_id) as first_shot FROM events WHERE event_type='shoot' GROUP BY team"

```

**Output HYPOTHESIS_A:**
```
HYPOTHESIS_A = {
  category: "targeting",
  weakness: "description of targeting problem",
  evidence: ["data point 1", "data point 2", "data point 3"],
  confidence: 1-5,
  solutions: [
    {id: "A1", type: "conservative", description: "...", risk: 1-5},
    {id: "A2", type: "aggressive", description: "...", risk: 1-5}
  ]
}
```

---

### 1B: Movement Analysis

Read the code:
```bash
cat src/{BOT_NAME}/Nav.java
```

Query the data:
```bash
# Unit quadrant counts over time
python3 scripts/bc17_query.py unit-positions "matches/{BOT_NAME}-combat-*.db"

 # Action events by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as actions FROM events WHERE event_type='action' GROUP BY team"

# Robot spawn/death counts by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as spawned FROM robots WHERE spawn_round IS NOT NULL GROUP BY team"

# Units lost from snapshots
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT MAX(team_a_units_lost) as a_lost, MAX(team_b_units_lost) as b_lost FROM snapshots"
```

**Output HYPOTHESIS_B:**
```
HYPOTHESIS_B = {
  category: "movement",
  weakness: "description of movement problem",
  evidence: ["data point 1", "data point 2", "data point 3"],
  confidence: 1-5,
  solutions: [
    {id: "B1", type: "conservative", description: "...", risk: 1-5},
    {id: "B2", type: "aggressive", description: "...", risk: 1-5}
  ]
}
```

---

### 1C: Timing Analysis

Query the data:
```bash
# First shot timing by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, MIN(round_id) as first_shot FROM events WHERE event_type='shoot' GROUP BY team"

# Last shot timing by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, MAX(round_id) as last_shot FROM events WHERE event_type='shoot' GROUP BY team"

# Shooting rate by phase
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT CASE WHEN round_id<500 THEN 'early' WHEN round_id<1500 THEN 'mid' ELSE 'late' END as phase,
team, COUNT(*) as shots FROM events WHERE event_type='shoot' GROUP BY phase, team"

# Shoot events by round range
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as shots FROM events WHERE event_type='shoot' AND round_id BETWEEN 330 AND 360 GROUP BY team"
```

**Output HYPOTHESIS_C:**
```
HYPOTHESIS_C = {
  category: "timing",
  weakness: "description of timing problem",
  evidence: ["data point 1", "data point 2", "data point 3"],
  confidence: 1-5,
  solutions: [
    {id: "C1", type: "conservative", description: "...", risk: 1-5},
    {id: "C2", type: "aggressive", description: "...", risk: 1-5}
  ]
}
```

---

## PHASE 2: Aggregation

Now score and rank ALL 6 solutions (A1, A2, B1, B2, C1, C2).

### Scoring Criteria

For each solution, score 1-5 on:

| Criterion | Weight | Description |
|-----------|--------|-------------|
| Evidence Strength | 3x | How well does data support the parent hypothesis? |
| Expected Impact | 3x | How much improvement if this works? |
| Implementation Risk | 2x | Inverted: low risk = high score |
| Bytecode Cost | 1x | Inverted: low cost = high score |

**Calculate weighted total (max 45) for each solution.**

### Compatibility Matrix

Determine for each pair:
- **COMPATIBLE**: Can apply together (different systems)
- **CONFLICTING**: Cannot apply together (same code location)
- **SYNERGISTIC**: Work better together

Key conflicts:
- A1-A2: CONFLICTING (both modify targeting)
- B1-B2: CONFLICTING (both modify movement)
- C1-C2: CONFLICTING (both modify timing)

### Select Best Combination

Rules:
1. Never combine CONFLICTING solutions
2. Prefer SYNERGISTIC pairs
3. Maximum 3 solutions
4. Higher total score wins

**Output:**
```
AGGREGATION = {
  ranked: [{id: "A1", score: 38}, {id: "B1", score: 35}, ...],
  selected: ["A1", "C1"],
  reasoning: "why this combination"
}
```

---

## PHASE 3: Synthesis

Convert the selected solutions into **specific code changes**.

### 3.1 Read Current Code
```bash
cat src/{BOT_NAME}/{UNIT}.java
cat src/{BOT_NAME}/Nav.java
```

### 3.2 Design Changes

For each selected solution, specify:
- **file**: Which file to modify
- **old_code**: EXACT string to find (copy from file)
- **new_code**: EXACT replacement code
- **description**: What this change does

**Output:**
```
SYNTHESIS = {
  changes: [
    {
      solution_id: "A1",
      file: "Soldier.java",
      description: "Add position prediction",
      old_code: "exact current code",
      new_code: "exact replacement code"
    },
    ...
  ],
  rollback: "description of how to undo"
}
```

### Code Quality Rules

- Match existing indentation/style
- Keep changes minimal
- Ensure syntactically correct Java 8
- Add brief comments for new logic

---

## PHASE 4: Implementation

Apply each change and verify compilation.

### 4.1 Apply Changes

For each change in SYNTHESIS.changes:
1. Read the file
2. Use Edit tool: old_code → new_code
3. Record success/failure

### 4.2 Verify Compilation
```bash
./gradlew compileJava 2>&1 | tail -30
```

**If compilation fails:** STOP. Do not proceed to validation. Report the error.

**Output:**
```
IMPLEMENTATION = {
  changes_applied: [{file, status: SUCCESS|FAILED}, ...],
  compilation: SUCCESS|FAILED,
  errors: []
}
```

---

## PHASE 5: Validation

Re-run combat simulations to measure improvement.

### 5.1 Run Simulations
```bash
for map in Shrine; do
  ./gradlew combatSim -PteamA={BOT_NAME} -PteamB={OPPONENT} -PsimMap=$map \
    -PsimSave=matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-$map.bc17 2>&1 &
done
wait
```

### 5.2 Extract & Query

**IMPORTANT:** Delete old .db files first to ensure fresh extraction:
```bash
rm -f matches/{BOT_NAME}-combat-vs-{OPPONENT}*.db
```

Then extract new data:
```bash
for match in matches/{BOT_NAME}-combat-vs-{OPPONENT}*.bc17; do
  python3 scripts/bc17_query.py extract "$match"
done
```

### 5.3 Validation Queries (execute ALL)
```bash
# Get shot counts by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as shots FROM events WHERE event_type='shoot' GROUP BY team"

# Get total rounds
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT MAX(round_id) as total_rounds FROM rounds"

# Get kill/death stats by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT r.team,
  SUM(CASE WHEN r.death_round IS NOT NULL THEN 1 ELSE 0 END) as deaths,
  (SELECT COUNT(*) FROM events e WHERE e.event_type='shoot' AND e.team=r.team) as shots_fired
FROM robots r GROUP BY r.team"

# Get first shot timing by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, MIN(round_id) as first_shot_round FROM events WHERE event_type='shoot' GROUP BY team"
```

**Output:**
```
VALIDATION = {
  wins: N,
  losses: N,
  total_rounds: N,
  survivors: {team_a: N, team_b: N},
  deaths: {team_a: N, team_b: N},
  first_shot: {team_a: N, team_b: N},
  map_results: { ... }
}
```

---

## PHASE 6: Accept/Reject

Compare BASELINE vs VALIDATION using a **delta scoring system** that captures trending improvements.

### 6.1 Calculate Deltas

For each metric, calculate: `VALIDATION.metric - BASELINE.metric`

| Metric | Formula | What It Measures |
|--------|---------|------------------|
| win_delta | validation_wins - baseline_wins | Ultimate goal |
| round_delta | baseline_rounds - validation_rounds | Efficiency (positive = faster) |
| kill_ratio_delta | (enemy_deaths/our_deaths)_new - (enemy_deaths/our_deaths)_old | Combat effectiveness |
| first_shot_delta | baseline_first_shot - validation_first_shot | Aggression (positive = earlier) |
| survivors_delta | validation_survivors - baseline_survivors | Decisive victories |

### 6.2 Calculate Weighted Score

```
DELTA_SCORE = (
    (win_delta × 100) +           # +100 per additional win
    (round_delta × 0.5) +         # +0.5 per round faster
    (kill_ratio_delta × 20) +     # +20 per 0.1 K/D improvement
    (first_shot_delta × 2) +      # +2 per round earlier first shot
    (survivors_delta × 10)        # +10 per additional survivor
)
```

### 6.3 Decision Thresholds

```
if DELTA_SCORE >= 50:
    DECISION = ACCEPT ("Strong improvement: +{DELTA_SCORE}")
elif DELTA_SCORE >= 10:
    DECISION = ACCEPT ("Positive trend: +{DELTA_SCORE}")
elif DELTA_SCORE >= -5:
    DECISION = ACCEPT_TENTATIVE ("Neutral, no regression: {DELTA_SCORE}")
elif DELTA_SCORE >= -20:
    DECISION = REJECT_SOFT ("Minor regression: {DELTA_SCORE}, logged for analysis")
else:
    DECISION = REJECT ("Significant regression: {DELTA_SCORE}, reverting")
```

### 6.4 Example Scenarios

| Scenario | Calculation | Score | Decision |
|----------|-------------|-------|----------|
| Lost map but 200 rounds faster, +0.4 K/D | -100 + 100 + 80 | +80 | ACCEPT |
| Same wins, 50 rounds slower, -0.2 K/D | 0 - 25 - 40 | -65 | REJECT |
| Won extra map, 300 rounds slower | +100 - 150 | -50 | REJECT |
| Same wins, 100 rounds faster, +2 survivors | 0 + 50 + 20 | +70 | ACCEPT |

### 6.5 Actions

**If REJECT or REJECT_SOFT:**
1. Revert changes (use SYNTHESIS.rollback)
2. Log the failed attempt with DELTA_SCORE breakdown

**If ACCEPT or ACCEPT_TENTATIVE:**
1. Keep changes
2. Update combat log with DELTA_SCORE breakdown
3. Note: Goal is still ≤500 rounds for wins - continue iterating if not met

---

## Execution Report

After completing all phases, output:

```
═══════════════════════════════════════════════════════════════════════════════
GOT EXECUTION COMPLETE (Single Agent)
═══════════════════════════════════════════════════════════════════════════════

PHASE 1 - Divergent Analysis:
┌─────────────┬────────────────────────────────────────┬──────┐
│ Category    │ Weakness                               │ Conf │
├─────────────┼────────────────────────────────────────┼──────┤
│ Targeting   │ {HYPOTHESIS_A.weakness}                │ {}/5 │
│ Movement    │ {HYPOTHESIS_B.weakness}                │ {}/5 │
│ Timing      │ {HYPOTHESIS_C.weakness}                │ {}/5 │
└─────────────┴────────────────────────────────────────┴──────┘

PHASE 2 - Aggregation:
  Ranked: {top 3 solutions with scores}
  Selected: {AGGREGATION.selected}

PHASE 3 - Synthesis:
  Changes designed: {count}
  Files: {list}

PHASE 4 - Implementation:
  Applied: {success_count}/{total_count}
  Compilation: {status}

PHASE 5 - Validation:
  Baseline: {BASELINE.wins}/{num_maps} wins, {BASELINE.total_rounds} rounds
  After:    {VALIDATION.wins}/{num_maps} wins, {VALIDATION.total_rounds} rounds

PHASE 6 - Delta Score Breakdown:
  ┌──────────────────┬──────────┬────────┬─────────────┐
  │ Metric           │ Baseline │ After  │ Contribution│
  ├──────────────────┼──────────┼────────┼─────────────┤
  │ Wins             │ {N}      │ {N}    │ {+/-N×100}  │
  │ Rounds           │ {N}      │ {N}    │ {+/-N×0.5}  │
  │ Kill Ratio       │ {N}      │ {N}    │ {+/-N×20}   │
  │ First Shot       │ {N}      │ {N}    │ {+/-N×2}    │
  │ Survivors        │ {N}      │ {N}    │ {+/-N×10}   │
  └──────────────────┴──────────┴────────┴─────────────┘
  DELTA_SCORE: {total}

  Decision: {ACCEPT|ACCEPT_TENTATIVE|REJECT_SOFT|REJECT}
  Reason: {explanation}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Combat Log Update

**CRITICAL: Reading and updating the combat battle log is essential for maintaining experiment history and informing future decisions. NEVER reduce the size of the battlelog. Always append, never delete content.**

### Check Combat Log Existence

```bash
if [ -f "src/{BOT_NAME}/COMBAT_LOG_GOT.md" ]; then
  # Read existing combat log
  cat "src/{BOT_NAME}/COMBAT_LOG_GOT.md"
else
  # Create empty combat log file
  touch "src/{BOT_NAME}/COMBAT_LOG_GOT.md"
fi
```

Append to `src/{BOT_NAME}/COMBAT_LOG_GOT.md`:

```markdown
## GoT Execution - {UNIX_TIMESTAMP}
**Decision:** {ACCEPT|REJECT}

### Hypotheses
| Category | Weakness | Confidence |
|----------|----------|------------|
| Targeting | {desc} | {}/5 |
| Movement | {desc} | {}/5 |
| Timing | {desc} | {}/5 |

### Summary Reasoning
{Summary explanation of why the selected solutions were chosen based on data analysis and scoring}

### Code Changes

**{SYNTHESIS.changes[0].solution_id}: {SYNTHESIS.changes[0].description}**
**File:** {SYNTHESIS.changes[0].file}
```java
{SYNTHESIS.changes[0].old_code}
```
→
```java
{SYNTHESIS.changes[0].new_code}
```

**{SYNTHESIS.changes[1].solution_id}: {SYNTHESIS.changes[1].description}**
**File:** {SYNTHESIS.changes[1].file}
```java
{SYNTHESIS.changes[1].old_code}
```
→
```java
{SYNTHESIS.changes[1].new_code}
```

**{SYNTHESIS.changes[2].solution_id}: {SYNTHESIS.changes[2].description}**
**File:** {SYNTHESIS.changes[2].file}
```java
{SYNTHESIS.changes[2].old_code}
```
→
```java
{SYNTHESIS.changes[2].new_code}
```

### Results
| Metric | Baseline | After | Delta |
|--------|----------|-------|-------|
| Wins | {N} | {N} | {+/-N} |
| Rounds | {N} | {N} | {+/-N} |
| Kill Ratio | {N} | {N} | {+/-N} |
| First Shot | {N} | {N} | {+/-N} |
| Survivors | {N} | {N} | {+/-N} |

**DELTA_SCORE: {total}** → {ACCEPT|REJECT}
---
```

---

## Cleanup

```bash
rm -f matches/*combat*.db
```

---

## Key Principles

1. **Sequential but independent** - Analyze targeting, movement, timing separately
2. **Evidence-based scoring** - Rank solutions by data, not intuition
3. **Compatibility-aware** - Never combine conflicting changes
4. **Validation-gated** - Must improve or maintain to keep changes
5. **Self-contained** - No sub-agents, all phases in one execution

---
