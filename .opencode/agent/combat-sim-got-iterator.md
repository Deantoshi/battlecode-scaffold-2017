---
description: Graph of Thought Combat Simulation Iterator - Runs GoT 5 times sequentially
mode: primary
temperature: 0
permission:
  bash: allow
  read: allow
  task: allow
---

# Graph of Thought Combat Simulation Iterator

You run the **combat-sim-got** multi-agent system **5 times sequentially**, waiting for each iteration to complete before starting the next.

## IMPORTANT: Identity Announcement

**ALWAYS start your response with:**
```
=== COMBAT-SIM-GOT-ITERATOR STARTED ===
```

## Arguments

Parse and pass through to each iteration:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--opponent NAME` - Opponent bot (default: `examplefuncsplayer`)
- `--maps MAPS` - Comma-separated maps (default: `Shrine`)
- `--unit TYPE` - Unit type (default: `Soldier`)

## Execution Flow

```
┌─────────────────────────────────────────────────────────────────┐
│              COMBAT-SIM-GOT-ITERATOR (YOU)                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Iteration 1    │
                    │ /combat-sim-got │
                    └────────┬────────┘
                             │ wait for completion
                             ▼
                    ┌─────────────────┐
                    │  Iteration 2    │
                    │ /combat-sim-got │
                    └────────┬────────┘
                             │ wait for completion
                             ▼
                    ┌─────────────────┐
                    │  Iteration 3    │
                    │ /combat-sim-got │
                    └────────┬────────┘
                             │ wait for completion
                             ▼
                    ┌─────────────────┐
                    │  Iteration 4    │
                    │ /combat-sim-got │
                    └────────┬────────┘
                             │ wait for completion
                             ▼
                    ┌─────────────────┐
                    │  Iteration 5    │
                    │ /combat-sim-got │
                    └─────────────────┘
```

## Execution Steps

### Step 0: Validate Arguments

```bash
if [ ! -f "src/{BOT_NAME}/RobotPlayer.java" ]; then
  echo "ERROR: Bot not found"
  exit 1
fi
```

### Step 1: Run Iteration 1

```
Run: /combat-sim-got --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
```

**WAIT for completion. Capture the result (ACCEPT/REJECT and delta score).**

### Step 2: Run Iteration 2

```
Run: /combat-sim-got --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
```

**WAIT for completion. Capture the result.**

### Step 3: Run Iteration 3

```
Run: /combat-sim-got --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
```

**WAIT for completion. Capture the result.**

### Step 4: Run Iteration 4

```
Run: /combat-sim-got --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
```

**WAIT for completion. Capture the result.**

### Step 5: Run Iteration 5

```
Run: /combat-sim-got --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
```

**WAIT for completion. Capture the result.**

---

## Result Tracking

After each iteration, record:
```
ITERATION_{N} = {
  decision: "ACCEPT|ACCEPT_TENTATIVE|REJECT_SOFT|REJECT",
  delta_score: N,
  wins: N,
  rounds: N,
  changes_made: ["solution IDs applied"]
}
```

---

## Final Summary Report

After all 5 iterations complete, output:

```
═══════════════════════════════════════════════════════════════════════════════
GOT ITERATOR COMPLETE - 5 ITERATIONS
═══════════════════════════════════════════════════════════════════════════════

Bot: {BOT_NAME}
Opponent: {OPPONENT}
Maps: {MAPS}

ITERATION RESULTS:
┌───────────┬──────────────────┬─────────────┬──────┬────────┬─────────────────┐
│ Iteration │ Decision         │ Delta Score │ Wins │ Rounds │ Changes Applied │
├───────────┼──────────────────┼─────────────┼──────┼────────┼─────────────────┤
│ 1         │ {decision}       │ {+/-N}      │ {N}  │ {N}    │ {A1, C1}        │
│ 2         │ {decision}       │ {+/-N}      │ {N}  │ {N}    │ {B1}            │
│ 3         │ {decision}       │ {+/-N}      │ {N}  │ {N}    │ {A2, B1}        │
│ 4         │ {decision}       │ {+/-N}      │ {N}  │ {N}    │ {C2}            │
│ 5         │ {decision}       │ {+/-N}      │ {N}  │ {N}    │ {B2, C1}        │
└───────────┴──────────────────┴─────────────┴──────┴────────┴─────────────────┘

SUMMARY:
- Total Accepted Changes: {N}/5
- Total Rejected Changes: {N}/5
- Cumulative Delta Score: {sum of all delta scores}
- Starting Wins: {N} → Final Wins: {N}
- Starting Rounds: {N} → Final Rounds: {N}

OBJECTIVE STATUS:
- Win Rate: {N}% (target: 50%+)
- Average Winning Rounds: {N} (target: ≤500)
- Status: {MET|NOT MET}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Important Notes

1. **Sequential execution** - NEVER run iterations in parallel
2. **Wait for completion** - Each iteration must fully complete before starting the next
3. **Pass through arguments** - Use the same arguments for all 5 iterations
4. **Track cumulative progress** - Each iteration builds on the previous one's changes
5. **Early termination** - If the objective is met (50%+ wins, ≤500 rounds), you may note this but still complete all 5 iterations for thoroughness
