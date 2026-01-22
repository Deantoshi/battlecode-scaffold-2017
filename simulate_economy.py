#!/usr/bin/env python3
"""
Battlecode 2017 Economy Simulator

Parses a bot's Java source files and simulates unit/resource production
assuming no combat losses. Outputs a timeline of builds and final army composition.

Usage: python3 simulate_economy.py <bot_directory> [--rounds N]
"""

import os
import re
import sys
import argparse
from dataclasses import dataclass, field
from typing import List, Dict, Optional, Tuple

# ============================================================================
# GAME CONSTANTS (from HOW_TO_PLAY_BATTLE_CODE_2017.md)
# ============================================================================

INITIAL_BULLETS = 300
MAX_ROUNDS = 3000

# Unit costs
UNIT_COSTS = {
    'ARCHON': 0,       # Archons are free (starting units)
    'GARDENER': 100,
    'LUMBERJACK': 100,
    'SOLDIER': 100,
    'TANK': 300,
    'SCOUT': 80,
}

# Tree constants
TREE_PLANT_COST = 50
TREE_MATURATION_ROUNDS = 80
TREE_MAX_HEALTH = 40.0  # radius 1.0 * some factor, team trees have radius 1
TREE_INCOME_PER_HEALTH = 1.0 / 50.0  # health / 50 per round
TREE_DECAY_RATE = 0.5  # per round after maturation
TREE_WATER_HEAL = 5.0  # healing per water action

# Build cooldown
BUILD_COOLDOWN = 10


# Victory Points
VP_BASE_COST = 7.5
VP_INCREASE_PER_ROUND = 12.5 / 3000.0
VICTORY_VP = 1000.0


# ============================================================================
# DATA STRUCTURES
# ============================================================================

@dataclass
class DonationRule:
    min_round: int = 0
    max_round: int = MAX_ROUNDS
    trigger_bullets: float = 1000.0
    keep_bullets: float = 0.0

@dataclass
class Tree:
    planted_round: int
    health: float = 8.0  # 20% of max (40) = 8
    max_health: float = 40.0

    def is_mature(self, current_round: int) -> bool:
        return (current_round - self.planted_round) >= TREE_MATURATION_ROUNDS

    def get_income(self, current_round: int) -> float:
        if self.is_mature(current_round) and self.health > 0:
            return self.health / 50.0
        return 0.0

    def tick(self, current_round: int, watered: bool = False):
        """Update tree for one round."""
        if not self.is_mature(current_round):
            # Growing phase: health increases
            growth_per_round = (self.max_health - 8.0) / TREE_MATURATION_ROUNDS
            self.health = min(self.max_health, self.health + growth_per_round)
        else:
            # Producing phase: decay
            self.health -= TREE_DECAY_RATE

        # Watering heals
        if watered:
            self.health = min(self.max_health, self.health + TREE_WATER_HEAL)


@dataclass
class Unit:
    unit_type: str
    built_round: int

    def is_active(self, current_round: int) -> bool:
        """Units built by Gardener are inactive for 20 rounds."""
        if self.unit_type in ['ARCHON', 'GARDENER']:
            return True
        return (current_round - self.built_round) >= 20


@dataclass
class BotProfile:
    """Extracted build logic from bot source."""
    name: str
    max_gardeners: int = 99
    max_trees_per_gardener: int = 6

    # Build order: list of (unit_type, count) before cycling
    initial_build_order: List[Tuple[str, int]] = field(default_factory=list)

    # Cycling build pattern after initial
    cycle_pattern: List[str] = field(default_factory=list)

    # Resource thresholds
    min_bullets_for_build: float = 0

    # Flags
    waters_trees: bool = True

    # Donation logic
    donation_rules: List[DonationRule] = field(default_factory=list)


@dataclass
class GameState:
    """Current simulation state."""
    round_num: int = 0
    bullets: float = INITIAL_BULLETS
    vp: float = 0.0

    units: List[Unit] = field(default_factory=list)
    trees: List[Tree] = field(default_factory=list)

    # Build tracking
    gardeners_hired: int = 0
    trees_planted: int = 0
    units_built: Dict[str, int] = field(default_factory=lambda: {
        'GARDENER': 0, 'LUMBERJACK': 0, 'SOLDIER': 0, 'TANK': 0, 'SCOUT': 0
    })

    # Cooldowns (per gardener, simplified to single gardener)
    gardener_cooldown: int = 0
    archon_cooldown: int = 0

    # Event log
    events: List[Tuple[int, str]] = field(default_factory=list)

    def log(self, msg: str):
        self.events.append((self.round_num, msg))


# ============================================================================
# BOT PARSER
# ============================================================================

def parse_bot(bot_dir: str) -> BotProfile:
    """Parse Java source files to extract build logic."""
    profile = BotProfile(name=os.path.basename(bot_dir))

    archon_file = os.path.join(bot_dir, 'Archon.java')
    gardener_file = os.path.join(bot_dir, 'Gardener.java')
    robot_player_file = os.path.join(bot_dir, 'RobotPlayer.java')

    # Read source files
    archon_code = ""
    if os.path.exists(archon_file):
        with open(archon_file, 'r') as f:
            archon_code = f.read()

    gardener_code = ""
    if os.path.exists(gardener_file):
        with open(gardener_file, 'r') as f:
            gardener_code = f.read()

    robot_player_code = ""
    if os.path.exists(robot_player_file):
        with open(robot_player_file, 'r') as f:
            robot_player_code = f.read()

    # --- Parse Archon/Gardener Limits ---
    
    # Parse Archon for gardener limits
    if archon_code:
        # Look for gardener limit patterns
        match = re.search(r'gardenersHired\s*>=\s*(\d+)', archon_code)
        if match:
            profile.max_gardeners = int(match.group(1))

        match = re.search(r'gardenersHired\s*<\s*(\d+)', archon_code)
        if match:
            profile.max_gardeners = int(match.group(1))

        match = re.search(r'(?:MAX_GARDENERS|maxGardeners)\s*=\s*(\d+)', archon_code)
        if match:
            profile.max_gardeners = int(match.group(1))

    # Parse Gardener for tree/unit logic
    if gardener_code:
        # Look for max trees
        match = re.search(r'MAX_TREES\s*=\s*(\d+)', gardener_code)
        if match:
            profile.max_trees_per_gardener = int(match.group(1))

        match = re.search(r'treesPlanted\s*>=\s*(\d+)', gardener_code)
        if match:
            profile.max_trees_per_gardener = int(match.group(1))

        match = re.search(r'treesPlanted\s*<\s*(\d+)', gardener_code)
        if match:
            profile.max_trees_per_gardener = int(match.group(1))

        # Extract build order from conditionals
        build_order = []
        unit_patterns = [
            (r'lumberjacksBuilt\s*<\s*(\d+)', 'LUMBERJACK'),
            (r'scoutsBuilt\s*<\s*(\d+)', 'SCOUT'),
            (r'soldiersBuilt\s*<\s*(\d+)', 'SOLDIER'),
            (r'tanksBuilt\s*<\s*(\d+)', 'TANK'),
        ]

        for pattern, unit_type in unit_patterns:
            match = re.search(pattern, gardener_code)
            if match:
                count = int(match.group(1))
                pos = match.start()
                build_order.append((unit_type, count, pos))

        build_order.sort(key=lambda x: x[2])
        profile.initial_build_order = [(t, c) for t, c, _ in build_order]

        # Look for cycling pattern
        cycle_match = re.search(
            r'switch\s*\([^)]*%\s*\d+\)\s*\{([^}]+)\}',
            gardener_code,
            re.DOTALL
        )
        if cycle_match:
            switch_body = cycle_match.group(1)
            cycle = []
            for case_match in re.finditer(r'case\s+\d+:\s*toBuild\s*=\s*RobotType\.(\w+)', switch_body):
                cycle.append(case_match.group(1))
            if cycle:
                profile.cycle_pattern = cycle

        # Check if waters trees
        profile.waters_trees = 'water(' in gardener_code or 'canWater' in gardener_code

    # Default cycle if none found
    if not profile.cycle_pattern:
        profile.cycle_pattern = ['SOLDIER']

    # --- Parse Donation Logic (RobotPlayer or Archon) ---
    combined_code = archon_code + "\n" + robot_player_code
    
    if 'donate(' in combined_code:
        # Look for round limits and bullet thresholds
        round_limits = sorted([int(m) for m in re.findall(r'round\s*[<]=?\s*(\d+)', combined_code)])
        bullet_thresholds = [int(m) for m in re.findall(r'bullets\s*>\s*(\d+)', combined_code)]
        
        # Simple heuristic mapping for typical "phase" bots
        if round_limits and bullet_thresholds:
            # Assume phases define increasing aggression (lower thresholds later)
            # Sort thresholds descending (assuming later game = lower threshold/more aggressive)
            bullet_thresholds.sort(reverse=True)
            
            # Logic:
            # If we have [600, 1500] and [150, 50]
            # 600-1500 -> 150
            # 1500-MAX -> 50
            
            if len(round_limits) >= 2 and len(bullet_thresholds) >= 2:
                 # 600-1500 -> 150
                 profile.donation_rules.append(DonationRule(
                     min_round=round_limits[0],
                     max_round=round_limits[1],
                     trigger_bullets=bullet_thresholds[0],
                     keep_bullets=bullet_thresholds[0] - 50 # Heuristic
                 ))
                 # 1500-MAX -> 50
                 profile.donation_rules.append(DonationRule(
                     min_round=round_limits[1],
                     max_round=MAX_ROUNDS,
                     trigger_bullets=bullet_thresholds[1],
                     keep_bullets=0 # Aggressive
                 ))
            elif len(round_limits) >= 1 and len(bullet_thresholds) >= 1:
                 # 0-limit: setup? or limit-MAX: donate?
                 # Usually: if (round < X) return; donate...
                 # So donate after X.
                 profile.donation_rules.append(DonationRule(
                     min_round=round_limits[0],
                     max_round=MAX_ROUNDS,
                     trigger_bullets=bullet_thresholds[-1],
                     keep_bullets=0
                 ))
        else:
            # Fallback: if donate is present but no complex logic found
            profile.donation_rules.append(DonationRule(
                min_round=0, 
                max_round=MAX_ROUNDS, 
                trigger_bullets=1000, 
                keep_bullets=0
            ))

    return profile


# ============================================================================
# SIMULATOR
# ============================================================================

def calculate_base_income(current_bullets: float) -> float:
    """Base bullet income per round."""
    return max(0, 2 - 0.01 * current_bullets)


def simulate(profile: BotProfile, max_rounds: int = MAX_ROUNDS, verbose: bool = False) -> GameState:
    """Run economy simulation for the bot."""
    state = GameState()

    # Start with 1 Archon
    state.units.append(Unit('ARCHON', 0))
    state.log(f"Game start: 1 ARCHON, {INITIAL_BULLETS} bullets")

    # Track build order progress
    initial_order_idx = 0
    initial_order_count = [0] * len(profile.initial_build_order)
    cycle_idx = 0
    total_combat_units_built = 0

    for round_num in range(1, max_rounds + 1):
        state.round_num = round_num

        # === INCOME PHASE ===
        # Base income
        base_income = calculate_base_income(state.bullets)
        state.bullets += base_income

        # Tree income
        tree_income = 0
        for tree in state.trees:
            tree_income += tree.get_income(round_num)
        state.bullets += tree_income

        # === VP / DONATION PHASE ===
        # Calculate VP cost
        vp_cost = VP_BASE_COST + (round_num * VP_INCREASE_PER_ROUND)
        
        # Check donation rules
        for rule in profile.donation_rules:
            if rule.min_round <= round_num < rule.max_round:
                if state.bullets > rule.trigger_bullets:
                    # Donate excess
                    to_donate = state.bullets - rule.keep_bullets
                    # Donation must be positive
                    if to_donate > 0:
                        import math
                        vps_to_buy = math.floor(to_donate / vp_cost)
                        if vps_to_buy > 0:
                            cost_of_vps = vps_to_buy * vp_cost
                            state.bullets -= cost_of_vps
                            state.vp += vps_to_buy
                            state.log(f"Donated {cost_of_vps:.1f} bullets for {vps_to_buy} VP (cost: {vp_cost:.2f})")
                            
                            if state.vp >= VICTORY_VP:
                                state.log(f"VICTORY! Reached {state.vp} VP at round {round_num}")
                                return state
                # Only apply one rule per turn (priority to first match)
                break

        # === ARCHON PHASE: Hire Gardeners ===
        if state.archon_cooldown > 0:
            state.archon_cooldown -= 1

        if (state.gardeners_hired < profile.max_gardeners and
            state.bullets >= UNIT_COSTS['GARDENER'] and
            state.archon_cooldown == 0):

            state.bullets -= UNIT_COSTS['GARDENER']
            state.gardeners_hired += 1
            state.units_built['GARDENER'] += 1
            state.units.append(Unit('GARDENER', round_num))
            state.archon_cooldown = BUILD_COOLDOWN
            state.log(f"ARCHON hired GARDENER #{state.gardeners_hired}")

        # === GARDENER PHASE ===
        # Only act if we have an active gardener
        active_gardeners = [u for u in state.units
                          if u.unit_type == 'GARDENER' and u.is_active(round_num)]

        if not active_gardeners:
            # Update trees anyway
            for tree in state.trees:
                tree.tick(round_num, watered=False)
            continue

        # Decrement gardener cooldown
        if state.gardener_cooldown > 0:
            state.gardener_cooldown -= 1

        # Water trees first (doesn't use cooldown)
        if profile.waters_trees and state.trees:
            # Water the lowest health tree
            lowest_tree = min(state.trees, key=lambda t: t.health)
            for tree in state.trees:
                tree.tick(round_num, watered=(tree is lowest_tree))
        else:
            for tree in state.trees:
                tree.tick(round_num, watered=False)

        # Remove dead trees
        state.trees = [t for t in state.trees if t.health > 0]

        # Skip build actions if on cooldown
        if state.gardener_cooldown > 0:
            continue

        # Plant trees (priority over units early)
        max_trees = profile.max_trees_per_gardener * state.gardeners_hired
        if (state.trees_planted < max_trees and
            state.bullets >= TREE_PLANT_COST):

            state.bullets -= TREE_PLANT_COST
            state.trees_planted += 1
            state.trees.append(Tree(round_num))
            state.gardener_cooldown = BUILD_COOLDOWN
            state.log(f"GARDENER planted tree #{state.trees_planted}/{max_trees}")
            continue

        # Build units from initial order
        built_this_turn = False

        if initial_order_idx < len(profile.initial_build_order):
            unit_type, target_count = profile.initial_build_order[initial_order_idx]
            cost = UNIT_COSTS[unit_type]

            if state.bullets >= cost:
                state.bullets -= cost
                state.units_built[unit_type] += 1
                total_combat_units_built += 1
                initial_order_count[initial_order_idx] += 1
                state.units.append(Unit(unit_type, round_num))
                state.gardener_cooldown = BUILD_COOLDOWN
                state.log(f"GARDENER built {unit_type} #{state.units_built[unit_type]} (initial order)")

                if initial_order_count[initial_order_idx] >= target_count:
                    initial_order_idx += 1

                built_this_turn = True

        # Cycle builds after initial order complete
        elif profile.cycle_pattern and state.bullets >= UNIT_COSTS.get(profile.cycle_pattern[0], 100) + 50:
            unit_type = profile.cycle_pattern[cycle_idx % len(profile.cycle_pattern)]
            cost = UNIT_COSTS[unit_type]

            if state.bullets >= cost:
                state.bullets -= cost
                state.units_built[unit_type] += 1
                total_combat_units_built += 1
                state.units.append(Unit(unit_type, round_num))
                state.gardener_cooldown = BUILD_COOLDOWN
                cycle_idx += 1
                state.log(f"GARDENER built {unit_type} #{state.units_built[unit_type]} (cycle)")

    return state


# ============================================================================
# OUTPUT
# ============================================================================

def print_report(profile: BotProfile, state: GameState, verbose: bool = False):
    """Print simulation results."""
    print("=" * 70)
    print(f"BATTLECODE 2017 ECONOMY SIMULATION: {profile.name}")
    print("=" * 70)
    print()

    print("BOT PROFILE (extracted from source):")
    print(f"  Max Gardeners: {profile.max_gardeners}")
    print(f"  Trees per Gardener: {profile.max_trees_per_gardener}")
    print(f"  Waters Trees: {profile.waters_trees}")
    if profile.donation_rules:
        print("  Donation Rules:")
        for rule in profile.donation_rules:
            print(f"    R{rule.min_round}-{rule.max_round}: Donate > {rule.trigger_bullets}, Keep {rule.keep_bullets}")
    else:
        print("  Donation Rules: None")
    print()

    print("FINAL STATE (round {}):".format(state.round_num))
    print(f"  Bullets: {state.bullets:.1f}")
    
    vp_cost = VP_BASE_COST + (state.round_num * VP_INCREASE_PER_ROUND)
    print(f"  Victory Points: {state.vp} / {VICTORY_VP} (Current Cost: {vp_cost:.2f})")
    if state.vp >= VICTORY_VP:
        print("  RESULT: VICTORY by VP!")
    
    print(f"  Trees alive: {len(state.trees)} (planted: {state.trees_planted})")
    print()

    print("ARMY COMPOSITION:")
    print(f"  Archons:     1 (starting)")
    for unit_type in ['GARDENER', 'LUMBERJACK', 'SOLDIER', 'TANK', 'SCOUT']:
        count = state.units_built[unit_type]
        if count > 0:
            cost = UNIT_COSTS[unit_type]
            print(f"  {unit_type:12} {count:3}  (cost: {cost} each, total: {count * cost})")

    total_spent = sum(state.units_built[t] * UNIT_COSTS[t] for t in state.units_built)
    total_spent += state.trees_planted * TREE_PLANT_COST
    print()
    print(f"  Total bullets spent: {total_spent}")
    print()

    # Event timeline (condensed)
    print("BUILD TIMELINE:")

    # Group events by type for cleaner output
    last_event_round = 0
    event_groups = []
    current_group = []

    for round_num, msg in state.events:
        if round_num - last_event_round > 50 and current_group:
            event_groups.append(current_group)
            current_group = []
        current_group.append((round_num, msg))
        last_event_round = round_num

    if current_group:
        event_groups.append(current_group)

    # Print first 20 events in detail
    print("\n  First 20 events:")
    for round_num, msg in state.events[:20]:
        print(f"    R{round_num:4}: {msg}")

    if len(state.events) > 20:
        print(f"\n  ... ({len(state.events) - 20} more events) ...")
        print("\n  Last 10 events:")
        for round_num, msg in state.events[-10:]:
            print(f"    R{round_num:4}: {msg}")

    print()

    # Summary stats
    if state.events:
        build_rounds = [r for r, m in state.events if 'built' in m or 'hired' in m]
        if build_rounds:
            print("PRODUCTION STATS:")
            print(f"  First unit built: round {build_rounds[0] if build_rounds else 'N/A'}")
            print(f"  Last unit built: round {build_rounds[-1] if build_rounds else 'N/A'}")
            total_units = sum(state.units_built.values())
            if state.round_num > 0:
                print(f"  Average build rate: {total_units / state.round_num * 100:.2f} units per 100 rounds")


def main():
    parser = argparse.ArgumentParser(description='Simulate Battlecode 2017 bot economy')
    parser.add_argument('bot_dir', help='Path to bot source directory')
    parser.add_argument('--rounds', type=int, default=3000, help='Rounds to simulate (default: 3000)')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')

    args = parser.parse_args()

    if not os.path.isdir(args.bot_dir):
        print(f"Error: {args.bot_dir} is not a directory")
        sys.exit(1)

    print(f"Parsing bot: {args.bot_dir}")
    profile = parse_bot(args.bot_dir)

    print(f"Simulating {args.rounds} rounds...")
    state = simulate(profile, args.rounds, args.verbose)

    print()
    print_report(profile, state, args.verbose)


if __name__ == '__main__':
    main()
