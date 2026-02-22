#!/usr/bin/env python3
"""
Track Claude Code token usage per session.

This wrapper runs Claude Code in print JSON mode and stores per-turn usage,
including:
- input_tokens
- output_tokens
- cache_read_input_tokens (cached input)
- cache_creation_input_tokens
- reasoning_tokens (when exposed by Claude Code)
- model used
- reasoning level used (effort)

Usage examples:
  python3 scripts/claude_session_token_tracker.py new --name my-run
  python3 scripts/claude_session_token_tracker.py prompt --session <SESSION_ID> --prompt "Summarize this repo"
  python3 scripts/claude_session_token_tracker.py summary --session <SESSION_ID>
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

TRACKER_DIR = Path(".claude-token-tracker")


@dataclass
class TokenUsage:
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_input_tokens: int = 0
    cache_creation_input_tokens: int = 0
    reasoning_tokens: Optional[int] = None


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def as_int(value: Any) -> int:
    if isinstance(value, bool):
        return 0
    if isinstance(value, (int, float)):
        return int(value)
    return 0


def parse_reasoning_tokens(payload: Dict[str, Any]) -> Optional[int]:
    """Best-effort extraction. Claude Code may omit this today."""
    candidates = []

    usage = payload.get("usage") if isinstance(payload.get("usage"), dict) else {}
    model_usage = payload.get("modelUsage") if isinstance(payload.get("modelUsage"), dict) else {}

    def add_candidate(value: Any) -> None:
        if isinstance(value, bool):
            return
        if isinstance(value, (int, float)):
            candidates.append(int(value))

    # Common shapes
    add_candidate(usage.get("reasoning_tokens"))

    details = usage.get("output_tokens_details")
    if isinstance(details, dict):
        add_candidate(details.get("reasoning_tokens"))

    reasoning_obj = usage.get("reasoning")
    if isinstance(reasoning_obj, dict):
        add_candidate(reasoning_obj.get("tokens"))

    # Model-level fallback shapes
    for model_data in model_usage.values():
        if not isinstance(model_data, dict):
            continue
        add_candidate(model_data.get("reasoningTokens"))
        add_candidate(model_data.get("reasoning_tokens"))

        out_details = model_data.get("outputTokensDetails")
        if isinstance(out_details, dict):
            add_candidate(out_details.get("reasoningTokens"))
            add_candidate(out_details.get("reasoning_tokens"))

    # Iteration-level fallback (future-proofing)
    iterations = usage.get("iterations")
    if isinstance(iterations, list):
        for item in iterations:
            if not isinstance(item, dict):
                continue
            iter_usage = item.get("usage")
            if isinstance(iter_usage, dict):
                add_candidate(iter_usage.get("reasoning_tokens"))
                iter_details = iter_usage.get("output_tokens_details")
                if isinstance(iter_details, dict):
                    add_candidate(iter_details.get("reasoning_tokens"))

    if not candidates:
        return None

    return sum(candidates)


def extract_usage(payload: Dict[str, Any]) -> TokenUsage:
    usage = payload.get("usage") if isinstance(payload.get("usage"), dict) else {}

    return TokenUsage(
        input_tokens=as_int(usage.get("input_tokens")),
        output_tokens=as_int(usage.get("output_tokens")),
        cache_read_input_tokens=as_int(usage.get("cache_read_input_tokens")),
        cache_creation_input_tokens=as_int(usage.get("cache_creation_input_tokens")),
        reasoning_tokens=parse_reasoning_tokens(payload),
    )


def extract_model_used(payload: Dict[str, Any], requested_model: Optional[str]) -> str:
    model_usage = payload.get("modelUsage") if isinstance(payload.get("modelUsage"), dict) else {}
    models = [k for k in model_usage.keys() if isinstance(k, str) and k]
    if models:
        return ", ".join(sorted(models))

    model = payload.get("model")
    if isinstance(model, str) and model:
        return model

    if requested_model:
        return requested_model

    return "unknown"


def extract_reasoning_level_used(payload: Dict[str, Any], requested_effort: Optional[str]) -> str:
    # Best-effort extraction. Claude Code currently may not echo this explicitly.
    if requested_effort:
        return requested_effort

    for key in ("effort", "reasoning_effort", "reasoningLevel", "reasoning_level"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value

    usage = payload.get("usage") if isinstance(payload.get("usage"), dict) else {}
    for key in ("effort", "reasoning_effort", "reasoningLevel", "reasoning_level"):
        value = usage.get(key)
        if isinstance(value, str) and value:
            return value

    return "default"


def parse_claude_json(stdout: str) -> Dict[str, Any]:
    # Claude may emit extra lines in some environments; parse from bottom.
    lines = [line.strip() for line in stdout.splitlines() if line.strip()]
    for line in reversed(lines):
        if not line.startswith("{"):
            continue
        try:
            parsed = json.loads(line)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            continue

    # Fallback: try entire stdout.
    parsed = json.loads(stdout)
    if not isinstance(parsed, dict):
        raise ValueError("Claude output is not a JSON object")
    return parsed


def ensure_tracker_dir() -> None:
    TRACKER_DIR.mkdir(parents=True, exist_ok=True)


def session_file(session_id: str) -> Path:
    return TRACKER_DIR / f"{session_id}.json"


def load_session(session_id: str) -> Dict[str, Any]:
    path = session_file(session_id)
    if not path.exists():
        return {
            "session_id": session_id,
            "created_at": now_iso(),
            "updated_at": now_iso(),
            "name": None,
            "turns": [],
            "totals": {
                "input_tokens": 0,
                "output_tokens": 0,
                "cache_read_input_tokens": 0,
                "cache_creation_input_tokens": 0,
                "reasoning_tokens": 0,
                "reasoning_tokens_missing_turns": 0,
                "turns": 0,
            },
        }

    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_session(data: Dict[str, Any]) -> None:
    data["updated_at"] = now_iso()
    path = session_file(data["session_id"])
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def create_session(name: Optional[str]) -> str:
    ensure_tracker_dir()
    session_id = str(uuid.uuid4())
    data = load_session(session_id)
    data["name"] = name
    save_session(data)
    return session_id


def run_claude_prompt(
    session_id: str,
    prompt: str,
    model: Optional[str] = None,
    effort: Optional[str] = None,
    max_budget_usd: Optional[float] = None,
    resume: bool = False,
) -> Tuple[Dict[str, Any], str]:
    cmd = [
        "claude",
        "--dangerously-skip-permissions",
        "-p",
        "--output-format",
        "json",
    ]

    if resume:
        cmd.extend(["-r", session_id])
    else:
        cmd.extend(["--session-id", session_id])

    if model:
        cmd.extend(["--model", model])
    if effort:
        cmd.extend(["--effort", effort])
    if max_budget_usd is not None:
        cmd.extend(["--max-budget-usd", str(max_budget_usd)])

    cmd.append(prompt)

    proc = subprocess.run(cmd, capture_output=True, text=True)

    # If a session already exists, retry with --resume.
    if (
        proc.returncode != 0
        and not resume
        and "already in use" in (proc.stderr or "").lower()
    ):
        retry_cmd = [
            "claude",
            "--dangerously-skip-permissions",
            "-p",
            "--output-format",
            "json",
            "-r",
            session_id,
        ]
        if model:
            retry_cmd.extend(["--model", model])
        if effort:
            retry_cmd.extend(["--effort", effort])
        if max_budget_usd is not None:
            retry_cmd.extend(["--max-budget-usd", str(max_budget_usd)])
        retry_cmd.append(prompt)
        proc = subprocess.run(retry_cmd, capture_output=True, text=True)
        cmd = retry_cmd

    if proc.returncode != 0:
        raise RuntimeError(
            "Claude command failed\n"
            f"Command: {' '.join(cmd)}\n"
            f"Exit code: {proc.returncode}\n"
            f"stderr:\n{proc.stderr.strip()}\n"
            f"stdout:\n{proc.stdout.strip()}"
        )

    payload = parse_claude_json(proc.stdout)
    return payload, proc.stdout


def append_turn(
    session_data: Dict[str, Any],
    prompt: str,
    payload: Dict[str, Any],
    requested_model: Optional[str],
    requested_effort: Optional[str],
) -> TokenUsage:
    usage = extract_usage(payload)
    model_used = extract_model_used(payload, requested_model)
    reasoning_level_used = extract_reasoning_level_used(payload, requested_effort)

    turn = {
        "timestamp": now_iso(),
        "prompt": prompt,
        "response": payload.get("result"),
        "claude_session_id": payload.get("session_id"),
        "model": payload.get("model"),
        "model_requested": requested_model,
        "model_used": model_used,
        "reasoning_level_requested": requested_effort or "default",
        "reasoning_level_used": reasoning_level_used,
        "stop_reason": payload.get("stop_reason"),
        "total_cost_usd": payload.get("total_cost_usd"),
        "usage": {
            "input_tokens": usage.input_tokens,
            "output_tokens": usage.output_tokens,
            "cache_read_input_tokens": usage.cache_read_input_tokens,
            "cache_creation_input_tokens": usage.cache_creation_input_tokens,
            "reasoning_tokens": usage.reasoning_tokens,
        },
        "raw_usage": payload.get("usage"),
    }

    session_data.setdefault("turns", []).append(turn)

    totals = session_data.setdefault("totals", {})
    totals["input_tokens"] = as_int(totals.get("input_tokens")) + usage.input_tokens
    totals["output_tokens"] = as_int(totals.get("output_tokens")) + usage.output_tokens
    totals["cache_read_input_tokens"] = (
        as_int(totals.get("cache_read_input_tokens")) + usage.cache_read_input_tokens
    )
    totals["cache_creation_input_tokens"] = (
        as_int(totals.get("cache_creation_input_tokens")) + usage.cache_creation_input_tokens
    )

    if usage.reasoning_tokens is None:
        totals["reasoning_tokens_missing_turns"] = as_int(totals.get("reasoning_tokens_missing_turns")) + 1
    else:
        totals["reasoning_tokens"] = as_int(totals.get("reasoning_tokens")) + usage.reasoning_tokens

    totals["turns"] = as_int(totals.get("turns")) + 1

    return usage


def cmd_new(args: argparse.Namespace) -> int:
    sid = create_session(args.name)
    print(sid)
    print(f"Tracker file: {session_file(sid)}")
    return 0


def cmd_prompt(args: argparse.Namespace) -> int:
    ensure_tracker_dir()
    session_data = load_session(args.session)
    if args.name and not session_data.get("name"):
        session_data["name"] = args.name

    turns = session_data.get("turns") if isinstance(session_data.get("turns"), list) else []
    payload, _ = run_claude_prompt(
        session_id=args.session,
        prompt=args.prompt,
        model=args.model,
        effort=args.effort,
        max_budget_usd=args.max_budget_usd,
        resume=len(turns) > 0,
    )

    usage = append_turn(
        session_data,
        args.prompt,
        payload,
        requested_model=args.model,
        requested_effort=args.effort,
    )
    save_session(session_data)

    response = payload.get("result")
    print(response if isinstance(response, str) else json.dumps(response, ensure_ascii=False))
    print("---")
    print(f"Session: {args.session}")
    last_turn = session_data.get("turns", [])[-1] if session_data.get("turns") else {}
    print(f"Model used: {last_turn.get('model_used', 'unknown')}")
    print(f"Reasoning level used: {last_turn.get('reasoning_level_used', 'default')}")
    print(f"Input tokens: {usage.input_tokens}")
    print(f"Output tokens: {usage.output_tokens}")
    print(f"Cached input tokens (read): {usage.cache_read_input_tokens}")
    print(f"Cache creation input tokens: {usage.cache_creation_input_tokens}")
    if usage.reasoning_tokens is None:
        print("Reasoning tokens: unavailable in Claude Code output for this turn")
    else:
        print(f"Reasoning tokens: {usage.reasoning_tokens}")

    return 0


def cmd_summary(args: argparse.Namespace) -> int:
    data = load_session(args.session)
    totals = data.get("totals", {})
    turns = data.get("turns") if isinstance(data.get("turns"), list) else []

    model_counts: Dict[str, int] = {}
    reasoning_level_counts: Dict[str, int] = {}
    for turn in turns:
        if not isinstance(turn, dict):
            continue

        model = turn.get("model_used")
        if not isinstance(model, str) or not model:
            model = turn.get("model") if isinstance(turn.get("model"), str) else "unknown"
        model_counts[model] = model_counts.get(model, 0) + 1

        level = turn.get("reasoning_level_used")
        if not isinstance(level, str) or not level:
            req = turn.get("reasoning_level_requested")
            level = req if isinstance(req, str) and req else "default"
        reasoning_level_counts[level] = reasoning_level_counts.get(level, 0) + 1

    print(f"Session: {data.get('session_id')}")
    if data.get("name"):
        print(f"Name: {data.get('name')}")
    print(f"Turns: {as_int(totals.get('turns'))}")
    print(f"Input tokens: {as_int(totals.get('input_tokens'))}")
    print(f"Output tokens: {as_int(totals.get('output_tokens'))}")
    print(f"Cached input tokens (read): {as_int(totals.get('cache_read_input_tokens'))}")
    print(f"Cache creation input tokens: {as_int(totals.get('cache_creation_input_tokens'))}")

    missing = as_int(totals.get("reasoning_tokens_missing_turns"))
    reasoning = as_int(totals.get("reasoning_tokens"))
    if missing > 0:
        print(f"Reasoning tokens: {reasoning} (missing on {missing} turn(s))")
    else:
        print(f"Reasoning tokens: {reasoning}")

    if model_counts:
        formatted_models = ", ".join(f"{name} ({count})" for name, count in sorted(model_counts.items()))
        print(f"Models used: {formatted_models}")
    else:
        print("Models used: unknown")

    if reasoning_level_counts:
        formatted_levels = ", ".join(
            f"{level} ({count})" for level, count in sorted(reasoning_level_counts.items())
        )
        print(f"Reasoning levels used: {formatted_levels}")
    else:
        print("Reasoning levels used: default")

    print(f"Tracker file: {session_file(args.session)}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Track Claude Code tokens per session (input/output/cache/reasoning/model/effort)."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_new = sub.add_parser("new", help="Create a new tracked session")
    p_new.add_argument("--name", default=None, help="Optional human-readable name")
    p_new.set_defaults(func=cmd_new)

    p_prompt = sub.add_parser("prompt", help="Send one prompt to Claude and record usage")
    p_prompt.add_argument("--session", required=True, help="Tracker session UUID")
    p_prompt.add_argument("--prompt", required=True, help="Prompt text")
    p_prompt.add_argument("--name", default=None, help="Set session name if currently empty")
    p_prompt.add_argument("--model", default=None, help="Claude model alias/name")
    p_prompt.add_argument("--effort", default=None, choices=["low", "medium", "high"], help="Reasoning effort")
    p_prompt.add_argument("--max-budget-usd", type=float, default=None, help="Max budget for this request")
    p_prompt.set_defaults(func=cmd_prompt)

    p_summary = sub.add_parser("summary", help="Show aggregate usage for a tracked session")
    p_summary.add_argument("--session", required=True, help="Tracker session UUID")
    p_summary.set_defaults(func=cmd_summary)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except Exception as exc:  # noqa: BLE001
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
