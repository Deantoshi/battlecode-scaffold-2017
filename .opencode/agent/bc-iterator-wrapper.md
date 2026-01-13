---
description: Time-boxed wrapper that runs bc-manager iterations for 30 minutes
mode: subagent
temperature: 0
tools:
  bash: true
  task: true
  read: true
  glob: true
---

## CRITICAL RESTRICTION: File Access

**You are ONLY allowed to create or modify files inside the `src/` folder.**
- Allowed: `src/**/*`
- NOT allowed: Any file outside `src/` (build.gradle, CLAUDE.md, engine/, client/, etc.)

## Purpose

This agent wraps `bc-manager` in a time-boxed loop that runs for approximately 30 minutes. It uses the ralph-loop plugin to automatically re-trigger iterations until the time limit is reached.

## Arguments

Parse the Arguments section for:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--opponent NAME` - Opponent bot (default: `copy_bot`)
- `--iterations N` - Iterations per bc-manager cycle (default: `3`)
- `--duration M` - Total duration in minutes (default: `30`)

**Example:**
```
/bc-iterator-wrapper --bot my_bot
/bc-iterator-wrapper --bot my_bot --opponent other_bot --duration 45
```

## How It Works

1. Records the start time
2. Starts a ralph-loop with the bc-manager workflow as the prompt
3. Each iteration runs bc-manager for N iterations
4. After each bc-manager cycle completes, checks elapsed time
5. When 30+ minutes (or custom duration) has passed, outputs the completion promise to end the loop

## Startup Procedure

### Step 1: Record Start Time

Create a timestamp file to track when the session started:

```bash
echo "$(date +%s)" > /tmp/bc-iterator-start-time.txt
echo "Started bc-iterator-wrapper at $(date)"
```

### Step 2: Start the Ralph Loop

Use the `ralph_loop` tool with these parameters:

```
prompt: "Run bc-manager workflow iteration. Arguments: --bot {BOT_NAME} --opponent {OPPONENT} --iterations {ITERATIONS}

IMPORTANT TIME CHECK:
1. Read the start time: cat /tmp/bc-iterator-start-time.txt
2. Get current time: date +%s
3. Calculate elapsed minutes: echo $(( ($(date +%s) - $(cat /tmp/bc-iterator-start-time.txt)) / 60 ))

If elapsed time >= {DURATION} minutes, output:
<promise>Training session complete</promise>

Otherwise, invoke bc-manager using the Task tool:
- description: 'Run bc-manager iteration'
- prompt: 'Run bc-manager workflow. --bot {BOT_NAME} --opponent {OPPONENT} --iterations {ITERATIONS}'
- subagent_type: 'bc-manager'

Then report the results and let the loop continue."

max_iterations: 0  (unlimited - time-based termination)
completion_promise: "Training session complete"
```

## Per-Iteration Workflow

Each ralph-loop iteration should:

### 1. Check Elapsed Time
```bash
START_TIME=$(cat /tmp/bc-iterator-start-time.txt)
CURRENT_TIME=$(date +%s)
ELAPSED_MINUTES=$(( (CURRENT_TIME - START_TIME) / 60 ))
echo "Elapsed time: ${ELAPSED_MINUTES} minutes"
```

### 2. If Time Limit Reached
If `ELAPSED_MINUTES >= {DURATION}`:
- Output summary of all training accomplished
- Output: `<promise>Training session complete</promise>`
- This ends the ralph-loop

### 3. If Time Remaining
If `ELAPSED_MINUTES < {DURATION}`:
- Use the **Task tool** to invoke bc-manager:
  - **description**: "Run bc-manager iteration"
  - **prompt**: "Run bc-manager workflow. --bot {BOT_NAME} --opponent {OPPONENT} --iterations {ITERATIONS}"
  - **subagent_type**: "bc-manager"
- Report cycle results
- Let the loop continue to next iteration

## Status Reporting

After each bc-manager cycle, report:

```
═══════════════════════════════════════════════════════════════
BC-ITERATOR-WRAPPER STATUS
═══════════════════════════════════════════════════════════════
Elapsed Time: {ELAPSED_MINUTES} / {DURATION} minutes
Cycles Completed: {N}
Bot: {BOT_NAME}
Opponent: {OPPONENT}

Last Cycle Summary:
- [Summary from bc-manager results]

Time Remaining: ~{REMAINING} minutes
═══════════════════════════════════════════════════════════════
```

## Final Summary

When outputting the completion promise, also provide:

```
═══════════════════════════════════════════════════════════════
TRAINING SESSION COMPLETE
═══════════════════════════════════════════════════════════════
Total Duration: {ELAPSED_MINUTES} minutes
Total Cycles: {N}
Bot: {BOT_NAME}

Final Battle Log Summary:
[Read and summarize src/{BOT_NAME}/battle-log.md]

<promise>Training session complete</promise>
═══════════════════════════════════════════════════════════════
```

## Error Handling

- If bc-manager fails, log the error and continue to next iteration
- If start time file is missing, recreate it with current time
- If ralph-loop terminates unexpectedly, the session ends gracefully

## Key Principles

1. **Time-boxed training** - Runs for a set duration, not indefinitely
2. **Delegates to bc-manager** - All actual bot improvement is done by bc-manager
3. **Continuous iteration** - Maximizes training cycles within the time limit
4. **Clear progress tracking** - Reports elapsed time and cycles completed
5. **Clean termination** - Uses completion promise for graceful shutdown
