---
description: Run a Battlecode match and save results to state file
mode: primary
temperature: 0.3
permission:
  bash: allow
  read: allow
  unsafe-write: allow
---

# Game Run Match

Run a single match and save structured results for the analyze phase.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`
- `--opponent NAME` - **REQUIRED**: Opponent folder in `src/NAME/`
- `--maps MAP` - Single map (default: `MagicWood`)

---

## Identity

**Start with:**
```
=== GAME-RUN-MATCH STARTED ===
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

**⚠️ IMPORTANT:**
- Do NOT run any database queries yourself
- Do NOT run sqlite3 commands
- Do NOT run Python scripts to query .db files
- The script output is your ONLY data source

---

## Step 2: Parse Output

From the script output, extract:

1. **RESULT section:**
   - `OUTCOME` (WIN or LOSS)
   - `ROUNDS` (number)
   - `GOAL_MET` (YES or NO)
   - Final bullets/VP for both teams

2. **UNIT SUMMARY table:**
   - Produced / Lost / Alive counts per unit type
   - For both teams

3. **ECONOMY TIMELINE:**
   - Bullets/VP at intervals
   - Cumulative generated/spent

4. **COMBAT TIMELINE:**
   - Deaths by period

5. **MOVEMENT ANALYSIS:**
   - Unit distribution
   - Any ⚠ stuck unit warnings

6. **VP ACTIVITY:**
   - Donation events if any

---

## Step 3: Save Results

Write the COMPLETE script output to `src/{BOT}/.state/match-result.txt`:

The file should contain the ENTIRE output from the match script, preserving all sections exactly as they appeared.

Also write a summary file `src/{BOT}/.state/match-summary.txt` with just:
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

## Step 4: Finish

Print:
```
=== GAME-RUN-MATCH COMPLETE ===
Result: {OUTCOME} in {ROUNDS} rounds
Goal Met: {GOAL_MET}
Saved to: src/{BOT}/.state/match-result.txt
```
