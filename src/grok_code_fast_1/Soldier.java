package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Soldier {
    static RobotController rc;

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
        if (enemies.length > 0) {
            Comms.broadcastEnemyLocation(enemies[0].location);
            RobotInfo target = findTarget();
            tryShoot(target, enemies);
            // Kiting: If health low, move away instead of toward
            if (rc.getHealth() < rc.getType().maxHealth * 0.3 && !rc.hasMoved()) {
                Direction away = rc.getLocation().directionTo(target.location).opposite();
                Nav.tryMove(away);
            } else if (!rc.hasMoved()) {
                Nav.moveToward(target.location);
            }
        }
        // Enhanced bullet evasion
        BulletInfo[] bullets = rc.senseNearbyBullets();
        if (bullets.length > 0 && !rc.hasMoved()) {
            Direction bestDir = null;
            float bestDist = Float.MAX_VALUE;
            for (Direction dir : Utils.getDirections()) {
                MapLocation nextLoc = rc.getLocation().add(dir, rc.getType().strideRadius);
                boolean safe = true;
                for (BulletInfo bullet : bullets) {
                    if (bullet.getLocation().add(bullet.getDir(), bullet.getSpeed()).distanceTo(nextLoc) < rc.getType().bodyRadius + 0.1f) {
                        safe = false;
                        break;
                    }
                }
                if (safe) {
                    float distToTarget = (enemies.length > 0) ? nextLoc.distanceTo(enemies[0].location) : 0;
                    if (bestDir == null || distToTarget < bestDist) {
                        bestDir = dir;
                        bestDist = distToTarget;
                    }
                }
            }
            if (bestDir != null) {
                Nav.tryMove(bestDir);
            }
        } else if (!rc.hasMoved()) {
            MapLocation rallyPoint = Comms.getEnemyLocation();
            if (rallyPoint != null) {
                Nav.moveToward(rallyPoint);
            } else {
                MapLocation enemyLoc = Comms.getEnemyArchonLocation();
                if (enemyLoc != null) {
                    Nav.moveToward(enemyLoc);
                } else {
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.TANK) {
                return enemy;
            }
        }
        return Utils.findLowestHealthTarget(enemies);
    }

    static boolean tryShoot(RobotInfo target, RobotInfo[] enemies) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadius, rc.getTeam());
        for (RobotInfo ally : allies) {
            Direction toAlly = rc.getLocation().directionTo(ally.location);
            float dist = rc.getLocation().distanceTo(ally.location);
            float distToTarget = rc.getLocation().distanceTo(target.location);
            if (dist < distToTarget && Math.abs(dir.degreesBetween(toAlly)) < 15) {
                return false;
            }
        }
        // Adaptive: Use pentad if multiple enemies nearby
        if (enemies.length > 2 && rc.canFirePentadShot()) {
            rc.firePentadShot(dir);
        } else if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
        }
        return true;
    }
}
