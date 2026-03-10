#!/bin/bash
# variant-loop.sh - Variant archetype optimization loop
#
# Usage: ./scripts/variant-loop.sh --bot <bot> [options]
#
# This script orchestrates variant-based bot improvement:
#   1. A coding-agent worker generates variant archetypes
#   2. Creates variant folders as copies of original
#   3. Parallel coding-agent workers implement each archetype
#   4. All variants + original run against opponent
#   5. Best performer is promoted if better than original
#   6. Loop until goal achieved or max iterations

set -e

# Colors
RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
BLUE=$'\033[0;34m'
CYAN=$'\033[0;36m'
BOLD=$'\033[1m'
NC=$'\033[0m'

# Defaults
BOT=""
OPPONENT="copy_bot"
MAP="Clusters"
MAX_ITERS="20"
NUM_VARIANTS="16"
CODING_AGENT="${CODING_AGENT:-${AI_ENGINE:-pi}}"

# Agent/runtime options
MODEL="${MODEL:-}"
PI_THINKING="${PI_THINKING:-}"
CODEX_REASONING_EFFORT="${CODEX_REASONING_EFFORT:-${MODEL_REASONING_EFFORT:-}}"
OPENCODE_VARIANT="${OPENCODE_VARIANT:-${MODEL_VARIANT:-}}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)-$$}"

print_usage() {
    echo "Usage: $0 --bot <bot> [options]"
    echo ""
    echo "Required:"
    echo "  -b, --bot <name>            Your bot folder name under src/"
    echo ""
    echo "Options:"
    echo "  -o, --opponent <name>       Opponent bot folder (default: copy_bot)"
    echo "  -m, --map <name>            Map name (default: Clusters)"
    echo "  -i, --max-iters <n>         Maximum improvement cycles (default: 20)"
    echo "  -n, --num-variants <n>      Variants generated per iteration (default: 16)"
    echo "  -a, --agent <name>          Worker CLI: claude | pi | opencode | codex (default: pi)"
    echo "      --model <name>          Optional model override (pi, opencode, or codex)"
    echo "      --thinking <level>      Optional thinking level (pi only)"
    echo "      --reasoning-effort <level>  Optional reasoning effort (codex)"
    echo "      --variant <name>        Optional model variant / reasoning mode (opencode)"
    echo "  -h, --help                  Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 --bot grok_code_fast_1"
    echo "  $0 --bot grok_code_fast_1 --max-iters 1 --num-variants 2 --agent opencode"
}

POSITIONAL=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--bot)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            BOT="$2"
            shift 2
            ;;
        -o|--opponent)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            OPPONENT="$2"
            shift 2
            ;;
        -m|--map)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            MAP="$2"
            shift 2
            ;;
        -i|--max-iters|--max-iterations)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            MAX_ITERS="$2"
            shift 2
            ;;
        -n|--num-variants)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            NUM_VARIANTS="$2"
            shift 2
            ;;
        -a|--agent|--coding-agent)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            CODING_AGENT="$2"
            shift 2
            ;;
        --model)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            MODEL="$2"
            shift 2
            ;;
        --thinking)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            PI_THINKING="$2"
            shift 2
            ;;
        --reasoning-effort)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            CODEX_REASONING_EFFORT="$2"
            shift 2
            ;;
        --variant)
            [[ $# -lt 2 ]] && { printf '%s\n' "${RED}Missing value for $1${NC}"; exit 1; }
            OPENCODE_VARIANT="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        --)
            shift
            while [[ $# -gt 0 ]]; do
                POSITIONAL+=("$1")
                shift
            done
            ;;
        -*)
            printf '%s\n' "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
        *)
            POSITIONAL+=("$1")
            shift
            ;;
    esac
done

if [[ ${#POSITIONAL[@]} -gt 0 ]]; then
    if [[ -z "$BOT" ]]; then
        printf '%s\n' "${YELLOW}Warning: positional args are deprecated; please use flags.${NC}"
        BOT="${POSITIONAL[0]:-}"
        OPPONENT="${POSITIONAL[1]:-$OPPONENT}"
        MAP="${POSITIONAL[2]:-$MAP}"
        MAX_ITERS="${POSITIONAL[3]:-$MAX_ITERS}"
        NUM_VARIANTS="${POSITIONAL[4]:-$NUM_VARIANTS}"
        CODING_AGENT="${POSITIONAL[5]:-$CODING_AGENT}"
        if [[ ${#POSITIONAL[@]} -gt 6 ]]; then
            printf '%s\n' "${RED}Too many positional arguments${NC}"
            print_usage
            exit 1
        fi
    else
        printf '%s\n' "${RED}Unexpected positional arguments: ${POSITIONAL[*]}${NC}"
        print_usage
        exit 1
    fi
fi

# Validate arguments
if [[ -z "$BOT" ]]; then
    printf '%s\n' "${RED}Error: --bot is required${NC}"
    print_usage
    exit 1
fi

if [[ ! "$MAX_ITERS" =~ ^[0-9]+$ ]] || [[ "$MAX_ITERS" -lt 1 ]]; then
    printf '%s\n' "${RED}Error: --max-iters must be a positive integer (got: $MAX_ITERS)${NC}"
    exit 1
fi

if [[ ! "$NUM_VARIANTS" =~ ^[0-9]+$ ]] || [[ "$NUM_VARIANTS" -lt 1 ]]; then
    printf '%s\n' "${RED}Error: --num-variants must be a positive integer (got: $NUM_VARIANTS)${NC}"
    exit 1
fi

CODING_AGENT="$(printf '%s' "$CODING_AGENT" | tr '[:upper:]' '[:lower:]')"
case "$CODING_AGENT" in
    pi|pi-coding-agent)
        CODING_AGENT="pi"
        ;;
    claude|opencode|codex)
        ;;
    *)
        printf '%s\n' "${RED}Unsupported coding-agent: $CODING_AGENT${NC}"
        printf '%s\n' "${RED}Supported coding agents: claude, pi, opencode, codex${NC}"
        exit 1
        ;;
esac

if ! command -v "$CODING_AGENT" >/dev/null 2>&1; then
    printf '%s\n' "${RED}Error: '$CODING_AGENT' CLI not found in PATH${NC}"
    exit 1
fi

# Verify bot exists
if [[ ! -d "src/$BOT" ]]; then
    printf '%s\n' "${RED}Error: Bot folder not found: src/$BOT${NC}"
    exit 1
fi

# State directory
STATE_DIR="src/$BOT/.state"
STRATEGY_HISTORY="$STATE_DIR/strategy-history.json"
USAGE_LOG="$STATE_DIR/usage-log.jsonl"
mkdir -p "$STATE_DIR"

resolve_codex_setting() {
    local key="$1"
    local config_file="${HOME}/.codex/config.toml"
    [[ -f "$config_file" ]] || return 0
    python3 - "$config_file" "$key" <<'PY'
import re
import sys

config_file, key = sys.argv[1], sys.argv[2]
pattern = re.compile(r'^\s*' + re.escape(key) + r'\s*=\s*"([^"]*)"')
with open(config_file, 'r', encoding='utf-8') as f:
    for line in f:
        if line.lstrip().startswith('['):
            break
        match = pattern.match(line)
        if match:
            print(match.group(1))
            break
PY
}

if [[ "$CODING_AGENT" == "codex" ]]; then
    [[ -z "$MODEL" ]] && MODEL="$(resolve_codex_setting model)"
    [[ -z "$CODEX_REASONING_EFFORT" ]] && CODEX_REASONING_EFFORT="$(resolve_codex_setting model_reasoning_effort)"
fi

usage_logging_enabled() {
    case "$1" in
        claude|codex|opencode)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

make_opencode_session_title() {
    local agent_name="$1"
    local context="$2"
    if [[ "$context" == iter:* ]]; then
        printf 'variant-loop:%s:%s:%s:%s:%s:%s' \
            "$RUN_ID" \
            "$BOT" \
            "$OPPONENT" \
            "$MAP" \
            "$agent_name" \
            "$context"
    else
        printf 'variant-loop:%s:%s:%s:%s:%s:iter:%s:%s' \
            "$RUN_ID" \
            "$BOT" \
            "$OPPONENT" \
            "$MAP" \
            "$agent_name" \
            "${iter:-0}" \
            "$context"
    fi
}

# Function to accumulate usage stats from agent JSON output
accumulate_usage() {
    local agent_kind="$1"
    local json_output="$2"
    local worker_label="$3"
    local requested_model="$4"
    local requested_reasoning_effort="$5"
    local measured_duration_ms="$6"
    local session_title="$7"
    # Append a line to the JSONL usage log
    python3 -c "
import collections
import json, os, sqlite3, sys, time
from pathlib import Path

def normalize_model(provider, model):
    provider = (provider or '').strip()
    model = (model or '').strip()
    if not model:
        return None
    if provider:
        return f'{provider}/{model}'
    return model

def lookup_opencode_default_variant(model):
    model = (model or '').strip()
    if not model:
        return None

    state_file = Path.home() / '.local' / 'state' / 'opencode' / 'model.json'
    if not state_file.is_file():
        return None

    try:
        state = json.loads(state_file.read_text(encoding='utf-8'))
    except Exception:
        return None

    variants = state.get('variant')
    if not isinstance(variants, dict):
        return None

    direct = variants.get(model)
    if isinstance(direct, str) and direct.strip():
        return direct.strip()

    if '/' in model:
        _, model_id = model.split('/', 1)
        fallback = variants.get(model_id)
        if isinstance(fallback, str) and fallback.strip():
            return fallback.strip()
    else:
        suffix = f'/{model}'
        for key, value in variants.items():
            if not isinstance(key, str) or not isinstance(value, str):
                continue
            if key.endswith(suffix) and value.strip():
                return value.strip()

    return None

def resolve_opencode_reasoning_effort(requested_effort, observed_effort, reasoning_tokens, resolved_model):
    observed_effort = (observed_effort or '').strip()
    requested_effort = (requested_effort or '').strip()

    if observed_effort:
        return observed_effort
    if requested_effort:
        return requested_effort

    default_variant = lookup_opencode_default_variant(resolved_model)
    if default_variant:
        return default_variant

    if (reasoning_tokens or 0) > 0:
        return 'default'

    return 'none'

def extract_opencode_session_meta(session_id, messages):
    if not messages:
        return None

    assistant_model_counts = collections.Counter()
    requested_model_counts = collections.Counter()
    variant_counts = collections.Counter()
    messages_by_id = {}

    for msg in messages:
        if not isinstance(msg, dict):
            continue
        msg_id = msg.get('id')
        if msg_id:
            messages_by_id[msg_id] = msg

    for msg in messages:
        if not isinstance(msg, dict):
            continue

        role = msg.get('role') or ''
        model_obj = msg.get('model') or {}
        provider = msg.get('providerID') or model_obj.get('providerID') or ''
        model = msg.get('modelID') or model_obj.get('modelID') or ''
        normalized_model = normalize_model(provider, model)
        variant = (msg.get('variant') or model_obj.get('variant') or '').strip()

        if role == 'assistant' and normalized_model:
            assistant_model_counts[normalized_model] += 1
        elif role == 'user' and normalized_model:
            requested_model_counts[normalized_model] += 1

        if variant:
            variant_counts[variant] += 1

        parent_id = msg.get('parentID')
        if parent_id:
            parent = messages_by_id.get(parent_id) or {}
            parent_variant = (parent.get('variant') or '').strip()
            if parent_variant:
                variant_counts[parent_variant] += 1

    resolved_model = None
    if assistant_model_counts:
        resolved_model = assistant_model_counts.most_common(1)[0][0]
    elif requested_model_counts:
        resolved_model = requested_model_counts.most_common(1)[0][0]

    resolved_variant = variant_counts.most_common(1)[0][0] if variant_counts else None

    return {
        'session_id': session_id,
        'model': resolved_model,
        'variant': resolved_variant,
    }

def lookup_opencode_session_db(title, cwd):
    db_path = Path.home() / '.local' / 'share' / 'opencode' / 'opencode.db'
    if not db_path.is_file():
        return None

    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        cur = conn.cursor()
        session = cur.execute(
            '''
            select id
            from session
            where title = ? and directory = ?
            order by time_updated desc
            limit 1
            ''',
            (title, cwd),
        ).fetchone()
        if not session:
            return None

        rows = cur.execute(
            '''
            select data
            from message
            where session_id = ?
            order by time_created asc
            ''',
            (session['id'],),
        ).fetchall()
    finally:
        conn.close()

    messages = []
    for row in rows:
        try:
            messages.append(json.loads(row['data']))
        except Exception:
            continue

    return extract_opencode_session_meta(session['id'], messages)

def lookup_opencode_session_storage(title, cwd):
    storage_root = Path.home() / '.local' / 'share' / 'opencode' / 'storage'
    session_root = storage_root / 'session'
    message_root = storage_root / 'message'
    if not session_root.is_dir():
        return None

    candidates = []
    for session_file in session_root.glob('*/*.json'):
        try:
            session = json.loads(session_file.read_text(encoding='utf-8'))
        except Exception:
            continue
        if session.get('title') != title:
            continue
        if os.path.realpath(session.get('directory') or '') != cwd:
            continue
        updated = ((session.get('time') or {}).get('updated')) or 0
        candidates.append((updated, session.get('id')))

    for _, session_id in sorted(candidates, reverse=True):
        if not session_id:
            continue
        message_dir = message_root / session_id
        if not message_dir.is_dir():
            continue
        messages = []
        for message_file in sorted(message_dir.glob('*.json')):
            try:
                messages.append(json.loads(message_file.read_text(encoding='utf-8')))
            except Exception:
                continue
        meta = extract_opencode_session_meta(session_id, messages)
        if meta:
            return meta

    return None

def lookup_opencode_session(title):
    if not title:
        return None

    cwd = os.path.realpath(os.getcwd())

    for _ in range(40):
        db_meta = None
        storage_meta = None

        try:
            db_meta = lookup_opencode_session_db(title, cwd)
        except Exception:
            db_meta = None

        try:
            storage_meta = lookup_opencode_session_storage(title, cwd)
        except Exception:
            storage_meta = None

        merged = db_meta or storage_meta
        if db_meta and storage_meta:
            merged = {
                'session_id': db_meta.get('session_id') or storage_meta.get('session_id'),
                'model': db_meta.get('model') or storage_meta.get('model'),
                'variant': db_meta.get('variant') or storage_meta.get('variant'),
            }

        if merged and (merged.get('model') or merged.get('variant')):
            return merged

        if merged:
            return merged

        time.sleep(0.25)

    return None

try:
    agent_kind = sys.argv[1]
    raw_output = sys.argv[2]
    worker_label = sys.argv[3]
    requested_model = sys.argv[4]
    requested_reasoning_effort = sys.argv[5]
    measured_duration_ms = int(sys.argv[6] or '0')
    session_title = sys.argv[7]

    model = requested_model or 'unknown'
    reasoning_effort = requested_reasoning_effort
    cost = 0
    duration_ms = 0
    num_turns = 0
    input_tokens = 0
    output_tokens = 0
    reasoning_tokens = 0
    cache_read_tokens = 0
    cache_creation_tokens = 0
    fast_mode = 'unknown'

    if agent_kind == 'claude':
        data = json.loads(raw_output)
        model_usage = data.get('modelUsage', {})
        if model_usage:
            model = ', '.join(model_usage.keys())
        elif data.get('model'):
            model = data.get('model')

        cost = data.get('total_cost_usd', 0)
        duration_ms = data.get('duration_ms', 0)
        num_turns = data.get('num_turns', 0)
        usage = data.get('usage', {})
        input_tokens = usage.get('input_tokens', 0)
        output_tokens = usage.get('output_tokens', 0)
        reasoning_tokens = usage.get('reasoning_tokens', 0)
        cache_read_tokens = usage.get('cache_read_input_tokens', 0)
        cache_creation_tokens = usage.get('cache_creation_input_tokens', 0)
        fast_mode = data.get('fast_mode_state', 'unknown')
    elif agent_kind == 'opencode':
        session_meta = lookup_opencode_session(session_title)
        if session_meta:
            if session_meta.get('model'):
                model = session_meta['model']
            if session_meta.get('variant'):
                reasoning_effort = session_meta['variant']
        for line in raw_output.splitlines():
            line = line.strip()
            if not line or not line.startswith('{'):
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue

            if event.get('type') == 'step_finish':
                part = event.get('part') or {}
                tokens = part.get('tokens') or {}
                cache = tokens.get('cache') or {}

                input_tokens += tokens.get('input', 0) or 0
                output_tokens += tokens.get('output', 0) or 0
                reasoning_tokens += tokens.get('reasoning', 0) or 0
                cache_read_tokens += cache.get('read', 0) or 0
                cache_creation_tokens += cache.get('write', 0) or 0
                num_turns += 1

                maybe_cost = part.get('cost')
                if isinstance(maybe_cost, (int, float)):
                    cost += maybe_cost

                maybe_model = (
                    part.get('modelID')
                    or (part.get('message') or {}).get('modelID')
                    or event.get('modelID')
                )
                maybe_provider = (
                    part.get('providerID')
                    or (part.get('message') or {}).get('providerID')
                    or event.get('providerID')
                )
                if maybe_model:
                    normalized_model = normalize_model(maybe_provider, maybe_model)
                    if normalized_model:
                        model = normalized_model
                    elif model in ('', 'unknown') or '/' not in str(model):
                        model = maybe_model

                maybe_variant = (
                    part.get('variant')
                    or (part.get('message') or {}).get('variant')
                )
                if maybe_variant:
                    reasoning_effort = maybe_variant

        reasoning_effort = resolve_opencode_reasoning_effort(
            requested_reasoning_effort,
            reasoning_effort,
            reasoning_tokens,
            model,
        )
    elif agent_kind == 'codex':
        for line in raw_output.splitlines():
            line = line.strip()
            if not line or not line.startswith('{'):
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue

            if event.get('type') == 'turn.completed':
                usage = event.get('usage', {})
                input_tokens = usage.get('input_tokens', 0)
                output_tokens = usage.get('output_tokens', 0)
                reasoning_tokens = usage.get('reasoning_tokens', 0)
                cache_read_tokens = usage.get('cached_input_tokens', 0)
                num_turns += 1

            if event.get('type') == 'item.completed':
                item = event.get('item', {})
                if isinstance(item, dict):
                    maybe_model = item.get('model')
                    if maybe_model:
                        model = maybe_model
    else:
        raise ValueError(f'Unsupported agent_kind: {agent_kind}')

    if duration_ms <= 0 and measured_duration_ms > 0:
        duration_ms = measured_duration_ms

    entry = {
        'agent': agent_kind,
        'worker': worker_label,
        'model': model,
        'reasoning_effort': reasoning_effort or ('unknown' if agent_kind != 'opencode' else 'default'),
        'reasoning_effort_requested': requested_reasoning_effort or ('default' if agent_kind == 'opencode' else 'unknown'),
        'cost_usd': cost,
        'duration_ms': duration_ms,
        'num_turns': num_turns,
        'input_tokens': input_tokens,
        'output_tokens': output_tokens,
        'reasoning_tokens': reasoning_tokens,
        'cache_read_tokens': cache_read_tokens,
        'cache_creation_tokens': cache_creation_tokens,
        'fast_mode': fast_mode,
    }
    print(json.dumps(entry))
except Exception as e:
    print(json.dumps({'agent': sys.argv[1], 'worker': sys.argv[3], 'error': str(e)}), file=sys.stderr)
" "$agent_kind" "$json_output" "$worker_label" "$requested_model" "$requested_reasoning_effort" "$measured_duration_ms" "$session_title" >> "$USAGE_LOG" 2>/dev/null
}

# Function to print usage summary for current iteration
print_usage_summary() {
    if [[ ! -f "$USAGE_LOG" ]]; then
        return
    fi
    python3 << 'USAGE_EOF' - "$USAGE_LOG"
import json, sys

log_file = sys.argv[1]
total_cost = 0.0
total_input = 0
total_output = 0
total_reasoning = 0
total_cache_read = 0
total_cache_create = 0
total_duration = 0
total_turns = 0
model_set = set()
reasoning_set = set()
entries = []

with open(log_file) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            e = json.loads(line)
            entries.append(e)
            total_cost += e.get("cost_usd", 0)
            total_input += e.get("input_tokens", 0)
            total_output += e.get("output_tokens", 0)
            total_reasoning += e.get("reasoning_tokens", 0)
            total_cache_read += e.get("cache_read_tokens", 0)
            total_cache_create += e.get("cache_creation_tokens", 0)
            total_duration += e.get("duration_ms", 0)
            total_turns += e.get("num_turns", 0)
            m = e.get("model", "")
            if m and m != "unknown":
                model_set.add(m)
            r = e.get("reasoning_effort", "")
            if r and r != "unknown":
                reasoning_set.add(r)
        except json.JSONDecodeError:
            continue

if not entries:
    return

print(f"\033[1m\033[36m{'─' * 60}\033[0m")
print(f"\033[1m\033[36m  USAGE SUMMARY ({len(entries)} worker calls)\033[0m")
print(f"\033[1m\033[36m{'─' * 60}\033[0m")
if model_set:
    print(f"  Model(s):        {', '.join(sorted(model_set))}")
if reasoning_set:
    print(f"  Reasoning:       {', '.join(sorted(reasoning_set))}")
print(f"  Total cost:      ${total_cost:.4f}")
print(f"  Input tokens:    {total_input:,}")
print(f"  Output tokens:   {total_output:,}")
if total_reasoning > 0:
    print(f"  Reasoning:       {total_reasoning:,}")
if total_cache_read > 0:
    print(f"  Cache read:      {total_cache_read:,}")
if total_cache_create > 0:
    print(f"  Cache creation:  {total_cache_create:,}")
print(f"  Total turns:     {total_turns:,}")
dur_s = total_duration / 1000
if dur_s >= 60:
    print(f"  Total duration:  {dur_s/60:.1f}m")
else:
    print(f"  Total duration:  {dur_s:.1f}s")
print(f"\033[1m\033[36m{'─' * 60}\033[0m")

# Per-worker breakdown
print(f"  {'Worker':<30} {'Cost':>8} {'In':>8} {'Out':>8}")
for e in entries:
    w = e.get("worker", "?")
    c = e.get("cost_usd", 0)
    i = e.get("input_tokens", 0)
    o = e.get("output_tokens", 0)
    print(f"  {w:<30} ${c:>7.4f} {i:>7,} {o:>7,}")
USAGE_EOF
}

# Count existing champions
NUM_CHAMPIONS=0
while [[ -d "src/${BOT}_champion_${NUM_CHAMPIONS}" ]]; do
    NUM_CHAMPIONS=$((NUM_CHAMPIONS + 1))
done

# Print header
printf '%s\n' "${BOLD}${CYAN}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                      VARIANT ARCHETYPE OPTIMIZER"
echo "═══════════════════════════════════════════════════════════════════════════════"
printf '%s\n' "${NC}"
printf '%s\n' "${BLUE}Bot:${NC}        $BOT"
printf '%s\n' "${BLUE}Opponent:${NC}   $OPPONENT"
printf '%s\n' "${BLUE}Map:${NC}        $MAP"
printf '%s\n' "${BLUE}Max Iters:${NC}  $MAX_ITERS"
printf '%s\n' "${BLUE}Variants:${NC}   $NUM_VARIANTS"
printf '%s\n' "${BLUE}Champions:${NC}  $NUM_CHAMPIONS"
printf '%s\n' "${BLUE}Coding Agent:${NC} $CODING_AGENT"
[[ -n "$MODEL" ]] && printf '%s\n' "${BLUE}Model:${NC}      $MODEL"
[[ -n "$PI_THINKING" ]] && printf '%s\n' "${BLUE}Thinking:${NC}   $PI_THINKING"
[[ -n "$CODEX_REASONING_EFFORT" ]] && printf '%s\n' "${BLUE}Reasoning:${NC}  $CODEX_REASONING_EFFORT"
[[ -n "$OPENCODE_VARIANT" ]] && printf '%s\n' "${BLUE}Variant:${NC}    $OPENCODE_VARIANT"
printf '%s\n' "${BLUE}Run ID:${NC}     $RUN_ID"
echo ""

# Function to run a coding-agent worker session
run_agent() {
    local agent_name="$1"
    local args="$2"
    local context="$3"
    local exit_code=0
    local worker_prompt=""
    local usage_reasoning_effort="$CODEX_REASONING_EFFORT"
    local usage_session_title=""

    case "$agent_name" in
        archetype-creator)
            worker_prompt="scripts/pi-workers/archetype-creator.md"
            ;;
        archetype-implementer)
            worker_prompt="scripts/pi-workers/archetype-implementer.md"
            ;;
        *)
            printf '%s\n' "${RED}Unknown worker: $agent_name${NC}"
            return 1
            ;;
    esac

    if [[ ! -f "$worker_prompt" ]]; then
        printf '%s\n' "${RED}Worker prompt not found: $worker_prompt${NC}"
        return 1
    fi

    printf '%s\n' "${YELLOW}━━━ Running ${CODING_AGENT} worker: ${agent_name} ━━━${NC}"

    local worker_message
    worker_message=$(cat <<EOF
You are running as worker "${agent_name}" for battlecode variant-loop.
Arguments: ${args}
Run metadata: run_id=${RUN_ID}, context=${context:-none}, bot=${BOT}, opponent=${OPPONENT}, map=${MAP}
Follow the attached worker spec exactly.
EOF
)

    local worker_spec
    worker_spec="$(<"$worker_prompt")"

    local full_prompt
    full_prompt=$(cat <<EOF
${worker_spec}

---
Runtime Invocation Context (from orchestrator):
${worker_message}
EOF
)

    local -a agent_cmd=()
    case "$CODING_AGENT" in
        pi)
            agent_cmd=(pi -p --no-session)
            [[ -n "$MODEL" ]] && agent_cmd+=(--model "$MODEL")
            [[ -n "$PI_THINKING" ]] && agent_cmd+=(--thinking "$PI_THINKING")
            agent_cmd+=("@${worker_prompt}" "$worker_message")
            ;;
        claude)
            agent_cmd=(claude -p --dangerously-skip-permissions --output-format json "$full_prompt")
            ;;
        opencode)
            local session_title
            session_title="$(make_opencode_session_title "$agent_name" "$context")"
            usage_session_title="$session_title"
            usage_reasoning_effort="$OPENCODE_VARIANT"
            agent_cmd=(env "OPENCODE_PERMISSION={\"*\":\"allow\"}" opencode run --format json --title "$session_title")
            [[ -n "$MODEL" ]] && agent_cmd+=(--model "$MODEL")
            [[ -n "$OPENCODE_VARIANT" ]] && agent_cmd+=(--variant "$OPENCODE_VARIANT")
            agent_cmd+=("$full_prompt")
            ;;
        codex)
            agent_cmd=(codex exec --json --dangerously-bypass-approvals-and-sandbox)
            [[ -n "$MODEL" ]] && agent_cmd+=(--model "$MODEL")
            [[ -n "$CODEX_REASONING_EFFORT" ]] && agent_cmd+=(-c "model_reasoning_effort=\"$CODEX_REASONING_EFFORT\"")
            agent_cmd+=("$full_prompt")
            ;;
        *)
            printf '%s\n' "${RED}Unsupported coding-agent at runtime: $CODING_AGENT${NC}"
            return 1
            ;;
    esac

    if usage_logging_enabled "$CODING_AGENT"; then
        # Capture JSON output to extract usage stats
        local agent_output
        local start_ms
        local end_ms
        local elapsed_ms
        start_ms=$(python3 -c 'import time; print(int(time.time() * 1000))')
        agent_output=$("${agent_cmd[@]}" 2>&1) || exit_code=$?
        end_ms=$(python3 -c 'import time; print(int(time.time() * 1000))')
        elapsed_ms=$((end_ms - start_ms))

        if [[ $exit_code -ne 0 ]]; then
            printf '%s\n' "${RED}Worker ${agent_name} failed with exit code: $exit_code${NC}"
            printf '%s\n' "$agent_output" >&2
            return $exit_code
        fi

        # Log usage stats
        accumulate_usage "$CODING_AGENT" "$agent_output" "${agent_name}:${context}" "$MODEL" "$usage_reasoning_effort" "$elapsed_ms" "$usage_session_title"
    else
        "${agent_cmd[@]}" || exit_code=$?

        if [[ $exit_code -ne 0 ]]; then
            printf '%s\n' "${RED}Worker ${agent_name} failed with exit code: $exit_code${NC}"
            return $exit_code
        fi
    fi
}

ARCHETYPES_FILE="$STATE_DIR/archetypes.json"

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN LOOP
# ═══════════════════════════════════════════════════════════════════════════════

for iter in $(seq 1 "$MAX_ITERS"); do
    printf '%s\n' "${BOLD}${CYAN}"
    echo "═══════════════════════════════════════════════════════════════════════════════"
    echo "                            ITERATION $iter / $MAX_ITERS"
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
    echo "                            Champions: $NUM_CHAMPIONS"
    fi
    echo "═══════════════════════════════════════════════════════════════════════════════"
    printf '%s\n' "${NC}"

    # ─────────────────────────────────────────────────────────────────────────────
    # Clean slate: remove .state directory from previous iteration
    # (preserving strategy-history.json for feedback loop)
    # ─────────────────────────────────────────────────────────────────────────────
    if [[ -d "$STATE_DIR" ]]; then
        printf '%s\n' "${BLUE}Cleaning .state directory for fresh iteration...${NC}"
        # Back up persistent files before wiping
        STRATEGY_HISTORY_TMP=""
        USAGE_LOG_TMP=""
        if [[ -f "$STRATEGY_HISTORY" ]]; then
            STRATEGY_HISTORY_TMP="/tmp/${BOT}_strategy_history_$$"
            cp "$STRATEGY_HISTORY" "$STRATEGY_HISTORY_TMP"
        fi
        if [[ -f "$USAGE_LOG" ]]; then
            USAGE_LOG_TMP="/tmp/${BOT}_usage_log_$$"
            cp "$USAGE_LOG" "$USAGE_LOG_TMP"
        fi
        rm -rf "$STATE_DIR"
        mkdir -p "$STATE_DIR"
        # Restore persistent files
        if [[ -n "$STRATEGY_HISTORY_TMP" && -f "$STRATEGY_HISTORY_TMP" ]]; then
            mv "$STRATEGY_HISTORY_TMP" "$STRATEGY_HISTORY"
            printf '%s\n' "${BLUE}✓ Restored strategy history from previous iterations${NC}"
        fi
        if [[ -n "$USAGE_LOG_TMP" && -f "$USAGE_LOG_TMP" ]]; then
            mv "$USAGE_LOG_TMP" "$USAGE_LOG"
            printf '%s\n' "${BLUE}✓ Restored usage log from previous iterations${NC}"
        fi
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 0: Generate fresh archetypes for this iteration
    # ─────────────────────────────────────────────────────────────────────────────
    mkdir -p "$STATE_DIR"
    printf '%s\n' "${BOLD}${GREEN}[STEP 0] Generating $NUM_VARIANTS Variant Archetypes${NC}"

    # Prepare context for archetype creator
    {
        echo "# Bot Code"
        echo ""
        for f in src/"$BOT"/*.java; do
            if [[ -f "$f" ]]; then
                echo "=== FILE: $(basename "$f") ==="
                cat "$f"
                echo ""
            fi
        done
    } > "$STATE_DIR/bot-code-snapshot.txt"

    run_agent "archetype-creator" "--bot $BOT --opponent $OPPONENT --map $MAP --num-variants $NUM_VARIANTS" "iter:${iter}:phase0"

    if [[ ! -f "$ARCHETYPES_FILE" ]]; then
        printf '%s\n' "${RED}Error: Archetypes file not created at $ARCHETYPES_FILE${NC}"
        exit 1
    fi
    printf '%s\n' "${GREEN}✓ Archetypes generated${NC}"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 1: Create variant folders
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 1] Creating $NUM_VARIANTS variant folders${NC}"
    ./scripts/create-16-variants.sh "$BOT" "$NUM_VARIANTS"

    # Copy bot-code-snapshot.txt to each variant's .state folder
    for v in $(seq 1 $NUM_VARIANTS); do
        VARIANT_STATE_DIR="src/${BOT}_v${v}/.state"
        mkdir -p "$VARIANT_STATE_DIR"
        cp "$STATE_DIR/bot-code-snapshot.txt" "$VARIANT_STATE_DIR/"
    done
    printf '%s\n' "${BLUE}✓ Copied bot-code-snapshot.txt to all variant .state folders${NC}"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 2: Implement each archetype (fresh coding-agent worker per variant)
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 2] Implementing archetypes into variants (2 at a time)${NC}"

    PARALLEL=2
    for batch_start in $(seq 1 $PARALLEL $NUM_VARIANTS); do
        batch_end=$((batch_start + PARALLEL - 1))
        if [[ $batch_end -gt $NUM_VARIANTS ]]; then
            batch_end=$NUM_VARIANTS
        fi

        PIDS=()
        BATCH_VARIANTS=()

        for v in $(seq $batch_start $batch_end); do
            printf '%s\n' "${YELLOW}━━━ Launching Variant $v / $NUM_VARIANTS ━━━${NC}"

            # Extract this variant's archetype from JSON
            ARCHETYPE=$(python3 -c "
import json
with open('$ARCHETYPES_FILE', 'r') as f:
    data = json.load(f)
archetypes = data.get('archetypes', data)
if isinstance(archetypes, list):
    print(json.dumps(archetypes[$v - 1]))
else:
    print(json.dumps(archetypes.get('v$v', {})))
" 2>/dev/null || echo "{}")

            # Save archetype to variant-specific state dir to avoid race conditions
            VARIANT_STATE_DIR="src/${BOT}_v${v}/.state"
            mkdir -p "$VARIANT_STATE_DIR"
            echo "$ARCHETYPE" > "$VARIANT_STATE_DIR/current-archetype.json"
            # Also keep a copy in the main state dir for the worker to find
            echo "$ARCHETYPE" > "$STATE_DIR/current-archetype-v${v}.json"

            # Run worker in background
            (
                run_agent "archetype-implementer" "--bot $BOT --variant $v --opponent $OPPONENT" "iter:${iter}:v${v}"
            ) &
            PIDS+=($!)
            BATCH_VARIANTS+=($v)
        done

        printf '%s\n' "${BLUE}Waiting for variants ${BATCH_VARIANTS[*]} to complete...${NC}"

        # Wait for all workers in this batch
        BATCH_FAILED=0
        for i in "${!PIDS[@]}"; do
            pid=${PIDS[$i]}
            v=${BATCH_VARIANTS[$i]}
            if ! wait "$pid"; then
                printf '%s\n' "${RED}Warning: Variant $v worker exited with error${NC}"
                BATCH_FAILED=$((BATCH_FAILED + 1))
            fi
        done

        # Verify compilation for this batch
        for v in "${BATCH_VARIANTS[@]}"; do
            printf '%s\n' "${BLUE}Verifying compilation for ${BOT}_v${v}...${NC}"
            if ! ./gradlew compileJava -q 2>&1 | tail -5; then
                printf '%s\n' "${RED}Warning: Compilation may have issues${NC}"
            fi
        done

        printf '%s\n' "${GREEN}Batch (variants ${BATCH_VARIANTS[*]}) complete${NC}"
    done
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 3: Run all variants against opponent
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 3] Running all variants against $OPPONENT${NC}"
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
        printf '%s\n' "${BLUE}  (also playing against $NUM_CHAMPIONS champion(s))${NC}"
    fi
    ./scripts/run-all-variants.sh "$BOT" "$OPPONENT" "$MAP" "$NUM_CHAMPIONS"
    echo ""

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 4: Rank results, promote winner, cleanup
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BOLD}${GREEN}[STEP 4] Ranking results and promoting winner${NC}"
    ./scripts/rank-variants.sh "$BOT" "$OPPONENT" "$MAP" "$NUM_CHAMPIONS"

    # Read results
    RESULTS_FILE="$STATE_DIR/variant-results.json"
    if [[ ! -f "$RESULTS_FILE" ]]; then
        printf '%s\n' "${RED}Error: Results file not found${NC}"
        exit 1
    fi

    # Parse winner info
    WINNER=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('winner', 'original'))
")

    WINNER_SCORE=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('winner_score', 0))
")

    GOAL_MET=$(python3 -c "
import json
with open('$RESULTS_FILE', 'r') as f:
    data = json.load(f)
print(data.get('goal_met', 'NO'))
")

    # Recount champions (a new one may have been saved)
    NUM_CHAMPIONS=0
    while [[ -d "src/${BOT}_champion_${NUM_CHAMPIONS}" ]]; do
        NUM_CHAMPIONS=$((NUM_CHAMPIONS + 1))
    done

    echo ""
    printf '%s\n' "${CYAN}Winner: $WINNER (Score: $WINNER_SCORE)${NC}"
    if [[ $NUM_CHAMPIONS -gt 0 ]]; then
        printf '%s\n' "${CYAN}Champions: $NUM_CHAMPIONS${NC}"
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 4b: Update strategy history with this iteration's results
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BLUE}Updating strategy history...${NC}"
    python3 << 'HISTORY_EOF' - "$STATE_DIR" "$iter"
import json
import os
import sys

state_dir = sys.argv[1]
iteration = int(sys.argv[2])

archetypes_file = os.path.join(state_dir, "archetypes.json")
results_file = os.path.join(state_dir, "variant-results.json")
history_file = os.path.join(state_dir, "strategy-history.json")

# Load archetypes
archetypes = []
if os.path.exists(archetypes_file):
    with open(archetypes_file) as f:
        data = json.load(f)
    archetypes = data.get("archetypes", data)
    if not isinstance(archetypes, list):
        archetypes = []

# Load results
results_data = {}
if os.path.exists(results_file):
    with open(results_file) as f:
        results_data = json.load(f)

# Build archetype lookup: v1 -> archetype[0], v2 -> archetype[1], etc.
arch_lookup = {}
for i, arch in enumerate(archetypes):
    arch_lookup[f"v{i+1}"] = arch


def generate_post_mortem(r):
    """Generate a concise tactical summary from match results."""
    primary_won = r.get("primary_won", False)
    rounds = r.get("primary_rounds", 3000)

    matches = r.get("matches", [])
    primary_match = next(
        (m for m in matches if m.get("opponent_label") == "opponent"), {}
    )
    kills = primary_match.get("enemy_kills", 0)
    vp = primary_match.get("victory_points", 0)

    # Build outcome phrase
    if primary_won:
        if rounds <= 1500:
            outcome = f"Won fast ({rounds} rounds)"
        elif rounds <= 2500:
            outcome = f"Won ({rounds} rounds)"
        else:
            outcome = f"Won slowly ({rounds} rounds)"
    else:
        outcome = f"Lost ({rounds} rounds)"

    # Add key stats
    stats = []
    if kills > 0:
        stats.append(f"{kills} kills")
    if vp > 0:
        stats.append(f"{vp} VP")
    if stats:
        outcome += f" [{', '.join(stats)}]"

    # Add tactical insight
    if primary_won and rounds <= 1500:
        insight = "Effective fast strategy."
    elif primary_won and rounds > 2500:
        insight = "Won but too slowly; needs faster execution."
    elif primary_won:
        insight = "Solid win; push for faster finish."
    elif not primary_won and kills >= 5:
        insight = "Good combat but couldn't close."
    elif not primary_won and vp >= 500:
        insight = "Strong VP progress but fell short."
    elif not primary_won and vp >= 200:
        insight = "Some VP progress; not enough to win."
    else:
        insight = "Strategy ineffective."

    return f"{outcome}. {insight}"


# Build history entry with archetype info + results
variants = []
for r in results_data.get("results", []):
    name = r["name"]
    arch = arch_lookup.get(name, {})

    entry = {
        "id": name,
        "score": r.get("total_score", 0),
        "primary_won": r.get("primary_won", False),
        "primary_rounds": r.get("primary_rounds", 3000),
        "post_mortem": generate_post_mortem(r),
    }

    if arch:
        entry["archetype_name"] = arch.get("name", "Unknown")
        entry["type"] = arch.get("type", "exploration")
        entry["win_condition"] = arch.get("win_condition", "unknown")
        entry["philosophy"] = arch.get("philosophy", "")
    elif name == "original":
        entry["archetype_name"] = "Original (baseline)"
        entry["type"] = "baseline"

    variants.append(entry)

history_entry = {
    "iteration": iteration,
    "winner": results_data.get("winner", "original"),
    "winner_score": results_data.get("winner_score", 0),
    "promoted": results_data.get("should_promote", False),
    "variants": variants,
}

# Load existing history or create new
history = {"iterations": []}
if os.path.exists(history_file):
    try:
        with open(history_file) as f:
            history = json.load(f)
    except (json.JSONDecodeError, IOError):
        history = {"iterations": []}

history["iterations"].append(history_entry)

with open(history_file, "w") as f:
    json.dump(history, f, indent=2)

n = len(history["iterations"])
print(f"Strategy history updated: {n} iteration(s) recorded")
HISTORY_EOF

    # ─────────────────────────────────────────────────────────────────────────────
    # Usage summary (agents with usage logging)
    # ─────────────────────────────────────────────────────────────────────────────
    if usage_logging_enabled "$CODING_AGENT"; then
        print_usage_summary
    fi

    # ─────────────────────────────────────────────────────────────────────────────
    # Step 5: Copy current bot to copy_bot for next iteration's opponent
    # ─────────────────────────────────────────────────────────────────────────────
    printf '%s\n' "${BLUE}Copying $BOT to copy_bot...${NC}"
    bash "$(cd "$(dirname "$0")" && pwd)/copy_bot.sh" "src/$BOT"

    # Stop Gradle daemon to free heap memory (~200-500MB) between iterations
    printf '%s\n' "${BLUE}Stopping Gradle daemon to free memory...${NC}"
    ./gradlew --stop 2>/dev/null || true

    printf '%s\n' "${BLUE}Iteration $iter complete. Continuing...${NC}"
    echo ""
done

# ═══════════════════════════════════════════════════════════════════════════════
# ALL ITERATIONS COMPLETE
# ═══════════════════════════════════════════════════════════════════════════════

printf '%s\n' "${BOLD}${GREEN}"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                  ALL $MAX_ITERS ITERATIONS COMPLETE"
echo "═══════════════════════════════════════════════════════════════════════════════"
printf '%s\n' "${NC}"

echo "Best result saved in src/$BOT/"

# Final cumulative usage summary
if usage_logging_enabled "$CODING_AGENT" && [[ -f "$USAGE_LOG" ]]; then
    printf '\n%s\n' "${BOLD}${CYAN}CUMULATIVE USAGE (all iterations)${NC}"
    print_usage_summary
fi
exit 0
