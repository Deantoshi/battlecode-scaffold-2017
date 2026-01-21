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

Implement the improvement plan and update iteration history.

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
- **Match Summary**: OUTCOME (WIN/LOSS) and ROUNDS for history update
- **Iteration History**: Previous iterations for context

This single file contains everything you need - do NOT read the individual files separately.

---

## Step 2: Read Current Code

Read the file specified in the improvement plan:
`src/{BOT}/{filename}.java`

Understand the current implementation before making changes.

---

## Step 3: Implement the Change

Using `unsafe-write`, write the complete modified file.

**Guidelines:**
- Make ONLY the change specified in the plan
- Do NOT refactor unrelated code
- Do NOT add unnecessary comments
- Preserve existing code structure
- Ensure the change addresses the identified problem

---

## Step 4: Verify Compilation

```bash
./gradlew compileJava 2>&1 | tail -30
```

**If compilation fails:**
1. Read the error message
2. Fix the compilation error
3. Write the corrected file
4. Re-run compilation check
5. Repeat until successful

**Do NOT proceed until compilation succeeds.**

---

## Step 5: Update Iteration History

Using the Match Summary and Iteration History from `implement-context.md` (already read in Step 1):

Determine the next iteration number by counting existing rows in the Iterations table.

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

Write the updated history file.

---

## Step 6: Finish

Print:
```
=== GAME-IMPLEMENT COMPLETE ===
Iteration: {N}
File Modified: src/{BOT}/{filename}.java
Change: {brief description}
Compilation: SUCCESS
History updated: src/{BOT}/iteration-history.md
```

---

## Error Recovery

**If compilation keeps failing:**
1. Re-read the original file from git: `git show HEAD:src/{BOT}/{file}.java`
2. Try a simpler version of the change
3. If still failing, revert to original and note in history

**If plan file is missing:**
1. Print error: "No improvement plan found"
2. Exit without changes

---

## Code Quality Checklist

Before writing the file, verify:
- [ ] All imports are present
- [ ] All braces are matched
- [ ] No syntax errors
- [ ] Method signatures unchanged (unless intentional)
- [ ] Variable names consistent
- [ ] No duplicate method definitions
