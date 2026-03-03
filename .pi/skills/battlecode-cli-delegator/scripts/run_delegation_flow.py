#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from token_usage_utils import aggregate_token_metrics, extract_token_usage_from_text
from opponent_utils import detect_opponents, prepare_copy_bot, project_root_from_script

MARKER = "FINAL_STATUS: SUCCESS"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run delegated Battlecode loop with independent Gradle verification and token accounting"
    )
    parser.add_argument("agent", help="claude | opencode | codex | pi")
    parser.add_argument("src_folder", help="target folder under src/")
    parser.add_argument("--max-attempts", type=int, default=10, help="max loop attempts (default: 10)")
    parser.add_argument("--poll-seconds", type=int, default=5, help="status poll interval while delegate runs")
    parser.add_argument("--max-delegate-seconds", type=int, default=0, help="delegate timeout per attempt; 0 = unlimited")
    parser.add_argument("--tail-lines", type=int, default=2, help="tail lines in progress output")
    parser.add_argument(
        "--verify-command",
        help=(
            "override verification command. Default runs runWithSummary against copy_bot and all "
            "detected src/<src_folder>_champion_<N> opponents."
        ),
    )
    parser.add_argument(
        "--verify-timeout-seconds",
        type=int,
        default=0,
        help="verification command timeout; 0 = unlimited",
    )
    parser.add_argument(
        "--extra-feedback-file",
        help="optional initial feedback file to seed the first attempt",
    )
    parser.add_argument(
        "--skip-copy-bot-setup",
        action="store_true",
        help="skip src/copy_bot preparation (for outer orchestrators that already prepared it)",
    )
    return parser.parse_args()


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def tail_lines(path: Path, num_lines: int) -> list[str]:
    text = read_text(path)
    lines = text.splitlines()
    return lines[-num_lines:]


def inline_tail(path: Path, num_lines: int) -> str:
    lines = tail_lines(path, num_lines)
    if not lines:
        return "<no output yet>"
    return " ".join(" ".join(lines).split())


def build_default_verify_commands(src_folder: str, opponents: list[str]) -> list[str]:
    return [
        f"./gradlew runWithSummary -PteamA={src_folder} -PteamB={opp} -Pmaps=Clusters"
        for opp in opponents
    ]


def combine_verify_commands(commands: list[str]) -> str:
    if not commands:
        raise ValueError("at least one verification command is required")

    if len(commands) == 1:
        return commands[0]

    lines = ["set -e"]
    total = len(commands)
    for idx, cmd in enumerate(commands, start=1):
        lines.append(f"echo '[verify] {idx}/{total}: {cmd}'")
        lines.append(cmd)
    return "\n".join(lines)


def prepare_delegate_command(script_dir: Path, agent: str, src_folder: str, extra_file: Optional[Path]) -> str:
    cmd = ["python3", str(script_dir / "build_delegation_command.py"), agent, src_folder]
    if extra_file:
        cmd.extend(["--extra-file", str(extra_file)])
    out = subprocess.check_output(cmd, text=True).strip()
    if not out:
        raise RuntimeError("build_delegation_command.py returned an empty command")
    return out


def run_delegate_attempt(
    *,
    script_dir: Path,
    cycle_dir: Path,
    agent: str,
    src_folder: str,
    attempt: int,
    poll_seconds: int,
    max_delegate_seconds: int,
    tail_num_lines: int,
    extra_feedback_file: Optional[Path],
) -> dict:
    delegate_log = cycle_dir / f"attempt_{attempt:02d}_delegate.log"
    token_json = cycle_dir / f"attempt_{attempt:02d}_tokens.json"

    delegate_cmd = prepare_delegate_command(script_dir, agent, src_folder, extra_feedback_file)

    started = time.time()
    started_iso = now_iso()

    with delegate_log.open("w", encoding="utf-8") as log_fh:
        proc = subprocess.Popen(
            ["bash", "-lc", delegate_cmd],
            stdout=log_fh,
            stderr=subprocess.STDOUT,
        )

    marker_seen_while_running = False
    timed_out = False

    while proc.poll() is None:
        elapsed = int(time.time() - started)

        current_text = read_text(delegate_log)
        if MARKER in current_text:
            marker_seen_while_running = True
            print(f"[flow][attempt {attempt}] marker detected; waiting for process exit...", flush=True)
            break

        if max_delegate_seconds > 0 and elapsed >= max_delegate_seconds:
            print(f"[flow][attempt {attempt}] timeout at {elapsed}s; terminating delegate...", flush=True)
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=10)
            timed_out = True
            break

        print(
            f"[flow][attempt {attempt}] elapsed={elapsed}s marker=no tail={inline_tail(delegate_log, tail_num_lines)}",
            flush=True,
        )
        time.sleep(poll_seconds)

    exit_code = proc.wait()
    ended_iso = now_iso()

    log_text = read_text(delegate_log)
    marker_seen = MARKER in log_text
    metrics, evidence = extract_token_usage_from_text(log_text)

    token_payload = {
        "agent": agent,
        "attempt": attempt,
        "delegate_log": str(delegate_log),
        "metrics": metrics,
        "found_any": bool(metrics),
        "evidence": evidence,
    }
    token_json.write_text(json.dumps(token_payload, indent=2, sort_keys=True), encoding="utf-8")

    return {
        "attempt": attempt,
        "started_at": started_iso,
        "ended_at": ended_iso,
        "delegate_log": str(delegate_log),
        "delegate_exit_code": exit_code,
        "delegate_timed_out": timed_out,
        "marker_seen": marker_seen,
        "marker_seen_while_running": marker_seen_while_running,
        "token_file": str(token_json),
        "token_usage": metrics,
        "delegate_command": delegate_cmd,
    }


def run_verification(
    *,
    cycle_dir: Path,
    attempt: int,
    verify_command: str,
    timeout_seconds: int,
) -> dict:
    verify_log = cycle_dir / f"attempt_{attempt:02d}_verify.log"

    started_iso = now_iso()
    started = time.time()

    with verify_log.open("w", encoding="utf-8") as fh:
        try:
            proc = subprocess.run(
                ["bash", "-lc", verify_command],
                stdout=fh,
                stderr=subprocess.STDOUT,
                timeout=timeout_seconds if timeout_seconds > 0 else None,
                check=False,
            )
            exit_code = proc.returncode
            timed_out = False
        except subprocess.TimeoutExpired:
            exit_code = 124
            timed_out = True

    elapsed = int(time.time() - started)

    return {
        "verify_command": verify_command,
        "verify_log": str(verify_log),
        "verify_exit_code": exit_code,
        "verify_timed_out": timed_out,
        "verify_elapsed_seconds": elapsed,
        "verify_started_at": started_iso,
        "verify_ended_at": now_iso(),
        "verify_passed": exit_code == 0,
    }


def build_feedback_text(attempt_record: dict) -> str:
    parts: list[str] = []
    parts.append("Additional verification feedback from Pi. You must fix all issues below.")
    parts.append("")
    parts.append(f"Attempt: {attempt_record.get('attempt')}")
    parts.append(f"Delegate exit code: {attempt_record.get('delegate_exit_code')}")
    parts.append(f"Delegate timed out: {attempt_record.get('delegate_timed_out')}")
    parts.append(f"Completion marker seen: {attempt_record.get('marker_seen')}")
    parts.append(f"Verification attempted: {attempt_record.get('verification_attempted')}")
    parts.append(f"Verification exit code: {attempt_record.get('verify_exit_code')}")
    parts.append("")

    if not attempt_record.get("marker_seen"):
        parts.append("- You must print EXACTLY `FINAL_STATUS: SUCCESS` after successful runWithSummary.")
    if attempt_record.get("delegate_exit_code") != 0:
        parts.append("- Your delegated CLI run exited non-zero. Resolve the underlying errors.")
    if attempt_record.get("delegate_timed_out"):
        parts.append("- Previous attempt timed out. Prioritize quickly reaching a compile+run success state.")
    if attempt_record.get("verification_attempted") and attempt_record.get("verify_exit_code") != 0:
        parts.append("- Independent Pi verification failed. Fix the issues from the verification log tail below.")

    parts.append("")
    parts.append("Delegate log tail (last 120 lines):")
    parts.append("```text")
    parts.extend(tail_lines(Path(attempt_record["delegate_log"]), 120))
    parts.append("```")

    if attempt_record.get("verification_attempted"):
        parts.append("")
        parts.append("Verification log tail (last 120 lines):")
        parts.append("```text")
        parts.extend(tail_lines(Path(attempt_record["verify_log"]), 120))
        parts.append("```")

    return "\n".join(parts).rstrip() + "\n"


def write_cycle_report(cycle_summary: dict, report_path: Path) -> None:
    totals = cycle_summary.get("token_totals", {})
    attempts = cycle_summary.get("attempts", [])

    lines: list[str] = []
    lines.append("Battlecode Delegation Token Usage Report")
    lines.append(f"Cycle ID: {cycle_summary.get('cycle_id')}")
    lines.append(f"Agent: {cycle_summary.get('agent')}")
    lines.append(f"src_folder: {cycle_summary.get('src_folder')}")
    lines.append(f"opponents: {', '.join(cycle_summary.get('opponents', []))}")
    lines.append(f"Success: {cycle_summary.get('success')}")
    lines.append(f"Attempts: {cycle_summary.get('attempt_count')}")
    lines.append("")
    lines.append("Token totals across entire cycle:")
    if totals:
        for key in sorted(totals):
            lines.append(f"- {key}: {totals[key]}")
    else:
        lines.append("- (no token usage metrics found in logs)")

    lines.append("")
    lines.append("Per-attempt breakdown:")
    for rec in attempts:
        lines.append(
            f"- Attempt {rec['attempt']}: delegate_exit={rec['delegate_exit_code']} marker={rec['marker_seen']} verify_exit={rec.get('verify_exit_code')} success={rec['attempt_success']}"
        )
        if rec.get("token_usage"):
            for key in sorted(rec["token_usage"]):
                lines.append(f"    {key}: {rec['token_usage'][key]}")
        else:
            lines.append("    (no token usage metrics found)")

    report_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()

    if args.max_attempts < 1:
        print("--max-attempts must be >= 1", file=sys.stderr)
        sys.exit(1)
    if args.poll_seconds < 1:
        print("--poll-seconds must be >= 1", file=sys.stderr)
        sys.exit(1)
    if args.max_delegate_seconds < 0:
        print("--max-delegate-seconds must be >= 0", file=sys.stderr)
        sys.exit(1)
    if args.tail_lines < 1:
        print("--tail-lines must be >= 1", file=sys.stderr)
        sys.exit(1)
    if args.verify_timeout_seconds < 0:
        print("--verify-timeout-seconds must be >= 0", file=sys.stderr)
        sys.exit(1)

    if args.agent not in {"claude", "opencode", "codex", "pi"}:
        print("agent must be one of: claude, opencode, codex, pi", file=sys.stderr)
        sys.exit(1)

    project_root = project_root_from_script(Path(__file__))

    copy_bot_meta: dict = {"skipped": True}
    if not args.skip_copy_bot_setup:
        try:
            copy_bot_meta = prepare_copy_bot(project_root, args.src_folder)
        except Exception as exc:  # noqa: BLE001
            print(f"copy_bot setup failed: {type(exc).__name__}: {exc}", file=sys.stderr)
            sys.exit(1)

    opponents = detect_opponents(project_root, args.src_folder)
    verify_commands_default = build_default_verify_commands(args.src_folder, opponents)

    verify_command = args.verify_command or combine_verify_commands(verify_commands_default)

    runtime_dir = SCRIPT_DIR.parent / "runtime"
    runtime_dir.mkdir(parents=True, exist_ok=True)

    cycle_id = f"{args.agent}_{args.src_folder}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    cycle_dir = runtime_dir / f"cycle_{cycle_id}"
    cycle_dir.mkdir(parents=True, exist_ok=True)

    summary_file = cycle_dir / "cycle_summary.json"
    report_file = cycle_dir / "token_report.txt"

    ledger_file = runtime_dir / f"token_cycles_{args.agent}_{args.src_folder}.jsonl"

    print(f"[flow] cycle id: {cycle_id}")
    print(f"[flow] runtime dir: {cycle_dir}")
    if args.skip_copy_bot_setup:
        print("[flow] copy_bot setup: skipped by flag")
    else:
        print(
            "[flow] copy_bot setup: "
            f"src/{copy_bot_meta.get('source_folder')} -> src/{copy_bot_meta.get('target_folder')} "
            f"(rewritten_java_files={copy_bot_meta.get('java_files_rewritten')})"
        )
    print(f"[flow] opponents: {', '.join(opponents)}")
    print(f"[flow] verify command: {verify_command}")

    initial_feedback: Optional[Path] = None
    if args.extra_feedback_file:
        initial_feedback = Path(args.extra_feedback_file).resolve()
        if not initial_feedback.exists():
            print(f"extra feedback file not found: {initial_feedback}", file=sys.stderr)
            sys.exit(1)

    attempts: list[dict] = []
    success = False
    next_feedback_file = initial_feedback

    for attempt in range(1, args.max_attempts + 1):
        print(f"[flow] ===== attempt {attempt}/{args.max_attempts} =====")

        try:
            rec = run_delegate_attempt(
                script_dir=SCRIPT_DIR,
                cycle_dir=cycle_dir,
                agent=args.agent,
                src_folder=args.src_folder,
                attempt=attempt,
                poll_seconds=args.poll_seconds,
                max_delegate_seconds=args.max_delegate_seconds,
                tail_num_lines=args.tail_lines,
                extra_feedback_file=next_feedback_file,
            )
        except subprocess.CalledProcessError as exc:
            error_text = (
                f"Failed to prepare delegate command on attempt {attempt}.\n"
                f"Return code: {exc.returncode}\n"
                f"Output:\n{exc.output}\n"
            )
            err_file = cycle_dir / f"attempt_{attempt:02d}_prepare_error.txt"
            err_file.write_text(error_text, encoding="utf-8")
            rec = {
                "attempt": attempt,
                "started_at": now_iso(),
                "ended_at": now_iso(),
                "delegate_log": "",
                "delegate_exit_code": exc.returncode,
                "delegate_timed_out": False,
                "marker_seen": False,
                "marker_seen_while_running": False,
                "token_file": "",
                "token_usage": {},
                "delegate_command": "",
                "prepare_error_file": str(err_file),
            }

        should_verify = rec.get("delegate_exit_code") == 0 and rec.get("marker_seen")
        rec["verification_attempted"] = bool(should_verify)

        if should_verify:
            vrec = run_verification(
                cycle_dir=cycle_dir,
                attempt=attempt,
                verify_command=verify_command,
                timeout_seconds=args.verify_timeout_seconds,
            )
            rec.update(vrec)
        else:
            rec.update(
                {
                    "verify_command": verify_command,
                    "verify_log": "",
                    "verify_exit_code": None,
                    "verify_timed_out": False,
                    "verify_elapsed_seconds": 0,
                    "verify_started_at": None,
                    "verify_ended_at": None,
                    "verify_passed": False,
                }
            )

        rec["attempt_success"] = (
            rec.get("delegate_exit_code") == 0
            and rec.get("marker_seen") is True
            and rec.get("verify_passed") is True
        )

        attempts.append(rec)

        print(
            f"[flow] attempt {attempt} result: delegate_exit={rec.get('delegate_exit_code')} marker={rec.get('marker_seen')} verify_exit={rec.get('verify_exit_code')} success={rec.get('attempt_success')}",
            flush=True,
        )

        if rec["attempt_success"]:
            success = True
            break

        feedback_text = build_feedback_text(rec)
        feedback_file = cycle_dir / f"attempt_{attempt:02d}_feedback.txt"
        feedback_file.write_text(feedback_text, encoding="utf-8")
        next_feedback_file = feedback_file
        rec["feedback_file"] = str(feedback_file)

    token_totals = aggregate_token_metrics(attempts)

    cycle_summary = {
        "cycle_id": cycle_id,
        "agent": args.agent,
        "src_folder": args.src_folder,
        "started_at": attempts[0]["started_at"] if attempts else now_iso(),
        "ended_at": now_iso(),
        "attempt_count": len(attempts),
        "max_attempts": args.max_attempts,
        "success": success,
        "opponents": opponents,
        "copy_bot_setup_skipped": args.skip_copy_bot_setup,
        "copy_bot": copy_bot_meta,
        "verify_commands_default": verify_commands_default,
        "verify_command": verify_command,
        "token_totals": token_totals,
        "attempts": attempts,
        "summary_file": str(summary_file),
        "token_report_file": str(report_file),
    }

    summary_file.write_text(json.dumps(cycle_summary, indent=2, sort_keys=True), encoding="utf-8")
    write_cycle_report(cycle_summary, report_file)

    ledger_entry = {
        "cycle_id": cycle_id,
        "agent": args.agent,
        "src_folder": args.src_folder,
        "opponents": opponents,
        "success": success,
        "attempt_count": len(attempts),
        "token_totals": token_totals,
        "summary_file": str(summary_file),
        "token_report_file": str(report_file),
        "ended_at": cycle_summary["ended_at"],
    }
    with ledger_file.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(ledger_entry, sort_keys=True) + "\n")

    print(f"CYCLE_RESULT: {'SUCCESS' if success else 'FAILED'}")
    print(f"CYCLE_ID: {cycle_id}")
    print(f"CYCLE_DIR: {cycle_dir}")
    print(f"SUMMARY_FILE: {summary_file}")
    print(f"TOKEN_REPORT_FILE: {report_file}")
    if token_totals:
        for key in sorted(token_totals):
            print(f"TOTAL_{key.upper()}: {token_totals[key]}")
    else:
        print("TOTAL_TOKEN_USAGE_FOUND: no")

    if success:
        sys.exit(0)

    sys.exit(1)


if __name__ == "__main__":
    main()
