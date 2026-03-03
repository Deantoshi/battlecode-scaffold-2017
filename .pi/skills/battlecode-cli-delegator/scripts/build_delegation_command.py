#!/usr/bin/env python3
import argparse
import re
import shlex
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from opponent_utils import detect_opponents, project_root_from_script


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


def build_required_commands(src_folder: str, opponents: list[str]) -> list[str]:
    return [
        f"./gradlew runWithSummary -PteamA={src_folder} -PteamB={opp} -Pmaps=Clusters"
        for opp in opponents
    ]


def format_numbered_lines(lines: list[str]) -> str:
    return "\n".join(f"  {idx}) {line}" for idx, line in enumerate(lines, start=1))


def read_howto(project_root: Path) -> str:
    howto_path = project_root / "HOW_TO_PLAY_BATTLE_CODE_2017.md"
    if not howto_path.exists():
        print(f"HOW_TO_PLAY file not found: {howto_path}", file=sys.stderr)
        sys.exit(1)
    return howto_path.read_text(encoding="utf-8", errors="replace").strip()


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

    project_root = project_root_from_script(Path(__file__))

    # Ensure target folder exists (previously done by prepare_delegation_command.sh)
    (project_root / "src" / src_folder).mkdir(parents=True, exist_ok=True)

    opponents = detect_opponents(project_root, src_folder)
    required_commands = build_required_commands(src_folder, opponents)
    howto_content = read_howto(project_root)

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

REFERENCE MATERIAL
- You may read src/examplefuncsplayer/ as reference.
- You are NOT allowed to read any other src/ folders except:
   - src/{src_folder}/
   - src/examplefuncsplayer/

## HOW TO PLAY BATTLECODE 2017 (pre-loaded, do NOT re-read this file)
{howto_content}

MATCH OPPONENTS FOR THIS RUN (ALREADY PREPARED BY ORCHESTRATOR)
- Opponents to validate against:
{format_numbered_lines(opponents)}
- Do not edit opponent folders (including src/copy_bot or champion folders).

MANDATORY LOOP (DO NOT STOP EARLY)
- Keep iterating until EVERY command below succeeds without compile/runtime errors:
{format_numbered_lines(required_commands)}
- If any command fails, fix issues and rerun all required commands.
- Continue until all required commands are successful.

OUTPUT REQUIREMENTS
- When done, print exactly:
  FINAL_STATUS: SUCCESS
- Then print:
  - files changed
  - short rationale of strategy
  - final successful gradle output summary

Important: Do not end before FINAL_STATUS: SUCCESS is printed after all required runWithSummary commands succeed.
"""

    extra_text = read_extra(args.extra_file)
    if extra_text:
        prompt += f"""

ADDITIONAL VERIFICATION FEEDBACK FROM PI (MUST ADDRESS)
{extra_text}

You must fix these issues and rerun all required gradle commands until successful, then print FINAL_STATUS: SUCCESS.
"""

    print(f"{agent_cmd} {shlex.quote(prompt)}")


if __name__ == "__main__":
    main()
