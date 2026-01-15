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
- `--maps MAPS` - Comma-separated maps (default: `Shrine,Barrier,Bullseye,Lanes,Blitzkrieg`)
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
for map in Shrine Barrier Bullseye Lanes Blitzkrieg; do
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

### 0.6 List Database Tables

Get the available tables:
```bash
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
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

# Get robots spawned by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, body_type, COUNT(*) as spawned FROM robots GROUP BY team, body_type"

# Get robots alive at end
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as alive FROM robots WHERE death_round IS NULL GROUP BY team"

# Get final unit counts from snapshots
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team_a_units_lost, team_b_units_lost FROM snapshots WHERE round_id=(SELECT MAX(round_id) FROM snapshots)"
```

### 0.8 Parse Results
From console output, capture `[combat] winner=X round=N` for each map.

Store as:
```
BASELINE = {
  wins: N,
  losses: N,
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

 # Robot deaths by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, body_type, COUNT(*) as deaths FROM robots WHERE death_round IS NOT NULL GROUP BY team, body_type"
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

# Search for movement issues
python3 scripts/bc17_query.py search matches/{BOT_NAME}-combat-*.db "stuck"

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

# Resource levels over time (early game)
python3 scripts/bc17_query.py rounds matches/{BOT_NAME}-combat-*.db 1 50

# Resource levels over time (mid game)
python3 scripts/bc17_query.py rounds matches/{BOT_NAME}-combat-*.db 500 550

# Shooting rate by phase
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT CASE WHEN round_id<500 THEN 'early' WHEN round_id<1500 THEN 'mid' ELSE 'late' END as phase,
team, COUNT(*) as shots FROM events WHERE event_type='shoot' GROUP BY phase, team"

# Shoot events by round range
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as shots FROM events WHERE event_type='shoot' AND round_id BETWEEN 330 AND 360 GROUP BY team"

 # Death rounds by team
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, AVG(death_round) as avg_death_round FROM robots WHERE death_round IS NOT NULL GROUP BY team"
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
for map in Shrine Barrier Bullseye Lanes Blitzkrieg; do
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

 # Get robots alive at end
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team, COUNT(*) as alive FROM robots WHERE death_round IS NULL GROUP BY team"

# Get final unit counts from snapshots
python3 scripts/bc17_query.py sql matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-Shrine.db "
SELECT team_a_units_lost, team_b_units_lost FROM snapshots WHERE round_id=(SELECT MAX(round_id) FROM snapshots)"
```

**Output:**
```
VALIDATION = {
  wins: N,
  losses: N,
  map_results: { ... }
}
```

---

## PHASE 6: Accept/Reject

Compare BASELINE vs VALIDATION:

```
if VALIDATION.wins > BASELINE.wins:
    DECISION = ACCEPT ("Improved from X to Y wins")
elif VALIDATION.wins == BASELINE.wins:
    if avg_rounds_improved:
        DECISION = ACCEPT ("Same wins but faster")
    else:
        DECISION = ACCEPT_TENTATIVE ("No regression")
else:
    DECISION = REJECT ("Regression from X to Y wins")
```

### If REJECT:
1. Revert changes (use SYNTHESIS.rollback)
2. Log the failed attempt

### If ACCEPT:
1. Keep changes
2. Update combat log

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
  Baseline: {BASELINE.wins}/{num_maps} wins
  After:    {VALIDATION.wins}/{num_maps} wins
  Delta:    {+/-N}

PHASE 6 - Decision: {ACCEPT|REJECT}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Combat Log Update

Append to `src/{BOT_NAME}/COMBAT_LOG_GOT.md`:

```markdown
## GoT Execution - {DATE}
**Decision:** {ACCEPT|REJECT}

### Hypotheses
| Category | Weakness | Confidence |
|----------|----------|------------|
| Targeting | {desc} | {}/5 |
| Movement | {desc} | {}/5 |
| Timing | {desc} | {}/5 |

### Selected: {solutions}
### Changes: {descriptions}
### Results: {baseline} → {validation}
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

## Completion Signal

After Phase 6, check if the objective is met:

```
avg_win_rounds = average rounds for winning maps
if VALIDATION.wins >= ceil(num_maps / 2) AND avg_win_rounds <= 500:
    # Objective met! Signal completion
    Output: <promise>OBJECTIVE_MET</promise>
```

**CRITICAL: Only output `<promise>OBJECTIVE_MET</promise>` when BOTH conditions are true:**
- You have **won 50% or more** of the maps (e.g., 3/5, 2/3, 1/1)
- The **average rounds for those wins is <= 500**

If the objective is NOT met, simply end your response without the promise tag.
