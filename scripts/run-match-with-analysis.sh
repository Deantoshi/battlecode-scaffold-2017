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
./gradlew runWithSummary -PteamA="$BOT" -PteamB="$OPPONENT" -Pmaps="$MAP" --quiet 2>&1 | grep -E "^\[server\]"

# Check if match file exists
if [[ ! -f "$MATCH_FILE" ]]; then
    echo "ERROR: Match file not created: $MATCH_FILE"
    exit 1
fi

# Extract to database
python3 "$SCRIPT_DIR/bc17_query.py" extract "$MATCH_FILE" "$DB_FILE" > /dev/null

# Use Python for all database queries (no sqlite3 CLI dependency)
python3 - "$DB_FILE" <<'PYEOF'
import sqlite3
import sys
import json

db_path = sys.argv[1]
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row

# Get winner and total rounds
winner = conn.execute("SELECT value FROM metadata WHERE key='winner'").fetchone()
winner = winner['value'] if winner else 'UNKNOWN'

total_rounds_row = conn.execute("SELECT MAX(round_id) as max_round FROM rounds").fetchone()
total_rounds = total_rounds_row['max_round'] if total_rounds_row else 0

print()
print("───────────────────────────────────────────────────────────────────────────────")
print("RESULT")
print("───────────────────────────────────────────────────────────────────────────────")

# Determine if our bot won (we are Team A)
won = "YES" if winner == "A" else "NO"
outcome = "WIN" if winner == "A" else "LOSS"

# Get final state
final_data = conn.execute("""
    SELECT team_a_bullets, team_b_bullets, team_a_vp, team_b_vp
    FROM rounds ORDER BY round_id DESC LIMIT 1
""").fetchone()

if final_data:
    a_bullets = int(final_data['team_a_bullets'])
    b_bullets = int(final_data['team_b_bullets'])
    a_vp = final_data['team_a_vp']
    b_vp = final_data['team_b_vp']
else:
    a_bullets = b_bullets = a_vp = b_vp = 0

if (won == "YES" and total_rounds <= 1500) or a_vp >= 1000:
    goal_met = "YES"
else:
    goal_met = "NO"

print(f"OUTCOME={outcome}  ROUNDS={total_rounds}  TARGET=1500  GOAL_MET={goal_met}")
print(f"FINAL: A={a_bullets}bullets/{a_vp}vp  B={b_bullets}bullets/{b_vp}vp")

# UNIT SUMMARY
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("UNIT SUMMARY (Produced / Lost / Alive)")
print("───────────────────────────────────────────────────────────────────────────────")

unit_data = conn.execute("""
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
""").fetchall()

# Print as formatted table
print(f"{'Team':<6} {'Unit':<12} {'Prod':<6} {'Lost':<6} {'Alive':<6}")
print("-" * 40)
for row in unit_data:
    print(f"{row['Team']:<6} {row['Unit']:<12} {row['Prod']:<6} {row['Lost']:<6} {row['Alive']:<6}")

# ECONOMY TIMELINE
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("ECONOMY TIMELINE (current bullets/vp | cumulative generated/spent)")
print("───────────────────────────────────────────────────────────────────────────────")

econ_data = conn.execute("""
WITH cumulative AS (
    SELECT
        round_id,
        SUM(team_a_bullets_generated) OVER (ORDER BY round_id) as a_gen,
        SUM(team_a_bullets_spent) OVER (ORDER BY round_id) as a_spent,
        SUM(team_b_bullets_generated) OVER (ORDER BY round_id) as b_gen,
        SUM(team_b_bullets_spent) OVER (ORDER BY round_id) as b_spent
    FROM snapshots
)
SELECT
    r.round_id,
    r.team_a_bullets, r.team_a_vp,
    r.team_b_bullets, r.team_b_vp,
    COALESCE(c.a_gen, 0) as a_gen,
    COALESCE(c.a_spent, 0) as a_spent,
    COALESCE(c.b_gen, 0) as b_gen,
    COALESCE(c.b_spent, 0) as b_spent
FROM rounds r
LEFT JOIN cumulative c ON r.round_id = c.round_id
WHERE r.round_id % 500 = 0 OR r.round_id = (SELECT MAX(round_id) FROM rounds)
ORDER BY r.round_id
""").fetchall()

for row in econ_data:
    line = f"R{row['round_id']:<4} | "
    line += f"A: {int(row['team_a_bullets']):>3}/{row['team_a_vp']:<3} "
    line += f"(gen:{int(row['a_gen']):>4} spent:{int(row['a_spent']):>4})"
    line += f"  B: {int(row['team_b_bullets']):>3}/{row['team_b_vp']:<3} "
    line += f"(gen:{int(row['b_gen']):>4} spent:{int(row['b_spent']):>4})"
    print(line)

# COMBAT TIMELINE
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("COMBAT TIMELINE (deaths by period)")
print("───────────────────────────────────────────────────────────────────────────────")

combat_data = conn.execute("""
SELECT
    CASE
        WHEN round_id <= 500 THEN 'R1-500'
        WHEN round_id <= 1000 THEN 'R501-1000'
        WHEN round_id <= 1500 THEN 'R1001-1500'
        WHEN round_id <= 2000 THEN 'R1501-2000'
        ELSE 'R2001+'
    END as Period,
    SUM(CASE WHEN team='A' THEN 1 ELSE 0 END) as A_Deaths,
    SUM(CASE WHEN team='B' THEN 1 ELSE 0 END) as B_Deaths
FROM events
WHERE event_type='death'
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY Period
ORDER BY Period
""").fetchall()

if combat_data:
    for row in combat_data:
        print(f"{row['Period']}: A:{row['A_Deaths']} B:{row['B_Deaths']}")
else:
    print("(no combat deaths)")

# MOVEMENT ANALYSIS
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("MOVEMENT ANALYSIS (unit distribution changes)")
print("───────────────────────────────────────────────────────────────────────────────")

# Get first and last snapshot rounds
snapshots = conn.execute("SELECT round_id FROM snapshots ORDER BY round_id").fetchall()
if len(snapshots) >= 2:
    early_round = snapshots[min(2, len(snapshots)-1)]['round_id']
    late_round = snapshots[-1]['round_id']

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
        total_by_quad = {q: sum(units.values()) for q, units in data.items()}
        total = sum(total_by_quad.values())

        if total == 0:
            return "No units tracked"

        dominant = [(q, c) for q, c in total_by_quad.items() if c > 0]
        dominant.sort(key=lambda x: -x[1])

        if len(dominant) == 1 or (len(dominant) > 1 and dominant[0][1] > total * 0.8):
            quad = dominant[0][0]
            units_str = ', '.join(f"{c} {t}" for t, c in data[quad].items())
            return f"CONCENTRATED in {quad} ({units_str})"
        elif len(dominant) >= 2:
            spread = ', '.join(f"{q}:{c}" for q, c in dominant if c > 0)
            return f"Spread across quadrants ({spread})"

        return f"{total} units"

    def detect_stuck_units(early, late, team_name):
        stuck = []
        for quad in ['NW', 'NE', 'SW', 'SE']:
            early_units = early.get(quad, {})
            late_units = late.get(quad, {})
            for unit_type in set(early_units.keys()) | set(late_units.keys()):
                early_count = early_units.get(unit_type, 0)
                late_count = late_units.get(unit_type, 0)
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
else:
    print("Insufficient snapshots for movement analysis")

# VP ACTIVITY
print("───────────────────────────────────────────────────────────────────────────────")
print("VP ACTIVITY")
print("───────────────────────────────────────────────────────────────────────────────")

donate_count = conn.execute("SELECT COUNT(*) as cnt FROM events WHERE event_type='donate'").fetchone()['cnt']
if donate_count > 0:
    vp_data = conn.execute("""
        SELECT team, COUNT(*) as donations,
               COALESCE(SUM(json_extract(details, '$.vp_gain')), 0) as vp_gained
        FROM events WHERE event_type='donate'
        GROUP BY team
    """).fetchall()
    for row in vp_data:
        print(f"Team {row['team']}: {row['donations']} donations, {row['vp_gained']} VP gained")
else:
    print("(No VP donations)")

# BUILD ORDER
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("BUILD ORDER (first 15 units per team)")
print("───────────────────────────────────────────────────────────────────────────────")

for t, team_name in [('A', 'Team A'), ('B', 'Team B')]:
    rows = conn.execute("""
        SELECT sequence, round_id, body_type
        FROM build_order
        WHERE team = ?
        ORDER BY sequence
        LIMIT 15
    """, (t,)).fetchall()

    if rows:
        units = [f"R{r['round_id']}:{r['body_type']}" for r in rows]
        print(f"{team_name}: {', '.join(units)}")

        # Key milestones
        first_soldier = next((r for r in rows if r['body_type'] == 'SOLDIER'), None)
        first_lj = next((r for r in rows if r['body_type'] == 'LUMBERJACK'), None)
        milestones = []
        if first_soldier:
            milestones.append(f"1st SOLDIER R{first_soldier['round_id']}")
        if first_lj:
            milestones.append(f"1st LUMBERJACK R{first_lj['round_id']}")
        if milestones:
            print(f"  Milestones: {', '.join(milestones)}")
    else:
        print(f"{team_name}: (no build order data)")

# COMBAT ANALYSIS
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("COMBAT ANALYSIS (shots, efficiency, friendly fire)")
print("───────────────────────────────────────────────────────────────────────────────")

# Combat timing
first_combat = conn.execute("SELECT value FROM metadata WHERE key='first_combat_round'").fetchone()
last_combat = conn.execute("SELECT value FROM metadata WHERE key='last_combat_round'").fetchone()
first_combat = first_combat['value'] if first_combat else 'N/A'
last_combat = last_combat['value'] if last_combat else 'N/A'
print(f"Combat Duration: R{first_combat} - R{last_combat}")
print()

# Shot breakdown
combat_rows = conn.execute("SELECT * FROM combat_summary").fetchall()
if combat_rows:
    stats = {'A': None, 'B': None}
    for row in combat_rows:
        stats[row['team']] = row

    a = stats.get('A')
    b = stats.get('B')

    if a and b:
        print(f"{'Metric':<20} {'Team A':>10} {'Team B':>10}")
        print("-" * 42)
        print(f"{'Single shots':<20} {a['shots_single']:>10} {b['shots_single']:>10}")
        print(f"{'Triad shots (3b)':<20} {a['shots_triad']:>10} {b['shots_triad']:>10}")
        print(f"{'Pentad shots (5b)':<20} {a['shots_pentad']:>10} {b['shots_pentad']:>10}")
        print(f"{'Lumberjack strikes':<20} {a['lumberjack_strikes']:>10} {b['lumberjack_strikes']:>10}")
        print(f"{'Tree chops':<20} {a['chops']:>10} {b['chops']:>10}")
        print(f"{'BULLET COST':<20} {a['total_bullet_cost']:>10} {b['total_bullet_cost']:>10}")
        print()
        print(f"{'Enemy kills':<20} {a['enemy_kills']:>10} {b['enemy_kills']:>10}")
        print(f"{'Own deaths':<20} {a['total_deaths']:>10} {b['total_deaths']:>10}")
        kd_a = a['enemy_kills'] / a['total_deaths'] if a['total_deaths'] > 0 else 0
        kd_b = b['enemy_kills'] / b['total_deaths'] if b['total_deaths'] > 0 else 0
        print(f"{'K/D ratio':<20} {kd_a:>10.2f} {kd_b:>10.2f}")
        print(f"{'Bullets/kill':<20} {a['bullets_per_kill']:>10.1f} {b['bullets_per_kill']:>10.1f}")

        # Efficiency warnings
        if a['bullets_per_kill'] > 0 and b['bullets_per_kill'] > 0:
            if a['bullets_per_kill'] > b['bullets_per_kill'] * 2:
                print(f"\n⚠ Team A inefficient: {a['bullets_per_kill']:.1f} bullets/kill vs B's {b['bullets_per_kill']:.1f}")
            elif b['bullets_per_kill'] > a['bullets_per_kill'] * 2:
                print(f"\n⚠ Team B inefficient: {b['bullets_per_kill']:.1f} bullets/kill vs A's {a['bullets_per_kill']:.1f}")
else:
    print("(No combat data)")

# SHOTS BY UNIT TYPE
print()
print("Shots by Unit Type:")
print("-" * 70)

unit_shot_data = conn.execute("""
    SELECT
        team,
        json_extract(details, '$.robot_type') as unit_type,
        SUM(CASE WHEN json_extract(details, '$.shot_type') = 'single' THEN 1 ELSE 0 END) as single_shots,
        SUM(CASE WHEN json_extract(details, '$.shot_type') = 'triad' THEN 1 ELSE 0 END) as triad_shots,
        SUM(CASE WHEN json_extract(details, '$.shot_type') = 'pentad' THEN 1 ELSE 0 END) as pentad_shots,
        COUNT(*) as total_shots,
        SUM(json_extract(details, '$.cost')) as bullet_cost
    FROM events
    WHERE event_type = 'shoot'
    GROUP BY team, unit_type
    ORDER BY team, bullet_cost DESC
""").fetchall()

if unit_shot_data:
    current_team = None
    for row in unit_shot_data:
        if row['team'] != current_team:
            if current_team is not None:
                print()
            current_team = row['team']
            print(f"Team {current_team}:")
            print(f"  {'Unit':<12} {'Single':>8} {'Triad':>8} {'Pentad':>8} {'Total':>8} {'Bullets':>10}")

        unit_name = row['unit_type'] or 'UNKNOWN'
        print(f"  {unit_name:<12} {row['single_shots']:>8} {row['triad_shots']:>8} {row['pentad_shots']:>8} {row['total_shots']:>8} {int(row['bullet_cost']):>10}")
else:
    print("(No shot data by unit type)")

# Friendly fire suspects
ff_rows = conn.execute("""
    SELECT team, COUNT(*) as count
    FROM friendly_fire_suspects
    GROUP BY team
""").fetchall()

if ff_rows:
    print()
    print("Potential Friendly Fire:")
    for row in ff_rows:
        print(f"  Team {row['team']}: {row['count']} incidents")
        incidents = conn.execute("""
            SELECT round_id, dead_unit_type, own_attacks
            FROM friendly_fire_suspects
            WHERE team = ?
            ORDER BY round_id
            LIMIT 3
        """, (row['team'],)).fetchall()
        for inc in incidents:
            attacks = json.loads(inc['own_attacks']) if inc['own_attacks'] else []
            print(f"    R{inc['round_id']}: {inc['dead_unit_type']} died (own attacks: {', '.join(set(attacks))})")

# ERROR / SELF-DESTRUCT ANALYSIS
print()
print("───────────────────────────────────────────────────────────────────────────────")
print("ERROR / SELF-DESTRUCT ANALYSIS")
print("───────────────────────────────────────────────────────────────────────────────")

# Action-based deaths (exception/suicide)
action_rows = conn.execute("""
    SELECT team, body_type, COUNT(*) as count
    FROM events
    WHERE event_type='action' AND body_type IN ('DIE_EXCEPTION', 'DIE_SUICIDE')
    GROUP BY team, body_type
    ORDER BY team, body_type
""").fetchall()

if action_rows:
    print("Action deaths (from engine actions):")
    for row in action_rows:
        team = row['team'] or '?'
        print(f"  Team {team}: {row['body_type']} x{row['count']}")

    recent = conn.execute("""
        SELECT round_id, team, robot_id, body_type
        FROM events
        WHERE event_type='action' AND body_type IN ('DIE_EXCEPTION', 'DIE_SUICIDE')
        ORDER BY round_id
        LIMIT 10
    """).fetchall()
    if recent:
        print("  Sample incidents:")
        for r in recent:
            team = r['team'] or '?'
            print(f"    R{r['round_id']}: Team {team} robot {r['robot_id']} -> {r['body_type']}")
else:
    print("No DIE_EXCEPTION / DIE_SUICIDE actions recorded.")

# Log-based errors/exceptions
error_rows = conn.execute("""
    SELECT team, COUNT(*) as count
    FROM logs
    WHERE lower(message) LIKE '%exception%' OR lower(message) LIKE '%error%'
    GROUP BY team
""").fetchall()

if error_rows:
    print("Error/exception logs:")
    for row in error_rows:
        team = row['team'] or '?'
        print(f"  Team {team}: {row['count']} logs")

    samples = conn.execute("""
        SELECT round_id, team, robot_type, robot_id, message
        FROM logs
        WHERE lower(message) LIKE '%exception%' OR lower(message) LIKE '%error%'
        ORDER BY round_id
        LIMIT 10
    """).fetchall()
    if samples:
        print("  Sample logs:")
        for s in samples:
            team = s['team'] or '?'
            msg = s['message'].replace("\n", " ").strip()
            print(f"    R{s['round_id']}: Team {team} {s['robot_type']}#{s['robot_id']}: {msg}")
else:
    print("No error/exception logs recorded.")

print()
print("═══════════════════════════════════════════════════════════════════════════════")
print(f"Database: {db_path}")

conn.close()
PYEOF

# ASCII MAP
MAP_FILE="$PROJECT_DIR/maps/${MAP}.map17"
if [[ -f "$MAP_FILE" ]]; then
    echo ""
    echo "───────────────────────────────────────────────────────────────────────────────"
    echo "MAP LAYOUT: $MAP"
    echo "───────────────────────────────────────────────────────────────────────────────"
    python3 "$SCRIPT_DIR/map17_parser.py" "$MAP_FILE" --ascii
fi
