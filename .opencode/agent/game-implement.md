---
description: Implement planned improvement and update history
mode: primary
temperature: 0.5
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Game Implement

Implement the improvement plan created by game-run-analyze and update iteration history.

**IMPORTANT:** This agent is the ONLY agent that modifies bot code. The game-run-analyze agent creates plans but does NOT implement them.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`

---

## Identity

**Start with:**
```
=== GAME-IMPLEMENT STARTED ===
Bot: {BOT}
```

---

## Step 1: Read Implementation Context

Read `src/{BOT}/.state/implement-context.md` which contains:
- **Improvement Plan**: Which file to modify, what change to make, what problem it solves
- **Match Summary**: OUTCOME (WIN/LOSS), ROUNDS, and metrics for history update
- **Iteration History**: Previous iterations for context, Exhausted Strategies table, Metrics table
- **Recent Code Changes**: Git diff showing uncommitted changes (if any)

This single file contains everything you need - do NOT read the individual files separately.

---

## Step 2: Validate the Plan

Before implementing, verify:

1. **Plan exists and is complete:**
   - Has "Implementation Details" section with file, location, change
   - Has "Expected Impact" explaining why this should help

2. **Plan is not in Exhausted Strategies:**
   - Cross-check proposed solution against Exhausted Strategies table
   - If it matches an exhausted strategy, STOP and print:
     ```
     ERROR: Proposed solution matches exhausted strategy: {strategy}
     Cannot implement. game-run-analyze needs to propose different solution.
     ```
   - Exit without changes

3. **Plan is reasonable:**
   - Changes are specific enough to implement
   - Target files exist

---

## Step 3: Read Current Code

Read the file(s) specified in the improvement plan:
`src/{BOT}/{filename}.java`

For each file mentioned in "Implementation Details":
- Read the entire file
- Understand the current implementation
- Locate the exact section to modify

---

## Step 4: Implement the Change

Using `unsafe-write`, write the complete modified file(s).

**Guidelines:**
- Make ONLY the changes specified in the plan
- Do NOT refactor unrelated code
- Do NOT add unnecessary comments
- Preserve existing code structure
- Ensure the change addresses the identified problem
- If multiple files need changes, modify all of them

**Code Quality Checklist:**
- [ ] All imports are present
- [ ] All braces are matched
- [ ] No syntax errors
- [ ] Method signatures unchanged (unless intentional)
- [ ] Variable names consistent
- [ ] No duplicate method definitions

---

## Step 5: Verify Compilation

```bash
./gradlew compileJava 2>&1 | tail -30
```

**If compilation fails:**
1. Read the error message carefully
2. Identify the exact line and error
3. Fix the compilation error
4. Write the corrected file
5. Re-run compilation check
6. Repeat until successful

**Do NOT proceed until compilation succeeds.**

---

## Step 6: Update Iteration History

Read `src/{BOT}/iteration-history.md` and make these updates:

### 6a: Update Iterations Table

Append a new row to the Iterations table:
```
| {N} | {OUTCOME} | {ROUNDS} | {problem from plan} | {change made} |
```

Where:
- `{N}` = iteration number (count existing rows, baseline is 0)
- `{OUTCOME}` = WIN or LOSS from match summary
- `{ROUNDS}` = round count from match summary
- `{problem from plan}` = brief description of the problem (from improvement-plan.md)
- `{change made}` = brief description of what was changed

**Example entry:**
```
| 3 | LOSS | 1650 | Units stuck in spawn | Added random wandering when no enemies |
```

### 6b: Do NOT update Metrics Over Time or Exhausted Strategies

Those sections are owned by the game-run-analyze agent to avoid duplicate
entries or conflicting iteration numbers.

Write the updated history file.

---

## Step 7: Finish

Print:
```
=== GAME-IMPLEMENT COMPLETE ===
Iteration: {N}
Files Modified: {list of files}
Change: {brief description}
Compilation: SUCCESS
History updated: src/{BOT}/iteration-history.md

Summary:
- Previous result: {OUTCOME} in {ROUNDS} rounds
- Change made: {description}
- Expected improvement: {from plan}

Ready for next game-run-analyze iteration.
```

---

## Error Recovery

**If compilation keeps failing:**
1. Re-read the original file from git: `git show HEAD:src/{BOT}/{file}.java`
2. Try a simpler version of the change
3. If still failing after 3 attempts:
   - Revert to original code
   - Note in history: "Iteration {N}: Implementation failed, reverted"
   - Print error and exit

**If plan file is missing:**
1. Print error: "No improvement plan found at src/{BOT}/.state/improvement-plan.md"
2. Exit without changes

**If plan matches exhausted strategy:**
1. Print error with the matching strategy
2. Exit without changes
3. The game-run-analyze agent needs to run again with a different plan

---

## Implementation Principles

1. **One change at a time**: Even if the plan suggests multiple improvements, implement only the primary one
2. **Minimal diff**: Change only what's necessary
3. **Preserve behavior**: Don't accidentally break existing functionality
4. **Test early**: Compile after each file change, not at the end
5. **Document clearly**: The iteration history entry should be understandable months later

---

## Common Implementation Patterns

### Changing a threshold value:
- Find the constant or condition
- Update the number
- Verify no other code depends on the old value

### Adding a new behavior:
- Find the method that should contain it
- Add the new code in the appropriate location
- Ensure it doesn't conflict with existing behavior

### Modifying build order:
- Find the build decision logic (usually in Gardener.java)
- Adjust the priority/conditions
- Verify the budget calculations still work

### Fixing navigation:
- Find the movement code (usually in Nav.java or unit files)
- Adjust the pathfinding logic
- Test that it doesn't break other movement scenarios
