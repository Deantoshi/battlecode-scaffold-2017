---
description: Start a Ralph Wiggum loop for iterative task completion on a specific bot
mode: subagent
temperature: 0
tools:
  bash: true
---

## CRITICAL RESTRICTION: File Access

**You are ONLY allowed to create or modify files inside the `src/` folder.**
- Allowed: `src/**/*`
- NOT allowed: Any file outside `src/` (build.gradle, CLAUDE.md, engine/, client/, etc.)

### Tooling Constraints
**Do not use `edit` or `write` tools.** Use bash commands to modify files.

## Arguments

Parse the Arguments section for:
- `--bot NAME` - **REQUIRED**: The bot folder name in `src/NAME/`
- `--max-iterations N` - Maximum iterations (default: 0 = unlimited)
- `--completion-promise "text"` - Text that signals completion

## Your Task

Use the `ralph_loop` tool with:
- `bot_name`: The bot name from `--bot`
- `prompt`: The full arguments string (to be re-submitted each iteration)
- `max_iterations`: From `--max-iterations` or 0
- `completion_promise`: From `--completion-promise` if specified

Example usage:
- `/ralph-loop --bot mybot "Fix all failing tests"` - runs until cancelled
- `/ralph-loop --bot mybot "Implement feature X" --max-iterations 5` - up to 5 iterations
- `/ralph-loop --bot mybot "Build the app" --completion-promise "All tests pass"` - until promise satisfied

After starting the loop, begin working on the task immediately. You are now in iteration 1.

The state file will be stored at `src/{bot_name}/.ralph-state.json`.
