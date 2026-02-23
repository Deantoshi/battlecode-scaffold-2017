---
name: battlecode-cli-delegator
description: Delegate Battlecode 2017 bot creation to claude/opencode/codex via interactive_shell, with strict src-folder read limits, mandatory compile-until-success loop, and Pi-side verification. Use when Pi must orchestrate but not code.
---

# Battlecode CLI Delegator

Use this skill when the user wants Pi to **drive another coding-agent CLI** (Claude, OpenCode, Codex) to build a Battlecode bot while Pi only orchestrates.

## Inputs

Expected args:

```text
<agent> <src_folder>
```

- `<agent>` must be one of: `claude`, `opencode`, `codex`
- `<src_folder>` is a folder name under `src/` (example: `claude_sonet_4_6_high`)

Example:

```text
/skill:battlecode-cli-delegator claude claude_sonet_4_6_high
```

Optional one-command local runner:

```bash
python3 .pi/skills/battlecode-cli-delegator/scripts/run_delegation_flow.py claude claude_sonet_4_6_high
```

## Hard Rules

1. **Pi must not make coding edits** in `src/` for this workflow.
2. Pi may orchestrate with tools (`read`, `bash`, `interactive_shell`) and may create the target directory.
3. Delegated coding CLI must be instructed to:
   - read `HOW_TO_PLAY_BATTLE_CODE_2017.md`
   - read/write only inside `src/<src_folder>/` and read from `src/examplefuncsplayer/`
   - not read any other `src/*` folders
   - keep iterating until compile/run has no errors
4. Pi must verify completion itself by running a gradle command after delegate claims success.

## Required Workflow

1. **Load interactive-shell guidance first** (mandatory):
   - `read .pi/skills/interactive-shell/SKILL.md`
2. Parse args (`agent`, `src_folder`). If missing/invalid, ask user.
3. Prepare workflow in one step (creates `src/<src_folder>` and prints delegated command):
   - `bash .pi/skills/battlecode-cli-delegator/scripts/prepare_delegation_command.sh <agent> <src_folder>`
4. Start delegated CLI in overlay (hands-free):
   - `interactive_shell({ command: "<output-from-script>", mode: "hands-free", reason: "Battlecode bot delegation" })`
5. Monitor session periodically (respect rate limit) until delegated agent prints:
   - `FINAL_STATUS: SUCCESS`
6. If delegate reports errors/stalls, send follow-up input in same session telling it to continue fixing and recompiling.
7. After success marker, Pi runs independent verification:
   - `./gradlew runWithSummary -PteamA=<src_folder> -PteamB=examplefuncsplayer -Pmaps=Shrine`
8. If verification fails, feed errors back to same delegate session and continue.
9. Kill delegate session only after verified success.

## Notes

- Use one-shot initial prompt (all instructions in first command).
- Keep the delegated session active until compile/run is clean.
- Pi reports summary and verification result, but Pi does not author bot code directly.
- Helper scripts:
  - `scripts/prepare_delegation_command.sh` → creates folder + prints command (preferred)
  - `scripts/build_delegation_command.py` → prints command only (supports `--extra-file`)
  - `scripts/run_delegation_flow.py` → single-command local runner (launch delegate, wait for `FINAL_STATUS: SUCCESS`, verify with gradle, retry with feedback). Logs under `runtime/`.
