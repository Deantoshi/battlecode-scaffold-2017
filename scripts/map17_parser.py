#!/usr/bin/env python3
"""
Battlecode 2017 Map Parser

Parses .map17 files and extracts map information for LLM context:
- Map dimensions (width x height)
- Neutral tree positions, radii, and contents
- Initial robot positions

Usage:
    python3 map17_parser.py <map_file.map17>
    python3 map17_parser.py <map_file.map17> --json
    python3 map17_parser.py <map_file.map17> --ascii

The .map17 files use FlatBuffers with GameMap as the root table.
"""

import struct
import sys
import os
import json
from typing import Dict, List, Tuple, Optional, Any


# Body type constants (from BodyType enum in battlecode.fbs)
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


class FlatBufferReader:
    """FlatBuffer reader for binary parsing."""

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

    def read_float(self, offset: int) -> float:
        if offset < 0 or offset + 4 > self.size:
            return 0.0
        return struct.unpack_from('<f', self.data, offset)[0]

    def read_byte(self, offset: int) -> int:
        if offset < 0 or offset >= self.size:
            return 0
        return self.data[offset]

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

        if field_offset_pos + 2 > vtable + vtable_size:
            return 0

        return self.read_uint16(field_offset_pos)

    def get_field_table(self, table_pos: int, field_id: int) -> int:
        """Get a nested table field."""
        offset = self.get_field_offset(table_pos, field_id)
        if offset == 0:
            return 0
        field_pos = table_pos + offset
        indirect = self.read_int32(field_pos)
        return field_pos + indirect

    def get_field_vector(self, table_pos: int, field_id: int) -> Tuple[int, int]:
        """Get vector position and length."""
        offset = self.get_field_offset(table_pos, field_id)
        if offset == 0:
            return (0, 0)
        field_pos = table_pos + offset
        vec_offset = self.read_int32(field_pos)
        vec_pos = field_pos + vec_offset
        vec_len = self.read_int32(vec_pos)
        return (vec_pos + 4, vec_len)

    def get_field_string(self, table_pos: int, field_id: int) -> str:
        """Get a string field."""
        offset = self.get_field_offset(table_pos, field_id)
        if offset == 0:
            return ""
        string_offset_pos = table_pos + offset
        string_offset = self.read_int32(string_offset_pos)
        string_pos = string_offset_pos + string_offset
        string_len = self.read_int32(string_pos)
        if string_len <= 0 or string_pos + 4 + string_len > self.size:
            return ""
        return self.data[string_pos + 4:string_pos + 4 + string_len].decode('utf-8', errors='replace')

    def get_field_struct_float(self, table_pos: int, field_id: int, struct_offset: int) -> float:
        """Get a float from an inline struct field."""
        offset = self.get_field_offset(table_pos, field_id)
        if offset == 0:
            return 0.0
        return self.read_float(table_pos + offset + struct_offset)


class Map17Parser:
    """Parser for .map17 files."""

    def __init__(self, filepath: str):
        self.filepath = filepath
        self.reader = None
        self.map_data = {}

    def load(self) -> bool:
        """Load and parse the map file."""
        try:
            with open(self.filepath, 'rb') as f:
                data = f.read()
            self.reader = FlatBufferReader(data)
            return True
        except Exception as e:
            print(f"Error loading map file: {e}", file=sys.stderr)
            return False

    def parse(self) -> Dict[str, Any]:
        """Parse the map and extract all information."""
        if not self.reader:
            return {}

        root = self.reader.get_root_table()

        # GameMap fields (from battlecode.fbs):
        # 0: name (string)
        # 1: minCorner (Vec struct - x:float, y:float)
        # 2: maxCorner (Vec struct - x:float, y:float)
        # 3: bodies (SpawnedBodyTable)
        # 4: trees (NeutralTreeTable)
        # 5: randomSeed (int)

        self.map_data = {
            'name': self.reader.get_field_string(root, 0),
            'min_corner': {
                'x': self.reader.get_field_struct_float(root, 1, 0),
                'y': self.reader.get_field_struct_float(root, 1, 4),
            },
            'max_corner': {
                'x': self.reader.get_field_struct_float(root, 2, 0),
                'y': self.reader.get_field_struct_float(root, 2, 4),
            },
            'initial_bodies': [],
            'neutral_trees': [],
        }

        # Calculate dimensions
        self.map_data['width'] = self.map_data['max_corner']['x'] - self.map_data['min_corner']['x']
        self.map_data['height'] = self.map_data['max_corner']['y'] - self.map_data['min_corner']['y']

        # Parse initial bodies (SpawnedBodyTable)
        bodies_table = self.reader.get_field_table(root, 3)
        if bodies_table:
            self._parse_spawned_bodies(bodies_table)

        # Parse neutral trees (NeutralTreeTable)
        trees_table = self.reader.get_field_table(root, 4)
        if trees_table:
            self._parse_neutral_trees(trees_table)

        return self.map_data

    def _parse_spawned_bodies(self, table_pos: int):
        """Parse SpawnedBodyTable for initial robots."""
        # SpawnedBodyTable fields:
        # 0: robotIDs ([int])
        # 1: teamIDs ([byte])
        # 2: types ([byte] - BodyType enum)
        # 3: locs (VecTable)

        ids_pos, ids_len = self.reader.get_field_vector(table_pos, 0)
        teams_pos, teams_len = self.reader.get_field_vector(table_pos, 1)
        types_pos, types_len = self.reader.get_field_vector(table_pos, 2)

        # Get locs (VecTable)
        locs_table = self.reader.get_field_table(table_pos, 3)
        xs_pos, xs_len = (0, 0)
        ys_pos, ys_len = (0, 0)
        if locs_table:
            xs_pos, xs_len = self.reader.get_field_vector(locs_table, 0)
            ys_pos, ys_len = self.reader.get_field_vector(locs_table, 1)

        count = min(ids_len, teams_len, types_len, xs_len, ys_len) if xs_len > 0 else 0

        for i in range(count):
            robot_id = self.reader.read_int32(ids_pos + i * 4)
            team_id = self.reader.read_byte(teams_pos + i)
            body_type = self.reader.read_byte(types_pos + i)
            x = self.reader.read_float(xs_pos + i * 4)
            y = self.reader.read_float(ys_pos + i * 4)

            self.map_data['initial_bodies'].append({
                'id': robot_id,
                'team': 'A' if team_id == 0 else 'B',
                'type': BODY_TYPES.get(body_type, f'UNKNOWN_{body_type}'),
                'x': x,
                'y': y,
            })

    def _parse_neutral_trees(self, table_pos: int):
        """Parse NeutralTreeTable for neutral trees."""
        # NeutralTreeTable fields:
        # 0: robotIDs ([int])
        # 1: locs (VecTable)
        # 2: radii ([float])
        # 3: healths ([float])
        # 4: maxHealths ([float])
        # 5: containedBullets ([int])
        # 6: containedBodies ([byte] - BodyType enum)

        ids_pos, ids_len = self.reader.get_field_vector(table_pos, 0)
        radii_pos, radii_len = self.reader.get_field_vector(table_pos, 2)
        healths_pos, healths_len = self.reader.get_field_vector(table_pos, 3)
        max_healths_pos, max_healths_len = self.reader.get_field_vector(table_pos, 4)
        bullets_pos, bullets_len = self.reader.get_field_vector(table_pos, 5)
        bodies_pos, bodies_len = self.reader.get_field_vector(table_pos, 6)

        # Get locs (VecTable)
        locs_table = self.reader.get_field_table(table_pos, 1)
        xs_pos, xs_len = (0, 0)
        ys_pos, ys_len = (0, 0)
        if locs_table:
            xs_pos, xs_len = self.reader.get_field_vector(locs_table, 0)
            ys_pos, ys_len = self.reader.get_field_vector(locs_table, 1)

        count = ids_len

        for i in range(count):
            tree_id = self.reader.read_int32(ids_pos + i * 4) if ids_len > i else 0
            x = self.reader.read_float(xs_pos + i * 4) if xs_len > i else 0.0
            y = self.reader.read_float(ys_pos + i * 4) if ys_len > i else 0.0
            radius = self.reader.read_float(radii_pos + i * 4) if radii_len > i else 1.0
            health = self.reader.read_float(healths_pos + i * 4) if healths_len > i else 0.0
            max_health = self.reader.read_float(max_healths_pos + i * 4) if max_healths_len > i else 0.0
            contained_bullets = self.reader.read_int32(bullets_pos + i * 4) if bullets_len > i else 0
            contained_body = self.reader.read_byte(bodies_pos + i) if bodies_len > i else 9  # NONE

            self.map_data['neutral_trees'].append({
                'id': tree_id,
                'x': x,
                'y': y,
                'radius': radius,
                'health': health,
                'max_health': max_health,
                'contained_bullets': contained_bullets,
                'contained_body': BODY_TYPES.get(contained_body, 'NONE'),
            })


def format_for_llm(map_data: Dict[str, Any]) -> str:
    """Format map data for LLM context."""
    lines = []
    lines.append(f"## Map: {map_data['name']}")
    lines.append("")
    lines.append(f"**Dimensions:** {map_data['width']:.0f} x {map_data['height']:.0f}")
    lines.append(f"**Origin:** ({map_data['min_corner']['x']:.1f}, {map_data['min_corner']['y']:.1f})")
    lines.append(f"**Max Corner:** ({map_data['max_corner']['x']:.1f}, {map_data['max_corner']['y']:.1f})")
    lines.append("")

    # Initial bodies
    if map_data['initial_bodies']:
        lines.append("### Initial Units")
        team_a = [b for b in map_data['initial_bodies'] if b['team'] == 'A']
        team_b = [b for b in map_data['initial_bodies'] if b['team'] == 'B']

        if team_a:
            lines.append(f"**Team A:** {len(team_a)} units")
            for b in team_a:
                lines.append(f"  - {b['type']} at ({b['x']:.1f}, {b['y']:.1f})")

        if team_b:
            lines.append(f"**Team B:** {len(team_b)} units")
            for b in team_b:
                lines.append(f"  - {b['type']} at ({b['x']:.1f}, {b['y']:.1f})")
        lines.append("")

    # Neutral trees
    if map_data['neutral_trees']:
        lines.append("### Neutral Trees")
        lines.append(f"**Total:** {len(map_data['neutral_trees'])} trees")
        lines.append("")

        # Summary by contents
        with_bullets = [t for t in map_data['neutral_trees'] if t['contained_bullets'] > 0]
        with_bodies = [t for t in map_data['neutral_trees'] if t['contained_body'] != 'NONE']
        total_bullets = sum(t['contained_bullets'] for t in map_data['neutral_trees'])

        if total_bullets > 0:
            lines.append(f"**Total bullets in trees:** {total_bullets}")
            lines.append(f"**Trees with bullets:** {len(with_bullets)}")

        if with_bodies:
            lines.append(f"**Trees with robots:** {len(with_bodies)}")
            for t in with_bodies:
                lines.append(f"  - {t['contained_body']} at ({t['x']:.1f}, {t['y']:.1f}), radius {t['radius']:.1f}")

        lines.append("")
        lines.append("**Tree positions (x, y, radius, bullets):**")

        # Group trees by quadrant for spatial overview
        min_x = map_data['min_corner']['x']
        min_y = map_data['min_corner']['y']
        mid_x = min_x + map_data['width'] / 2
        mid_y = min_y + map_data['height'] / 2

        quadrants = {'NW': [], 'NE': [], 'SW': [], 'SE': []}
        for t in map_data['neutral_trees']:
            if t['x'] < mid_x:
                q = 'NW' if t['y'] >= mid_y else 'SW'
            else:
                q = 'NE' if t['y'] >= mid_y else 'SE'
            quadrants[q].append(t)

        for q_name in ['NW', 'NE', 'SW', 'SE']:
            trees = quadrants[q_name]
            if trees:
                lines.append(f"  {q_name} quadrant: {len(trees)} trees")
                for t in trees[:5]:  # Show first 5 per quadrant
                    bullet_info = f", {t['contained_bullets']}b" if t['contained_bullets'] > 0 else ""
                    lines.append(f"    ({t['x']:.1f}, {t['y']:.1f}) r={t['radius']:.1f}{bullet_info}")
                if len(trees) > 5:
                    lines.append(f"    ... and {len(trees) - 5} more")

    return "\n".join(lines)


def generate_ascii_map(map_data: Dict[str, Any], width: int = 60, height: int = 30) -> str:
    """Generate ASCII art representation of the map."""
    min_x = map_data['min_corner']['x']
    min_y = map_data['min_corner']['y']
    map_width = map_data['width']
    map_height = map_data['height']

    # Create grid
    grid = [[' ' for _ in range(width)] for _ in range(height)]

    def to_grid(x: float, y: float) -> Tuple[int, int]:
        gx = int((x - min_x) / map_width * (width - 1))
        gy = int((map_height - (y - min_y)) / map_height * (height - 1))  # Flip y
        return (max(0, min(width - 1, gx)), max(0, min(height - 1, gy)))

    # Plot neutral trees
    for tree in map_data['neutral_trees']:
        gx, gy = to_grid(tree['x'], tree['y'])
        if tree['contained_bullets'] > 0:
            grid[gy][gx] = '$'  # Tree with bullets
        elif tree['contained_body'] != 'NONE':
            grid[gy][gx] = '?'  # Tree with robot
        else:
            grid[gy][gx] = 'T'  # Regular tree

    # Plot initial bodies
    for body in map_data['initial_bodies']:
        gx, gy = to_grid(body['x'], body['y'])
        char = body['type'][0]  # First letter (A for Archon, G for Gardener, etc.)
        if body['team'] == 'A':
            char = char.upper()
        else:
            char = char.lower()
        grid[gy][gx] = char

    # Build output
    lines = []
    lines.append(f"Map: {map_data['name']} ({map_data['width']:.0f}x{map_data['height']:.0f})")
    lines.append("Legend: A/a=Archon, G/g=Gardener, S/s=Soldier, L/l=Lumberjack")
    lines.append("        T=Tree, $=Tree+bullets, ?=Tree+robot, UPPER=TeamA, lower=TeamB")
    lines.append("+" + "-" * width + "+")
    for row in grid:
        lines.append("|" + "".join(row) + "|")
    lines.append("+" + "-" * width + "+")

    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    map_file = sys.argv[1]
    output_json = '--json' in sys.argv
    output_ascii = '--ascii' in sys.argv

    parser = Map17Parser(map_file)
    if not parser.load():
        sys.exit(1)

    map_data = parser.parse()

    if output_json:
        print(json.dumps(map_data, indent=2))
    elif output_ascii:
        print(generate_ascii_map(map_data))
    else:
        print(format_for_llm(map_data))


if __name__ == '__main__':
    main()
