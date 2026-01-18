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
            // Aggressive engagement: close distance for faster kills
            if (!rc.hasMoved()) {
                if (dist < 3.0f) {  // Very close range - minimal kiting
                    Direction awayDir = rc.getLocation().directionTo(target.location).opposite();
                    Nav.tryMove(awayDir);
                } else {
                    // Always advance toward target
                    Nav.moveToward(target.location);
                }
            }
        }
        // Clustering avoidance
        if (!rc.hasMoved()) {
            RobotInfo[] allies = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (allies.length > 5) {
                MapLocation allyCentroid = Utils.calculateCentroid(allies);
                Direction away = rc.getLocation().directionTo(allyCentroid).opposite();
                Nav.tryMove(away);
                return;
            }
        }
        // Enhanced bullet evasion: priority over other movement
        BulletInfo[] bullets = rc.senseNearbyBullets();
        if (bullets.length > 0 && !rc.hasMoved()) {
            Direction bestDir = null;
            float bestDist = Float.MAX_VALUE;
            float minDistToBullet = Float.MAX_VALUE;
            for (Direction dir : Utils.getDirections()) {
                MapLocation nextLoc = rc.getLocation().add(dir, rc.getType().strideRadius);
                boolean safe = true;
                float closestBulletDist = Float.MAX_VALUE;
                for (BulletInfo bullet : bullets) {
                    float bulletDist = bullet.getLocation().add(bullet.getDir(), bullet.getSpeed()).distanceTo(nextLoc);
                    if (bulletDist < rc.getType().bodyRadius + 0.5f) {
                        safe = false;
                        break;
                    }
                    if (bulletDist < closestBulletDist) closestBulletDist = bulletDist;
                }
                if (safe && closestBulletDist > minDistToBullet) {
                    bestDir = dir;
                    minDistToBullet = closestBulletDist;
                }
            }
            if (bestDir != null) {
                Nav.tryMove(bestDir);
            }
        } else if (!rc.hasMoved()) {
            MapLocation enemyLoc = Comms.getEnemyLocation();
            if (enemyLoc != null) {
                Nav.moveToward(enemyLoc);
            } else {
                MapLocation enemyArchonLoc = Comms.getEnemyArchonLocation();
                if (enemyArchonLoc != null) {
                    Nav.moveToward(enemyArchonLoc);
                } else {
                    MapLocation rally = Comms.getRallyPoint();
                    if (rally != null) {
                        Nav.moveToward(rally);
                    } else {
                        MapLocation center = new MapLocation(50.0f, 50.0f);
                        Nav.moveToward(center);
                    }
                }
            }
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
        // THIRD: Closest target - advance quickly
        RobotInfo closest = null;
        float minDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.location);
            if (dist < minDist) {
                minDist = dist;
                closest = enemy;
            }
        }
        return closest;
    }

    static void tryShoot(RobotInfo target, RobotInfo[] enemies) throws GameActionException {
        if (target == null || !rc.canFireSingleShot()) return;
        MapLocation aimLocation = target.location;
        int id = target.ID;
        if (enemyVelocities.containsKey(id)) {
            Direction velDir = enemyVelocities.get(id);
            float speed = enemySpeeds.get(id);
            float bulletSpeed = 3.0f;
            float dist = rc.getLocation().distanceTo(target.location);
            float time = dist / bulletSpeed;
            MapLocation predicted = target.location.add(velDir, speed * time * 0.8f); // Slightly less conservative
            aimLocation = predicted;
        }
        if (!hasLineOfSight(rc.getLocation(), aimLocation)) {
            aimLocation = target.location;
        }
        if (hasLineOfSight(rc.getLocation(), aimLocation)) {
            // Maximum aggression - use triad whenever available
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