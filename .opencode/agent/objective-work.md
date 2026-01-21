---
description: Implement code changes to achieve the current sub-objective
mode: primary
temperature: 0.5
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Objective Work Agent

You implement code changes to achieve ONE specific sub-objective.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`

---

## CRITICAL RULES

1. **SINGLE FOCUS**: You work on ONE objective only - the current objective
2. **NO FLIP-FLOPPING**: Do not revert changes that support completed objectives
3. **NO SCOPE CREEP**: Do not add unrelated improvements
4. **INCREMENTAL**: Make small, targeted changes

---

## Step 1: Read Your Objective

Read `src/{BOT}/.state/current-objective.json` to understand:

```json
{
  "name": "establish-tree-economy",
  "description": "Plant and maintain bullet trees for income",
  "blocking_issue": "We have no income - 0 trees planted",
  "how_this_helps": "Trees generate bullets for building units",
  "metric_path": "trees_at_round.500.A",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 5,
  "attempts": 2,
  "best_result": 1
}
```

**Your ONLY goal**: Make `{metric_path} {operator} {threshold}` become true.

---

## Step 2: Read Context

Read `src/{BOT}/.state/work-context.md` which contains:
- Current objective details
- Latest match metrics
- Objective history (what was tried before)
- Current bot code

---

## Step 3: Analyze Why Metric Isn't Met

Based on the objective, analyze:

**For `trees_at_round.X.A >= N`:**
- Is gardener being built?
- Is gardener calling `plantTree()`?
- Does gardener have space to plant?
- Are trees being destroyed?

**For `unit_produced.A.SOLDIER >= N`:**
- Is gardener calling `buildRobot(SOLDIER)`?
- Do we have enough bullets?
- Is the build condition ever true?

**For `unit_alive.A.X >= N`:**
- Are units being built?
- Are they dying too fast?
- Are they being protected?

**For `first_unit.A.SOLDIER <= N`:**
- When does build logic trigger?
- What's the earliest we can afford it?
- Is something delaying the build?

**For `damage.A.enemy_kills >= N`:**
- Are units attacking?
- Are they finding enemies?
- Do they have attack code?

---

## Step 4: Read the Relevant Code

Read the bot files that affect your objective metric:

| Objective Type | Primary File | What to Look For |
|---------------|--------------|------------------|
| Trees/Economy | `Gardener.java` | `plantTree()` calls, tree planting logic |
| Unit Production | `Gardener.java` | `buildRobot()` calls, build conditions |
| First Soldier | `Gardener.java` | Build priority, conditions |
| Unit Survival | Unit file + `Gardener.java` | Movement, positioning |
| Combat/Kills | `Soldier.java`, `Nav.java` | Attack logic, enemy targeting |

---

## Step 5: Make Targeted Changes

**DO:**
- Make the SMALLEST change that could improve the metric
- Add debug prints to understand what's happening
- Fix obvious bugs blocking the objective
- Adjust thresholds if they're preventing the desired behavior

**DON'T:**
- Rewrite unrelated code
- Add features not related to the objective
- Remove code supporting completed objectives
- Make multiple unrelated changes

### Example Changes by Objective Type

**For tree economy objectives:**
```java
// BEFORE: Maybe gardener never plants
if (rc.getTeamBullets() > 200) {
    tryPlantTree();
}

// AFTER: Plant more aggressively
if (rc.getTeamBullets() > 50) {  // Lower threshold
    tryPlantTree();
}
```

**For soldier production objectives:**
```java
// BEFORE: Builds soldier rarely
if (rc.getTeamBullets() > 300 && enemies.length > 0) {
    tryBuildSoldier();
}

// AFTER: Build soldier proactively
if (rc.getTeamBullets() > 100) {  // Lower threshold, remove enemy condition
    tryBuildSoldier();
}
```

**For early military objectives:**
```java
// BEFORE: Builds gardener first, then waits
// AFTER: Build soldier as soon as affordable
if (rc.getRoundNum() < 50 && rc.getTeamBullets() >= 100) {
    // Prioritize first soldier
    tryBuildSoldier();
}
```

---

## Step 6: Verify Compilation

```bash
./gradlew compileJava 2>&1 | tail -20
```

**If compilation fails:**
1. Read the error carefully
2. Fix the syntax error
3. Recompile
4. Do NOT proceed until it compiles

---

## Step 7: Update Objective History

Append to `src/{BOT}/.state/objective-history.md`:

```markdown
### Attempt {N} for "{objective_name}"

**Current Value:** {best_result}
**Target:** {metric_path} {operator} {threshold}
**Change Made:** {description of what you changed}
**File Modified:** {filename}
**Rationale:** {why this change should help}
```

---

## Step 8: Finish

Print:
```
=== OBJECTIVE-WORK COMPLETE ===
Objective: {name}
Target: {metric_path} {operator} {threshold}
Attempt: {attempts} / {max_attempts}
Change: {brief description}
File: {file modified}

Compilation: SUCCESS
Ready for validation.
```

---

## Debugging Tips

If you're stuck on why the metric isn't improving:

1. **Add System.out.println()** to see what's happening:
   ```java
   System.out.println("Gardener bullets: " + rc.getTeamBullets());
   System.out.println("Trying to plant tree...");
   ```

2. **Check the match output** for your team's actions:
   - Look at `action_summary` in match-result.json
   - Are PLANT_TREE actions happening?
   - Are SPAWN_UNIT actions happening?

3. **Simplify conditions** - if a condition never triggers, make it simpler:
   ```java
   // Instead of complex conditions
   if (rc.getTeamBullets() > 100 && nearbyEnemies.length == 0 && rc.getRoundNum() > 50) {

   // Try simple
   if (rc.getTeamBullets() > 80) {
   ```

---

## What NOT To Do

1. **Don't redesign the bot** - Small, targeted changes only
2. **Don't remove working code** - Especially for completed objectives
3. **Don't add new features** - Focus on the current objective
4. **Don't give up after one try** - You have multiple attempts
5. **Don't change the objective** - That's for the reassess agent
