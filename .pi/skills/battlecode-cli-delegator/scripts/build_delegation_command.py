#!/usr/bin/env python3
import argparse
import re
import shlex
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a shell command that launches a delegated Battlecode coding CLI run (non-interactive mode)"
    )
    parser.add_argument("agent", help="claude | opencode | codex | pi")
    parser.add_argument("src_folder", help="Target folder under src/")
    parser.add_argument(
        "--extra-file",
        help="Optional file containing additional feedback/instructions to append to the prompt",
    )
    return parser.parse_args()


def validate_src_folder(src_folder: str) -> None:
    if not src_folder:
        print("src_folder cannot be empty", file=sys.stderr)
        sys.exit(1)

    if not re.fullmatch(r"[a-z][a-z0-9_]*", src_folder):
        print(
            "src_folder must match ^[a-z][a-z0-9_]*$ (lowercase Java package-style folder name)",
            file=sys.stderr,
        )
        sys.exit(1)


def read_extra(extra_file: str | None) -> str:
    if not extra_file:
        return ""

    path = Path(extra_file)
    if not path.exists():
        print(f"extra file does not exist: {extra_file}", file=sys.stderr)
        sys.exit(1)

    text = path.read_text(encoding="utf-8", errors="replace").strip()
    return text


def main() -> None:
    args = parse_args()
    agent = args.agent.strip().lower()
    src_folder = args.src_folder.strip()

    validate_src_folder(src_folder)

    agent_map = {
        "claude": "claude -p --dangerously-skip-permissions",
        "opencode": "opencode run",
        "codex": "codex exec --dangerously-bypass-approvals-and-sandbox",
        "pi": "pi -p --no-session",
    }

    if agent not in agent_map:
        print("agent must be one of: claude, opencode, codex, pi", file=sys.stderr)
        sys.exit(1)

    agent_cmd = agent_map[agent]

    prompt = f"""You are an autonomous Battlecode 2017 coding agent working inside this repository.

RUNTIME MODE
- You are running in NON-INTERACTIVE CLI mode.
- Do not ask questions.
- Do not wait for user input.
- Make reasonable assumptions and complete the full task in this single run.

MANDATORY TASK
- Build the strongest bot you can in src/{src_folder}/.
- If src/{src_folder}/ does not exist, create it.
- Implement/update src/{src_folder}/RobotPlayer.java.

MANDATORY READING
1) Read HOW_TO_PLAY_BATTLE_CODE_2017.md first.
2) You may read src/examplefuncsplayer/ as reference.
3) You are NOT allowed to read any other src/ folders except:
   - src/{src_folder}/
   - src/examplefuncsplayer/

MANDATORY LOOP (DO NOT STOP EARLY)
- Keep iterating until this command succeeds without compile/runtime errors:
  ./gradlew runWithSummary -PteamA={src_folder} -PteamB=examplefuncsplayer -Pmaps=Shrine
- If the command fails, fix issues and rerun.
- Continue until success.

OUTPUT REQUIREMENTS
- When done, print exactly:
  FINAL_STATUS: SUCCESS
- Then print:
  - files changed
  - short rationale of strategy
  - final successful gradle output summary

Important: Do not end before FINAL_STATUS: SUCCESS is printed after a successful runWithSummary execution.
"""

    extra_text = read_extra(args.extra_file)
    if extra_text:
        prompt += f"""

ADDITIONAL VERIFICATION FEEDBACK FROM PI (MUST ADDRESS)
{extra_text}

You must fix these issues and rerun the required gradle command until successful, then print FINAL_STATUS: SUCCESS.
"""

    print(f"{agent_cmd} {shlex.quote(prompt)}")


if __name__ == "__main__":
    main()
