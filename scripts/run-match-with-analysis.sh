#!/bin/bash
# run-match-with-analysis.sh - Run a match and get comprehensive analysis including unit positions
#
# Usage: ./scripts/run-match-with-analysis.sh <bot> <opponent> [map] [options]
#
# Options:
#   --round=N         Query unit positions at specific round (default: all snapshots)
#   --team=A|B        Query positions for specific team (default: both)
#   --include-trees   Include trees in unit position output
#   --query-all       Query all matches in matches/*.db instead of just this match
#
# Examples:
#   ./scripts/run-match-with-analysis.sh mybot examplefuncsplayer MagicWood
#   ./scripts/run-match-with-analysis.sh mybot opponent Arena --round=500 --team=A
#   ./scripts/run-match-with-analysis.sh mybot opponent --query-all

set -e

# Parse arguments
BOT=""
OPPONENT=""
MAP="MagicWood"
ROUND_OPT=""
TEAM_OPT=""
INCLUDE_TREES=""
QUERY_ALL=false

for arg in "$@"; do
    case "$arg" in
        --round=*)
            ROUND_OPT="$arg"
            ;;
        --team=*)
            TEAM_OPT="$arg"
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
    echo "  --team=A|B        Query positions for specific team (default: both)"
    echo "  --include-trees   Include trees in unit position output"
    echo "  --query-all       Query all matches in matches/*.db"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MATCH_FILE="$PROJECT_DIR/matches/${BOT}-vs-${OPPONENT}-on-${MAP}.bc17"
DB_FILE="${MATCH_FILE%.bc17}.db"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "RUNNING MATCH: $BOT vs $OPPONENT on $MAP"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Clean previous match
rm -f "$MATCH_FILE" "$DB_FILE"

# Run match
cd "$PROJECT_DIR"
./gradlew run -PteamA="$BOT" -PteamB="$OPPONENT" -Pmaps="$MAP" --quiet

# Check if match file exists
if [[ ! -f "$MATCH_FILE" ]]; then
    echo "ERROR: Match file not created: $MATCH_FILE"
    exit 1
fi

# Extract to database
python3 "$SCRIPT_DIR/bc17_query.py" extract "$MATCH_FILE" "$DB_FILE" > /dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "INITIAL MAP TERRAIN (ASCII)"
echo "═══════════════════════════════════════════════════════════════════════════════"

./gradlew visualizeMap -Pmap="$MAP" --quiet

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "MATCH RESULT"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Get winner and total rounds
WINNER=$(python3 "$SCRIPT_DIR/bc17_query.py" sql "$DB_FILE" "SELECT value FROM metadata WHERE key='winner'" 2>/dev/null | tail -2 | head -1 || echo "UNKNOWN")
TOTAL_ROUNDS=$(python3 "$SCRIPT_DIR/bc17_query.py" sql "$DB_FILE" "SELECT MAX(round_id) FROM rounds" 2>/dev/null | tail -2 | head -1 || echo "0")

# Determine if our bot won (we are Team A)
if [[ "$WINNER" == "A" ]]; then
    WON="YES"
    echo "OUTCOME: WIN in $TOTAL_ROUNDS rounds"
else
    WON="NO"
    echo "OUTCOME: LOSS after $TOTAL_ROUNDS rounds"
fi

echo ""
echo "WIN_STATUS=$WON"
echo "ROUNDS=$TOTAL_ROUNDS"
echo "TARGET=1500"

# Get final VP for team A
A_VP=$(python3 "$SCRIPT_DIR/bc17_query.py" sql "$DB_FILE" "SELECT team_a_vp FROM rounds ORDER BY round_id DESC LIMIT 1" 2>/dev/null | tail -2 | head -1 || echo "0")

if [[ "$WON" == "YES" && "$TOTAL_ROUNDS" -le 1500 ]] || [[ "$A_VP" -ge 1000 ]]; then
    echo "GOAL_MET=YES"
    GOAL_MET="YES"
else
    echo "GOAL_MET=NO"
    GOAL_MET="NO"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "FINAL STATE"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Get final round data
python3 "$SCRIPT_DIR/bc17_query.py" sql "$DB_FILE" "
SELECT
    'A' as Team,
    team_a_bullets as Bullets,
    team_a_vp as VP
FROM rounds WHERE round_id = (SELECT MAX(round_id) FROM rounds)
UNION ALL
SELECT
    'B' as Team,
    team_b_bullets as Bullets,
    team_b_vp as VP
FROM rounds WHERE round_id = (SELECT MAX(round_id) FROM rounds)
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "UNITS AT END"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Units alive at end (combat units only)
sqlite3 -header -column "$DB_FILE" "
SELECT team as Team, body_type as UnitType, COUNT(*) as Alive
FROM robots
WHERE death_round IS NULL
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY team, body_type
ORDER BY team, body_type
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "ECONOMY TIMELINE (every 200 rounds)"
echo "═══════════════════════════════════════════════════════════════════════════════"

sqlite3 -header -column "$DB_FILE" "
SELECT round_id as Round,
       team_a_bullets as A_Bullets,
       team_b_bullets as B_Bullets,
       team_a_vp as A_VP,
       team_b_vp as B_VP
FROM rounds
WHERE round_id % 200 = 0 OR round_id = (SELECT MAX(round_id) FROM rounds)
ORDER BY round_id
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "UNIT QUADRANTS (likely stuck since last snapshot)"
echo "═══════════════════════════════════════════════════════════════════════════════"

# Determine which database(s) to query
if [[ "$QUERY_ALL" == true ]]; then
    QUERY_DB="matches/*.db"
else
    QUERY_DB="$DB_FILE"
fi

# Build unit-positions command
if [[ -n "$TEAM_OPT" ]]; then
    # Query specific team
    TEAM_VAL="${TEAM_OPT#--team=}"
    echo "Team $TEAM_VAL positions:"
    python3 "$SCRIPT_DIR/bc17_query.py" unit-positions "$QUERY_DB" $ROUND_OPT --team="$TEAM_VAL" $INCLUDE_TREES
else
    # Query both teams
    echo "Team A ($BOT) positions:"
    python3 "$SCRIPT_DIR/bc17_query.py" unit-positions "$QUERY_DB" $ROUND_OPT --team=A $INCLUDE_TREES
    echo ""
    echo "Team B ($OPPONENT) positions:"
    python3 "$SCRIPT_DIR/bc17_query.py" unit-positions "$QUERY_DB" $ROUND_OPT --team=B $INCLUDE_TREES
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "UNIT PRODUCTION (Team A = $BOT)"
echo "═══════════════════════════════════════════════════════════════════════════════"

sqlite3 -header -column "$DB_FILE" "
SELECT body_type as UnitType, COUNT(*) as Produced
FROM events
WHERE event_type='spawn' AND team='A'
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY body_type
ORDER BY Produced DESC
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "UNIT LOSSES (Team A = $BOT)"
echo "═══════════════════════════════════════════════════════════════════════════════"

sqlite3 -header -column "$DB_FILE" "
SELECT body_type as UnitType, COUNT(*) as Lost
FROM events
WHERE event_type='death' AND team='A'
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY body_type
ORDER BY Lost DESC
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "DEATH TIMELINE (when units died)"
echo "═══════════════════════════════════════════════════════════════════════════════"

sqlite3 -header -column "$DB_FILE" "
SELECT
    CASE
        WHEN round_id <= 300 THEN 'R1-300'
        WHEN round_id <= 600 THEN 'R301-600'
        WHEN round_id <= 900 THEN 'R601-900'
        WHEN round_id <= 1200 THEN 'R901-1200'
        ELSE 'R1201+'
    END as Period,
    team as Team,
    COUNT(*) as Deaths
FROM events
WHERE event_type='death'
  AND body_type NOT IN ('TREE_BULLET', 'TREE_NEUTRAL', 'BULLET', 'NONE')
GROUP BY Period, team
ORDER BY Period, team
" 2>/dev/null

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "VP DONATIONS (if any)"
echo "═══════════════════════════════════════════════════════════════════════════════"

DONATE_COUNT=$(sqlite3 "$DB_FILE" "SELECT COUNT(*) FROM events WHERE event_type='donate'" 2>/dev/null || echo "0")
if [[ "$DONATE_COUNT" -gt 0 ]]; then
    sqlite3 -header -column "$DB_FILE" "
    SELECT team as Team, COUNT(*) as DonateEvents,
           SUM(json_extract(details, '$.vp_gain')) as TotalVP
    FROM events
    WHERE event_type='donate'
    GROUP BY team
    " 2>/dev/null
else
    echo "(No VP donations recorded)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "MATCH ANALYSIS COMPLETE"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Database: $DB_FILE"
echo ""
echo "Additional queries available:"
echo "  python3 scripts/bc17_query.py summary $DB_FILE"
echo "  python3 scripts/bc17_query.py events $DB_FILE --type=death --team=A"
echo "  python3 scripts/bc17_query.py economy $DB_FILE --round=500"
echo "  python3 scripts/bc17_query.py unit-positions $DB_FILE --round=1000 --team=A"
echo "  python3 scripts/bc17_query.py sql $DB_FILE \"SELECT * FROM events LIMIT 10\""
