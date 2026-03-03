---
name: battlecode-cli-delegator
description: Run Battlecode 2017 delegated bot-building from inside the Pi TUI using non-interactive CLI modes (claude -p / opencode run / codex exec / pi -p), enforce src-folder read limits by instruction, require compile-until-success, independently verify with Gradle, and log token usage across the full loop.
---

# Battlecode CLI Delegator

Use this skill when you want **Pi (in the TUI)** to orchestrate another coding CLI (Claude, OpenCode, Codex, or Pi CLI) to build a Battlecode bot while Pi remains the orchestrator.

This version is **CLI-only** for delegates (no `interactive_shell`, no delegate TUI sessions).

## Preferred User Flow (Pi TUI)

Start Pi in your terminal:

```bash
pi
```

Then in Pi, run:

```text
/skill:battlecode-cli-delegator <agent> <src_folder>
```

Example:

```text
/skill:battlecode-cli-delegator claude claude_sonet_4_6_high
```

Optional launcher (opens Pi TUI with the skill command as initial prompt):

```bash
bash .pi/skills/battlecode-cli-delegator/scripts/open_pi_tui_for_delegation.sh claude claude_sonet_4_6_high
```

## Inputs

Expected args:

```text
<agent> <src_folder>
```

- `<agent>` must be one of: `claude`, `opencode`, `codex`, `pi`
- `<src_folder>` is a folder name under `src/` (example: `claude_sonet_4_6_high`)

## Hard Rules

1. **Pi must not make coding edits** in `src/` for this workflow, except automated `src/copy_bot/` preparation.
2. Pi may orchestrate with tools (`read`, `bash`, `write`) and may create/update `src/copy_bot`, the target directory, plus runtime feedback/report files under `.pi/skills/battlecode-cli-delegator/runtime/`.
3. At the beginning of each delegation run, Pi must prepare `src/copy_bot`:
   - if `src/<src_folder>/` already has Java code, clone that into `src/copy_bot/`
   - otherwise clone `src/examplefuncsplayer/` into `src/copy_bot/`
   - rewrite package/import references so copied files use package `copy_bot`
4. Delegated coding CLI must be instructed to:
   - read `HOW_TO_PLAY_BATTLE_CODE_2017.md`
   - read/write only inside `src/<src_folder>/` and read from `src/examplefuncsplayer/`
   - not read any other `src/*` folders
   - keep iterating until compile/run has no errors against opponents: `copy_bot` + any `src/<src_folder>_champion_<N>` folders
5. Pi must verify completion itself by running Gradle after delegate claims success, against the same opponent set (`copy_bot` + champions).
6. Pi must retain token usage logs for the full cycle when available from provider output.

## Required Pi Workflow (Default)

1. Parse args (`agent`, `src_folder`). If missing/invalid, ask user.
2. Run the full orchestrated loop script (this is the default path):
   - `python3 .pi/skills/battlecode-cli-delegator/scripts/run_delegation_flow.py <agent> <src_folder>`
3. Wait for final cycle status in output:
   - `CYCLE_RESULT: SUCCESS` or `CYCLE_RESULT: FAILED`
4. Report artifacts to the user:
   - cycle directory
   - `cycle_summary.json`
   - `token_report.txt`

The loop script automatically prepares `src/copy_bot`, creates delegate commands, retries with feedback, verifies against `copy_bot` + champions, and writes cycle-wide token aggregation.

## Optional Utilities

Watched single-run delegate execution (useful for manual debugging):

```bash
POLL_SECONDS=5 MAX_SECONDS=1800 bash .pi/skills/battlecode-cli-delegator/scripts/run_delegation_with_watch.sh claude claude_sonet_4_6_high
```

- Detects completion marker: `FINAL_STATUS: SUCCESS`
- Streams periodic status using log tail snapshots
- Writes full logs to `.pi/skills/battlecode-cli-delegator/runtime/`
- Extracts token metrics (when present in provider output) into per-run JSON files

Parallel 16-variant orchestration (2 workers, then matches + ranking/promotion):

```bash
python3 .pi/skills/battlecode-cli-delegator/scripts/run_parallel_variant_iteration.py <bot> \
  --map MagicWood \
  --agents claude,codex \
  --parallel 2 \
  --num-variants 16 \
  --drop-failed-variants
```

(If an opponent positional arg is supplied, it is overridden to `copy_bot` by policy.)

What this does:
- prepares `src/copy_bot` from `src/<bot>/` (fallback: `src/examplefuncsplayer/`) with package/import rewrite to `copy_bot`
- optionally creates `src/<bot>_v1..v16`
- runs delegated `run_delegation_flow.py` jobs in parallel (default 2 at a time)
- verifies global compile once delegation finishes
- runs `scripts/run-all-variants.sh` against `copy_bot` + **all detected champions** (`src/<bot>_champion_0..N`)
- runs `scripts/rank-variants.sh` for scoring, promotion, and champion saving
- writes iteration artifacts into `.pi/skills/battlecode-cli-delegator/runtime/parallel_iteration_*`
- appends iteration summaries to `src/<bot>/.state/delegated-variant-iterations.jsonl`

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
  - `scripts/run_parallel_variant_iteration.py` → orchestrates 16-variant delegation with configurable parallelism, then runs matches + ranking/promotion with persisted iteration summaries
