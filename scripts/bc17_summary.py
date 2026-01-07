#!/usr/bin/env python3
"""
Battlecode 2017 Match Summary Generator

Parses .bc17 replay files and generates LLM-friendly summaries of matches.
Extracts logs, game events, unit production, resources, and victory points.
Provides snapshots every 100 rounds for game progression analysis.

Usage:
    python3 bc17_summary.py <match_file.bc17> [output_file.md]
    python3 bc17_summary.py <match_file.bc17> --json
"""

import gzip
import struct
import sys
import os
import re
import json
from datetime import datetime
from collections import defaultdict
from typing import Dict, List, Tuple, Optional, Any

# FlatBuffer event type constants
EVENT_NONE = 0
EVENT_GAME_HEADER = 1
EVENT_MATCH_HEADER = 2
EVENT_ROUND = 3
EVENT_MATCH_FOOTER = 4
EVENT_GAME_FOOTER = 5

# Body type constants (from BodyType enum)
BODY_TYPES = {
    0: 'ARCHON',
    1: 'GARDENER',
    2: 'LUMBERJACK',
    3: 'SOLDIER',
    4: 'TANK',
    5: 'SCOUT',
    6: 'TREE_BULLET',
    7: 'TREE_NEUTRAL',
    8: 'BULLET',
    9: 'NONE'
}

# Unit costs in bullets (from game constants)
UNIT_COSTS = {
    'ARCHON': 0,       # Can't be built
    'GARDENER': 100,
    'LUMBERJACK': 100,
    'SOLDIER': 100,
    'TANK': 300,
    'SCOUT': 80,
    'TREE_BULLET': 50,
}

# Combat unit types (excludes trees)
COMBAT_UNITS = {'ARCHON', 'GARDENER', 'LUMBERJACK', 'SOLDIER', 'TANK', 'SCOUT'}


class FlatBufferReader:
    """FlatBuffer reader for .bc17 files with proper table/vector parsing."""

    def __init__(self, data: bytes):
        self.data = data
        self.size = len(data)

    def read_int32(self, offset: int) -> int:
        if offset < 0 or offset + 4 > self.size:
            return 0
        return struct.unpack_from('<i', self.data, offset)[0]

    def read_uint32(self, offset: int) -> int:
        if offset < 0 or offset + 4 > self.size:
            return 0
        return struct.unpack_from('<I', self.data, offset)[0]

    def read_uint16(self, offset: int) -> int:
        if offset < 0 or offset + 2 > self.size:
            return 0
        return struct.unpack_from('<H', self.data, offset)[0]

    def read_int16(self, offset: int) -> int:
        if offset < 0 or offset + 2 > self.size:
            return 0
        return struct.unpack_from('<h', self.data, offset)[0]

    def read_float(self, offset: int) -> float:
        if offset < 0 or offset + 4 > self.size:
            return 0.0
        return struct.unpack_from('<f', self.data, offset)[0]

    def read_byte(self, offset: int) -> int:
        if offset < 0 or offset >= self.size:
            return 0
        return self.data[offset]

    def read_sbyte(self, offset: int) -> int:
        if offset < 0 or offset >= self.size:
            return 0
        return struct.unpack_from('<b', self.data, offset)[0]

    def get_root_table(self) -> int:
        """Get the position of the root table."""
        return self.read_int32(0)

    def get_vtable(self, table_pos: int) -> int:
        """Get vtable position for a table."""
        if table_pos <= 0 or table_pos >= self.size:
            return 0
        soffset = self.read_int32(table_pos)
        return table_pos - soffset

    def get_field_offset(self, table_pos: int, field_id: int) -> int:
        """Get offset of a field in a table. field_id is 0-indexed."""
        vtable = self.get_vtable(table_pos)
        if vtable <= 0 or vtable >= self.size:
            return 0

        vtable_size = self.read_uint16(vtable)
        field_offset_pos = vtable + 4 + field_id * 2

        if field_offset_pos >= vtable + vtable_size:
            return 0

        field_offset = self.read_uint16(field_offset_pos)
        if field_offset == 0:
            return 0

        return table_pos + field_offset

    def get_vector_start(self, field_offset: int) -> int:
        """Get the start of vector data (after length)."""
        if field_offset <= 0:
            return 0
        vector_offset = field_offset + self.read_int32(field_offset)
        return vector_offset + 4  # Skip length

    def get_vector_length(self, field_offset: int) -> int:
        """Get length of a vector."""
        if field_offset <= 0:
            return 0
        vector_offset = field_offset + self.read_int32(field_offset)
        if vector_offset <= 0 or vector_offset >= self.size:
            return 0
        return self.read_int32(vector_offset)

    def get_indirect(self, offset: int) -> int:
        """Follow an indirect offset (for nested tables)."""
        if offset <= 0 or offset >= self.size:
            return 0
        return offset + self.read_int32(offset)


class RoundData:
    """Data extracted from a single round."""
    def __init__(self):
        self.round_id = 0
        self.team_bullets = [0.0, 0.0]  # Team A (index 0), Team B (index 1)
        self.team_victory_points = [0, 0]
        self.spawned_units = {0: defaultdict(int), 1: defaultdict(int)}  # team -> {type: count}
        self.deaths = {0: 0, 1: 0}  # team -> count
        self.logs = ""


class GameSnapshot:
    """Snapshot of game state at a point in time."""
    def __init__(self, round_num: int):
        self.round = round_num
        self.team_a_bullets = 0.0
        self.team_b_bullets = 0.0
        self.team_a_vp = 0
        self.team_b_vp = 0
        self.team_a_units_produced = defaultdict(int)
        self.team_b_units_produced = defaultdict(int)
        self.team_a_units_lost = 0
        self.team_b_units_lost = 0
        self.team_a_bullets_spent = 0.0
        self.team_b_bullets_spent = 0.0
        self.team_a_bullets_earned = 0.0
        self.team_b_bullets_earned = 0.0


class BC17Parser:
    """Parser for .bc17 match replay files with full FlatBuffer support."""

    def __init__(self, filepath: str):
        self.filepath = filepath
        self.data = None
        self.reader = None
        self.rounds: List[RoundData] = []
        self.snapshots: List[GameSnapshot] = []
        self.total_rounds = 0
        self.winner = None

    def load(self) -> bool:
        """Load and decompress the .bc17 file."""
        try:
            with gzip.open(self.filepath, 'rb') as f:
                self.data = f.read()
            self.reader = FlatBufferReader(self.data)
            return True
        except Exception as e:
            print(f"Error loading file: {e}", file=sys.stderr)
            return False

    def parse_game_wrapper(self) -> bool:
        """Parse the root GameWrapper to get all events."""
        if not self.reader:
            return False

        try:
            # Root table position
            root_pos = self.reader.get_root_table()

            # GameWrapper fields:
            # 0: events (vector of EventWrapper)
            # 1: matchHeaders (vector of int)
            # 2: matchFooters (vector of int)

            events_offset = self.reader.get_field_offset(root_pos, 0)
            if events_offset <= 0:
                return False

            num_events = self.reader.get_vector_length(events_offset)
            events_start = self.reader.get_vector_start(events_offset)

            prev_bullets = [0.0, 0.0]
            current_snapshot = GameSnapshot(0)

            for i in range(num_events):
                # Each event is an offset to EventWrapper table
                event_ptr = events_start + i * 4
                event_offset = self.reader.get_indirect(event_ptr)

                if event_offset <= 0:
                    continue

                # EventWrapper fields:
                # 0: eType (byte)
                # 1: e (union - table offset)
                etype_offset = self.reader.get_field_offset(event_offset, 0)
                e_offset = self.reader.get_field_offset(event_offset, 1)

                if etype_offset <= 0:
                    continue

                event_type = self.reader.read_byte(etype_offset)

                if event_type == EVENT_ROUND and e_offset > 0:
                    round_table = self.reader.get_indirect(e_offset)
                    round_data = self.parse_round(round_table)

                    if round_data and round_data.round_id > 0:
                        self.rounds.append(round_data)

                        # Calculate bullets earned this round
                        bullets_earned_a = max(0, round_data.team_bullets[0] - prev_bullets[0])
                        bullets_earned_b = max(0, round_data.team_bullets[1] - prev_bullets[1])

                        # Calculate bullets spent on units
                        spent_a = sum(UNIT_COSTS.get(BODY_TYPES.get(t, ''), 0) * c
                                     for t, c in round_data.spawned_units[0].items())
                        spent_b = sum(UNIT_COSTS.get(BODY_TYPES.get(t, ''), 0) * c
                                     for t, c in round_data.spawned_units[1].items())

                        # Add to bullets earned if we spent (means we earned more than shown)
                        bullets_earned_a += spent_a
                        bullets_earned_b += spent_b

                        # Update current snapshot
                        current_snapshot.team_a_bullets = round_data.team_bullets[0]
                        current_snapshot.team_b_bullets = round_data.team_bullets[1]
                        current_snapshot.team_a_vp = round_data.team_victory_points[0]
                        current_snapshot.team_b_vp = round_data.team_victory_points[1]
                        current_snapshot.team_a_bullets_earned += bullets_earned_a
                        current_snapshot.team_b_bullets_earned += bullets_earned_b
                        current_snapshot.team_a_bullets_spent += spent_a
                        current_snapshot.team_b_bullets_spent += spent_b
                        current_snapshot.team_a_units_lost += round_data.deaths[0]
                        current_snapshot.team_b_units_lost += round_data.deaths[1]

                        for unit_type, count in round_data.spawned_units[0].items():
                            type_name = BODY_TYPES.get(unit_type, f'UNKNOWN_{unit_type}')
                            current_snapshot.team_a_units_produced[type_name] += count

                        for unit_type, count in round_data.spawned_units[1].items():
                            type_name = BODY_TYPES.get(unit_type, f'UNKNOWN_{unit_type}')
                            current_snapshot.team_b_units_produced[type_name] += count

                        prev_bullets = round_data.team_bullets[:]

                        # Save snapshot every 100 rounds
                        if round_data.round_id % 100 == 0:
                            current_snapshot.round = round_data.round_id
                            self.snapshots.append(current_snapshot)
                            current_snapshot = GameSnapshot(round_data.round_id)

                elif event_type == EVENT_MATCH_FOOTER and e_offset > 0:
                    footer_table = self.reader.get_indirect(e_offset)
                    self.parse_match_footer(footer_table)

            # Save final snapshot if we have remaining data
            if self.rounds and current_snapshot.round != self.rounds[-1].round_id:
                current_snapshot.round = self.rounds[-1].round_id
                self.snapshots.append(current_snapshot)

            return True

        except Exception as e:
            print(f"Error parsing game wrapper: {e}", file=sys.stderr)
            import traceback
            traceback.print_exc()
            return False

    def parse_round(self, table_pos: int) -> Optional[RoundData]:
        """Parse a Round table."""
        if table_pos <= 0:
            return None

        round_data = RoundData()

        try:
            # Round fields (0-indexed):
            # 0: teamIDs, 1: teamBullets, 2: teamVictoryPoints
            # 3: movedIDs, 4: movedLocs, 5: spawnedBodies
            # 6: spawnedBullets, 7: healthChangedIDs, 8: healthChangeLevels
            # 9: diedIDs, 10: diedBulletIDs
            # ...
            # 21: logs, 22: roundID

            # Get roundID (field 22)
            round_id_offset = self.reader.get_field_offset(table_pos, 22)
            if round_id_offset > 0:
                round_data.round_id = self.reader.read_int32(round_id_offset)

            # Get teamBullets (field 1) - vector of floats
            bullets_offset = self.reader.get_field_offset(table_pos, 1)
            if bullets_offset > 0:
                num_teams = self.reader.get_vector_length(bullets_offset)
                bullets_start = self.reader.get_vector_start(bullets_offset)
                for t in range(min(num_teams, 2)):
                    round_data.team_bullets[t] = self.reader.read_float(bullets_start + t * 4)

            # Get teamVictoryPoints (field 2) - vector of ints
            vp_offset = self.reader.get_field_offset(table_pos, 2)
            if vp_offset > 0:
                num_teams = self.reader.get_vector_length(vp_offset)
                vp_start = self.reader.get_vector_start(vp_offset)
                for t in range(min(num_teams, 2)):
                    round_data.team_victory_points[t] = self.reader.read_int32(vp_start + t * 4)

            # Get spawnedBodies (field 5) - SpawnedBodyTable
            spawned_offset = self.reader.get_field_offset(table_pos, 5)
            if spawned_offset > 0:
                spawned_table = self.reader.get_indirect(spawned_offset)
                self.parse_spawned_bodies(spawned_table, round_data)

            # Get diedIDs (field 9) - vector of ints (robot IDs that died)
            # Note: We can't easily determine which team they belonged to without tracking
            died_offset = self.reader.get_field_offset(table_pos, 9)
            if died_offset > 0:
                num_died = self.reader.get_vector_length(died_offset)
                # For now, we'll track total deaths and estimate team distribution later

            return round_data

        except Exception as e:
            return None

    def parse_spawned_bodies(self, table_pos: int, round_data: RoundData):
        """Parse SpawnedBodyTable to get unit spawns."""
        if table_pos <= 0:
            return

        try:
            # SpawnedBodyTable fields:
            # 0: robotIDs (vector of int)
            # 1: teamIDs (vector of byte)
            # 2: types (vector of byte - BodyType)
            # 3: locs (VecTable)

            team_ids_offset = self.reader.get_field_offset(table_pos, 1)
            types_offset = self.reader.get_field_offset(table_pos, 2)

            if team_ids_offset <= 0 or types_offset <= 0:
                return

            num_bodies = self.reader.get_vector_length(team_ids_offset)
            teams_start = self.reader.get_vector_start(team_ids_offset)
            types_start = self.reader.get_vector_start(types_offset)

            for i in range(num_bodies):
                team_id = self.reader.read_byte(teams_start + i)
                body_type = self.reader.read_byte(types_start + i)

                # team_id: 1 = Team A, 2 = Team B (convert to 0-indexed)
                team_idx = team_id - 1 if team_id in (1, 2) else -1
                if team_idx >= 0:
                    round_data.spawned_units[team_idx][body_type] += 1

        except Exception:
            pass

    def parse_match_footer(self, table_pos: int):
        """Parse MatchFooter for winner and total rounds."""
        if table_pos <= 0:
            return

        try:
            # MatchFooter fields:
            # 0: winner (byte)
            # 1: totalRounds (int)

            winner_offset = self.reader.get_field_offset(table_pos, 0)
            rounds_offset = self.reader.get_field_offset(table_pos, 1)

            if winner_offset > 0:
                self.winner = self.reader.read_byte(winner_offset)

            if rounds_offset > 0:
                self.total_rounds = self.reader.read_int32(rounds_offset)

        except Exception:
            pass

    def extract_logs_from_binary(self) -> List[Dict]:
        """Extract log strings from binary using regex pattern matching."""
        logs = []
        pattern = rb'\[([AB]):([A-Z_]+)#(\d+)@(\d+)\]\s*([^\[\x00]*)'

        for match in re.finditer(pattern, self.data):
            try:
                team = match.group(1).decode('utf-8')
                robot_type = match.group(2).decode('utf-8')
                robot_id = int(match.group(3))
                round_num = int(match.group(4))
                message = match.group(5).decode('utf-8', errors='replace').strip()
                message = re.sub(r'[\x00-\x1f\x7f-\xff].*', '', message).strip()

                if message:
                    logs.append({
                        'team': team,
                        'type': robot_type,
                        'id': robot_id,
                        'round': round_num,
                        'message': message
                    })
            except Exception:
                continue

        logs.sort(key=lambda x: x['round'])
        return logs

    def parse_metadata_from_filename(self) -> Dict[str, Any]:
        """Parse match metadata from filename."""
        metadata = {
            'filename': os.path.basename(self.filepath),
            'file_size_bytes': len(self.data) if self.data else 0,
            'teams': [],
            'map_name': 'Unknown',
            'winner': None,
            'total_rounds': 0
        }

        basename = os.path.splitext(os.path.basename(self.filepath))[0]
        if '-vs-' in basename and '-on-' in basename:
            parts = basename.split('-vs-')
            if len(parts) == 2:
                team_a = parts[0]
                rest = parts[1].split('-on-')
                if len(rest) == 2:
                    team_b = rest[0]
                    map_name = rest[1]
                    metadata['teams'] = [team_a, team_b]
                    metadata['map_name'] = map_name

        metadata['file_size_kb'] = round(metadata['file_size_bytes'] / 1024, 1)
        return metadata

    def parse(self) -> Dict[str, Any]:
        """Parse the match file and return structured data."""
        if not self.data:
            return {}

        metadata = self.parse_metadata_from_filename()
        logs = self.extract_logs_from_binary()

        # Parse the FlatBuffer for game state
        self.parse_game_wrapper()

        # Update metadata with parsed info
        if self.winner is not None:
            metadata['winner'] = 'A' if self.winner == 1 else 'B' if self.winner == 2 else f'Team {self.winner}'
        if self.total_rounds > 0:
            metadata['total_rounds'] = self.total_rounds
        elif self.rounds:
            metadata['total_rounds'] = max(r.round_id for r in self.rounds)

        return {
            'metadata': metadata,
            'logs': logs,
            'snapshots': self.snapshots,
            'rounds_parsed': len(self.rounds),
            'log_count': len(logs)
        }


class MatchSummarizer:
    """Generate LLM-friendly summaries from parsed match data."""

    MAX_LOGS_PER_TYPE = 5
    MAX_TOTAL_LOGS = 50
    MAX_RAW_LOGS = 20

    def __init__(self, parsed_data: Dict[str, Any]):
        self.data = parsed_data
        self.metadata = parsed_data.get('metadata', {})
        self.logs = parsed_data.get('logs', [])
        self.snapshots = parsed_data.get('snapshots', [])

    def categorize_logs(self) -> Dict[str, List[Dict]]:
        """Categorize logs by type and importance."""
        categorized = {
            'errors': [],
            'strategic': [],
            'spawn': [],
            'other': []
        }

        for log in self.logs:
            msg = log['message'].lower()
            if 'exception' in msg or 'error' in msg:
                categorized['errors'].append(log)
            elif any(word in msg for word in ["i'm a", "i'm an", "spawned", "created"]):
                categorized['spawn'].append(log)
            elif any(word in msg for word in ['attack', 'move', 'target', 'enemy', 'retreat', 'defend', 'shoot', 'fire']):
                categorized['strategic'].append(log)
            else:
                categorized['other'].append(log)

        return categorized

    def deduplicate_logs(self, logs: List[Dict]) -> List[Dict]:
        """Deduplicate similar log messages."""
        seen_messages = {}
        deduped = []

        for log in logs:
            key = (log['team'], log['type'], log['message'])
            if key not in seen_messages:
                seen_messages[key] = {'count': 1, 'first_round': log['round'], 'last_round': log['round']}
                deduped.append(log.copy())
            else:
                seen_messages[key]['count'] += 1
                seen_messages[key]['last_round'] = log['round']

        for log in deduped:
            key = (log['team'], log['type'], log['message'])
            info = seen_messages[key]
            log['count'] = info['count']
            log['first_round'] = info['first_round']
            log['last_round'] = info['last_round']

        return deduped

    def get_round_range(self) -> Tuple[int, int]:
        """Get the range of rounds covered by logs."""
        if not self.logs:
            return (0, 0)
        rounds = [log['round'] for log in self.logs]
        return (min(rounds), max(rounds))

    def get_team_stats(self) -> Dict[str, Dict]:
        """Get statistics per team from logs."""
        stats = {'A': defaultdict(int), 'B': defaultdict(int)}
        for log in self.logs:
            team = log['team']
            stats[team]['total_logs'] += 1
            stats[team][f"{log['type']}_logs"] += 1
        return {k: dict(v) for k, v in stats.items()}

    def format_snapshot(self, snapshot: GameSnapshot, prev_snapshot: Optional[GameSnapshot] = None) -> List[str]:
        """Format a single snapshot as markdown lines."""
        lines = []

        # Calculate period stats (changes since last snapshot)
        if prev_snapshot:
            period_start = prev_snapshot.round + 1
        else:
            period_start = 1

        lines.append(f"### Round {snapshot.round} (Period: R{period_start}-R{snapshot.round})")
        lines.append("")
        lines.append("| Metric | Team A | Team B |")
        lines.append("|--------|--------|--------|")
        lines.append(f"| **Current Bullets** | {snapshot.team_a_bullets:.1f} | {snapshot.team_b_bullets:.1f} |")
        lines.append(f"| **Victory Points** | {snapshot.team_a_vp} | {snapshot.team_b_vp} |")
        lines.append(f"| Bullets Earned (period) | {snapshot.team_a_bullets_earned:.1f} | {snapshot.team_b_bullets_earned:.1f} |")
        lines.append(f"| Bullets Spent (period) | {snapshot.team_a_bullets_spent:.1f} | {snapshot.team_b_bullets_spent:.1f} |")
        lines.append(f"| Units Lost (period) | {snapshot.team_a_units_lost} | {snapshot.team_b_units_lost} |")

        # Units produced this period
        all_unit_types = set(snapshot.team_a_units_produced.keys()) | set(snapshot.team_b_units_produced.keys())
        combat_types = [t for t in all_unit_types if t in COMBAT_UNITS]

        if combat_types:
            lines.append("")
            lines.append("**Units Produced (period):**")
            for unit_type in sorted(combat_types):
                a_count = snapshot.team_a_units_produced.get(unit_type, 0)
                b_count = snapshot.team_b_units_produced.get(unit_type, 0)
                if a_count > 0 or b_count > 0:
                    lines.append(f"- {unit_type}: A={a_count}, B={b_count}")

        lines.append("")
        return lines

    def generate_summary(self, include_raw_logs: bool = True) -> str:
        """Generate a markdown summary of the match."""
        lines = []

        # Header
        teams = self.metadata.get('teams', ['Unknown', 'Unknown'])
        map_name = self.metadata.get('map_name', 'Unknown')
        filename = self.metadata.get('filename', 'unknown.bc17')
        file_size = self.metadata.get('file_size_kb', 0)
        winner = self.metadata.get('winner')
        total_rounds = self.metadata.get('total_rounds', 0)

        team_a = teams[0] if len(teams) > 0 else 'Unknown'
        team_b = teams[1] if len(teams) > 1 else 'Unknown'

        lines.append("# Battlecode 2017 Match Summary")
        lines.append("")
        lines.append("## Match Information")
        lines.append(f"- **File**: `{filename}` ({file_size} KB)")
        lines.append(f"- **Team A**: {team_a}")
        lines.append(f"- **Team B**: {team_b}")
        lines.append(f"- **Map**: {map_name}")

        if total_rounds > 0:
            lines.append(f"- **Total Rounds**: {total_rounds}")

        if winner:
            winner_name = team_a if winner == 'A' else team_b if winner == 'B' else winner
            lines.append(f"- **Winner**: Team {winner} ({winner_name})")

        lines.append(f"- **Total log entries**: {len(self.logs)}")
        lines.append("")

        # Game Timeline Snapshots (every 100 rounds)
        if self.snapshots:
            lines.append("## Game Timeline (Snapshots every 100 rounds)")
            lines.append("")
            lines.append("Each snapshot shows the cumulative state and what happened in the preceding period.")
            lines.append("")

            prev_snapshot = None
            for snapshot in self.snapshots:
                lines.extend(self.format_snapshot(snapshot, prev_snapshot))
                prev_snapshot = snapshot
        else:
            lines.append("## Game Timeline")
            lines.append("")
            lines.append("*No game state data could be extracted from this match file.*")
            lines.append("")

        # Log Activity Summary
        stats = self.get_team_stats()
        lines.append("## Log Activity by Team")
        lines.append("")

        for team, label in [('A', team_a), ('B', team_b)]:
            team_stats = stats.get(team, {})
            lines.append(f"### Team {team} ({label})")
            if team_stats:
                for key, value in sorted(team_stats.items()):
                    lines.append(f"- {key}: {value}")
            else:
                lines.append("- No logs recorded")
            lines.append("")

        # Categorized logs
        categorized = self.categorize_logs()

        # Errors
        if categorized['errors']:
            lines.append("## Errors and Exceptions (IMPORTANT)")
            lines.append("")
            for log in categorized['errors'][:20]:
                lines.append(f"- **[R{log['round']}]** Team {log['team']} {log['type']}#{log['id']}: `{log['message']}`")
            lines.append("")

        # Strategic logs
        if categorized['strategic']:
            deduped = self.deduplicate_logs(categorized['strategic'])
            lines.append("## Strategic Decisions")
            lines.append("")
            for log in deduped[:self.MAX_LOGS_PER_TYPE * 2]:
                count_str = f" (x{log['count']}, R{log['first_round']}-R{log['last_round']})" if log.get('count', 1) > 1 else ""
                lines.append(f"- Team {log['team']} {log['type']}: {log['message']}{count_str}")
            lines.append("")

        # Spawn messages
        if categorized['spawn']:
            deduped = self.deduplicate_logs(categorized['spawn'])
            lines.append("## Unit Spawn Logs")
            lines.append("")
            for log in deduped[:self.MAX_LOGS_PER_TYPE]:
                count_str = f" (x{log['count']})" if log.get('count', 1) > 1 else ""
                lines.append(f"- Team {log['team']} {log['type']}: {log['message']}{count_str}")
            lines.append("")

        # Raw logs sample
        if include_raw_logs and self.logs:
            sample_size = min(self.MAX_RAW_LOGS, len(self.logs))
            lines.append(f"## Raw Log Sample (First {sample_size} entries)")
            lines.append("")
            lines.append("```")
            for log in self.logs[:sample_size]:
                lines.append(f"[{log['team']}:{log['type']}#{log['id']}@{log['round']}] {log['message']}")
            if len(self.logs) > sample_size:
                lines.append(f"... and {len(self.logs) - sample_size} more entries")
            lines.append("```")
            lines.append("")

        # Context for LLM
        lines.append("## Context for Analysis")
        lines.append("")
        lines.append("**Battlecode 2017 Game Rules:**")
        lines.append("- Each team starts with ARCHON units that hire GARDENERs (cost: 100 bullets)")
        lines.append("- GARDENERs build: SOLDIER (100), LUMBERJACK (100), TANK (300), SCOUT (80)")
        lines.append("- Teams earn bullets from trees and can win by 1000 victory points or elimination")
        lines.append("- Team A starts on the left, Team B on the right")
        lines.append("")
        lines.append("**Snapshot Interpretation:**")
        lines.append("- 'Bullets Earned' = total income in that 100-round period")
        lines.append("- 'Bullets Spent' = cost of units produced in that period")
        lines.append("- Higher bullet counts indicate better economy")
        lines.append("- More units produced suggests aggressive expansion")
        lines.append("")

        return "\n".join(lines)

    def generate_json(self) -> str:
        """Generate a JSON summary for programmatic use."""
        categorized = self.categorize_logs()

        snapshots_data = []
        for s in self.snapshots:
            snapshots_data.append({
                'round': s.round,
                'team_a': {
                    'bullets': s.team_a_bullets,
                    'victory_points': s.team_a_vp,
                    'bullets_earned': s.team_a_bullets_earned,
                    'bullets_spent': s.team_a_bullets_spent,
                    'units_produced': dict(s.team_a_units_produced),
                    'units_lost': s.team_a_units_lost
                },
                'team_b': {
                    'bullets': s.team_b_bullets,
                    'victory_points': s.team_b_vp,
                    'bullets_earned': s.team_b_bullets_earned,
                    'bullets_spent': s.team_b_bullets_spent,
                    'units_produced': dict(s.team_b_units_produced),
                    'units_lost': s.team_b_units_lost
                }
            })

        summary = {
            'metadata': self.metadata,
            'snapshots': snapshots_data,
            'round_range': self.get_round_range(),
            'team_stats': self.get_team_stats(),
            'log_summary': {
                'total': len(self.logs),
                'errors': len(categorized['errors']),
                'strategic': len(categorized['strategic']),
                'spawn': len(categorized['spawn']),
                'other': len(categorized['other'])
            },
            'log_sample': [
                {'team': l['team'], 'type': l['type'], 'id': l['id'], 'round': l['round'], 'message': l['message']}
                for l in self.logs[:self.MAX_TOTAL_LOGS]
            ]
        }
        return json.dumps(summary, indent=2, default=str)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    input_file = sys.argv[1]

    if not os.path.exists(input_file):
        print(f"Error: File not found: {input_file}", file=sys.stderr)
        sys.exit(1)

    parser = BC17Parser(input_file)
    if not parser.load():
        sys.exit(1)

    parsed = parser.parse()
    summarizer = MatchSummarizer(parsed)

    output_json = '--json' in sys.argv

    if output_json:
        output = summarizer.generate_json()
    else:
        output = summarizer.generate_summary()

    # Determine output file
    output_file = None
    for i, arg in enumerate(sys.argv[2:], 2):
        if not arg.startswith('--'):
            output_file = arg
            break

    if output_file:
        os.makedirs(os.path.dirname(output_file) or '.', exist_ok=True)
        with open(output_file, 'w') as f:
            f.write(output)
        print(f"Summary written to: {output_file}")
    else:
        print(output)


if __name__ == '__main__':
    main()
