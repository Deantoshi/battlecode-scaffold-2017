#!/usr/bin/env python3
"""
BC17 Match Query Tool - RLM-style searchable match data

This tool extracts .bc17 match files into a SQLite database that can be
queried by OpenCode sub-agents without loading the entire match into context.

Usage:
    # Extract match to database
    python3 bc17_query.py extract <match.bc17> [output.db]

    # Query commands (work on extracted database)
    python3 bc17_query.py summary <match.db>
    python3 bc17_query.py rounds <match.db> <start> <end>
    python3 bc17_query.py events <match.db> [--type=spawn|death|vp|action|donate|shoot] [--team=A|B] [--round=N]
    python3 bc17_query.py units <match.db> [--round=N] [--type=SOLDIER|GARDENER|...]
    python3 bc17_query.py unit-positions <match.db> [--round=N] [--team=A|B] [--include-trees]
    python3 bc17_query.py economy <match.db> [--round=N]
    python3 bc17_query.py search <match.db> <query>
    python3 bc17_query.py sql <match.db> "<SQL query>"

    # Aggregate stats for battle log (across multiple matches)
    python3 bc17_query.py battlelog-stats "matches/*.db" [--team=A|B]

This enables RLM-style interaction where the LLM can:
1. Get a summary without seeing all data
2. Drill down into specific rounds/events
3. Search for patterns across the match
4. Run arbitrary SQL for complex queries
5. Generate pre-formatted battle log stats with battlelog-stats
"""

import sqlite3
import gzip
import struct
import sys
import os
import json
import re
from collections import defaultdict
from typing import Dict, List, Tuple, Optional, Any

# Import the parser from bc17_summary.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bc17_summary import BC17Parser, BODY_TYPES, UNIT_COSTS, COMBAT_UNITS, ACTION_TYPES

ACTION_COSTS = {
    'FIRE': 1,
    'FIRE_TRIAD': 4,
    'FIRE_PENTAD': 6,
}


class BC17Database:
    """SQLite database for queryable match data."""

    def __init__(self, db_path: str):
        self.db_path = db_path
        self.conn = None

    def connect(self):
        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row

    def close(self):
        if self.conn:
            self.conn.close()

    def create_schema(self):
        """Create database tables."""
        self.conn.executescript("""
            -- Match metadata
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT
            );

            -- Round-by-round state
            CREATE TABLE IF NOT EXISTS rounds (
                round_id INTEGER PRIMARY KEY,
                team_a_bullets REAL,
                team_b_bullets REAL,
                team_a_vp INTEGER,
                team_b_vp INTEGER
            );

            -- All robots that existed in the match
            CREATE TABLE IF NOT EXISTS robots (
                robot_id INTEGER PRIMARY KEY,
                team TEXT,  -- 'A' or 'B'
                body_type TEXT,
                spawn_round INTEGER,
                death_round INTEGER
            );

            -- Events (spawns, deaths, VP changes, etc.)
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                round_id INTEGER,
                event_type TEXT,  -- 'spawn', 'death', 'vp_gain', 'economy_shift'
                team TEXT,
                robot_id INTEGER,
                body_type TEXT,
                details TEXT,
                FOREIGN KEY (round_id) REFERENCES rounds(round_id)
            );

            -- Logs extracted from match
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                round_id INTEGER,
                team TEXT,
                robot_type TEXT,
                robot_id INTEGER,
                message TEXT,
                FOREIGN KEY (round_id) REFERENCES rounds(round_id)
            );

            -- Periodic snapshots (every N rounds)
            CREATE TABLE IF NOT EXISTS snapshots (
                round_id INTEGER PRIMARY KEY,
                team_a_bullets REAL,
                team_b_bullets REAL,
                team_a_vp INTEGER,
                team_b_vp INTEGER,
                team_a_units_alive TEXT,  -- JSON
                team_b_units_alive TEXT,  -- JSON
                team_a_bullets_generated REAL,
                team_b_bullets_generated REAL,
                team_a_bullets_spent REAL,
                team_b_bullets_spent REAL,
                team_a_units_lost INTEGER,
                team_b_units_lost INTEGER
            );

            -- Unit positions captured at snapshot rounds
            CREATE TABLE IF NOT EXISTS unit_positions (
                round_id INTEGER,
                team TEXT,
                robot_id INTEGER,
                body_type TEXT,
                x REAL,
                y REAL,
                PRIMARY KEY (round_id, robot_id)
            );

            -- Create indexes for fast querying
            CREATE INDEX IF NOT EXISTS idx_events_round ON events(round_id);
            CREATE INDEX IF NOT EXISTS idx_events_type ON events(event_type);
            CREATE INDEX IF NOT EXISTS idx_events_team ON events(team);
            CREATE INDEX IF NOT EXISTS idx_logs_round ON logs(round_id);
            CREATE INDEX IF NOT EXISTS idx_logs_team ON logs(team);
            CREATE INDEX IF NOT EXISTS idx_robots_team ON robots(team);
            CREATE INDEX IF NOT EXISTS idx_robots_type ON robots(body_type);
            CREATE INDEX IF NOT EXISTS idx_unit_positions_round ON unit_positions(round_id);
            CREATE INDEX IF NOT EXISTS idx_unit_positions_team ON unit_positions(team);
        """)
        self.conn.commit()

    def extract_from_bc17(self, bc17_path: str):
        """Extract a .bc17 file into the database."""
        parser = BC17Parser(bc17_path)
        if not parser.load():
            raise Exception(f"Failed to load {bc17_path}")

        parsed = parser.parse()

        # Store metadata
        metadata = parsed.get('metadata', {})
        for key, value in metadata.items():
            if isinstance(value, list):
                value = json.dumps(value)
            self.conn.execute(
                "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
                (key, str(value))
            )

        # Store logs
        logs = parsed.get('logs', [])
        for log in logs:
            self.conn.execute(
                "INSERT INTO logs (round_id, team, robot_type, robot_id, message) VALUES (?, ?, ?, ?, ?)",
                (log['round'], log['team'], log['type'], log['id'], log['message'])
            )

        # Parse engine spend logs for full accounting (donate/shoot)
        donate_re = re.compile(r"^donate bullets=([0-9.]+) vp_gain=([0-9]+) vp_cost=([0-9.]+)$")
        shoot_re = re.compile(r"^shoot type=([a-z]+) cost=([0-9.]+) bullets_before=([0-9.]+) bullets_after=([0-9.]+)$")

        for log in logs:
            msg = (log.get('message') or "").strip()
            if not msg:
                continue

            m = donate_re.match(msg)
            if m:
                bullets = float(m.group(1))
                vp_gain = int(m.group(2))
                vp_cost = float(m.group(3))
                self.conn.execute(
                    "INSERT INTO events (round_id, event_type, team, robot_id, body_type, details) VALUES (?, ?, ?, ?, ?, ?)",
                    (log['round'], 'donate', log['team'], log['id'], 'DONATE',
                     json.dumps({
                         'bullets': bullets,
                         'vp_gain': vp_gain,
                         'vp_cost': vp_cost,
                         'robot_type': log.get('type')
                     }))
                )
                continue

            m = shoot_re.match(msg)
            if m:
                shot_type = m.group(1)
                cost = float(m.group(2))
                before = float(m.group(3))
                after = float(m.group(4))
                self.conn.execute(
                    "INSERT INTO events (round_id, event_type, team, robot_id, body_type, details) VALUES (?, ?, ?, ?, ?, ?)",
                    (log['round'], 'shoot', log['team'], log['id'], 'SHOOT',
                     json.dumps({
                         'shot_type': shot_type,
                         'cost': cost,
                         'bullets_before': before,
                         'bullets_after': after,
                         'robot_type': log.get('type')
                     }))
                )

        # Now parse round-by-round data
        parser.parse_game_wrapper()

        # Track robots
        robot_registry = {}  # robot_id -> (team, type, spawn_round)
        prev_bullets = [0.0, 0.0]
        prev_vp = [0, 0]

        for round_data in parser.rounds:
            # Store round state
            self.conn.execute(
                "INSERT OR REPLACE INTO rounds (round_id, team_a_bullets, team_b_bullets, team_a_vp, team_b_vp) VALUES (?, ?, ?, ?, ?)",
                (round_data.round_id, round_data.team_bullets[0], round_data.team_bullets[1],
                 round_data.team_victory_points[0], round_data.team_victory_points[1])
            )

            # Track spawns
            for robot_id, team_idx, body_type in round_data.spawned_robot_info:
                team = 'A' if team_idx == 0 else 'B'
                type_name = BODY_TYPES.get(body_type, f'UNKNOWN_{body_type}')
                robot_registry[robot_id] = (team, type_name, round_data.round_id)

                # Record spawn event
                self.conn.execute(
                    "INSERT INTO events (round_id, event_type, team, robot_id, body_type, details) VALUES (?, ?, ?, ?, ?, ?)",
                    (round_data.round_id, 'spawn', team, robot_id, type_name,
                     json.dumps({'cost': UNIT_COSTS.get(type_name, 0)}))
                )

            # Track deaths
            for robot_id in round_data.died_ids:
                if robot_id in robot_registry:
                    team, type_name, spawn_round = robot_registry[robot_id]
                    lifespan = round_data.round_id - spawn_round

                    self.conn.execute(
                        "INSERT INTO events (round_id, event_type, team, robot_id, body_type, details) VALUES (?, ?, ?, ?, ?, ?)",
                        (round_data.round_id, 'death', team, robot_id, type_name,
                         json.dumps({'lifespan': lifespan, 'spawn_round': spawn_round}))
                    )

            # Track actions (FIRE/PLANT/etc.)
            for robot_id, action_type, target_id in round_data.actions:
                action_name = ACTION_TYPES.get(action_type, f'UNKNOWN_{action_type}')
                team = None
                if robot_id in robot_registry:
                    team = robot_registry[robot_id][0]

                details = {'target_id': target_id}
                if action_name in ACTION_COSTS:
                    details['cost'] = ACTION_COSTS[action_name]

                self.conn.execute(
                    "INSERT INTO events (round_id, event_type, team, robot_id, body_type, details) VALUES (?, ?, ?, ?, ?, ?)",
                    (round_data.round_id, 'action', team, robot_id, action_name, json.dumps(details))
                )

            # Track VP changes
            for team_idx, team in enumerate(['A', 'B']):
                vp_gained = round_data.team_victory_points[team_idx] - prev_vp[team_idx]
                if vp_gained > 0:
                    self.conn.execute(
                        "INSERT INTO events (round_id, event_type, team, body_type, details) VALUES (?, ?, ?, ?, ?)",
                        (round_data.round_id, 'vp_gain', team, None,
                         json.dumps({'gained': vp_gained, 'total': round_data.team_victory_points[team_idx]}))
                    )

            prev_bullets = round_data.team_bullets[:]
            prev_vp = round_data.team_victory_points[:]

        # Store robots with death info
        for robot_id, (team, type_name, spawn_round) in robot_registry.items():
            # Find death round if any
            death_round = None
            cursor = self.conn.execute(
                "SELECT round_id FROM events WHERE event_type='death' AND robot_id=?",
                (robot_id,)
            )
            row = cursor.fetchone()
            if row:
                death_round = row[0]

            self.conn.execute(
                "INSERT OR REPLACE INTO robots (robot_id, team, body_type, spawn_round, death_round) VALUES (?, ?, ?, ?, ?)",
                (robot_id, team, type_name, spawn_round, death_round)
            )

        # Store snapshots
        for snapshot in parser.snapshots:
            self.conn.execute("""
                INSERT OR REPLACE INTO snapshots
                (round_id, team_a_bullets, team_b_bullets, team_a_vp, team_b_vp,
                 team_a_units_alive, team_b_units_alive, team_a_bullets_generated,
                 team_b_bullets_generated, team_a_bullets_spent, team_b_bullets_spent,
                 team_a_units_lost, team_b_units_lost)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                snapshot.round,
                snapshot.team_a_bullets, snapshot.team_b_bullets,
                snapshot.team_a_vp, snapshot.team_b_vp,
                json.dumps(dict(snapshot.team_a_units_alive)),
                json.dumps(dict(snapshot.team_b_units_alive)),
                snapshot.team_a_bullets_generated, snapshot.team_b_bullets_generated,
                snapshot.team_a_bullets_spent, snapshot.team_b_bullets_spent,
                snapshot.team_a_units_lost, snapshot.team_b_units_lost
            ))

        # Store unit positions for snapshot rounds
        for snapshot in parser.snapshots:
            for unit in snapshot.team_a_unit_positions:
                self.conn.execute(
                    "INSERT OR REPLACE INTO unit_positions (round_id, team, robot_id, body_type, x, y) VALUES (?, ?, ?, ?, ?, ?)",
                    (snapshot.round, 'A', unit['id'], unit['type'], unit['x'], unit['y'])
                )
            for unit in snapshot.team_b_unit_positions:
                self.conn.execute(
                    "INSERT OR REPLACE INTO unit_positions (round_id, team, robot_id, body_type, x, y) VALUES (?, ?, ?, ?, ?, ?)",
                    (snapshot.round, 'B', unit['id'], unit['type'], unit['x'], unit['y'])
                )

        self.conn.commit()
        return len(parser.rounds)


def cmd_extract(bc17_path: str, db_path: str = None):
    """Extract .bc17 to SQLite database."""
    if db_path is None:
        db_path = bc17_path.replace('.bc17', '.db')

    db = BC17Database(db_path)
    db.connect()
    db.create_schema()

    rounds = db.extract_from_bc17(bc17_path)
    db.close()

    print(f"Extracted {rounds} rounds to {db_path}")
    return db_path


def cmd_summary(db_path: str):
    """Get match summary (minimal context, high-level overview)."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    # Get metadata
    metadata = {}
    for row in conn.execute("SELECT key, value FROM metadata"):
        metadata[row['key']] = row['value']

    # Get basic stats
    stats = conn.execute("""
        SELECT
            COUNT(*) as total_rounds,
            (SELECT COUNT(*) FROM events WHERE event_type='spawn') as total_spawns,
            (SELECT COUNT(*) FROM events WHERE event_type='death') as total_deaths,
            (SELECT COUNT(*) FROM logs) as total_logs
        FROM rounds
    """).fetchone()

    # Get final state
    final = conn.execute("""
        SELECT * FROM rounds ORDER BY round_id DESC LIMIT 1
    """).fetchone()

    # Get unit counts by team
    units_a = conn.execute("""
        SELECT body_type, COUNT(*) as count
        FROM robots WHERE team='A'
        GROUP BY body_type
    """).fetchall()

    units_b = conn.execute("""
        SELECT body_type, COUNT(*) as count
        FROM robots WHERE team='B'
        GROUP BY body_type
    """).fetchall()

    print("=" * 60)
    print("MATCH SUMMARY (Query-friendly)")
    print("=" * 60)
    print(f"Teams: {metadata.get('teams', 'Unknown')}")
    print(f"Map: {metadata.get('map_name', 'Unknown')}")
    print(f"Winner: Team {metadata.get('winner', 'Unknown')}")
    print(f"Total Rounds: {stats['total_rounds']}")
    print(f"Total Spawns: {stats['total_spawns']}")
    print(f"Total Deaths: {stats['total_deaths']}")
    print(f"Total Logs: {stats['total_logs']}")
    print()
    print("Final State:")
    print(f"  Team A: {final['team_a_bullets']:.1f} bullets, {final['team_a_vp']} VP")
    print(f"  Team B: {final['team_b_bullets']:.1f} bullets, {final['team_b_vp']} VP")
    print()
    print("Units Produced:")
    print(f"  Team A: {dict(units_a)}")
    print(f"  Team B: {dict(units_b)}")
    print()
    print("Available queries: rounds, events, units, unit-positions, economy, search, sql")

    conn.close()


def cmd_rounds(db_path: str, start: int, end: int):
    """Get round data for a specific range."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    rows = conn.execute("""
        SELECT * FROM rounds
        WHERE round_id >= ? AND round_id <= ?
        ORDER BY round_id
    """, (start, end)).fetchall()

    print(f"Rounds {start}-{end}:")
    print("-" * 80)
    print(f"{'Round':<8} {'A Bullets':<12} {'B Bullets':<12} {'A VP':<8} {'B VP':<8}")
    print("-" * 80)

    for row in rows:
        print(f"{row['round_id']:<8} {row['team_a_bullets']:<12.1f} {row['team_b_bullets']:<12.1f} {row['team_a_vp']:<8} {row['team_b_vp']:<8}")

    conn.close()


def cmd_events(db_path: str, event_type: str = None, team: str = None, round_id: int = None, limit: int = 50):
    """Query events with filters."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    query = "SELECT * FROM events WHERE 1=1"
    params = []

    if event_type:
        query += " AND event_type = ?"
        params.append(event_type)
    if team:
        query += " AND team = ?"
        params.append(team)
    if round_id:
        query += " AND round_id = ?"
        params.append(round_id)

    query += f" ORDER BY round_id LIMIT {limit}"

    rows = conn.execute(query, params).fetchall()

    print(f"Events (filters: type={event_type}, team={team}, round={round_id}):")
    print("-" * 100)

    for row in rows:
        details = json.loads(row['details']) if row['details'] else {}
        print(f"R{row['round_id']:>4} | {row['event_type']:<8} | Team {row['team']} | {row['body_type'] or '-':<10} | {details}")

    print(f"\nShowing {len(rows)} events (limit={limit})")
    conn.close()


def cmd_units(db_path: str, round_id: int = None, body_type: str = None):
    """Query unit information."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    if round_id:
        # Units alive at specific round
        rows = conn.execute("""
            SELECT team, body_type, COUNT(*) as count
            FROM robots
            WHERE spawn_round <= ? AND (death_round IS NULL OR death_round > ?)
            GROUP BY team, body_type
            ORDER BY team, body_type
        """, (round_id, round_id)).fetchall()

        print(f"Units alive at round {round_id}:")
    else:
        # All units produced
        query = "SELECT team, body_type, COUNT(*) as count FROM robots"
        params = []
        if body_type:
            query += " WHERE body_type = ?"
            params.append(body_type)
        query += " GROUP BY team, body_type ORDER BY team, body_type"

        rows = conn.execute(query, params).fetchall()
        print("All units produced:")

    print("-" * 40)
    for row in rows:
        print(f"Team {row['team']}: {row['body_type']:<12} x{row['count']}")

    conn.close()


def cmd_unit_positions(db_path: str, round_id: int = None, team: str = 'A', include_trees: bool = False):
    """Query unit positions from snapshot rounds."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    filters = ["team = ?"]
    params = [team]
    if not include_trees:
        filters.append("body_type != 'TREE_BULLET'")
    where_clause = " AND ".join(filters)

    snapshot_round = None
    if round_id is not None:
        row = conn.execute(
            f"SELECT MAX(round_id) AS round_id FROM unit_positions WHERE {where_clause} AND round_id <= ?",
            params + [round_id]
        ).fetchone()
        snapshot_round = row['round_id'] if row else None
        if snapshot_round is None:
            print(f"No unit position snapshot found for Team {team} at or before round {round_id}.")
            conn.close()
            return
        where_clause = f"{where_clause} AND round_id = ?"
        params = params + [snapshot_round]

    rows = conn.execute(
        f"""
        SELECT round_id, robot_id, body_type, x, y
        FROM unit_positions
        WHERE {where_clause}
        ORDER BY round_id, body_type, robot_id
        """,
        params
    ).fetchall()

    if not rows:
        print("No unit position snapshots found.")
        conn.close()
        return

    if snapshot_round is not None:
        print(f"Unit positions at round {snapshot_round} (Team {team}):")
    else:
        print(f"Unit positions (Team {team}):")

    current_round = None
    for row in rows:
        if row['round_id'] != current_round:
            if current_round is not None:
                print()
            current_round = row['round_id']
            print(f"Round {current_round}")
        print(f"  {row['body_type']}#{row['robot_id']} @ ({row['x']:.1f}, {row['y']:.1f})")

    conn.close()


def cmd_economy(db_path: str, round_id: int = None):
    """Query economy snapshots."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    if round_id:
        # Find nearest snapshot
        row = conn.execute("""
            SELECT * FROM snapshots
            WHERE round_id <= ?
            ORDER BY round_id DESC LIMIT 1
        """, (round_id,)).fetchone()

        if row:
            print(f"Economy snapshot (nearest to round {round_id}):")
            print(f"  Round: {row['round_id']}")
            print(f"  Team A: {row['team_a_bullets']:.1f} bullets, {row['team_a_vp']} VP")
            print(f"  Team B: {row['team_b_bullets']:.1f} bullets, {row['team_b_vp']} VP")
            print(f"  A Generated: {row['team_a_bullets_generated']:.1f}")
            print(f"  B Generated: {row['team_b_bullets_generated']:.1f}")
            print(f"  A Units Lost: {row['team_a_units_lost']}")
            print(f"  B Units Lost: {row['team_b_units_lost']}")
    else:
        # All snapshots summary
        rows = conn.execute("SELECT * FROM snapshots ORDER BY round_id").fetchall()

        print("Economy Timeline:")
        print("-" * 100)
        print(f"{'Round':<8} {'A Bullets':<12} {'B Bullets':<12} {'A VP':<8} {'B VP':<8} {'A Gen':<10} {'B Gen':<10}")
        print("-" * 100)

        for row in rows:
            print(f"{row['round_id']:<8} {row['team_a_bullets']:<12.1f} {row['team_b_bullets']:<12.1f} "
                  f"{row['team_a_vp']:<8} {row['team_b_vp']:<8} "
                  f"{row['team_a_bullets_generated']:<10.1f} {row['team_b_bullets_generated']:<10.1f}")

    conn.close()


def cmd_search(db_path: str, query: str):
    """Search logs and events for a pattern."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    # Search logs
    log_results = conn.execute("""
        SELECT * FROM logs
        WHERE message LIKE ?
        ORDER BY round_id
        LIMIT 20
    """, (f'%{query}%',)).fetchall()

    # Search event details
    event_results = conn.execute("""
        SELECT * FROM events
        WHERE details LIKE ? OR body_type LIKE ?
        ORDER BY round_id
        LIMIT 20
    """, (f'%{query}%', f'%{query}%')).fetchall()

    print(f"Search results for '{query}':")
    print()

    if log_results:
        print(f"=== Logs ({len(log_results)} matches) ===")
        for row in log_results:
            print(f"R{row['round_id']:>4} | Team {row['team']} {row['robot_type']}#{row['robot_id']}: {row['message']}")

    if event_results:
        print(f"\n=== Events ({len(event_results)} matches) ===")
        for row in event_results:
            print(f"R{row['round_id']:>4} | {row['event_type']:<8} | Team {row['team']} | {row['body_type']} | {row['details']}")

    conn.close()


def cmd_sql(db_path: str, query: str):
    """Execute arbitrary SQL query."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    try:
        rows = conn.execute(query).fetchall()

        if rows:
            # Print column headers
            columns = rows[0].keys()
            print(" | ".join(columns))
            print("-" * (len(" | ".join(columns)) + 10))

            for row in rows:
                print(" | ".join(str(row[col]) for col in columns))

            print(f"\n({len(rows)} rows)")
        else:
            print("No results")

    except Exception as e:
        print(f"SQL Error: {e}")

    conn.close()


def cmd_battlelog_stats(db_pattern: str, team: str = 'A'):
    """
    Aggregate stats across all matches for battle log.

    Usage: bc17_query.py battlelog-stats "matches/*.db" [--team=A|B]

    Outputs pre-formatted stats for the battle log entry.
    """
    import glob as glob_module

    db_files = glob_module.glob(db_pattern)
    if not db_files:
        print(f"No database files found matching: {db_pattern}")
        return

    # Aggregated stats
    units_produced = defaultdict(int)
    units_died = defaultdict(int)
    trees_planted = 0
    trees_destroyed = 0
    bullets_generated = 0.0
    bullets_spent = 0.0

    for db_path in db_files:
        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row

        # Units produced (spawns) for our team
        rows = conn.execute("""
            SELECT body_type, COUNT(*) as count
            FROM events
            WHERE event_type='spawn' AND team=?
            GROUP BY body_type
        """, (team,)).fetchall()
        for row in rows:
            body_type = row['body_type']
            if body_type and body_type != 'TREE':
                units_produced[body_type] += row['count']

        # Units died for our team
        rows = conn.execute("""
            SELECT body_type, COUNT(*) as count
            FROM events
            WHERE event_type='death' AND team=?
            GROUP BY body_type
        """, (team,)).fetchall()
        for row in rows:
            body_type = row['body_type']
            if body_type and body_type != 'TREE':
                units_died[body_type] += row['count']

        # Trees: count PLANT actions (our team planting) and tree deaths
        # Trees planted = PLANT actions by our team
        plant_count = conn.execute("""
            SELECT COUNT(*) as count
            FROM events
            WHERE event_type='action' AND body_type='PLANT_TREE' AND team=?
        """, (team,)).fetchone()['count']
        trees_planted += plant_count

        # Trees destroyed = tree deaths (trees don't have a team, count all)
        # Actually trees spawned by gardeners belong to that team
        tree_deaths = conn.execute("""
            SELECT COUNT(*) as count
            FROM events
            WHERE event_type='death' AND body_type='TREE'
        """).fetchone()['count']
        trees_destroyed += tree_deaths

        # Economy from final snapshot
        final_snapshot = conn.execute("""
            SELECT * FROM snapshots ORDER BY round_id DESC LIMIT 1
        """).fetchone()

        if final_snapshot:
            if team == 'A':
                bullets_generated += final_snapshot['team_a_bullets_generated'] or 0
                bullets_spent += final_snapshot['team_a_bullets_spent'] or 0
            else:
                bullets_generated += final_snapshot['team_b_bullets_generated'] or 0
                bullets_spent += final_snapshot['team_b_bullets_spent'] or 0

        conn.close()

    # Calculate totals
    total_produced = sum(units_produced.values())
    total_died = sum(units_died.values())
    trees_net = trees_planted - trees_destroyed
    bullets_net = bullets_generated - bullets_spent

    # Format output in battle log format - tabular with full names
    unit_order = ['ARCHON', 'GARDENER', 'SOLDIER', 'LUMBERJACK', 'SCOUT', 'TANK']
    unit_names = {
        'ARCHON': 'Archon',
        'GARDENER': 'Gardener',
        'SOLDIER': 'Soldier',
        'LUMBERJACK': 'Lumberjack',
        'SCOUT': 'Scout',
        'TANK': 'Tank'
    }

    net_sign = '+' if trees_net >= 0 else ''
    bullets_net_sign = '+' if bullets_net >= 0 else ''

    print("=" * 70)
    print(f"BATTLE LOG STATS (Team {team}, {len(db_files)} matches)")
    print("=" * 70)
    print()
    print("**Units & Trees (totals across all maps):**")
    print()
    print(f"| {'Type':<12} | {'Produced':>10} | {'Lost':>10} | {'Surviving':>10} |")
    print(f"|{'-'*14}|{'-'*12}|{'-'*12}|{'-'*12}|")
    for unit in unit_order:
        produced = units_produced.get(unit, 0)
        died = units_died.get(unit, 0)
        surviving = produced - died
        print(f"| {unit_names[unit]:<12} | {produced:>10} | {died:>10} | {surviving:>10} |")
    print(f"| {'Trees':<12} | {trees_planted:>10} | {trees_destroyed:>10} | {trees_net:>10} |")
    print(f"|{'-'*14}|{'-'*12}|{'-'*12}|{'-'*12}|")
    print(f"| {'TOTAL':<12} | {total_produced + trees_planted:>10} | {total_died + trees_destroyed:>10} | {(total_produced - total_died) + trees_net:>10} |")
    print()
    print("**Economy (totals across all maps):**")
    print()
    print(f"| {'Metric':<12} | {'Bullets':>12} |")
    print(f"|{'-'*14}|{'-'*14}|")
    print(f"| {'Generated':<12} | {int(bullets_generated):>12} |")
    print(f"| {'Spent':<12} | {int(bullets_spent):>12} |")
    print(f"| {'Net':<12} | {bullets_net_sign}{int(bullets_net):>11} |")
    print()
    print("=" * 70)
    print("COPY-PASTE READY FORMAT FOR BATTLE LOG")
    print("=" * 70)
    print()
    print("**Units & Trees (totals across all maps):**")
    print(f"| Type       | Produced |     Lost | Surviving |")
    print(f"|------------|----------|----------|-----------|")
    for unit in unit_order:
        produced = units_produced.get(unit, 0)
        died = units_died.get(unit, 0)
        surviving = produced - died
        print(f"| {unit_names[unit]:<10} | {produced:>8} | {died:>8} | {surviving:>9} |")
    print(f"| Trees      | {trees_planted:>8} | {trees_destroyed:>8} | {trees_net:>9} |")
    print(f"| **TOTAL**  | {total_produced + trees_planted:>8} | {total_died + trees_destroyed:>8} | {(total_produced - total_died) + trees_net:>9} |")
    print()
    print("**Economy (totals across all maps):**")
    print(f"| Metric    |     Bullets |")
    print(f"|-----------|-------------|")
    print(f"| Generated | {int(bullets_generated):>11} |")
    print(f"| Spent     | {int(bullets_spent):>11} |")
    print(f"| Net       | {bullets_net_sign}{int(bullets_net):>10} |")


def print_usage():
    print(__doc__)


def main():
    if len(sys.argv) < 2:
        print_usage()
        sys.exit(1)

    cmd = sys.argv[1]

    if cmd == 'extract':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py extract <match.bc17> [output.db]")
            sys.exit(1)
        db_path = sys.argv[3] if len(sys.argv) > 3 else None
        cmd_extract(sys.argv[2], db_path)

    elif cmd == 'summary':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py summary <match.db>")
            sys.exit(1)
        cmd_summary(sys.argv[2])

    elif cmd == 'rounds':
        if len(sys.argv) < 5:
            print("Usage: bc17_query.py rounds <match.db> <start> <end>")
            sys.exit(1)
        cmd_rounds(sys.argv[2], int(sys.argv[3]), int(sys.argv[4]))

    elif cmd == 'events':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py events <match.db> [--type=spawn|death|vp|action|donate|shoot] [--team=A|B] [--round=N]")
            sys.exit(1)
        # Parse optional args
        event_type = team = round_id = None
        for arg in sys.argv[3:]:
            if arg.startswith('--type='):
                event_type = arg.split('=')[1]
            elif arg.startswith('--team='):
                team = arg.split('=')[1]
            elif arg.startswith('--round='):
                round_id = int(arg.split('=')[1])
        cmd_events(sys.argv[2], event_type, team, round_id)

    elif cmd == 'units':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py units <match.db> [--round=N] [--type=X]")
            sys.exit(1)
        round_id = body_type = None
        for arg in sys.argv[3:]:
            if arg.startswith('--round='):
                round_id = int(arg.split('=')[1])
            elif arg.startswith('--type='):
                body_type = arg.split('=')[1]
        cmd_units(sys.argv[2], round_id, body_type)

    elif cmd == 'unit-positions':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py unit-positions <match.db> [--round=N] [--team=A|B] [--include-trees]")
            sys.exit(1)
        round_id = None
        team = 'A'
        include_trees = False
        for arg in sys.argv[3:]:
            if arg.startswith('--round='):
                round_id = int(arg.split('=')[1])
            elif arg.startswith('--team='):
                team = arg.split('=')[1]
            elif arg == '--include-trees':
                include_trees = True
        cmd_unit_positions(sys.argv[2], round_id, team, include_trees)

    elif cmd == 'economy':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py economy <match.db> [--round=N]")
            sys.exit(1)
        round_id = None
        for arg in sys.argv[3:]:
            if arg.startswith('--round='):
                round_id = int(arg.split('=')[1])
        cmd_economy(sys.argv[2], round_id)

    elif cmd == 'search':
        if len(sys.argv) < 4:
            print("Usage: bc17_query.py search <match.db> <query>")
            sys.exit(1)
        cmd_search(sys.argv[2], sys.argv[3])

    elif cmd == 'sql':
        if len(sys.argv) < 4:
            print("Usage: bc17_query.py sql <match.db> \"<SQL query>\"")
            sys.exit(1)
        cmd_sql(sys.argv[2], sys.argv[3])

    elif cmd == 'battlelog-stats':
        if len(sys.argv) < 3:
            print("Usage: bc17_query.py battlelog-stats \"matches/*.db\" [--team=A|B]")
            sys.exit(1)
        team = 'A'
        for arg in sys.argv[3:]:
            if arg.startswith('--team='):
                team = arg.split('=')[1]
        cmd_battlelog_stats(sys.argv[2], team)

    else:
        print(f"Unknown command: {cmd}")
        print_usage()
        sys.exit(1)


if __name__ == '__main__':
    main()
