---
description: Reassess a stalled objective - adjust, decompose, or abandon
mode: primary
temperature: 0.6
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Objective Reassess Agent

The current objective has reached max attempts without success. You must decide what to do next.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`

---

## Step 1: Understand the Situation

Read `src/{BOT}/.state/current-objective.json`:

```json
{
  "name": "establish-tree-economy",
  "metric_path": "trees_at_round.500.A",
  "operator": ">=",
  "threshold": 5,
  "max_attempts": 5,
  "attempts": 5,
  "best_result": 2
}
```

Read `src/{BOT}/.state/objective-history.md` to see what was tried.

---

## Step 2: Analyze Why It Failed

Consider:
1. **Was progress made?** (best_result improved from 0?)
2. **What was tried?** (check history)
3. **Is the objective realistic?** (maybe threshold too high?)
4. **Is something fundamentally broken?** (e.g., gardener code crashes)

---

## Step 3: Choose Your Action

You have FOUR options:

### Option A: ADJUST (Lower the Bar)

If progress was made but we didn't hit the target, make the objective easier:

**When to use:**
- best_result > 0 but < threshold
- We're making progress, just not fast enough
- The target might be too ambitious

**How to do it:**
1. Lower the threshold (e.g., 5 → 3)
2. Or extend the measurement round (e.g., round 300 → round 500)
3. Reset attempts to 0
4. Write updated objective to `current-objective.json`

**Example:**
```json
{
  "name": "establish-tree-economy-adjusted",
  "metric_path": "trees_at_round.500.A",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 4,
  "attempts": 0,
  "best_result": 2,
  "adjusted_from": "trees_at_round.500.A >= 5"
}
```

---

### Option B: DECOMPOSE (Break It Down)

If the objective is too complex, break it into smaller steps:

**When to use:**
- Objective requires multiple things to work
- best_result is 0 (nothing worked)
- Need to solve a prerequisite first

**How to do it:**
1. Identify the prerequisite objective
2. Write a NEW simpler objective to `current-objective.json`
3. The original objective can be tackled later

**Example:**
Original: `unit_produced.A.SOLDIER >= 3`
Decompose into: `unit_produced.A.GARDENER >= 1` (need gardener first!)

```json
{
  "name": "prerequisite-gardener",
  "description": "Build at least one gardener before soldiers",
  "blocking_issue": "Can't build soldiers without a gardener",
  "how_this_helps": "Gardener builds soldiers",
  "metric_path": "unit_produced.A.GARDENER",
  "operator": ">=",
  "threshold": 1,
  "max_attempts": 3,
  "attempts": 0,
  "best_result": 0,
  "decomposed_from": "unit_produced.A.SOLDIER >= 3"
}
```

---

### Option C: ABANDON (Give Up and Try Something Else)

If the objective is fundamentally blocked or not useful:

**When to use:**
- Objective is impossible on this map
- Approach was completely wrong
- Better strategy identified

**How to do it:**
1. Delete `current-objective.json`
2. Log the abandonment in objective-history.md
3. Next iteration will propose a new objective

**Example situations:**
- "Build trees" but map has no space for trees
- "Build soldiers" but we're trying a VP-rush strategy instead
- "Kill 5 enemies" but enemy has no units

---

### Option D: RETRY (New Approach)

If you believe the objective is still valid but needs a completely different approach:

**When to use:**
- Previous attempts all used similar strategies
- You have a genuinely NEW idea
- The objective is still the right goal

**How to do it:**
1. Reset attempts to 0
2. Document the NEW approach in objective-history.md
3. Keep the same objective in `current-objective.json`

**Important:** The approach MUST be different from what was tried before!

---

## Step 4: Execute Your Decision

Based on your choice:

### If ADJUST:
```bash
# Update current-objective.json with lower threshold
```

### If DECOMPOSE:
```bash
# Write NEW simpler objective to current-objective.json
```

### If ABANDON:
```bash
rm src/{BOT}/.state/current-objective.json
```

### If RETRY:
```bash
# Update current-objective.json with attempts=0
```

---

## Step 5: Update History

Append to `src/{BOT}/.state/objective-history.md`:

```markdown
### Objective Reassessment: {name}

**Status:** {ADJUSTED|DECOMPOSED|ABANDONED|RETRY}
**Best Result:** {best_result} / {threshold}
**Attempts Used:** {attempts}
**Reason:** {why this decision}
**Next Step:** {what happens next}
```

If ABANDONED, also add to the Failed Objectives table:
```markdown
| # | {name} | {metric} | {threshold} | {best_result} | {reason} | {iteration} |
```

---

## Step 6: Finish

Print:
```
=== OBJECTIVE-REASSESS COMPLETE ===
Objective: {name}
Decision: {ADJUST|DECOMPOSE|ABANDON|RETRY}
Reason: {brief reason}
Next: {what happens next iteration}
```

---

## Decision Framework

```
Was progress made? (best_result > 0)
├── YES → Is target realistic?
│   ├── YES → RETRY with new approach
│   └── NO → ADJUST (lower threshold)
│
└── NO (best_result = 0)
    ├── Is there a prerequisite we're missing?
    │   ├── YES → DECOMPOSE
    │   └── NO → Is objective even possible?
    │       ├── YES → RETRY with completely different approach
    │       └── NO → ABANDON
```

---

## Examples

### Example 1: Partial Progress → ADJUST
```
Objective: trees_at_round.500.A >= 5
Best result: 3
Decision: ADJUST to threshold 3
Reason: We can plant 3 trees consistently, 5 is too ambitious
```

### Example 2: No Progress → DECOMPOSE
```
Objective: unit_produced.A.SOLDIER >= 3
Best result: 0
Analysis: We never built a gardener, so couldn't build soldiers
Decision: DECOMPOSE to "build 1 gardener first"
```

### Example 3: Fundamentally Wrong → ABANDON
```
Objective: trees_at_round.300.A >= 5
Best result: 1
Analysis: Map is densely packed, no space for trees
Decision: ABANDON, propose economy objective based on resource trees instead
```

### Example 4: Same Approach Repeatedly → RETRY
```
Objective: damage.A.enemy_kills >= 3
Best result: 1
Analysis: All attempts tweaked soldier count, none fixed targeting
Decision: RETRY - will fix enemy detection code instead
```
