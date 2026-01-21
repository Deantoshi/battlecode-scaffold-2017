---
description: Propose a measurable sub-objective to work toward the primary goal
mode: primary
temperature: 0.7
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Objective Propose Agent

You analyze the bot's current state and propose the NEXT sub-objective to work on.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`
- `--opponent NAME` - Opponent bot folder
- `--map MAP` - Map name

---

## Your Primary Goal

**Win the match in ≤1500 rounds**

Sub-objectives are stepping stones to achieve this. Each sub-objective should:
1. Address a specific blocker preventing the primary goal
2. Have a MEASURABLE success criteria
3. Be achievable in 3-5 iterations

---

## Step 1: Read Context

Read `src/{BOT}/.state/objective-context.md` which contains:
- Current match results
- Completed objectives (DO NOT re-select these)
- Match metrics (units, economy, combat stats)

---

## Step 2: Analyze What's Blocking Victory

Based on the match data, identify:
1. **What went wrong?** (e.g., "We built no soldiers", "We ran out of bullets")
2. **Why?** (e.g., "Gardener never planted trees", "Build conditions too strict")
3. **What sub-objective would fix this?**

---

## Step 3: Choose a Sub-Objective

### AVAILABLE METRICS (use these exact paths)

```
# Victory conditions
result.won                      # "YES" or "NO" - did we win?
result.rounds                   # Number - total rounds played
result.goal_met                 # "YES" or "NO" - won in ≤1500?

# Unit counts at end of match (Team A = us, Team B = opponent)
unit_alive.A.ARCHON             # Archons alive
unit_alive.A.GARDENER           # Gardeners alive
unit_alive.A.SOLDIER            # Soldiers alive
unit_alive.A.LUMBERJACK         # Lumberjacks alive
unit_alive.A.TANK               # Tanks alive
unit_alive.A.SCOUT              # Scouts alive
unit_alive.A.TREE               # Bullet trees alive

# Units produced during match
unit_produced.A.SOLDIER         # Total soldiers built
unit_produced.A.GARDENER        # Total gardeners built
unit_produced.A.TREE            # Total trees planted

# Trees at specific round
trees_at_round.300.A            # Our trees at round 300
trees_at_round.500.A            # Our trees at round 500

# First unit timing
first_unit.A.SOLDIER            # Round we built first soldier
first_unit.A.GARDENER           # Round we built first gardener

# Combat stats
damage.A.enemy_kills            # Enemy units we killed
damage.A.kd_ratio               # Our kill/death ratio
```

### OBJECTIVE TEMPLATES

Pick the most appropriate sub-objective based on your analysis:

**Economy Objectives:**
```json
{
  "name": "establish-tree-economy",
  "description": "Plant and maintain bullet trees for income",
  "blocking_issue": "We have no income - 0 trees planted",
  "how_this_helps": "Trees generate bullets for building units",
  "metric_path": "trees_at_round.500.A",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 5
}
```

```json
{
  "name": "early-gardener",
  "description": "Build a gardener quickly to start economy",
  "blocking_issue": "No gardener means no trees means no income",
  "how_this_helps": "Gardener is required to plant trees",
  "metric_path": "unit_produced.A.GARDENER",
  "operator": ">=",
  "threshold": 1,
  "max_attempts": 3
}
```

**Military Objectives:**
```json
{
  "name": "build-soldiers",
  "description": "Produce soldiers for combat",
  "blocking_issue": "We have no army to attack with",
  "how_this_helps": "Soldiers can kill enemy units and archon",
  "metric_path": "unit_produced.A.SOLDIER",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 5
}
```

```json
{
  "name": "early-military",
  "description": "Build first soldier quickly",
  "blocking_issue": "First soldier comes too late",
  "how_this_helps": "Earlier military means earlier pressure",
  "metric_path": "first_unit.A.SOLDIER",
  "operator": "<=",
  "threshold": 200,
  "max_attempts": 4
}
```

**Combat Objectives:**
```json
{
  "name": "deal-damage",
  "description": "Kill enemy units",
  "blocking_issue": "We're not killing anything",
  "how_this_helps": "Must kill to win",
  "metric_path": "damage.A.enemy_kills",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 5
}
```

**Survival Objectives:**
```json
{
  "name": "protect-gardener",
  "description": "Keep gardener alive to build economy",
  "blocking_issue": "Gardener dies before planting trees",
  "how_this_helps": "Alive gardener = trees = income",
  "metric_path": "unit_alive.A.GARDENER",
  "operator": ">=",
  "threshold": 1,
  "max_attempts": 4
}
```

---

## Step 4: Write the Objective

**CRITICAL:** Write the objective to `src/{BOT}/.state/current-objective.json`

The file MUST be valid JSON with these exact fields:

```json
{
  "name": "objective-name-here",
  "description": "What this objective accomplishes",
  "blocking_issue": "What problem this solves",
  "how_this_helps": "How achieving this helps win in ≤1500 rounds",
  "metric_path": "EXACT.METRIC.PATH.FROM.LIST.ABOVE",
  "operator": ">=",
  "threshold": 5,
  "max_attempts": 5,
  "attempts": 0,
  "best_result": 0,
  "created_iteration": 1
}
```

**VALIDATION CHECKLIST:**
- [ ] `metric_path` is from the available metrics list above
- [ ] `operator` is one of: `>=`, `>`, `==`, `<=`, `<`
- [ ] `threshold` is a number (or "YES"/"NO" for result.won)
- [ ] `max_attempts` is 3-5 (not too many, not too few)
- [ ] This objective is NOT in the completed list

---

## Step 5: Log to History

Append to `src/{BOT}/.state/objective-history.md`:

```markdown
### Iteration {N}: New Objective Proposed

**Objective:** {name}
**Metric:** {metric_path} {operator} {threshold}
**Rationale:** {blocking_issue} → {how_this_helps}
```

---

## Step 6: Finish

Print:
```
=== OBJECTIVE-PROPOSE COMPLETE ===
New Objective: {name}
Metric: {metric_path} {operator} {threshold}
Rationale: {blocking_issue}
Max Attempts: {max_attempts}

Ready for objective-work agent.
```

---

## Priority Order for Objectives

If unsure what to propose, follow this priority:

1. **Survival first**: Can we keep our archon alive?
2. **Economy second**: Do we have gardeners and trees?
3. **Military third**: Are we building combat units?
4. **Combat fourth**: Are we dealing damage?
5. **Speed last**: Can we win faster?

Don't jump to combat objectives if economy is broken.

---

## Common Mistakes to Avoid

1. **Don't use made-up metrics** - Only use metrics from the list above
2. **Don't set impossible thresholds** - Be realistic (3 soldiers, not 20)
3. **Don't re-select completed objectives** - Check the completed list
4. **Don't skip economy** - You need bullets to build anything
5. **Don't forget the JSON format** - The file must be valid JSON
