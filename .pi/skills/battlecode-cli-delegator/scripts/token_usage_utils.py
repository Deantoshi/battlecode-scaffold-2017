#!/usr/bin/env python3
"""Token usage extraction helpers for delegated CLI logs."""

from __future__ import annotations

import re
from typing import Dict, List, Tuple

NUM_PATTERN = r"([0-9][0-9,_]*(?:\.[0-9]+)?(?:[kKmM])?)"

PATTERNS: dict[str, list[str]] = {
    "input_tokens": [
        rf'"input_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"inputTokens"\s*:\s*{NUM_PATTERN}',
        rf'\binput_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\binput\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
        rf'"prompt_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"promptTokens"\s*:\s*{NUM_PATTERN}',
        rf'\bprompt\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "output_tokens": [
        rf'"output_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"outputTokens"\s*:\s*{NUM_PATTERN}',
        rf'\boutput_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\boutput\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
        rf'"completion_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"completionTokens"\s*:\s*{NUM_PATTERN}',
        rf'\bcompletion\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "cache_input_tokens": [
        rf'"cache_input_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"cacheInputTokens"\s*:\s*{NUM_PATTERN}',
        rf'\bcache_input_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\bcache(?:d)?\s+input\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "cache_creation_input_tokens": [
        rf'"cache_creation_input_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"cacheCreationInputTokens"\s*:\s*{NUM_PATTERN}',
        rf'\bcache_creation_input_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\bcache\s+creation\s+input\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\bcache\s+write\s+input\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "cache_read_input_tokens": [
        rf'"cache_read_input_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"cacheReadInputTokens"\s*:\s*{NUM_PATTERN}',
        rf'\bcache_read_input_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\bcache\s+read\s+input\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\bcache\s+hit\s+input\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "reasoning_tokens": [
        rf'"reasoning_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"reasoningTokens"\s*:\s*{NUM_PATTERN}',
        rf'\breasoning_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\breasoning\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
    "total_tokens": [
        rf'"total_tokens"\s*:\s*{NUM_PATTERN}',
        rf'"totalTokens"\s*:\s*{NUM_PATTERN}',
        rf'\btotal_tokens\b\s*[:=]\s*{NUM_PATTERN}',
        rf'\btotal\s+tokens?\b\s*[:=]\s*{NUM_PATTERN}',
    ],
}


def _parse_intish(raw: str) -> int | None:
    if raw is None:
        return None

    cleaned = raw.strip().replace(",", "").replace("_", "")
    if not cleaned:
        return None

    mult = 1
    last = cleaned[-1].lower()
    if last == "k":
        mult = 1_000
        cleaned = cleaned[:-1]
    elif last == "m":
        mult = 1_000_000
        cleaned = cleaned[:-1]

    try:
        value = float(cleaned)
    except ValueError:
        return None

    return int(round(value * mult))


def extract_token_usage_from_text(text: str) -> Tuple[Dict[str, int], Dict[str, List[dict]]]:
    metrics: Dict[str, int] = {}
    evidence: Dict[str, List[dict]] = {}

    for metric, pattern_list in PATTERNS.items():
        best_value = None
        best_evidence = None
        all_hits: List[dict] = []

        for pattern in pattern_list:
            for match in re.finditer(pattern, text, flags=re.IGNORECASE):
                raw = match.group(1)
                parsed = _parse_intish(raw)
                if parsed is None:
                    continue

                hit = {
                    "value": parsed,
                    "raw": raw,
                    "span": [match.start(), match.end()],
                    "pattern": pattern,
                }
                all_hits.append(hit)

                if best_value is None or parsed > best_value:
                    best_value = parsed
                    best_evidence = hit

        if best_value is not None and best_evidence is not None:
            metrics[metric] = best_value
            evidence[metric] = [best_evidence]

        if all_hits:
            evidence[f"{metric}__all_hits"] = all_hits

    if "cache_input_tokens" not in metrics:
        ccreate = metrics.get("cache_creation_input_tokens", 0)
        cread = metrics.get("cache_read_input_tokens", 0)
        if ccreate or cread:
            metrics["cache_input_tokens"] = ccreate + cread
            evidence.setdefault("cache_input_tokens", []).append(
                {
                    "value": metrics["cache_input_tokens"],
                    "raw": "derived",
                    "span": [-1, -1],
                    "pattern": "derived: cache_creation_input_tokens + cache_read_input_tokens",
                }
            )

    return metrics, evidence


def aggregate_token_metrics(records: list[dict]) -> Dict[str, int]:
    totals: Dict[str, int] = {}
    for rec in records:
        usage = rec.get("token_usage") or {}
        for key, value in usage.items():
            if isinstance(value, (int, float)):
                totals[key] = totals.get(key, 0) + int(value)
    return {k: totals[k] for k in sorted(totals)}
