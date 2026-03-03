#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from opponent_utils import prepare_copy_bot

SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
PI_DIR = SKILL_DIR.parent.parent
PROJECT_ROOT = PI_DIR.parent

FLOW_SCRIPT = SCRIPT_DIR / "run_delegation_flow.py"
CREATE_VARIANTS_SCRIPT = PROJECT_ROOT / "scripts" / "create-16-variants.sh"
RUN_ALL_VARIANTS_SCRIPT = PROJECT_ROOT / "scripts" / "run-all-variants.sh"
RANK_VARIANTS_SCRIPT = PROJECT_ROOT / "scripts" / "rank-variants.sh"


@dataclass(frozen=True)
class VariantTask:
    variant_index: int
    src_folder: str
    agent: str
    feedback_file: Optional[Path]


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def utc_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run delegated coding for many Battlecode variants in parallel, then run matches and ranking/promotion."
        )
    )
    parser.add_argument("bot", help="base bot package folder under src/ (e.g. mybot)")
    parser.add_argument(
        "opponent",
        nargs="?",
        default="copy_bot",
        help="optional primary opponent; currently overridden to copy_bot by skill policy",
    )
    parser.add_argument("--map", default="Clusters", help="map for run-all-variants/rank-variants (default: Clusters)")
    parser.add_argument("--num-variants", type=int, default=16, help="number of variants (default: 16)")
    parser.add_argument(
        "--agents",
        default="claude,codex",
        help="comma-separated delegate agents to rotate across variants (claude|opencode|codex|pi)",
    )
    parser.add_argument("--parallel", type=int, default=2, help="max concurrent delegation flows (default: 2)")

    parser.add_argument("--skip-create-variants", action="store_true", help="do not call scripts/create-16-variants.sh")
    parser.add_argument(
        "--feedback-dir",
        help="optional directory with per-variant feedback files named v<N>.txt (e.g. v1.txt, v2.txt)",
    )

    parser.add_argument("--max-attempts", type=int, default=10, help="delegation flow max attempts per variant")
    parser.add_argument("--poll-seconds", type=int, default=5, help="delegation flow polling interval")
    parser.add_argument(
        "--max-delegate-seconds",
        type=int,
        default=0,
        help="delegation flow max seconds per delegate attempt; 0 = unlimited",
    )
    parser.add_argument("--tail-lines", type=int, default=2, help="delegation flow progress tail lines")
    parser.add_argument(
        "--verify-timeout-seconds",
        type=int,
        default=0,
        help="delegation flow verify timeout; 0 = unlimited",
    )

    parser.add_argument(
        "--continue-on-failure",
        action="store_true",
        help="continue to matches/ranking even when some delegation variants fail",
    )
    parser.add_argument(
        "--drop-failed-variants",
        action="store_true",
        help="delete src/<bot>_vN folders for failed delegation variants before compile/matches",
    )

    parser.add_argument("--skip-matches", action="store_true", help="skip scripts/run-all-variants.sh")
    parser.add_argument("--skip-ranking", action="store_true", help="skip scripts/rank-variants.sh")

    return parser.parse_args()


def run_command(cmd: list[str], *, log_file: Path, cwd: Path) -> tuple[int, int]:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    started = time.time()
    with log_file.open("w", encoding="utf-8") as fh:
        proc = subprocess.run(cmd, cwd=str(cwd), stdout=fh, stderr=subprocess.STDOUT, check=False)
    elapsed = int(time.time() - started)
    return proc.returncode, elapsed


def run_bash(command: str, *, log_file: Path, cwd: Path) -> tuple[int, int]:
    return run_command(["bash", "-lc", command], log_file=log_file, cwd=cwd)


def parse_flow_log(log_file: Path) -> dict:
    text = log_file.read_text(encoding="utf-8", errors="replace") if log_file.exists() else ""
    meta: dict[str, str] = {}
    keys = {
        "CYCLE_RESULT",
        "CYCLE_ID",
        "CYCLE_DIR",
        "SUMMARY_FILE",
        "TOKEN_REPORT_FILE",
    }
    for line in text.splitlines():
        for key in keys:
            prefix = f"{key}:"
            if line.startswith(prefix):
                meta[key] = line[len(prefix) :].strip()
    return meta


def detect_num_champions(bot: str) -> int:
    src_dir = PROJECT_ROOT / "src"
    count = 0
    while (src_dir / f"{bot}_champion_{count}").is_dir():
        count += 1
    return count


def load_variant_feedback(feedback_dir: Optional[Path], variant_index: int) -> Optional[Path]:
    if not feedback_dir:
        return None
    candidate = feedback_dir / f"v{variant_index}.txt"
    if candidate.exists():
        return candidate.resolve()
    return None


def build_tasks(args: argparse.Namespace, agents: list[str], feedback_dir: Optional[Path]) -> list[VariantTask]:
    tasks: list[VariantTask] = []
    for i in range(1, args.num_variants + 1):
        tasks.append(
            VariantTask(
                variant_index=i,
                src_folder=f"{args.bot}_v{i}",
                agent=agents[(i - 1) % len(agents)],
                feedback_file=load_variant_feedback(feedback_dir, i),
            )
        )
    return tasks


def run_variant_task(task: VariantTask, args: argparse.Namespace, iteration_dir: Path) -> dict:
    log_file = iteration_dir / "delegation_logs" / f"{task.src_folder}.log"
    cmd = [
        "python3",
        str(FLOW_SCRIPT),
        task.agent,
        task.src_folder,
        "--max-attempts",
        str(args.max_attempts),
        "--poll-seconds",
        str(args.poll_seconds),
        "--max-delegate-seconds",
        str(args.max_delegate_seconds),
        "--tail-lines",
        str(args.tail_lines),
        "--verify-timeout-seconds",
        str(args.verify_timeout_seconds),
        "--skip-copy-bot-setup",
    ]
    if task.feedback_file:
        cmd.extend(["--extra-feedback-file", str(task.feedback_file)])

    code, elapsed = run_command(cmd, log_file=log_file, cwd=PROJECT_ROOT)
    flow_meta = parse_flow_log(log_file)

    success = code == 0 and flow_meta.get("CYCLE_RESULT") == "SUCCESS"

    return {
        "variant": task.src_folder,
        "variant_index": task.variant_index,
        "agent": task.agent,
        "feedback_file": str(task.feedback_file) if task.feedback_file else None,
        "started_at": None,
        "ended_at": None,
        "elapsed_seconds": elapsed,
        "exit_code": code,
        "success": success,
        "flow": {
            "cycle_result": flow_meta.get("CYCLE_RESULT"),
            "cycle_id": flow_meta.get("CYCLE_ID"),
            "cycle_dir": flow_meta.get("CYCLE_DIR"),
            "summary_file": flow_meta.get("SUMMARY_FILE"),
            "token_report_file": flow_meta.get("TOKEN_REPORT_FILE"),
        },
        "log_file": str(log_file),
    }


def append_jsonl(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(payload, sort_keys=True) + "\n")


def main() -> None:
    args = parse_args()

    valid_agents = {"claude", "opencode", "codex", "pi"}
    agents = [a.strip().lower() for a in args.agents.split(",") if a.strip()]
    if not agents:
        print("--agents must specify at least one agent", file=sys.stderr)
        sys.exit(1)
    if any(a not in valid_agents for a in agents):
        bad = sorted({a for a in agents if a not in valid_agents})
        print(f"unsupported agent(s): {', '.join(bad)}", file=sys.stderr)
        print("supported agents: claude, opencode, codex, pi", file=sys.stderr)
        sys.exit(1)

    if args.num_variants < 1:
        print("--num-variants must be >= 1", file=sys.stderr)
        sys.exit(1)
    if args.parallel < 1:
        print("--parallel must be >= 1", file=sys.stderr)
        sys.exit(1)

    bot_dir = PROJECT_ROOT / "src" / args.bot
    if not bot_dir.is_dir():
        print(f"bot folder not found: {bot_dir}", file=sys.stderr)
        sys.exit(1)

    requested_opponent = args.opponent
    if args.opponent != "copy_bot":
        print(
            f"[parallel] overriding opponent '{requested_opponent}' -> 'copy_bot' "
            "(skill policy: evaluate against copy_bot + champions)"
        )
    args.opponent = "copy_bot"

    try:
        copy_bot_meta = prepare_copy_bot(PROJECT_ROOT, args.bot)
    except Exception as exc:  # noqa: BLE001
        print(f"copy_bot setup failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)

    if args.skip_ranking and not args.skip_matches:
        print("note: matches will run but ranking/promotion is skipped (--skip-ranking)")

    feedback_dir: Optional[Path] = None
    if args.feedback_dir:
        feedback_dir = Path(args.feedback_dir).resolve()
        if not feedback_dir.is_dir():
            print(f"feedback dir not found: {feedback_dir}", file=sys.stderr)
            sys.exit(1)

    runtime_dir = SKILL_DIR / "runtime"
    runtime_dir.mkdir(parents=True, exist_ok=True)

    iteration_id = f"{args.bot}_{utc_stamp()}"
    iteration_dir = runtime_dir / f"parallel_iteration_{iteration_id}"
    iteration_dir.mkdir(parents=True, exist_ok=True)

    summary_path = iteration_dir / "iteration_summary.json"
    delegation_results_path = iteration_dir / "delegation_results.json"

    print(f"[parallel] iteration id: {iteration_id}")
    print(f"[parallel] project root: {PROJECT_ROOT}")
    print(f"[parallel] runtime dir: {iteration_dir}")
    print(f"[parallel] bot={args.bot} opponent={args.opponent} map={args.map}")
    print(
        "[parallel] copy_bot setup: "
        f"src/{copy_bot_meta.get('source_folder')} -> src/{copy_bot_meta.get('target_folder')} "
        f"(rewritten_java_files={copy_bot_meta.get('java_files_rewritten')})"
    )
    print(f"[parallel] agents={','.join(agents)} parallel={args.parallel}")

    run_started = now_iso()

    create_variants_record = {
        "attempted": not args.skip_create_variants,
        "command": None,
        "exit_code": None,
        "elapsed_seconds": None,
        "log_file": None,
    }

    if not args.skip_create_variants:
        create_log = iteration_dir / "create_variants.log"
        create_cmd = f"{CREATE_VARIANTS_SCRIPT} {args.bot} {args.num_variants}"
        code, elapsed = run_bash(create_cmd, log_file=create_log, cwd=PROJECT_ROOT)
        create_variants_record.update(
            {
                "command": create_cmd,
                "exit_code": code,
                "elapsed_seconds": elapsed,
                "log_file": str(create_log),
            }
        )
        if code != 0:
            print(f"[parallel] create variants failed (exit={code})", file=sys.stderr)
            summary = {
                "iteration_id": iteration_id,
                "started_at": run_started,
                "ended_at": now_iso(),
                "bot": args.bot,
                "opponent": args.opponent,
                "requested_opponent": requested_opponent,
                "copy_bot": copy_bot_meta,
                "map": args.map,
                "status": "FAILED_CREATE_VARIANTS",
                "create_variants": create_variants_record,
            }
            summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")
            print(f"SUMMARY_FILE: {summary_path}")
            sys.exit(1)

    tasks = build_tasks(args, agents, feedback_dir)
    print(f"[parallel] dispatching {len(tasks)} variant delegation jobs...")

    delegation_results: list[dict] = []

    with ThreadPoolExecutor(max_workers=args.parallel) as executor:
        future_to_task = {
            executor.submit(run_variant_task, task, args, iteration_dir): task
            for task in tasks
        }

        completed = 0
        for future in as_completed(future_to_task):
            task = future_to_task[future]
            try:
                rec = future.result()
            except Exception as exc:  # noqa: BLE001
                rec = {
                    "variant": task.src_folder,
                    "variant_index": task.variant_index,
                    "agent": task.agent,
                    "feedback_file": str(task.feedback_file) if task.feedback_file else None,
                    "elapsed_seconds": 0,
                    "exit_code": 1,
                    "success": False,
                    "flow": {
                        "cycle_result": None,
                        "cycle_id": None,
                        "cycle_dir": None,
                        "summary_file": None,
                        "token_report_file": None,
                    },
                    "log_file": None,
                    "error": f"{type(exc).__name__}: {exc}",
                }

            delegation_results.append(rec)
            completed += 1
            print(
                f"[parallel] [{completed}/{len(tasks)}] {rec['variant']} via {rec['agent']} -> "
                f"{'SUCCESS' if rec['success'] else 'FAILED'} (exit={rec['exit_code']})"
            )

    delegation_results.sort(key=lambda r: r.get("variant_index", 0))
    delegation_results_path.write_text(json.dumps(delegation_results, indent=2, sort_keys=True), encoding="utf-8")

    failed = [r for r in delegation_results if not r.get("success")]
    succeeded = [r for r in delegation_results if r.get("success")]

    dropped_variants: list[str] = []
    if args.drop_failed_variants and failed:
        for rec in failed:
            variant_dir = PROJECT_ROOT / "src" / rec["variant"]
            if variant_dir.is_dir():
                shutil.rmtree(variant_dir)
                dropped_variants.append(rec["variant"])

    compile_log = iteration_dir / "compile_verify.log"
    compile_cmd = "./gradlew compileJava"
    compile_exit, compile_elapsed = run_bash(compile_cmd, log_file=compile_log, cwd=PROJECT_ROOT)
    compile_ok = compile_exit == 0

    run_matches_record = {
        "attempted": False,
        "command": None,
        "exit_code": None,
        "elapsed_seconds": None,
        "log_file": None,
    }

    ranking_record = {
        "attempted": False,
        "command": None,
        "exit_code": None,
        "elapsed_seconds": None,
        "log_file": None,
        "results_file": None,
        "winner": None,
        "winner_score": None,
        "goal_met": None,
        "should_promote": None,
    }

    num_champions = detect_num_champions(args.bot)

    delegation_ok_for_pipeline = (not failed) or args.continue_on_failure
    pipeline_ok = delegation_ok_for_pipeline and compile_ok

    if not args.skip_matches and pipeline_ok:
        matches_log = iteration_dir / "run_all_variants.log"
        run_matches_record["attempted"] = True
        run_matches_cmd = f"{RUN_ALL_VARIANTS_SCRIPT} {args.bot} {args.opponent} {args.map} {num_champions}"
        run_matches_record["command"] = run_matches_cmd
        exit_code, elapsed = run_bash(run_matches_cmd, log_file=matches_log, cwd=PROJECT_ROOT)
        run_matches_record["exit_code"] = exit_code
        run_matches_record["elapsed_seconds"] = elapsed
        run_matches_record["log_file"] = str(matches_log)

    matches_ok = (
        (not run_matches_record["attempted"]) or (run_matches_record.get("exit_code") == 0)
    )

    if not args.skip_ranking and not args.skip_matches and pipeline_ok and matches_ok:
        rank_log = iteration_dir / "rank_variants.log"
        ranking_record["attempted"] = True
        rank_cmd = f"{RANK_VARIANTS_SCRIPT} {args.bot} {args.opponent} {args.map} {num_champions}"
        ranking_record["command"] = rank_cmd
        exit_code, elapsed = run_bash(rank_cmd, log_file=rank_log, cwd=PROJECT_ROOT)
        ranking_record["exit_code"] = exit_code
        ranking_record["elapsed_seconds"] = elapsed
        ranking_record["log_file"] = str(rank_log)

        results_file = PROJECT_ROOT / "src" / args.bot / ".state" / "variant-results.json"
        if results_file.exists():
            ranking_record["results_file"] = str(results_file)
            try:
                payload = json.loads(results_file.read_text(encoding="utf-8", errors="replace"))
                ranking_record["winner"] = payload.get("winner")
                ranking_record["winner_score"] = payload.get("winner_score")
                ranking_record["goal_met"] = payload.get("goal_met")
                ranking_record["should_promote"] = payload.get("should_promote")
            except Exception:  # noqa: BLE001
                pass

    status = "SUCCESS"
    if not delegation_ok_for_pipeline:
        status = "FAILED_DELEGATION"
    elif not compile_ok:
        status = "FAILED_COMPILE"
    elif run_matches_record["attempted"] and run_matches_record.get("exit_code") != 0:
        status = "FAILED_MATCHES"
    elif ranking_record["attempted"] and ranking_record.get("exit_code") != 0:
        status = "FAILED_RANKING"

    summary = {
        "iteration_id": iteration_id,
        "started_at": run_started,
        "ended_at": now_iso(),
        "bot": args.bot,
        "opponent": args.opponent,
        "requested_opponent": requested_opponent,
        "copy_bot": copy_bot_meta,
        "map": args.map,
        "num_variants": args.num_variants,
        "agents": agents,
        "parallel": args.parallel,
        "num_champions": num_champions,
        "status": status,
        "create_variants": create_variants_record,
        "delegation": {
            "result_file": str(delegation_results_path),
            "succeeded": len(succeeded),
            "failed": len(failed),
            "failed_variants": [r["variant"] for r in failed],
            "dropped_failed_variants": dropped_variants,
        },
        "compile_verify": {
            "command": compile_cmd,
            "exit_code": compile_exit,
            "elapsed_seconds": compile_elapsed,
            "log_file": str(compile_log),
            "passed": compile_ok,
        },
        "matches": run_matches_record,
        "ranking": ranking_record,
    }

    summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")

    state_history = PROJECT_ROOT / "src" / args.bot / ".state" / "delegated-variant-iterations.jsonl"
    append_jsonl(state_history, summary)

    print(f"SUMMARY_FILE: {summary_path}")
    print(f"DELEGATION_RESULTS_FILE: {delegation_results_path}")
    print(f"STATE_HISTORY_FILE: {state_history}")
    print(f"FINAL_STATUS: {status}")

    if status != "SUCCESS":
        sys.exit(1)


if __name__ == "__main__":
    main()
