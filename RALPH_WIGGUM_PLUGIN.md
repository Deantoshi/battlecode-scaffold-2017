# Ralph Wiggum Plugin for OpenCode

An iterative, self-referential development loop plugin that continuously re-feeds prompts until completion criteria are met. Inspired by the Claude Code Ralph Wiggum plugin.

## Overview

The Ralph Wiggum plugin enables autonomous, persistent iteration within OpenCode sessions. When active, the plugin automatically re-submits your prompt after each response, allowing the AI to see its previous work and iteratively refine output until the task is complete.

## Installation

The plugin is installed globally at:
```
~/.config/opencode/plugin/ralph-wiggum.ts
```

Commands are available at:
```
~/.config/opencode/command/ralph-loop.md
~/.config/opencode/command/cancel-ralph.md
~/.config/opencode/command/ralph-status.md
```

## Usage

### Starting a Loop

Use the `/ralph-loop` command or ask the AI to use the `ralph_loop` tool:

```
/ralph-loop "Fix all failing tests"
```

With options:
```
/ralph-loop "Implement feature X" --max-iterations 5
/ralph-loop "Build the app" --completion-promise "All tests pass"
```

### Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `prompt` | The task to iterate on | Required |
| `max_iterations` | Maximum loop iterations (0 = unlimited) | 0 |
| `completion_promise` | Text that signals task completion | None |

### Checking Status

```
/ralph-status
```

Output example:
```
Ralph loop active:
- Current iteration: 3
- Max iterations: 5
- Elapsed time: 45s
- Completion promise: "All tests pass"
- Prompt: "Fix all failing tests..."
```

### Canceling a Loop

```
/cancel-ralph
```

## Completion Promise

When you set a completion promise, the AI must output the exact text wrapped in `<promise>` tags to end the loop:

```
<promise>All tests pass</promise>
```

The loop only ends when the promise text matches exactly. This prevents premature exit - the AI should only declare completion when the task is truly done.

## How It Works

1. **Start**: You invoke `/ralph-loop` with a prompt
2. **Execute**: The AI works on the task
3. **Check**: When the AI finishes (session goes idle), the plugin:
   - Checks if max iterations reached
   - Checks if completion promise was satisfied
   - If neither, increments iteration and re-submits the prompt
4. **Repeat**: The AI sees its previous work (in files, git history) and continues
5. **Complete**: Loop ends when criteria met or manually cancelled

## State File

Loop state is stored in your project directory:
```
.opencode/.opencode-ralph-state.json
```

This file is automatically cleaned up when the loop completes or is cancelled.

## Best Practices

### Write Clear Prompts
Include specific completion criteria:
```
/ralph-loop "Fix all TypeScript errors. Run 'npm run build' to verify." --max-iterations 10
```

### Use Test-Driven Loops
Let tests determine completion:
```
/ralph-loop "Make all tests pass. Run 'npm test' after each change." --completion-promise "All tests passing"
```

### Set Safety Limits
Always use `--max-iterations` for open-ended tasks:
```
/ralph-loop "Optimize performance" --max-iterations 5
```

### Incremental Goals
Break large tasks into phases:
```
/ralph-loop "Phase 1: Add user model. Phase 2: Add API endpoints. Phase 3: Add tests." --max-iterations 10
```

## Example Workflows

### Bug Fixing
```
/ralph-loop "Fix the authentication bug. The login should redirect to /dashboard after success. Test by running 'npm test auth'." --completion-promise "Auth tests passing"
```

### Code Refactoring
```
/ralph-loop "Refactor the UserService class to use dependency injection. Ensure all existing tests still pass." --max-iterations 5
```

### Feature Implementation
```
/ralph-loop "Implement dark mode toggle. Requirements: 1) Add toggle to settings 2) Persist preference 3) Apply theme on load. Run 'npm test' to verify." --max-iterations 8 --completion-promise "Dark mode complete"
```

## Troubleshooting

### Loop Not Starting
- Check if OpenCode is running with a valid model
- Verify the plugin is loaded: tools should include `ralph_loop`

### Loop Not Stopping
- The completion promise must match exactly (case-sensitive)
- Check `/ralph-status` to see current state
- Use `/cancel-ralph` to force stop

### State File Issues
If the loop behaves unexpectedly, manually remove the state file:
```bash
rm .opencode/.opencode-ralph-state.json
```

## Differences from Claude Code Version

| Feature | Claude Code | OpenCode |
|---------|-------------|----------|
| Hook mechanism | Stop hook (bash) | `session.idle` event |
| State file | `.claude/ralph-loop.local.md` | `.opencode/.opencode-ralph-state.json` |
| Tools | Bash commands | Native TypeScript tools |
| Notifications | Console output | Toast notifications |

