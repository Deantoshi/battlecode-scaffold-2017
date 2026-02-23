#!/usr/bin/env python3
import argparse
import os
import select
import signal
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

SUCCESS_MARKER = b"FINAL_STATUS: SUCCESS"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run an end-to-end Battlecode delegation flow: launch agent, wait for FINAL_STATUS, verify with gradle, retry if needed."
    )
    parser.add_argument("agent", help="claude | opencode | codex")
    parser.add_argument("src_folder", help="Target folder under src/")
    parser.add_argument("--max-attempts", type=int, default=3, help="Max delegate+verify attempts (default: 3)")
    parser.add_argument(
        "--session-timeout",
        type=int,
        default=1800,
        help="Per-attempt delegate session timeout in seconds (default: 1800)",
    )
    parser.add_argument(
        "--runtime-dir",
        default=".pi/skills/battlecode-cli-delegator/runtime",
        help="Directory for runtime logs and retry feedback files",
    )
    return parser.parse_args()


def run_capture(command: list[str]) -> tuple[int, str, str]:
    proc = subprocess.run(command, capture_output=True, text=True)
    return proc.returncode, proc.stdout, proc.stderr


def build_delegate_command(agent: str, src_folder: str, extra_file: Path | None) -> str:
    script = Path(".pi/skills/battlecode-cli-delegator/scripts/prepare_delegation_command.sh")
    cmd = ["bash", str(script), agent, src_folder]
    if extra_file is not None:
        cmd.append(str(extra_file))

    rc, out, err = run_capture(cmd)
    if rc != 0:
        raise RuntimeError(err.strip() or out.strip() or "failed to build delegation command")

    command = out.strip()
    if not command:
        raise RuntimeError("delegation command was empty")
    return command


def terminate_process_group(pid: int, sig: int) -> None:
    try:
        os.killpg(os.getpgid(pid), sig)
    except ProcessLookupError:
        pass


def run_delegate_session(command: str, timeout_sec: int, log_path: Path) -> tuple[bool, bool, int]:
    log_path.parent.mkdir(parents=True, exist_ok=True)

    master_fd, slave_fd = os.openpty()
    proc = subprocess.Popen(
        ["/bin/bash", "-lc", command],
        stdin=slave_fd,
        stdout=slave_fd,
        stderr=slave_fd,
        preexec_fn=os.setsid,
        close_fds=True,
    )
    os.close(slave_fd)

    start = time.time()
    deadline = start + timeout_sec
    tail = b""
    found_success = False
    exit_sent = False
    exit_sent_at = 0.0
    timed_out = False

    with open(log_path, "wb") as log_file:
        try:
            while True:
                now = time.time()
                if now >= deadline:
                    timed_out = True
                    break

                if proc.poll() is not None:
                    break

                rlist, _, _ = select.select([master_fd], [], [], 0.2)
                if rlist:
                    chunk = os.read(master_fd, 8192)
                    if chunk:
                        log_file.write(chunk)
                        log_file.flush()
                        sys.stdout.buffer.write(chunk)
                        sys.stdout.buffer.flush()

                        tail = (tail + chunk)[-16384:]
                        if (not found_success) and (SUCCESS_MARKER in tail):
                            found_success = True
                            # Ask interactive CLIs to exit gracefully after success.
                            os.write(master_fd, b"\nexit\n")
                            exit_sent = True
                            exit_sent_at = time.time()

                if found_success and exit_sent and proc.poll() is None and (time.time() - exit_sent_at > 8):
                    terminate_process_group(proc.pid, signal.SIGINT)
                    time.sleep(1)
                    if proc.poll() is None:
                        terminate_process_group(proc.pid, signal.SIGTERM)
                        time.sleep(1)
                    if proc.poll() is None:
                        terminate_process_group(proc.pid, signal.SIGKILL)

            if timed_out and proc.poll() is None:
                terminate_process_group(proc.pid, signal.SIGTERM)
                time.sleep(1)
                if proc.poll() is None:
                    terminate_process_group(proc.pid, signal.SIGKILL)
        finally:
            os.close(master_fd)

    rc = proc.wait(timeout=10)
    return found_success, timed_out, rc


def run_gradle_verify(src_folder: str, verify_log_path: Path) -> tuple[int, str]:
    cmd = [
        "./gradlew",
        "runWithSummary",
        f"-PteamA={src_folder}",
        "-PteamB=examplefuncsplayer",
        "-Pmaps=Shrine",
    ]

    verify_log_path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []

    with open(verify_log_path, "w", encoding="utf-8") as log:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        assert proc.stdout is not None
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            log.write(line)
            lines.append(line)

        rc = proc.wait()

    return rc, "".join(lines)


def make_feedback(text: str) -> str:
    lines = text.splitlines()
    tail = lines[-160:] if len(lines) > 160 else lines
    trimmed = "\n".join(tail).strip()
    max_chars = 12000
    if len(trimmed) > max_chars:
        trimmed = trimmed[-max_chars:]
    return (
        "Previous independent verification failed. Fix all issues shown below, then rerun the required\n"
        "./gradlew runWithSummary command until successful:\n\n"
        f"{trimmed}\n"
    )


def main() -> int:
    args = parse_args()
    runtime_dir = Path(args.runtime_dir)
    runtime_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    extra_feedback_file: Path | None = None

    print(
        f"[runner] starting delegation flow: agent={args.agent}, src_folder={args.src_folder}, "
        f"max_attempts={args.max_attempts}, session_timeout={args.session_timeout}s"
    )

    for attempt in range(1, args.max_attempts + 1):
        print(f"\n[runner] === attempt {attempt}/{args.max_attempts} ===")

        try:
            delegate_command = build_delegate_command(args.agent, args.src_folder, extra_feedback_file)
        except Exception as exc:
            print(f"[runner] failed to prepare delegation command: {exc}", file=sys.stderr)
            return 1

        delegate_log = runtime_dir / f"delegate_{args.src_folder}_{timestamp}_attempt{attempt}.log"
        verify_log = runtime_dir / f"verify_{args.src_folder}_{timestamp}_attempt{attempt}.log"

        print(f"[runner] delegate log: {delegate_log}")
        found_success, timed_out, delegate_rc = run_delegate_session(
            delegate_command, args.session_timeout, delegate_log
        )
        print(
            f"\n[runner] delegate session complete: rc={delegate_rc}, "
            f"success_marker_found={found_success}, timed_out={timed_out}"
        )

        if not found_success:
            if timed_out:
                print("[runner] delegate session timed out before success marker", file=sys.stderr)
            else:
                print("[runner] success marker not found in delegate output", file=sys.stderr)

            if attempt == args.max_attempts:
                return 1

            continue

        print("[runner] running independent gradle verification...")
        verify_rc, verify_output = run_gradle_verify(args.src_folder, verify_log)
        print(f"[runner] verification rc={verify_rc}; log: {verify_log}")

        if verify_rc == 0:
            print("[runner] VERIFIED SUCCESS")
            return 0

        if attempt == args.max_attempts:
            print("[runner] verification failed and no attempts remain", file=sys.stderr)
            return 1

        feedback = make_feedback(verify_output)
        extra_feedback_file = runtime_dir / f"feedback_{args.src_folder}_{timestamp}_attempt{attempt}.txt"
        extra_feedback_file.write_text(feedback, encoding="utf-8")
        print(f"[runner] verification failed; retry feedback saved to {extra_feedback_file}")

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
