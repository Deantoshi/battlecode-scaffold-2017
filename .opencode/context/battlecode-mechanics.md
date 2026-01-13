# Battlecode 2017 Game Mechanics Reference

This is the shared reference for all Battlecode agents. Read this to understand game mechanics.

## Unit Capabilities & Costs

| Unit | Cost | HP | Speed | Sensor | Attack | Special Abilities |
|------|------|----|----|--------|--------|-------------------|
| **ARCHON** | - | 400 | 0.5 | 10 | None | Hires Gardeners (100 bullets). Cannot attack. Mobile base. Generates 2 bullets/turn. |
| **GARDENER** | 100 | 40* | 0.5 | 7 | None | Plants bullet trees (50 bullets). Waters trees (+5 HP). Builds all combat units. 10-turn build cooldown. |
| **SOLDIER** | 100 | 50* | 0.8 | 7 | 2.0 | Ranged: Single shot (1 bullet), Triad/3-way (4 bullets), Pentad/5-way (6 bullets). Bullet speed: 2.0 |
| **LUMBERJACK** | 100 | 50* | 0.75 | 7 | 2.0 | Melee combat. Chops trees (-5 HP/chop). STRIKE: 2-radius AoE to ALL units (friend+foe). |
| **SCOUT** | 80 | 10* | 1.25 | 14 | 0.5 | Fastest unit. Huge vision (14 sensor, 20 bullet sight). Shakes trees for bullets. Very fragile. |
| **TANK** | 300 | 200* | 0.5 | 7 | 5.0 | Heavy unit. Body slam destroys trees (-4 HP). High damage. Bullet speed: 4.0. Expensive. |
| **BULLET TREE** | 50 | 50 | 0 | - | None | Generates 1 bullet/turn when watered. Decays -0.5 HP/turn. Radius: 1.0. Planted by Gardeners. |

*Units spawn at 20% HP and must be watered to full health by Gardeners.

## Key Game Constants

- Starting bullets: 300
- Win condition: 1000 victory points OR destroy all enemy units OR 3000 rounds (most VP wins)
- Bullet income penalty: -0.01 bullets/turn per bullet held (discourages hoarding)
- Interaction range: 1.0 from robot edge (for water/shake/chop)
- Water heals: +5 HP per water action
- Lumberjack chop: -5 HP to trees
- Lumberjack strike radius: 2.0 (hits ALL units in range)
- Tree planting cooldown: 10 turns
- Victory point costs: 7.5 bullets initially, increases by 12.5/3000 per round

## Critical API Methods

Key methods from `RobotController.java`:
- Movement: `canMove()`, `move(Direction, float distance)`
- Combat: `fireSingleShot()`, `fireTriadShot()`, `firePentadShot()`, `strike()`
- Building: `hireGardener()`, `buildRobot()`, `plantTree()`
- Tree interaction: `water()`, `shake()`, `chop()`
- Sensing: `senseNearbyRobots()`, `senseNearbyTrees()`, `senseNearbyBullets()`
- Economy: `getTeamBullets()`, `donate()` (converts bullets to victory points)

## Navigation & Pathfinding

Tree obstacles are a MAJOR factor on maps like Bullseye (35% trees), Barrier, and Lanes.

### Key Navigation API Methods:
- `rc.isCircleOccupied(MapLocation center, float radius)` - Check if position is blocked
- `rc.isLocationOccupied(MapLocation loc)` - Check if exact location is blocked
- `rc.senseNearbyTrees(float radius)` - Detect trees to path around
- `rc.canMove(Direction dir)` - Basic movement check (considers obstacles)
- `rc.onTheMap(MapLocation loc)` - Boundary check before moving

### Navigation Red Flags:
- **High unit count but low deaths** (<30% death rate) = units are STUCK
- **Games going to timeout** (2500+ rounds) with many units alive = GRIDLOCK
- **Losses on Bullseye/Barrier/Lanes but wins on Shrine** = tree pathing broken

### Death Rate Formula:
```
death_rate = total_deaths / total_units_created
- HEALTHY: death_rate > 50% (units engaging effectively)
- CONCERNING: death_rate 30-50% (some units stuck)
- BROKEN: death_rate < 30% (PATHING ISSUE - fix immediately!)
```

## Game Mechanics Source Files (READ-ONLY Reference)

When making strategic decisions, these files contain exact mechanics:
- `engine/battlecode/common/RobotType.java` - All unit stats
- `engine/battlecode/common/GameConstants.java` - Game rules, costs, limits
- `engine/battlecode/common/RobotController.java` - Full API
- `engine/battlecode/common/Direction.java` - Movement directions
- `engine/battlecode/common/MapLocation.java` - Position calculations

## The 5 Test Maps

| Map | Category | Tests |
|-----|----------|-------|
| Shrine | Fast | Early aggression, 1v1, minimal obstacles |
| Barrier | Balanced | 2v2 archons, adaptable play, tree barriers |
| Bullseye | Exploration | Dense pathfinding (35% trees), navigation |
| Lanes | Slow | 3v3 archons, lane strategy, late-game |
| Blitzkrieg | Balanced | 3v3 archons, rapid tactical formations |
