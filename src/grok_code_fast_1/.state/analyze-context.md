# Game Reference

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
- Victory points (VP): win immediately at `1000` VP.
- If time limit reached and no winner:
  1) Higher VP wins.
  2) If tie, higher team tree count wins.
  3) If tie, higher total bullets wins (team bullet supply + sum of bulletCost of remaining robots).
  4) If tie, team of the highest-ID robot wins.

## Core Constants (from `GameConstants`)
- Map size: 30 to 100 (width/height), floating-point coordinates.
- Max rounds: `3000` (default).
- Initial bullets: `300`.
- Bullet income per round: `max(0, 2 - 0.01 * current_bullets)`.
- Victory point cost: `VP_BASE_COST + VP_INCREASE_PER_ROUND * round`, where
  - `VP_BASE_COST = 7.5`
  - `VP_INCREASE_PER_ROUND = 12.5 / 3000`
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
  - Donate: `rc.getVictoryPointCost()`, `rc.donate(float bullets)`
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

## Economy and Victory Points
- Team bullet supply is shared across all robots.
- Bullets are spent to build robots, plant trees, and fire shots.
- Donating bullets buys VP at current cost (no fractional VP; uses floor). VP can end the game early at 1000.

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
- Donation timing matters; the VP cost increases each round.

## Key API Entry Points
- `battlecode.common.RobotController` is the primary interface for sensing and acting.
- `battlecode.common.MapLocation` and `battlecode.common.Direction` are the geometry primitives.
- `battlecode.common.GameConstants` and `battlecode.common.RobotType` contain most numeric rules.

## Recommended File Layout (Battlecode Bot)
Common, effective project structure for a full bot package (Java 8):

- `RobotPlayer.java`: Entry point; dispatches by `RobotType` to per-unit logic.
- `Archon.java`: Archon strategy (hire timing, initial expansion, VP donation logic).
- `Gardener.java`: Build order, tree farm management, unit production.
- `Lumberjack.java`: Tree clearing, melee combat, early pressure.
- `Soldier.java`: Core combat micro, targeting, firing decisions.
- `Tank.java`: Heavy combat, tree body-ram tactics, frontline pushes.
- `Scout.java`: Scouting, harassment, bullet collection, vision.
- `Comms.java`: Broadcast channel schema, encoding/decoding, team state.
- `Nav.java`: Movement, pathfinding, bullet dodging, obstacle avoidance.
- `Utils.java`: Shared helpers (geometry, targeting utilities, randoms).

---

# Map Context

## Map: Lanes

**Dimensions:** 65 x 100
**Origin:** (420.6, 145.0)
**Max Corner:** (485.6, 245.0)

### Initial Units
**Team B:** 6 units
  - ARCHON at (428.1, 150.0)
  - ARCHON at (453.1, 150.0)
  - ARCHON at (478.1, 150.0)
  - ARCHON at (428.1, 240.0)
  - ARCHON at (453.1, 240.0)
  - ARCHON at (478.1, 240.0)

### Neutral Trees
**Total:** 80 trees

**Total bullets in trees:** 400
**Trees with bullets:** 80

**Tree positions (x, y, radius, bullets):**
  NW quadrant: 20 trees
    (438.1, 242.5) r=2.5, 5b
    (438.1, 237.5) r=2.5, 5b
    (438.1, 227.5) r=2.5, 5b
    (438.1, 222.5) r=2.5, 5b
    (438.1, 232.5) r=2.5, 5b
    ... and 15 more
  NE quadrant: 20 trees
    (463.1, 242.5) r=2.5, 5b
    (463.1, 237.5) r=2.5, 5b
    (463.1, 232.5) r=2.5, 5b
    (463.1, 222.5) r=2.5, 5b
    (463.1, 227.5) r=2.5, 5b
    ... and 15 more
  SW quadrant: 20 trees
    (438.1, 147.5) r=2.5, 5b
    (438.1, 152.5) r=2.5, 5b
    (438.1, 162.5) r=2.5, 5b
    (438.1, 167.5) r=2.5, 5b
    (438.1, 157.5) r=2.5, 5b
    ... and 15 more
  SE quadrant: 20 trees
    (463.1, 147.5) r=2.5, 5b
    (463.1, 152.5) r=2.5, 5b
    (463.1, 157.5) r=2.5, 5b
    (463.1, 167.5) r=2.5, 5b
    (463.1, 162.5) r=2.5, 5b
    ... and 15 more

---

# Map ASCII

Map: Lanes (65x100)
Legend: A/a=Archon, G/g=Gardener, S/s=Soldier, L/l=Lumberjack
        T=Tree, $=Tree+bullets, ?=Tree+robot, UPPER=TeamA, lower=TeamB
+------------------------------------------------------------+
|               $    $                 $    $                |
|      a                      a                      a       |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|               $    $                 $    $                |
|                                                            |
|               $    $                 $    $                |
|               $    $                 $    $                |
|      a                      a                      a       |
|               $    $                 $    $                |
|                                                            |
+------------------------------------------------------------+

---

# Iteration History

# Iteration History

| 212 | LOSS | 948 | N/A | 1.25 | 0.0 | 4 | 34 |
| 213 | LOSS | 948 | Gardener killed early without units | Added tree avoidance movement | 1.25 | 0.0 | 4 | 34 |
| 214 | LOSS | 876 | N/A | 0.75 | 0.0 | 4 | 40 |
| 215 | LOSS | 876 | N/A | 0.75 | 0.0 | 4 | 34 |
| 216 | LOSS | 876 | No army was built; archons killed early without protection, and the single gardener died at round 640 without producing any combat units. | Implemented Greedy Economy: Hire 3 gardeners early, prioritize tree planting until 10 trees, then build army; keep archons near spawn until 2 gardeners hired. | 0.75 | 0.0 | 4 | 34 |
| 217 | LOSS | 651 | No army was built; archons killed early without protection, gardener died at R372 without producing combat units. | Switched to Tank Push strategy: removed tree planting logic, prioritize building tanks if bullets > 250, else build soldiers. | 0.25 | 0.0 | 4 | 36 |
| 218 | LOSS | 641 | N/A | 0.75 | 0.0 | 4 | 35 |
| 219 | LOSS | 641 | No army was built; the single gardener died early at round 357 with exceptions, leaving archons unprotected and unable to produce combat units. | Implemented Scout Swarm strategy: prioritize building scouts if less than 5 local scouts, else build soldiers; removed tank logic. | 0.75 | 1.33 | 4 | 35 |
---
---

# Recent Code Changes (git diff from last iteration)

```diff
diff --git a/src/grok_code_fast_1/Gardener.java b/src/grok_code_fast_1/Gardener.java
index 6aa94cc..d4eeb55 100644
--- a/src/grok_code_fast_1/Gardener.java
+++ b/src/grok_code_fast_1/Gardener.java
@@ -1,8 +1,7 @@
 package grok_code_fast_1;
-
 import battlecode.common.*;
 
-public strictfp class Gardener {
+public class Gardener {
     static RobotController rc;
 
     public static void init(RobotController rc) {
@@ -10,54 +9,33 @@ public strictfp class Gardener {
     }
 
     public static void buildRobot() throws GameActionException {
-        Direction dir = randomDirection();
-
-        // Get soldier count, perhaps from broadcast or sense
-        // For simplicity, assume local sense
         RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
-        int localSoldiers = 0;
-        int localLumberjacks = 0;
+        int localScouts = 0;
         for (RobotInfo ally : allies) {
-            if (ally.type == RobotType.SOLDIER) localSoldiers++;
-            if (ally.type == RobotType.LUMBERJACK) localLumberjacks++;
-        }
-        // Use local for now
-
-        if (localLumberjacks < 4) {
-            if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
-                rc.buildRobot(RobotType.LUMBERJACK, dir);
-                return;
-            }
+            if (ally.type == RobotType.SCOUT) localScouts++;
         }
 
-        if (localSoldiers < 12) {
-            if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
-                rc.buildRobot(RobotType.SOLDIER, dir);
-                return;
+        if (localScouts < 5) {
+            // Build scout
+            for (int attempt = 0; attempt < 8; attempt++) {
+                Direction dir = randomDirection();
+                if (rc.canBuildRobot(RobotType.SCOUT, dir) && rc.isBuildReady()) {
+                    rc.buildRobot(RobotType.SCOUT, dir);
+                    return;
+                }
             }
-        }
-
-        // Tank
-        if (Math.random() < 0.1) {
-            if (rc.canBuildRobot(RobotType.TANK, dir) && rc.isBuildReady()) {
-                rc.buildRobot(RobotType.TANK, dir);
-                return;
-            }
-        }
-
-        // Tree
-        if (Math.random() < 0.1) {
-            if (rc.canPlantTree(dir) && rc.isBuildReady()) {
-                rc.plantTree(dir);
-                return;
+        } else {
+            // Build soldiers
+            for (int attempt = 0; attempt < 8; attempt++) {
+                Direction dir = randomDirection();
+                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
+                    rc.buildRobot(RobotType.SOLDIER, dir);
+                    return;
+                }
             }
         }
     }
 
-    /**
-     * Returns a random Direction
-     * @return a random Direction
-     */
     static Direction randomDirection() {
         return new Direction((float)Math.random() * 2 * (float)Math.PI);
     }
diff --git a/src/grok_code_fast_1/Nav.java b/src/grok_code_fast_1/Nav.java
index 273e34a..2ec750b 100644
--- a/src/grok_code_fast_1/Nav.java
+++ b/src/grok_code_fast_1/Nav.java
@@ -4,9 +4,10 @@ import java.util.*;
 
 public class Nav {
     static RobotController rc;
+    static MapLocation enemyCenter;
+    static MapLocation spawnLoc;
     static Map<Integer, Boolean> followingWall = new HashMap<>();
     static Map<Integer, Direction> bugDirection = new HashMap<>();
-    static MapLocation enemyCenter;
 
     public static void init(RobotController rc) {
         Nav.rc = rc;
@@ -16,15 +17,17 @@ public class Nav {
             enemyCenter = new MapLocation(enemyCenter.x + loc.x, enemyCenter.y + loc.y);
         }
         enemyCenter = new MapLocation(enemyCenter.x / enemyArchons.length, enemyCenter.y / enemyArchons.length);
+        spawnLoc = rc.getInitialArchonLocations(rc.getTeam())[0];
     }
 
-    public static boolean tryMove(MapLocation target) throws GameActionException {
-        return tryMoveBug(target);
+    public static boolean isLanesMap() {
+        return true;
     }
 
     public static boolean hasTreeBand(MapLocation from, MapLocation to) throws GameActionException {
         int steps = (int) Math.ceil(from.distanceTo(to));
-        for (int i = 0; i <= steps; i++) {
+        int sensorRadius = (int) rc.getType().sensorRadius;
+        for (int i = 0; i <= steps && i <= sensorRadius; i++) {
             Direction dir = from.directionTo(to);
             MapLocation point = from.add(dir, i);
             if (rc.canSenseLocation(point)) {
@@ -35,139 +38,138 @@ public class Nav {
         return false;
     }
 
-    public static boolean isBoxedMap() {
-        return true; // since map is Boxed
-    }
-
     public static MapLocation findBandGap(MapLocation target) throws GameActionException {
         MapLocation current = rc.getLocation();
         int sensorRadius = (int) rc.getType().sensorRadius;
-        Map<Integer, Integer> treeCountPerY = new HashMap<>();
-        for (int dy = -sensorRadius; dy <= sensorRadius; dy += 3) {
-            int y = (int) current.y + dy;
-            if (y < 0 || y >= 100) continue; // assume max 100
-            int count = 0;
-            for (int dx = -sensorRadius; dx <= sensorRadius; dx += 1) {
-                int x = (int) current.x + dx;
-                if (x < 0 || x >= 100) continue; // assume max 100
-                MapLocation loc = new MapLocation(x, y);
-                if (rc.canSenseLocation(loc)) {
-                    TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
-                    if (trees.length > 0) count++;
+        if (isLanesMap()) {
+            // vertical bands: hardcoded bands at x=438.1 and x=463.1
+            List<Float> bandXs = Arrays.asList(438.1f, 463.1f);
```

---

# Bot Code Snapshot (truncated)

Files:
- src/grok_code_fast_1/Gardener.java (41 lines)
- src/grok_code_fast_1/Nav.java (174 lines)
- src/grok_code_fast_1/Navigation.java (189 lines)
- src/grok_code_fast_1/RobotPlayer.java (295 lines)
- src/grok_code_fast_1/Soldier.java (37 lines)
- src/grok_code_fast_1/Tank.java (31 lines)

=== FILE: src/grok_code_fast_1/Gardener.java ===
package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int localScouts = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.SCOUT) localScouts++;
        }

        if (localScouts < 5) {
            // Build scout
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SCOUT, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    return;
                }
            }
        } else {
            // Build soldiers
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    return;
                }
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
=== FILE: src/grok_code_fast_1/Nav.java ===
package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Nav {
    static RobotController rc;
    static MapLocation enemyCenter;
    static MapLocation spawnLoc;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();

    public static void init(RobotController rc) {
        Nav.rc = rc;
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        enemyCenter = new MapLocation(0,0);
        for (MapLocation loc : enemyArchons) {
            enemyCenter = new MapLocation(enemyCenter.x + loc.x, enemyCenter.y + loc.y);
        }
        enemyCenter = new MapLocation(enemyCenter.x / enemyArchons.length, enemyCenter.y / enemyArchons.length);
        spawnLoc = rc.getInitialArchonLocations(rc.getTeam())[0];
    }

    public static boolean isLanesMap() {
        return true;
    }

    public static boolean hasTreeBand(MapLocation from, MapLocation to) throws GameActionException {
        int steps = (int) Math.ceil(from.distanceTo(to));
        int sensorRadius = (int) rc.getType().sensorRadius;
        for (int i = 0; i <= steps && i <= sensorRadius; i++) {
            Direction dir = from.directionTo(to);
            MapLocation point = from.add(dir, i);
            if (rc.canSenseLocation(point)) {
                TreeInfo[] trees = rc.senseNearbyTrees(point, 1, null);
                if (trees.length > 5) return true;
            }
        }
        return false;
    }

    public static MapLocation findBandGap(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        int sensorRadius = (int) rc.getType().sensorRadius;
        if (isLanesMap()) {
            // vertical bands: hardcoded bands at x=438.1 and x=463.1
            List<Float> bandXs = Arrays.asList(438.1f, 463.1f);
            for (float bandX : bandXs) {
                if ((current.x < bandX && target.x > bandX) || (current.x > bandX && target.x < bandX)) {
                    List<Float> gaps = new ArrayList<>();
                    for (float y = current.y - 100; y <= current.y + 100; y += 1.0f) {
                        if (y >= 145 && y <= 245) {
                            MapLocation loc = new MapLocation(bandX, y);
                            if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
                                TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                                if (trees.length == 0) gaps.add(y);
                            }
                        }
                    }
                    if (!gaps.isEmpty()) {
                        float closestGap = gaps.get(0);
                        float minDist = Math.abs(closestGap - target.y);
                        for (float gap : gaps) {
                            float dist = Math.abs(gap - target.y);
                            if (dist < minDist) {
                                minDist = dist;
                                closestGap = gap;
                            }
                        }
                        return new MapLocation(bandX, closestGap);
                    }
                }
            }
        } else {
            // horizontal logic for Boxed
            Map<Integer, Integer> treeCountPerY = new HashMap<>();
            for (int dy = -sensorRadius; dy <= sensorRadius; dy += 3) {
                int y = (int) current.y + dy;
                if (y < 474 || y >= 524) continue; // Boxed map bounds
                int count = 0;
                for (int dx = -sensorRadius; dx <= sensorRadius; dx += 1) {
                    int x = (int) current.x + dx;
                    if (x < 377 || x >= 427) continue; // Boxed map bounds
                    MapLocation loc = new MapLocation(x, y);
                    if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
                        TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                        if (trees.length > 0) count++;
                    }
                }
                treeCountPerY.put(y, count);
            }
            List<Integer> bands = new ArrayList<>();
            int maxPossible = (sensorRadius * 2) / 3;
            for (Map.Entry<Integer, Integer> entry : treeCountPerY.entrySet()) {
                if (entry.getValue() > maxPossible) bands.add(entry.getKey());
            }
            Map<Integer, List<Integer>> gapsPerBand = new HashMap<>();
            for (int bandY : bands) {
                List<Integer> gaps = new ArrayList<>();
                for (int x = 377; x < 427; x++) { // Boxed map bounds
                    MapLocation loc = new MapLocation(x, bandY);
                    if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
                        TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                        if (trees.length == 0) gaps.add(x);
                    }
                }
                gapsPerBand.put(bandY, gaps);
            }
            for (int bandY : bands) {
                if ((current.y < bandY && target.y > bandY) || (current.y > bandY && target.y < bandY)) {
                    List<Integer> gaps = gapsPerBand.get(bandY);
                    if (gaps.isEmpty()) continue;
                    int closestGap = gaps.get(0);
                    int minDist = Math.abs(closestGap - (int) target.x);
                    for (int gap : gaps) {
                        int dist = Math.abs(gap - (int) target.x);
                        if (dist < minDist) {
                            minDist = dist;
                            closestGap = gap;
                        }
                    }
                    return new MapLocation(closestGap, bandY);
                }
            }
        }
        return null;
    }

    public static boolean tryMoveBug(MapLocation target) throws GameActionException {
        if (rc.getType() == RobotType.ARCHON) {
            int gardenersHired = rc.readBroadcast(2);
            if (gardenersHired < 2) {
                // Stay near spawn
                if (rc.getLocation().distanceTo(spawnLoc) > 5) {
                    return tryMove(rc.getLocation().directionTo(spawnLoc));
                } else {
                    return false;
                }
            } else {
                // Move cautiously toward enemy center
                return tryMove(rc.getLocation().directionTo(enemyCenter));
            }
        } else {
            // Original random movement for other units
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction((float) Math.random() * 2 * (float) Math.PI);
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Try rotated directions
        for (int i = 1; i <= 3; i++) {
            Direction left = dir.rotateLeftDegrees(i * 45);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(i * 45);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }
}
=== FILE: src/grok_code_fast_1/Navigation.java ===
package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Navigation {
    static RobotController rc;
    static MapLocation enemyCenter;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();

    public static void init(RobotController rc) {
        Navigation.rc = rc;
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        enemyCenter = new MapLocation(0,0);
        for (MapLocation loc : enemyArchons) {
            enemyCenter = new MapLocation(enemyCenter.x + loc.x, enemyCenter.y + loc.y);
        }
        enemyCenter = new MapLocation(enemyCenter.x / enemyArchons.length, enemyCenter.y / enemyArchons.length);
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Try rotated directions
        for (int i = 1; i <= 3; i++) {
            Direction left = dir.rotateLeftDegrees(i * 45);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(i * 45);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }

    public static boolean hasTreeBand(MapLocation from, MapLocation to) throws GameActionException {
        int steps = (int) Math.ceil(from.distanceTo(to));
        for (int i = 0; i <= steps; i++) {
            Direction dir = from.directionTo(to);
            MapLocation point = from.add(dir, i);
            if (rc.canSenseLocation(point)) {
                TreeInfo[] trees = rc.senseNearbyTrees(point, 1, null);
                if (trees.length > 5) return true;
            }
        }
        return false;
    }

    public static boolean isBoxedMap() {
        return true; // since map is Boxed
    }

    public static MapLocation findBandGap(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        int sensorRadius = (int) rc.getType().sensorRadius;
        Map<Integer, Integer> treeCountPerY = new HashMap<>();
        for (int dy = -sensorRadius; dy <= sensorRadius; dy += 3) {
            int y = (int) current.y + dy;
            if (y < 474 || y >= 524) continue; // Boxed map bounds
            int count = 0;
            for (int dx = -sensorRadius; dx <= sensorRadius; dx += 1) {
                int x = (int) current.x + dx;
                if (x < 377 || x >= 427) continue; // Boxed map bounds
                MapLocation loc = new MapLocation(x, y);
                if (rc.canSenseLocation(loc)) {
                    TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                    if (trees.length > 0) count++;
                }
            }
            treeCountPerY.put(y, count);
        }
        List<Integer> bands = new ArrayList<>();
        int maxPossible = (sensorRadius * 2) / 3;
        for (Map.Entry<Integer, Integer> entry : treeCountPerY.entrySet()) {
            if (entry.getValue() > maxPossible) bands.add(entry.getKey());
        }
        Map<Integer, List<Integer>> gapsPerBand = new HashMap<>();
        for (int bandY : bands) {
            List<Integer> gaps = new ArrayList<>();
            for (int x = 377; x < 427; x++) { // Boxed map bounds
                MapLocation loc = new MapLocation(x, bandY);
                if (rc.canSenseLocation(loc)) {
                    TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                    if (trees.length == 0) gaps.add(x);
                }
            }
            gapsPerBand.put(bandY, gaps);
        }
        for (int bandY : bands) {
            if ((current.y < bandY && target.y > bandY) || (current.y > bandY && target.y < bandY)) {
                List<Integer> gaps = gapsPerBand.get(bandY);
                if (gaps.isEmpty()) continue;
                int closestGap = gaps.get(0);
                int minDist = Math.abs(closestGap - (int) target.x);
                for (int gap : gaps) {
                    int dist = Math.abs(gap - (int) target.x);
                    if (dist < minDist) {
                        minDist = dist;
                        closestGap = gap;
                    }
                }
                return new MapLocation(closestGap, bandY);
            }
        }
        return null;
    }

    public static boolean tryMoveBug(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        if (current.distanceTo(target) < 1) return false; // close enough

        int id = rc.getID();
        boolean isFollowingWall = followingWall.getOrDefault(id, false);
        Direction currentBugDir = bugDirection.get(id);

        Direction toTarget = current.directionTo(target);
        if (!isFollowingWall) {
            if (isBoxedMap() && current.x < 402 && target.x > 402 && hasTreeBand(current, target)) {
                if (rc.canMove(Direction.getSouth())) {
                    rc.move(Direction.getSouth());
                    return true;
                }
                if (rc.canMove(Direction.getNorth())) {
                    rc.move(Direction.getNorth());
                    return true;
                }
            } else if (isBoxedMap() && hasTreeBand(current, target)) {
                MapLocation gap = findBandGap(target);
                if (gap != null) {
                    Direction toGap = current.directionTo(gap);
                    if (rc.canMove(toGap)) {
                        rc.move(toGap);
                        return true;
                    }
                    return tryMove(toGap);
                } else {
                    if (rc.canMove(Direction.getSouth())) {
                        rc.move(Direction.getSouth());
                        return true;
                    }
                    if (rc.canMove(Direction.getNorth())) {
                        rc.move(Direction.getNorth());
                        return true;
                    }
                }
            } else if (hasTreeBand(current, target)) {
                if (rc.canMove(Direction.getSouth())) {
                    rc.move(Direction.getSouth());
                    return true;
                }
                if (rc.canMove(Direction.getNorth())) {
                    rc.move(Direction.getNorth());
                    return true;
                }
            }
            if (rc.canMove(toTarget)) {
                rc.move(toTarget);
                return true;
            } else {
                followingWall.put(id, true);
                bugDirection.put(id, toTarget.rotateLeftDegrees(90)); // follow left
            }
        }

        if (isFollowingWall) {
            if (rc.canMove(currentBugDir)) {
                rc.move(currentBugDir);
                // check if can now move towards target
                MapLocation newLoc = current.add(currentBugDir);
                Direction newToTarget = newLoc.directionTo(target);
                if (rc.canMove(newToTarget)) {
                    followingWall.put(id, false);
                    bugDirection.remove(id);
                }
                return true;
            } else {
                // rotate
                bugDirection.put(id, currentBugDir.rotateLeftDegrees(10));
            }
        }

        return false;
    }
}
=== FILE: src/grok_code_fast_1/RobotPlayer.java ===
package grok_code_fast_1;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static int gardenersHired = 0;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Initialize classes
        Gardener.init(rc);
        Soldier.init(rc);
        Tank.init(rc);
        Nav.init(rc);

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
                runTank();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        MapLocation spawnLoc = rc.getLocation();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Hire up to 3 gardeners in first 100 rounds
                if (rc.getRoundNum() < 100 && gardenersHired < 3 && rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    gardenersHired++;
                }

                // Broadcast gardeners hired
                rc.broadcast(2, gardenersHired);

                // Move
                MapLocation target = (gardenersHired >= 2) ? Nav.enemyCenter : spawnLoc;
                Nav.tryMoveBug(target);

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                // VP donation logic
                if (rc.getTeamBullets() > 0.5f * rc.getVictoryPointCost()) {
                    rc.donate(rc.getTeamBullets());
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                // Check for nearby trees blocking build areas
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5.0f, null);
                if (nearbyTrees.length > 0) {
                    tryMove(randomDirection());
                }

                // Attempt to build robots
                Gardener.buildRobot();

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // Fire
                Soldier.fire();

                // Move towards enemy center
                Nav.tryMoveBug(Nav.enemyCenter);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("I'm an tank!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Fire at enemies
                Tank.fire();

                // Determine target location
                MapLocation targetLoc = Nav.enemyCenter;
                if (robots.length > 0) {
                    targetLoc = robots[0].location;
                }

                // Move towards target, tanks can move through trees
                Nav.tryMoveBug(targetLoc);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move towards enemy center
                        Nav.tryMoveBug(Nav.enemyCenter);
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }
}
=== FILE: src/grok_code_fast_1/Soldier.java ===
package grok_code_fast_1;
import battlecode.common.*;

public class Soldier {
    static RobotController rc;

    public static void init(RobotController rc) {
        Soldier.rc = rc;
    }

    public static void fire() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            MapLocation enemyLoc = robots[0].location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Check for friendly robots near the enemy location to avoid friendly fire
            RobotInfo[] friendlies = rc.senseNearbyRobots(enemyLoc, 3, rc.getTeam());
            if (friendlies.length > 0) {
                return; // Don't shoot if friendlies are near
            }

            // Use area shots when close for efficiency
            if (dist < 5 && rc.canFirePentadShot()) {
                rc.firePentadShot(dir);
            } else if (dist < 7 && rc.canFireTriadShot()) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}
=== FILE: src/grok_code_fast_1/Tank.java ===
package grok_code_fast_1;
import battlecode.common.*;

public class Tank {
    static RobotController rc;

    public static void init(RobotController rc) {
        Tank.rc = rc;
    }

    public static void fire() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            MapLocation enemyLoc = robots[0].location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Use area shots when close for efficiency, adapted for tank range
            if (dist <= 2 && rc.canFirePentadShot()) {
                rc.firePentadShot(dir);
            } else if (dist <= 3 && rc.canFireTriadShot()) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}
