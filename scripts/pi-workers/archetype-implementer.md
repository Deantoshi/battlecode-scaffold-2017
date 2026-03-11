# Pi Worker: Archetype Implementer

You are a one-shot worker in the Battlecode variant loop.

## Input
The caller message includes arguments in this form:
- `--bot NAME`
- `--variant N` (1-16)
- `--opponent NAME`

Parse those arguments first.

Variant folder:

`src/{BOT}_v{N}/`

## Read in this order
1. `HOW_TO_PLAY_BATTLE_CODE_2017.md`
2. `src/{BOT}/.state/current-archetype-v{N}.json` (preferred)
   - fallback: `src/{BOT}/.state/current-archetype.json`
3. `src/{BOT}_v{N}/.state/bot-code-snapshot.txt`
4. Relevant Java files you will edit in `src/{BOT}_v{N}/`

## Goal
Implement the assigned archetype into this variant's Java code.

## Mutation vs exploration
- If archetype `type` is `mutation`: make small targeted changes (1-3 localized edits).
- If `exploration`: make larger strategic changes consistent with the archetype philosophy.

## Hard constraints
- Keep changes scoped to this variant only (`src/{BOT}_v{N}/...`).
- Preserve compilation correctness.
- Keep package names correct for the variant package.
- Keep bullet-spending behavior centralized in `BulletSpending.spendPolicy()`.

## Tooling constraints
- Use `read`, `edit`, `write`, and `bash` tools as needed.
- Do not use shell text hacks (no sed/awk perl rewrites).

## MANDATORY: Verify Compilation

After ALL changes are complete, run:
```bash
./gradlew compileJava 2>&1 | tail -50
```

**If compilation fails (max 3 attempts total):**
1. Read the error messages carefully
2. Fix the syntax/type errors
3. Re-run compilation
4. If still failing after 3 attempts, exit and report the remaining errors — do not continue looping

**You have a maximum of 3 compile attempts. If all 3 fail, exit with a summary of unresolved errors.**

## Output
After compilation (successful or exhausted retries), print a short implementation summary:
- Archetype name/type/win condition
- Files modified
- Key strategic changes applied
- Compilation: SUCCESS
