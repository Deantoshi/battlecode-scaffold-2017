# Objective Work Context

## YOUR CURRENT OBJECTIVE
{
  "name": "protect-archon",
  "description": "Keep archons alive to maintain production",
  "blocking_issue": "Lost 2 archons, limiting unit production",
  "how_this_helps": "Alive archons produce more units, enabling faster victory",
  "metric_path": "unit_alive.A.ARCHON",
  "operator": ">=",
  "threshold": 3,
  "max_attempts": 4,
  "attempts": 4,
  "best_result": 0,
  "created_iteration": 1
}

## Latest Match Metrics
{
  "winner": "A",
  "outcome": "WIN",
  "won": "YES",
  "rounds": 2999,
  "target_rounds": 1500,
  "goal_met": "NO",
  "final": {
    "team_a": {
      "bullets": 11,
      "vp": 26
    },
    "team_b": {
      "bullets": 76,
      "vp": 0
    }
  }
}

Unit Summary:
[
  {
    "team": "A",
    "unit": "ARCHON",
    "produced": 3,
    "lost": 2,
    "alive": 1
  },
  {
    "team": "A",
    "unit": "GARDENER",
    "produced": 1,
    "lost": 1,
    "alive": 0
  },
  {
    "team": "B",
    "unit": "ARCHON",
    "produced": 3,
    "lost": 0,
    "alive": 3
  },
  {
    "team": "B",
    "unit": "GARDENER",
    "produced": 16,
    "lost": 0,
    "alive": 16
  },
  {
    "team": "B",
    "unit": "SOLDIER",
    "produced": 13,
    "lost": 1,
    "alive": 12
  },
  {
    "team": "B",
    "unit": "LUMBERJACK",
    "produced": 16,
    "lost": 1,
    "alive": 15
  }
]

## Objective History
### Objective Reassessment: establish-tree-economy

**Status:** DECOMPOSED
**Best Result:** 0 / 3
**Attempts Used:** 5
**Reason:** No progress made, best result 0. Cannot plant trees without first building a gardener.
**Next Step:** New objective "prerequisite-gardener" to produce at least 1 gardener.

### Iteration 1: New Objective Proposed

**Objective:** protect-archon
**Metric:** unit_alive.A.ARCHON >= 3
**Rationale:** Lost 2 archons, limiting unit production → Alive archons produce more units, enabling faster victory

### Attempt 2 for "protect-archon"

**Current Value:** 0
**Target:** unit_alive.A.ARCHON >= 3
**Change Made:** Added enemy avoidance to archons (move away if enemy within 5 distance) and prioritized building lumberjacks in gardeners for defense.
**File Modified:** RobotPlayer.java, Gardener.java
**Rationale:** Archons will avoid nearby enemies to survive, and lumberjacks can attack enemies threatening the archons.

### Attempt 3 for "protect-archon"

**Current Value:** 0
**Target:** unit_alive.A.ARCHON >= 3
**Change Made:** Prioritized lumberjacks over tanks in gardener build order (up to 10 lumberjacks), increased archon avoidance distance to 10, increased gardener avoidance to 5, made lumberjacks move towards archon location when no enemies nearby.
**File Modified:** Gardener.java, RobotPlayer.java
**Rationale:** More lumberjacks built earlier for better defense, archons and gardeners avoid enemies at greater distance, lumberjacks cluster around archons to protect them.

### Attempt 4 for "protect-archon"

**Current Value:** 0
**Target:** unit_alive.A.ARCHON >= 3
**Change Made:** Made archons stay near spawn location instead of moving towards enemy center to avoid danger.
**File Modified:** RobotPlayer.java
**Rationale:** Keeping archons in a safe area should prevent them from being killed by enemy units, allowing all 3 to survive.

### Attempt 5 for "protect-archon"

**Current Value:** 0
**Target:** unit_alive.A.ARCHON >= 3
**Change Made:** Modified archon movement to prioritize fleeing from nearby enemies within 10 distance over other movements.
**File Modified:** RobotPlayer.java
**Rationale:** Archons will now actively move away from threats, hopefully surviving longer to maintain unit production.
## Bot Code

### Gardener.java
```java
package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;
    static int scoutsBuilt = 0;
    static int lumberjacksBuilt = 0;
    static int tanksBuilt = 0;
    static int factoriesBuilt = 0;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        System.out.println("Gardener bullets: " + rc.getTeamBullets());
        System.out.println("Scouts built: " + scoutsBuilt);
        System.out.println("Tanks built: " + tanksBuilt);

        // Prioritize building lumberjacks for defense, then tanks, then scouts
        if (lumberjacksBuilt < 10 && rc.getTeamBullets() >= 100) {
            System.out.println("Attempting to build lumberjack");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    lumberjacksBuilt++;
                    System.out.println("Built lumberjack successfully");
                    return;
                }
            }
            System.out.println("Failed to build lumberjack in all attempts");
        } else if (tanksBuilt < 5 && rc.getTeamBullets() >= 300) {
            System.out.println("Bullets >=300, attempting to build tank");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.TANK, dir)) {
                    if (rc.isBuildReady()) {
                        rc.buildRobot(RobotType.TANK, dir);
                        tanksBuilt++;
                        System.out.println("Built tank successfully");
                        return;
                    } else {
                        System.out.println("Build not ready for tank");
                    }
                } else {
                    System.out.println("Cannot build tank in direction " + dir);
                }
            }
            System.out.println("Failed to build tank in all directions");
        } else if (scoutsBuilt < 15 && rc.getTeamBullets() >= 100) {
            System.out.println("Attempting to build scout");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.SCOUT, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    scoutsBuilt++;
                    System.out.println("Built scout successfully");
                    return;
                }
            }
            System.out.println("Failed to build scout in all attempts");
        }
    }

    public static void plantTree() throws GameActionException {
        System.out.println("Trying to plant tree, bullets: " + rc.getTeamBullets());
        if (rc.getTeamBullets() >= 50) {
            Direction[] dirs = {
                Direction.getNorth(),
                Direction.getEast(),
                Direction.getSouth(),
                Direction.getWest(),
                Direction.getNorth().rotateRightDegrees(45), // NE
                Direction.getEast().rotateRightDegrees(45), // SE
                Direction.getSouth().rotateRightDegrees(45), // SW
                Direction.getWest().rotateRightDegrees(45) // NW
            };
            for (Direction dir : dirs) {
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    System.out.println("Planted tree!");
                    return;
                }
            }
        }
    }

    public static void waterTree() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2, rc.getTeam());
        for (TreeInfo tree : nearbyTrees) {
            if (rc.canWater(tree.ID)) {
                rc.water(tree.ID);
                return;
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}```

### Nav.java
```java
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
            // Stay near spawn
            if (rc.getLocation().distanceTo(spawnLoc) > 5) {
                return tryMove(rc.getLocation().directionTo(spawnLoc));
            } else {
                return false;
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
}```

### Navigation.java
```java
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
}```

### RobotPlayer.java
```java
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
        Scout.init(rc);
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
            case SCOUT:
                runScout();
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
                RobotInfo[] enemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    System.out.println("Archon fleeing from " + enemies.length + " enemies");
                    MapLocation enemyLoc = enemies[0].location;
                    Direction away = rc.getLocation().directionTo(enemyLoc).opposite();
                    tryMove(away);
                } else {
                    MapLocation target = (gardenersHired >= 2) ? Nav.enemyCenter : spawnLoc;
                    Nav.tryMoveBug(target);
                }

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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {
                MapLocation myLocation = rc.getLocation();

                // Fire
                Scout.fire();

                // Move towards enemy center
                Nav.tryMoveBug(Nav.enemyCenter);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
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
}```

### Scout.java
```java
package grok_code_fast_1;
import battlecode.common.*;

public class Scout {
    static RobotController rc;

    public static void init(RobotController rc) {
        Scout.rc = rc;
    }

    public static void fire() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            // Prioritize archons
            RobotInfo target = null;
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    target = robot;
                    break;
                }
            }
            if (target == null) {
                target = robots[0];
            }
            MapLocation enemyLoc = target.location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Check for friendly robots near the enemy location to avoid friendly fire
            RobotInfo[] friendlies = rc.senseNearbyRobots(enemyLoc, 3, rc.getTeam());
            boolean hasFriendliesNear = friendlies.length > 0;

            // Scouts can only fire single shot
            if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}```

### Soldier.java
```java
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
            // Prioritize archons
            RobotInfo target = null;
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    target = robot;
                    break;
                }
            }
            if (target == null) {
                target = robots[0];
            }
            MapLocation enemyLoc = target.location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Check for friendly robots near the enemy location to avoid friendly fire
            RobotInfo[] friendlies = rc.senseNearbyRobots(enemyLoc, 3, rc.getTeam());
            boolean hasFriendliesNear = friendlies.length > 0;

            // Use area shots when close for efficiency, but only if no friendlies near
            if (dist < 5 && rc.canFirePentadShot() && !hasFriendliesNear) {
                rc.firePentadShot(dir);
            } else if (dist < 7 && rc.canFireTriadShot() && !hasFriendliesNear) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}```

### Tank.java
```java
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
            // Prioritize archons
            RobotInfo target = null;
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    target = robot;
                    break;
                }
            }
            if (target == null) {
                target = robots[0];
            }
            MapLocation enemyLoc = target.location;
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
}```
