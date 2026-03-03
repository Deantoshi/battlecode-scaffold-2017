---
name: battlecode-cli-delegator
description: Run Battlecode 2017 delegated bot-building from inside the Pi TUI. Supports single bot delegation and variant tournament mode (parallel delegation + matches + ranking/promotion). Uses non-interactive CLI modes (claude -p / opencode run / codex exec / pi -p).
---

# Battlecode CLI Delegator

Use this skill when you want **Pi (in the TUI)** to orchestrate another coding CLI (Claude, OpenCode, Codex, or Pi CLI) to build a Battlecode bot while Pi remains the orchestrator.

This version is **CLI-only** for delegates (no `interactive_shell`, no delegate TUI sessions).

## Preferred User Flow (Pi TUI)

Start Pi in your terminal:

```bash
pi
```

### Single bot delegation

```text
/skill:battlecode-cli-delegator <agent> <src_folder>
```

Example:

```text
/skill:battlecode-cli-delegator claude claude_sonet_4_6_high
```

### Variant tournament (parallel delegation + matches + ranking)

```text
/skill:battlecode-cli-delegator variants <bot> [options]
```

Examples:

```text
/skill:battlecode-cli-delegator variants my_bot
/skill:battlecode-cli-delegator variants my_bot --agents claude,codex --parallel 2 --num-variants 16
/skill:battlecode-cli-delegator variants my_bot --map Clusters --num-variants 10 --agents claude
```

Optional launcher (opens Pi TUI with the skill command as initial prompt):

```bash
bash .pi/skills/battlecode-cli-delegator/scripts/open_pi_tui_for_delegation.sh claude claude_sonet_4_6_high
```

## Inputs

### Single bot mode

```text
<agent> <src_folder>
```

- `<agent>` must be one of: `claude`, `opencode`, `codex`, `pi`
- `<src_folder>` is a folder name under `src/` (example: `claude_sonet_4_6_high`)

### Variant tournament mode

```text
variants <bot> [--agents AGENTS] [--parallel N] [--num-variants N] [--map MAP] [--drop-failed-variants] [--variant-flow FLOW] [--mutation-count N]
```

- First arg must be the literal word `variants`
- `<bot>` is the base bot folder under `src/` (must already exist with Java code, or `examplefuncsplayer` is used as fallback)
- `--agents` comma-separated delegate agents (default: `claude,codex`)
- `--parallel` max concurrent delegation workers (default: `2`)
- `--num-variants` number of variant bots to create and delegate (default: `16`)
- `--map` map for matches (default: `Clusters`)
- `--drop-failed-variants` delete failed variant folders before matches
- `--variant-flow` split policy for variants (default: `mutation-exploration`)
  - `mutation-exploration`: v1..vK are mutations, remaining are explorations
  - `none`: no automatic mutation/exploration guidance
- `--mutation-count` optional override for K (default: `ceil(num_variants/2)`) when `--variant-flow mutation-exploration`
- `--feedback-dir` still works and takes precedence over auto-generated split guidance files

## Hard Rules

1. **Pi must not make coding edits** in `src/` for this workflow, except automated `src/copy_bot/` preparation.
2. Pi may orchestrate with tools (`read`, `bash`, `write`) and may create/update `src/copy_bot`, the target directory, plus runtime feedback/report files under `.pi/skills/battlecode-cli-delegator/runtime/`.
3. At the beginning of each delegation run, Pi must prepare `src/copy_bot`:
   - if `src/<src_folder>/` already has Java code, clone that into `src/copy_bot/`
   - otherwise clone `src/examplefuncsplayer/` into `src/copy_bot/`
   - rewrite package/import references so copied files use package `copy_bot`
4. Delegated coding CLI must be instructed to:
   - `HOW_TO_PLAY_BATTLE_CODE_2017.md` is pre-loaded into the delegate prompt (no file read needed)
   - read/write only inside `src/<src_folder>/` and read from `src/examplefuncsplayer/`
   - not read any other `src/*` folders
   - keep iterating until compile/run has no errors against opponents: `copy_bot` + any `src/<src_folder>_champion_<N>` folders
5. Pi must verify completion itself by running Gradle after delegate claims success, against the same opponent set (`copy_bot` + champions).
6. Pi must retain token usage logs for the full cycle when available from provider output.

## Required Pi Workflow — Mode Detection

Pi must detect which mode the user requested based on the first argument:

- If the first arg is `variants` → run the **Variant Tournament Workflow**
- Otherwise → run the **Single Bot Workflow**

---

## Single Bot Workflow

1. Parse args (`agent`, `src_folder`). If missing/invalid, ask user.
2. Run the full orchestrated loop script:
   - `python3 .pi/skills/battlecode-cli-delegator/scripts/run_delegation_flow.py <agent> <src_folder>`
3. Wait for final cycle status in output:
   - `CYCLE_RESULT: SUCCESS` or `CYCLE_RESULT: FAILED`
4. Report artifacts to the user:
   - cycle directory
   - `cycle_summary.json`
   - `token_report.txt`

The loop script automatically prepares `src/copy_bot`, creates delegate commands, retries with feedback, verifies against `copy_bot` + champions, and writes cycle-wide token aggregation.

---

## Variant Tournament Workflow

This mode creates multiple variant bots in parallel, runs a round-robin match tournament, and promotes the winner as a new champion.

1. Parse args. The first arg is the literal `variants`. Second arg is `<bot>` (the base bot folder). Remaining args are optional flags. If `<bot>` is missing/invalid, ask user.
2. Build the command from user-provided args (all flags are optional with sensible defaults):
   ```
   python3 .pi/skills/battlecode-cli-delegator/scripts/run_parallel_variant_iteration.py <bot> \
     --map <MAP> \
     --agents <AGENTS> \
     --parallel <N> \
     --num-variants <N> \
     --variant-flow <FLOW> \
     --mutation-count <N> \
     --drop-failed-variants
   ```
   Defaults if not specified by user: `--map Clusters --agents claude,codex --parallel 2 --num-variants 16 --variant-flow mutation-exploration --drop-failed-variants`
3. Run the command and stream output. The script handles the full pipeline:
   - Prepares `src/copy_bot` from `src/<bot>/` (fallback: `src/examplefuncsplayer/`)
   - Creates `src/<bot>_v1..<bot>_vN` variant folders
   - Applies mutation/exploration split guidance by default (`v1..vK` mutation, rest exploration; default `K=ceil(N/2)`)
   - Delegates coding to LLM agents in parallel (each variant gets its own `run_delegation_flow.py` cycle)
   - Verifies global compile
   - Runs `scripts/run-all-variants.sh` — all variants play against `copy_bot` + all existing champions
   - Runs `scripts/rank-variants.sh` — scores, ranks, promotes winner as new champion
4. Wait for final status line: `FINAL_STATUS: SUCCESS` or `FINAL_STATUS: FAILED_*`
5. Read and report the iteration summary to the user:
   - `SUMMARY_FILE` path (printed by the script)
   - Key fields: `status`, `variant_flow.mode`, `variant_flow.mutation_count`, `variant_flow.exploration_count`, `variant_flow.unassigned_count`, `delegation.succeeded`, `delegation.failed`, `delegation.succeeded_mutations`, `delegation.succeeded_explorations`, `ranking.winner`, `ranking.winner_score`, `ranking.should_promote`
   - If ranking produced a winner, report which variant won and its score
   - If failures occurred, report which variants failed

---

## Optional Utilities

Watched single-run delegate execution (useful for manual debugging):

```bash
POLL_SECONDS=5 MAX_SECONDS=1800 bash .pi/skills/battlecode-cli-delegator/scripts/run_delegation_with_watch.sh claude claude_sonet_4_6_high
```

- Detects completion marker: `FINAL_STATUS: SUCCESS`
- Streams periodic status using log tail snapshots
- Writes full logs to `.pi/skills/battlecode-cli-delegator/runtime/`
- Extracts token metrics (when present in provider output) into per-run JSON files

## Notes

- Delegates are run via CLI commands only:
  - `claude -p ...`
  - `opencode run ...`
  - `codex exec ...`
  - `pi -p --no-session ...`
- Use one-shot initial prompt (all instructions in first command).
- Pi reports summary and verification result, but Pi does not author bot code directly.
- Helper scripts:
  - `scripts/open_pi_tui_for_delegation.sh` → convenience launcher into Pi TUI + skill command
  - `scripts/prepare_copy_bot.py` → prepares `src/copy_bot` from `src/<src_folder>` (or fallback) and rewrites package/imports to `copy_bot`
  - `scripts/build_delegation_command.py` → creates target folder, inlines HOW_TO_PLAY into prompt, and prints delegated command (supports `--extra-file`)
  - `scripts/run_delegation_with_watch.sh` → runs delegate + polls frequently for progress and `FINAL_STATUS: SUCCESS`; also extracts per-run token metrics to JSON
  - `scripts/extract_token_usage.py` → parse a delegate log and emit token usage metrics JSON
  - `scripts/run_delegation_flow.py` → full retry/verification loop with cycle-wide token accounting and reports
  - `scripts/run_parallel_variant_iteration.py` → orchestrates multi-variant delegation with configurable parallelism, built-in mutation/exploration split guidance, then runs matches + ranking/promotion with persisted iteration summaries
