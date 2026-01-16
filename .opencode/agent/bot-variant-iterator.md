---
description: Bot Variant Iterator - Runs Bot Variant Optimizer 5 times sequentially
mode: primary
temperature: 1
permission:
  bash: allow
  read: allow
  edit: allow
  glob: allow
---

# Bot Variant Iterator

You run the **bot-variant-optimizer** agent **5 times sequentially**, waiting for each iteration to complete before starting the next.

## IMPORTANT: Identity Announcement

**ALWAYS start your response with:**
```
=== BOT-VARIANT-ITERATOR STARTED ===
```

## Arguments

Parse and pass through to each iteration:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--opponent NAME` - Opponent bot (default: `examplefuncsplayer`)
- `--maps MAPS` - Comma-separated maps (default: `Shrine`)

## Execution Flow

```
┌─────────────────────────────────────────────────────────────────┐
│              BOT-VARIANT-ITERATOR (YOU)                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌────────────────────────┐
                    │      Iteration 1       │
                    │ /bot-variant-optimizer │
                    └───────────┬────────────┘
                                │ wait for completion
                                ▼
                    ┌────────────────────────┐
                    │      Iteration 2       │
                    │ /bot-variant-optimizer │
                    └───────────┬────────────┘
                                │ wait for completion
                                ▼
                    ┌────────────────────────┐
                    │      Iteration 3       │
                    │ /bot-variant-optimizer │
                    └───────────┬────────────┘
                                │ wait for completion
                                ▼
                    ┌────────────────────────┐
                    │      Iteration 4       │
                    │ /bot-variant-optimizer │
                    └───────────┬────────────┘
                                │ wait for completion
                                ▼
                    ┌────────────────────────┐
                    │      Iteration 5       │
                    │ /bot-variant-optimizer │
                    └────────────────────────┘
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
Run: /bot-variant-optimizer --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS}
```

**WAIT for completion. Capture the result (WINNER variant and score).**

### Step 2: Run Iteration 2

```
Run: /bot-variant-optimizer --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS}
```

**WAIT for completion. Capture the result.**

### Step 3: Run Iteration 3

```
Run: /bot-variant-optimizer --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS}
```

**WAIT for completion. Capture the result.**

### Step 4: Run Iteration 4

```
Run: /bot-variant-optimizer --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS}
```

**WAIT for completion. Capture the result.**

### Step 5: Run Iteration 5

```
Run: /bot-variant-optimizer --bot {BOT_NAME} --opponent {OPPONENT} --maps {MAPS}
```

**WAIT for completion. Capture the result.**

---

## Result Tracking

After each iteration, record:
```
ITERATION_{N} = {
  winner: "original|v1|v2|v3|v4|v5",
  score: N,
  rounds: N,
  enemy_killed: N,
  survivors: N,
  won_match: true|false,
  code_changed: true|false
}
```

---

## Final Summary Report

After all 5 iterations complete, output:

```
═══════════════════════════════════════════════════════════════════════════════
BOT VARIANT ITERATOR COMPLETE - 5 ITERATIONS
═══════════════════════════════════════════════════════════════════════════════

Bot: {BOT_NAME}
Opponent: {OPPONENT}
Maps: {MAPS}

ITERATION RESULTS:
┌───────────┬──────────────┬───────┬────────┬──────────────┬───────────┬──────────────┐
│ Iteration │ Winner       │ Score │ Rounds │ Enemy Killed │ Survivors │ Code Changed │
├───────────┼──────────────┼───────┼────────┼──────────────┼───────────┼──────────────┤
│ 1         │ {variant}    │ {N}   │ {N}    │ {N}          │ {N}       │ {YES/NO}     │
│ 2         │ {variant}    │ {N}   │ {N}    │ {N}          │ {N}       │ {YES/NO}     │
│ 3         │ {variant}    │ {N}   │ {N}    │ {N}          │ {N}       │ {YES/NO}     │
│ 4         │ {variant}    │ {N}   │ {N}    │ {N}          │ {N}       │ {YES/NO}     │
│ 5         │ {variant}    │ {N}   │ {N}    │ {N}          │ {N}       │ {YES/NO}     │
└───────────┴──────────────┴───────┴────────┴──────────────┴───────────┴──────────────┘

SUMMARY:
- Total Iterations with Code Changes: {N}/5
- Total Iterations where Original Won: {N}/5
- Starting Score (Iter 1): {N} → Final Score (Iter 5): {N}
- Score Improvement: {+/-N}

OBJECTIVE STATUS:
- Final Match Result: {WON|LOST}
- Final Winning Rounds: {N} (target: ≤500)
- Status: {MET|NOT MET}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Important Notes

1. **Sequential execution** - NEVER run iterations in parallel
2. **Wait for completion** - Each iteration must fully complete before starting the next
3. **Pass through arguments** - Use the same arguments for all 5 iterations
4. **Track cumulative progress** - Each iteration builds on the previous one's changes (if any variant won)
5. **Early termination** - If the objective is met (wins in ≤500 rounds), you may note this but still complete all 5 iterations for thoroughness
6. **Code changes compound** - If a variant wins in iteration N, iteration N+1 starts with that improved code as the new base
