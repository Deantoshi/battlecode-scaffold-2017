---
description: Start a Ralph Wiggum loop for iterative task completion
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

Use the `ralph_loop` tool to start an iterative loop with the following parameters:
- prompt: the Arguments section (raw string)
- max_iterations: The user may specify --max-iterations N in their arguments
- completion_promise: The user may specify --completion-promise "text" in their arguments

Parse the Arguments section to extract these values. If no max_iterations is specified, use 0 (unlimited).
If no completion_promise is specified, omit it.

Example usage:
- `/ralph-loop "Fix all failing tests"` - runs until manually cancelled
- `/ralph-loop "Implement feature X" --max-iterations 5` - runs up to 5 iterations
- `/ralph-loop "Build the app" --completion-promise "All tests pass"` - runs until promise is satisfied

After starting the loop, begin working on the task immediately. You are now in iteration 1.
