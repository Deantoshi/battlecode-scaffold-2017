# Session Stats Tool

Quick session-by-session data viewer for OpenCode.

## Usage

From any project directory with a `.opencode/` folder:

```bash
# Option 1: Run directly
bun run ~/.opencode/scripts/session-stats.ts <session-id>

# Option 2: From this .opencode/ directory
cd .opencode
bun run session-stats <session-id>
```

## Finding Session IDs

```bash
opencode session list
```

## What It Shows

- **Session Overview**: ID, title, message count, reasoning levels used (LOW/MEDIUM/HIGH), timestamps
- **Cost & Tokens**: Total cost with breakdown:
  - Input tokens
  - Output tokens
  - Reasoning tokens (tracked separately!)
  - Cache read/write tokens
- **Model Usage**: Per-model breakdown showing:
  - Reasoning level (for extended thinking models)
  - Messages sent to each model
  - Token counts by type (including reasoning tokens)
  - Cost per model
- **Tool Usage**: Bar chart of tool usage frequency

## Example Output

```
┌────────────────────────────────────────────────────────┐
│                   SESSION OVERVIEW                     │
├────────────────────────────────────────────────────────┤
│Session ID               ses_3d9714f3bffeJu8y9A1H6jLRvk │
│Title               variant-loop:20260203-212818-122... │
│Messages                                              5 │
│Reasoning Levels                                    LOW │
│Created                            2/3/2026, 9:50:11 PM │
│Updated                            2/3/2026, 9:51:51 PM │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│                    COST & TOKENS                       │
├────────────────────────────────────────────────────────┤
│Total Cost                                      $0.0000 │
│Total Tokens                                      92.1K │
│Input                                             46.1K │
│Output                                             7.8K │
│Reasoning                                             0 │
│Cache Read                                        38.2K │
│Cache Write                                           0 │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│                      MODEL USAGE                       │
├────────────────────────────────────────────────────────┤
│ google/antigravity-claude-opus-4-5-thinking            │
│  Reasoning Level                                   LOW │
│  Messages                                            4 │
│  Input Tokens                                    46.1K │
│  Output Tokens                                    7.8K │
│  Reasoning Tokens                                    0 │
│  Cache Read                                      38.2K │
│  Cost                                          $0.0000 │
└────────────────────────────────────────────────────────┘
```

## Installation Tip

Add an alias to your shell config (~/.bashrc or ~/.zshrc):

```bash
alias session-stats='bun run ~/.opencode/scripts/session-stats.ts'
```

Then from anywhere:
```bash
session-stats <session-id>
```

## How It Works

The script reads session data directly from `~/.local/share/opencode/storage/` and doesn't require any modifications to the OpenCode codebase. It works across all your projects.
