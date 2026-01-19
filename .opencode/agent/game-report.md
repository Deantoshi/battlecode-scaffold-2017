---
description: Generate final report when goal is achieved
mode: primary
temperature: 0.5
permission:
  read: allow
  unsafe-write: allow
---

# Game Report

Generate a final report summarizing the improvement session.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`

---

## Identity

**Start with:**
```
=== GAME-REPORT STARTED ===
Bot: {BOT}
```

---

## Step 1: Read State Files

Read the following files:
- `src/{BOT}/iteration-history.md` - Full iteration history
- `src/{BOT}/.state/match-summary.txt` - Final match results
- `src/{BOT}/.state/goal-status.txt` - Goal status

---

## Step 2: Parse History

From iteration-history.md, extract:
- Map name
- Goal (Win in ≤1500 rounds)
- All iteration rows from the table
- Code analysis section (opponent and bot summaries)

Count total iterations (excluding baseline row 0).

---

## Step 3: Update History File with Final Status

Add a summary section to the END of `src/{BOT}/iteration-history.md`:

```markdown
## Final Status

**RESULT:** {WIN/LOSS} in {ROUNDS} rounds
**GOAL:** Win in ≤1500 rounds
**STATUS:** {ACHIEVED / NOT ACHIEVED after N iterations}

### Summary of Changes
{Brief description of the overall strategy evolution from baseline to final}

### Key Improvements
1. {Most impactful change}
2. {Second most impactful change}
3. {etc.}
```

---

## Step 4: Print Final Report

Output:
```
═══════════════════════════════════════════════════════════════════════════════
GAME-SELF-IMPROVER COMPLETE
═══════════════════════════════════════════════════════════════════════════════

Bot: {BOT}
Opponent: {OPPONENT from history}
Map: {MAP from history}

RESULT: {WIN/LOSS} in {ROUNDS} rounds
GOAL: Win in ≤1500 rounds
STATUS: {ACHIEVED / NOT ACHIEVED after N iterations}

ITERATIONS SUMMARY:
┌───────────┬────────┬────────┬─────────────────────────────────┐
│ Iteration │ Result │ Rounds │ Change Made                     │
├───────────┼────────┼────────┼─────────────────────────────────┤
│ 0         │ {W/L}  │ {N}    │ (baseline)                      │
│ 1         │ {W/L}  │ {N}    │ {description}                   │
│ ...       │        │        │                                 │
└───────────┴────────┴────────┴─────────────────────────────────┘

FINAL BOT CHANGES FROM ORIGINAL:
{List all changes made across iterations}

History file: src/{BOT}/iteration-history.md
═══════════════════════════════════════════════════════════════════════════════
```

---

## Step 5: Finish

```
=== GAME-REPORT COMPLETE ===
Final report generated.
```
