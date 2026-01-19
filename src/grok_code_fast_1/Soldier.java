package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public strictfp class Soldier {
    static RobotController rc;
    static Map<Integer, MapLocation> prevEnemyLocations = new HashMap<>();
    static Map<Integer, Direction> enemyVelocities = new HashMap<>();
    static Map<Integer, Float> enemySpeeds = new HashMap<>();

    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Nav.init(rc);
        Comms.init(rc);

        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void doTurn() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            int id = enemy.ID;
            if (prevEnemyLocations.containsKey(id)) {
                MapLocation prev = prevEnemyLocations.get(id);
                Direction velDir = prev.directionTo(enemy.location);
                float distMoved = prev.distanceTo(enemy.location);
                enemyVelocities.put(id, velDir);
                enemySpeeds.put(id, distMoved);
            }
            prevEnemyLocations.put(id, enemy.location);
        }
        if (enemies.length > 0) {
            Comms.broadcastEnemyLocation(enemies[0].location);
            RobotInfo target = findTarget();
            float dist = rc.getLocation().distanceTo(target.location);
            tryShoot(target, enemies);
            // ULTRA-AGGRESSIVE: suicide charge toward archons, ignore everything else
            if (!rc.hasMoved()) {
                if (target.type == RobotType.ARCHON) {
                    // Maximum aggression - get as close as possible to archon
                    Direction toArchon = rc.getLocation().directionTo(target.location);
                    if (rc.canMove(toArchon)) {
                        rc.move(toArchon);
                    } else {
                        // Try adjacent directions to get closer
                        for (Direction dir : Utils.getDirections()) {
                            if (rc.canMove(dir)) {
                                MapLocation newLoc = rc.getLocation().add(dir, rc.getType().strideRadius);
                                if (newLoc.distanceTo(target.location) < rc.getLocation().distanceTo(target.location)) {
                                    rc.move(dir);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // Still aggressive toward other targets
                    Nav.moveToward(target.location);
                }
            }
        }

        if (!rc.hasMoved()) {
            // Random movement like examplefuncsplayer for better exploration
            MapLocation target = Comms.getEnemyArchonLocation();
            if (target != null) Nav.moveToward(target); else Nav.tryMove(Nav.randomDirection());
        }
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        // ABSOLUTE PRIORITY: ARCHON - immediate win condition
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        // SECOND PRIORITY: GARDENER - cut off enemy production
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                return enemy;
            }
        }
        // THIRD: Any enemy - engage aggressively
        if (enemies.length > 0) {
            return enemies[0];
        }
        return null;
    }

    static void tryShoot(RobotInfo target, RobotInfo[] enemies) throws GameActionException {
        if (target == null || !rc.canFireSingleShot()) return;
        MapLocation aimLocation = target.location;
        if (hasLineOfSight(rc.getLocation(), aimLocation)) {
            // Maximum aggression - use triad whenever available to spend more bullets
            if (rc.canFireTriadShot()) {
                rc.fireTriadShot(rc.getLocation().directionTo(aimLocation));
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(rc.getLocation().directionTo(aimLocation));
            }
        }
    }

    private static boolean hasLineOfSight(MapLocation start, MapLocation end) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1);
        for (TreeInfo tree : trees) {
            if (intersectsLineSegmentCircle(start, end, tree.location, tree.radius)) {
                return false;
            }
        }
        return true;
    }

    private static boolean intersectsLineSegmentCircle(MapLocation start, MapLocation end, MapLocation center, float radius) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float fx = center.x - start.x;
        float fy = center.y - start.y;
        float l2 = dx * dx + dy * dy;
        if (l2 == 0) {
            return start.distanceTo(center) <= radius;
        }
        float t = Math.max(0, Math.min(1, (fx * dx + fy * dy) / l2));
        float cx = start.x + t * dx;
        float cy = start.y + t * dy;
        float distSq = (cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y);
        return distSq <= radius * radius;
    }
}