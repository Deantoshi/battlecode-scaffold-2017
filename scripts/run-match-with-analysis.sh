#!/bin/bash
# run-match-with-analysis.sh - Run a match and get consolidated LLM-friendly analysis
#
# Usage: ./scripts/run-match-with-analysis.sh <bot> <opponent> [map] [options]
#
# Options:
#   --round=N         Query unit positions at specific round (default: final)
#   --include-trees   Include trees in unit position output
#   --query-all       Query all matches in matches/*.db instead of just this match
#
# Examples:
#   ./scripts/run-match-with-analysis.sh mybot examplefuncsplayer MagicWood
#   ./scripts/run-match-with-analysis.sh mybot opponent Arena --round=500

set -e

# Parse arguments
BOT=""
OPPONENT=""
MAP="MagicWood"
ROUND_OPT=""
INCLUDE_TREES=""
QUERY_ALL=false

for arg in "$@"; do
    case "$arg" in
        --round=*)
            ROUND_OPT="$arg"
            ;;
        --include-trees)
            INCLUDE_TREES="--include-trees"
            ;;
        --query-all)
            QUERY_ALL=true
            ;;
        *)
            if [[ -z "$BOT" ]]; then
                BOT="$arg"
            elif [[ -z "$OPPONENT" ]]; then
                OPPONENT="$arg"
            else
                MAP="$arg"
            fi
            ;;
    esac
done

if [[ -z "$BOT" || -z "$OPPONENT" ]]; then
    echo "Usage: $0 <bot> <opponent> [map] [options]"
    echo ""
    echo "Options:"
    echo "  --round=N         Query unit positions at specific round"
    echo "  --include-trees   Include trees in unit position output"
    echo "  --query-all       Query all matches in matches/*.db"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MATCH_FILE="$PROJECT_DIR/matches/${BOT}-vs-${OPPONENT}-on-${MAP}.bc17"
DB_FILE="${MATCH_FILE%.bc17}.db"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "MATCH: $BOT (A) vs $OPPONENT (B) on $MAP"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Clean previous match
rm -f "$MATCH_FILE" "$DB_FILE"

# Run match
cd "$PROJECT_DIR"
./gradlew run -PteamA="$BOT" -PteamB="$OPPONENT" -Pmaps="$MAP" --quiet 2>&1 | grep -E "^\[server\]"

# Check if match file exists
if [[ ! -f "$MATCH_FILE" ]]; then
    echo "ERROR: Match file not created: $MATCH_FILE"
    exit 1
fi

# Extract to database
python3 "$SCRIPT_DIR/bc17_query.py" extract "$MATCH_FILE" "$DB_FILE" > /dev/null

# Get winner and total rounds
WINNER=$(sqlite3 "$DB_FILE" "SELECT value FROM metadata WHERE key='winner'" 2>/dev/null || echo "UNKNOWN")
TOTAL_ROUNDS=$(sqlite3 "$DB_FILE" "SELECT MAX(round_id) FROM rounds" 2>/dev/null || echo "0")

echo ""
echo "───────────────────────────────────────────────────────────────────────────────"
echo "RESULT"
echo "───────────────────────────────────────────────────────────────────────────────"

# Determine if our bot won (we are Team A)
if [[ "$WINNER" == "A" ]]; then
    WON="YES"
    OUTCOME="WIN"
else
    WON="NO"
    OUTCOME="LOSS"
fi

# Get final state
FINAL_DATA=$(sqlite3 "$DB_FILE" "
SELECT
    team_a_bullets, team_b_bullets, team_a_vp, team_b_vp
FROM rounds ORDER BY round_id DESC LIMIT 1
" 2>/dev/null)

A_BULLETS=$(echo "$FINAL_DATA" | cut -d'|' -f1 | xargs printf "%.0f")
B_BULLETS=$(echo "$FINAL_DATA" | cut -d'|' -f2 | xargs printf "%.0f")
A_VP=$(echo "$FINAL_DATA" | cut -d'|' -f3)
B_VP=$(echo "$FINAL_DATA" | cut -d'|' -f4)

if [[ "$WON" == "YES" && "$TOTAL_ROUNDS" -le 1500 ]] || [[ "$A_VP" -ge 1000 ]]; then
    GOAL_MET="YES"
else
    GOAL_MET="NO"
fi

echo "OUTCOME=$OUTCOME  ROUNDS=$TOTAL_ROUNDS  TARGET=1500  GOAL_MET=$GOAL_MET"
echo "FINAL: A=${A_BULLETS}bullets/${A_VP}vp  B=${B_BULLETS}bullets/${B_VP}vp"

echo ""
echo "───────────────────────────────────────────────────────────────────────────────"
echo "UNIT SUMMARY (Produced / Lost / Alive)"
echo "───────────────────────────────────────────────────────────────────────────────"

# Consolidated unit table: produced, lost, alive for both teams (including trees)
sqlite3 -header -column "$DB_FILE" "
WITH produced AS (
    SELECT team,
           CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END as body_type,
           COUNT(*) as prod
    FROM events WHERE event_type='spawn'
      AND body_type NOT IN ('TREE_NEUTRAL', 'BULLET', 'NONE')
    GROUP BY team, CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END
),
lost AS (
    SELECT team,
           CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END as body_type,
           COUNT(*) as died
    FROM events WHERE event_type='death'
      AND body_type NOT IN ('TREE_NEUTRAL', 'BULLET', 'NONE')
    GROUP BY team, CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END
),
alive AS (
    SELECT team,
           CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END as body_type,
           COUNT(*) as alive
    FROM robots WHERE death_round IS NULL
      AND body_type NOT IN ('TREE_NEUTRAL', 'BULLET', 'NONE')
    GROUP BY team, CASE WHEN body_type='TREE_BULLET' THEN 'TREE' ELSE body_type END
)
SELECT
    COALESCE(p.team, l.team, a.team) as Team,
    COALESCE(p.body_type, l.body_type, a.body_type) as Unit,
    COALESCE(p.prod, 0) as Prod,
    COALESCE(l.died, 0) as Lost,
    COALESCE(a.alive, 0) as Alive
FROM produced p
LEFT JOIN lost l ON p.team=l.team AND p.body_type=l.body_type
LEFT JOIN alive a ON p.team=a.team AND p.body_type=a.body_type
ORDER BY Team,
    CASE COALESCE(p.body_type, l.body_type, a.body_type)
        WHEN 'ARCHON' THEN 1
        WHEN 'GARDENER' THEN 2
        WHEN 'SOLDIER' THEN 3
        WHEN 'LUMBERJACK' THEN 4
        WHEN 'TANK' THEN 5
        WHEN 'SCOUT' THEN 6
        WHEN 'TREE' THEN 7
        ELSE 8
    END
" 2>/dev/null

echo ""
echo "───────────────────────────────────────────────────────────────────────────────"
echo "ECONOMY TIMELINE (bullets | vp)"
echo "───────────────────────────────────────────────────────────────────────────────"

sqlite3 "$DB_FILE" "
SELECT
    printf('R%-4d', round_id) || ' | ' ||
    'A: ' || printf('%4.0f', team_a_bullets) || '/' || printf('%-3d', team_a_vp) ||
    '  B: ' || printf('%4.0f', team_b_bullets) || '/' || printf('%-3d', team_b_vp)
FROM rounds
WHERE round_id % 500 = 0 OR round_id = (SELECT MAX(round_id) FROM rounds)
ORDER BY round_id
" 2>/dev/null

echo ""
echo "───────────────────────────────────────────────────────────────────────────────"
echo "COMBAT TIMELINE (deaths by period)"
echo "───────────────────────────────────────────────────────────────────────────────"

# Compact death timeline
sqlite3 "$DB_FILE" "
SELECT
    CASE
        WHEN round_id <= 500 THEN 'R1-500'
        WHEN round_id <= 1000 THEN 'R501-1000'
        WHEN round_id <= 1500 THEN 'R1001-1500'
        WHEN round_id <= 2000 THEN 'R1501-2000'
        ELSE 'R2001+'
    END as Period,
    'A:' || SUM(CASE WHEN team='A' THEN 1 ELSE 0 END) || ' B:' || SUM(CASE WHEN team='B' THEN 1 ELSE 0 END) as Deaths
FROM events
WHERE event_type='death'
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY Period
ORDER BY Period
" 2>/dev/null || echo "(no combat deaths)"

echo ""
echo "───────────────────────────────────────────────────────────────────────────────"
echo "MOVEMENT ANALYSIS (unit distribution changes)"
echo "───────────────────────────────────────────────────────────────────────────────"

# LLM-friendly movement analysis instead of giant table
# Compare early vs late snapshots to detect stuck units
python3 - "$DB_FILE" <<'PYEOF'
import sqlite3
import sys

db_path = sys.argv[1]
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row

# Get first and last snapshot rounds
snapshots = conn.execute("SELECT round_id FROM snapshots ORDER BY round_id").fetchall()
if len(snapshots) < 2:
    print("Insufficient snapshots for movement analysis")
    sys.exit(0)

early_round = snapshots[min(2, len(snapshots)-1)]['round_id']  # ~round 300
late_round = snapshots[-1]['round_id']

# Get quadrant data for both teams at early and late rounds
def get_quadrant_summary(round_id, team):
    rows = conn.execute("""
        SELECT quadrant, body_type, count
        FROM unit_quadrants
        WHERE round_id=? AND team=? AND body_type NOT IN ('TREE_BULLET','TREE_NEUTRAL')
        ORDER BY quadrant, body_type
    """, (round_id, team)).fetchall()

    by_quadrant = {'NW': {}, 'NE': {}, 'SW': {}, 'SE': {}}
    for r in rows:
        if r['count'] > 0:
            by_quadrant[r['quadrant']][r['body_type']] = r['count']
    return by_quadrant

def summarize_distribution(data):
    """Generate LLM-friendly summary of unit distribution"""
    total_by_quad = {q: sum(units.values()) for q, units in data.items()}
    total = sum(total_by_quad.values())

    if total == 0:
        return "No units tracked"

    # Find dominant quadrant(s)
    dominant = [(q, c) for q, c in total_by_quad.items() if c > 0]
    dominant.sort(key=lambda x: -x[1])

    # Check for concentration (potential stuck units)
    if len(dominant) == 1 or (len(dominant) > 1 and dominant[0][1] > total * 0.8):
        quad = dominant[0][0]
        units_str = ', '.join(f"{c} {t}" for t, c in data[quad].items())
        return f"CONCENTRATED in {quad} ({units_str})"
    elif len(dominant) >= 2:
        spread = ', '.join(f"{q}:{c}" for q, c in dominant if c > 0)
        return f"Spread across quadrants ({spread})"

    return f"{total} units"

def detect_stuck_units(early, late, team_name):
    """Detect units that haven't moved between snapshots"""
    stuck = []
    for quad in ['NW', 'NE', 'SW', 'SE']:
        early_units = early.get(quad, {})
        late_units = late.get(quad, {})
        for unit_type in set(early_units.keys()) | set(late_units.keys()):
            early_count = early_units.get(unit_type, 0)
            late_count = late_units.get(unit_type, 0)
            # If same or more units in same quadrant, likely stuck
            if early_count > 0 and late_count >= early_count:
                stuck.append(f"{late_count} {unit_type} in {quad}")

    if stuck:
        return f"{team_name} POTENTIAL STUCK: {', '.join(stuck)}"
    return None

print(f"Comparing R{early_round} vs R{late_round}:")
print()

for team, team_name in [('A', 'Team A'), ('B', 'Team B')]:
    early = get_quadrant_summary(early_round, team)
    late = get_quadrant_summary(late_round, team)

    print(f"  {team_name} at R{early_round}: {summarize_distribution(early)}")
    print(f"  {team_name} at R{late_round}: {summarize_distribution(late)}")

    stuck_msg = detect_stuck_units(early, late, team_name)
    if stuck_msg:
        print(f"  ⚠ {stuck_msg}")
    print()

conn.close()
PYEOF

echo "───────────────────────────────────────────────────────────────────────────────"
echo "VP ACTIVITY"
echo "───────────────────────────────────────────────────────────────────────────────"

DONATE_COUNT=$(sqlite3 "$DB_FILE" "SELECT COUNT(*) FROM events WHERE event_type='donate'" 2>/dev/null || echo "0")
if [[ "$DONATE_COUNT" -gt 0 ]]; then
    sqlite3 "$DB_FILE" "
    SELECT 'Team ' || team || ': ' || COUNT(*) || ' donations, ' ||
           COALESCE(SUM(json_extract(details, '\$.vp_gain')), 0) || ' VP gained'
    FROM events WHERE event_type='donate'
    GROUP BY team
    " 2>/dev/null
else
    echo "(No VP donations)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "Database: $DB_FILE"
