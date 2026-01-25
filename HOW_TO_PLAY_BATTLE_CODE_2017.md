# How to Play Battlecode 2017 (Engine-Derived Notes)

This document summarizes the gameplay rules, mechanics, and API behavior as implemented in the 2017 Battlecode engine in `engine/`. It is intended to be a compact, accurate reference for LLMs that need to understand the game and build strong bots.

## High-Level Game Loop
- The game runs for a fixed number of rounds (default 3000). The engine increments the round counter at the start of each round, then executes robots (first robot turns see `getRoundNum() == 1`).
- Each robot gets one turn per round (sequentially). Bullets also update once per round.
- Robots can move at most once per turn and attack at most once per turn.
- Many API calls are marked as costly; you must respect bytecode limits per robot type.

## Turn Order and Bullet Updates
- Robots update in spawn order.
- Bullets update in the same list as robots and are inserted immediately before the robot that fired them, so a bullet will move right before its shooter acts next round.
- If a bullet spawns overlapping a robot or tree, it immediately damages that body and is destroyed.

## Win Conditions and Tie-Breakers
- Instant win if the opponent has 0 robots remaining.
- If time limit reached and no winner:
  1) Higher team tree count wins.
  2) If tie, higher total bullets wins (team bullet supply + sum of bulletCost of remaining robots).
  3) If tie, team of the highest-ID robot wins.

## Core Constants (from `GameConstants`)
- Map size: 30 to 100 (width/height), floating-point coordinates.
- Max rounds: `3000` (default).
- Initial bullets: `300`.
- Bullet income per round: `max(0, 2 - 0.01 * current_bullets)`.
- Broadcasting: 10,000 int channels shared by team.
- Team memory across games: 32 longs.
- Exceptions cost: 500 bytecodes per thrown exception.

## Robot Types and Stats
All positions are continuous; robots are circles with `bodyRadius`.

| Type | HP | Bullet Cost | Body Radius | Bullet Speed | Attack Power | Sensor Radius | Bullet Sight | Stride | Bytecode Limit |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ARCHON | 400 | - | 2.0 | - | - | 10 | 15 | 0.5 | 30000 |
| GARDENER | 40 | 100 | 1.0 | - | - | 7 | 10 | 0.5 | 15000 |
| LUMBERJACK | 50 | 100 | 1.0 | - | 2 | 7 | 10 | 0.75 | 15000 |
| SOLDIER | 50 | 100 | 1.0 | 2.0 | 2 | 7 | 10 | 0.8 | 15000 |
| TANK | 200 | 300 | 2.0 | 4.0 | 5 | 7 | 10 | 0.5 | 15000 |
| SCOUT | 10 | 80 | 1.0 | 1.5 | 0.5 | 14 | 20 | 1.25 | 15000 |

Special rules:
- Only ARCHON can hire GARDENERs.
- Only GARDENER can build robots and plant trees.
- Only LUMBERJACK can chop trees and strike.
- SOLDIER and TANK can fire single, triad, and pentad shots.
- SCOUT can only fire single shots.
- ARCHON and GARDENER cannot fire bullets.

## Building and Spawning
- Spawns require a clear circle at `builderRadius + 0.01 + spawnedRadius` in the chosen direction.
- Build cooldown applies to the builder; hiring a Gardener, building any robot, or planting a tree all consume a 10-turn cooldown in this ruleset.

## Unit Action Permissions (What Each Type Can Do)
All robots can always: move, sense, broadcast, donate, draw indicators, disintegrate, resign, and read team memory/broadcasts (subject to range, bytecodes, and per-turn limits). The list below focuses on restricted actions.

- ARCHON
  - Can: hire Gardeners.
  - Cannot: attack, build robots, plant trees, chop, strike, or water.
- GARDENER
  - Can: build robots (Lumberjack, Soldier, Tank, Scout), plant trees, water team trees.
  - Cannot: hire Gardeners or attack.
- LUMBERJACK
  - Can: strike and chop trees (both count as attacks).
  - Cannot: fire bullets, build, or plant trees.
- SOLDIER
  - Can: fire single, triad, and pentad shots.
  - Cannot: build, plant, chop, or strike.
- TANK
  - Can: fire single, triad, and pentad shots; body-damage trees when moving into them.
  - Cannot: build, plant, chop, or strike.
- SCOUT
  - Can: fire single shots; move through trees.
  - Cannot: fire triad/pentad, build, plant, chop, or strike.

## Unit Action Syntax (RobotController Methods)
Use these method signatures when calling actions from a robot's `RobotController rc`.

- ARCHON
  - Hire Gardener: `rc.canHireGardener(Direction dir)`, `rc.hireGardener(Direction dir)`
- GARDENER
  - Build robot: `rc.canBuildRobot(RobotType type, Direction dir)`, `rc.buildRobot(RobotType type, Direction dir)`
  - Plant tree: `rc.canPlantTree(Direction dir)`, `rc.plantTree(Direction dir)`
  - Water tree: `rc.canWater(MapLocation loc)`, `rc.canWater(int id)`, `rc.water(MapLocation loc)`, `rc.water(int id)`, `rc.canWater()`
- LUMBERJACK
  - Strike: `rc.canStrike()`, `rc.strike()`
  - Chop: `rc.canChop(MapLocation loc)`, `rc.canChop(int id)`, `rc.chop(MapLocation loc)`, `rc.chop(int id)`
- SOLDIER / TANK
  - Fire single: `rc.canFireSingleShot()`, `rc.fireSingleShot(Direction dir)`
  - Fire triad: `rc.canFireTriadShot()`, `rc.fireTriadShot(Direction dir)`
  - Fire pentad: `rc.canFirePentadShot()`, `rc.firePentadShot(Direction dir)`
- SCOUT
  - Fire single: `rc.canFireSingleShot()`, `rc.fireSingleShot(Direction dir)`
- Any robot (tree interaction / economy / movement)
  - Shake: `rc.canShake(MapLocation loc)`, `rc.canShake(int id)`, `rc.shake(MapLocation loc)`, `rc.shake(int id)`, `rc.canShake()`
  - Move: `rc.canMove(Direction dir)`, `rc.canMove(Direction dir, float dist)`, `rc.canMove(MapLocation loc)`, `rc.move(Direction dir)`, `rc.move(Direction dir, float dist)`, `rc.move(MapLocation loc)`

## Newly Built Units Are Inactive at First
For units built by a Gardener (LUMBERJACK, SOLDIER, TANK, SCOUT):
- They cannot execute code for their first 20 rounds (bytecode limit is 0 until `roundsAlive >= 20`).
- During those first 20 rounds they auto-heal 4% of max HP each round.

Implication: freshly spawned units are temporarily inert, so account for this in build timing and early defense.

## Movement and Collision
- Movement is continuous. Robots are circles; trees are circles; bullets are point-projectiles.
- A robot can move up to its `strideRadius` once per turn.
- For most robots, movement is blocked by trees and other robots.
- SCOUTs ignore trees for collision (they only avoid robots) and can move through trees.
- TANKs also ignore trees for the initial move check, but if a tree overlaps their target position:
  - The tank damages the closest blocking tree for `4` damage.
  - If a tree still blocks the destination after damage, the tank does not move.
- You cannot move off the map: the entire circle must remain within the map boundary.

## Combat and Bullets
- Lumberjack strike: damages all robots and trees (including allies) within radius 2 of the lumberjack.
- Chopping: only Lumberjacks can chop trees. Chop deals 5 damage and counts as an attack.
- Ranged bullets:
  - Single shot cost: 1 bullet.
  - Triad shot cost: 4 bullets, spread 20 degrees.
  - Pentad shot cost: 6 bullets, spread 15 degrees.
  - Bullets spawn slightly outside the robot radius (`bodyRadius + 0.05`).
  - Bullets move in a straight line for their speed each turn, and collide with the first robot or tree intersecting the line segment.
  - Friendly fire is possible: bullets and strikes damage allies.

## Trees
There are two types of trees:
- Neutral trees (map fixtures):
  - Radius range: 0.5 to 10.
  - Max health: `200 * radius`.
  - Can contain bullets and/or a robot type.
  - Only chopping can release contained bullets or robots.
- Team trees (planted by Gardeners):
  - Cost: 50 bullets.
  - Radius: 1.0.
  - Start at 20% max health, grow for 80 rounds, then generate bullets and decay.
  - Bullet income per tree per round: `health / 50` (i.e., 1 bullet per round at full health).
  - Decay rate: `0.5` health per round once producing.
  - Watering (Gardener only) restores `5` health, once per turn.

Tree interactions:
- Any robot can shake a tree once per turn to collect contained bullets.
- Only Gardeners can water (once per turn), and only team-owned trees can be watered.
- Only Lumberjacks can chop (chop counts as an attack).
- Tanks can body-damage trees when attempting to move into them.
- Chop/shake/water require the tree edge to be within 1.0 of the robot edge.
- If a neutral tree contains a robot, chopping can spawn that robot for the chopping team; overlapping Scouts are destroyed to resolve collisions.

## Economy
- Team bullet supply is shared across all robots.
- Bullets are spent to build robots, plant trees, and fire shots.

## Sensing and Information
- `sensorRadius` controls robot and tree sensing.
- `bulletSightRadius` controls bullet sensing.
- Many sensing calls throw if the target is out of range.
- `senseNearbyRobots/Trees/Bullets` returns all within radius (or full sensor range if -1 is passed). The API docs state results are sorted by distance.
- `getInitialArchonLocations(team)` returns initial archon locations sorted by x then y.
- `senseBroadcastingRobotLocations()` returns locations of robots that broadcast in the previous round.

## Communication
- Shared array has 10,000 int channels.
- Reads and writes are team-wide. You can also encode floats with `Float.floatToRawIntBits` / `Float.intBitsToFloat`.
- The API docs state broadcast writes are applied at the end of the robot's turn.

## Bytecodes and Turn Control
- Each robot has a per-turn bytecode limit (see table above).
- `Clock.getBytecodesLeft()` and `Clock.getBytecodeNum()` expose current usage.
- `Clock.yield()` ends your turn immediately.
- Throwing exceptions costs 500 bytecodes, so avoid control flow via exceptions.

## Practical Bot-Building Notes (Based on Engine Behavior)
- Check `canMove`/`canFire` before acting to avoid exceptions and wasted bytecodes.
- Avoid friendly fire; bullets collide with any team.
- Use Scouts for pathing through dense forests; use Tanks to clear trees.
- Plan around the 20-round inactivity of newly built units.
- Tree economies take time: planted trees only start producing after 80 rounds.

## Repo Rule: Bullet Spending Must Be Centralized
CRITICAL: In this repo, **all bullet spending activities** must live in `BulletSpending.java` and nowhere else, and they must be called only inside `spendPolicy()`. This includes:
- `rc.hireGardener(Direction dir)`
- `rc.buildRobot(RobotType type, Direction dir)`
- `rc.plantTree(Direction dir)`

Do not add or keep bullet-spending logic in any other file, and do not call any of the methods above anywhere outside `BulletSpending.spendPolicy()`. All other files may only call `BulletSpending.spendPolicy()` and must never call any other `BulletSpending` method directly. Centralizing spending order in `spendPolicy()` makes overall economic intent and timing easy to audit and reason about.

## Key API Entry Points
- `battlecode.common.RobotController` is the primary interface for sensing and acting.
- `battlecode.common.MapLocation` and `battlecode.common.Direction` are the geometry primitives.
- `battlecode.common.GameConstants` and `battlecode.common.RobotType` contain most numeric rules.

## Recommended File Layout (Battlecode Bot)
Common, effective project structure for a full bot package (Java 8):

- `RobotPlayer.java`: Entry point; dispatches by `RobotType` to per-unit logic.
- `Archon.java`: Archon strategy (hire timing, initial expansion).
- `Gardener.java`: Build order, tree farm management, unit production.
- `Lumberjack.java`: Tree clearing, melee combat, early pressure.
- `Soldier.java`: Core combat micro, targeting, firing decisions.
- `Tank.java`: Heavy combat, tree body-ram tactics, frontline pushes.
- `Scout.java`: Scouting, harassment, bullet collection, vision.
- `Comms.java`: Broadcast channel schema, encoding/decoding, team state.
- `Nav.java`: Movement, pathfinding, bullet dodging, obstacle avoidance.
- `Utils.java`: Shared helpers (geometry, targeting utilities, randoms).
- `BulletSpending.java`: **All bullet spending** (donations, hire gardener, build robot, plant tree) centralized here.
