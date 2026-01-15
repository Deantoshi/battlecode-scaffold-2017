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
            // Aggressive pursuit: always move toward nearest enemy
            if (!rc.hasMoved()) {
                Nav.moveToward(target.location);
            }
        }
        // In doTurn, after enemy check, before movement
        if (!rc.hasMoved()) {
            RobotInfo[] allies = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (allies.length > 5) { // Too clustered
                MapLocation allyCentroid = Utils.calculateCentroid(allies);
                Direction away = rc.getLocation().directionTo(allyCentroid).opposite();
                Nav.tryMove(away);
                return; // Prioritize spacing
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
                    if (bullet.getLocation().add(bullet.getDir(), bullet.getSpeed()).distanceTo(nextLoc) < rc.getType().bodyRadius + 0.5f) {
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
            MapLocation rally = Comms.getRallyPoint();
            if (rally != null) {
                Nav.moveToward(rally);
            } else {
                MapLocation enemyLoc = Comms.getEnemyLocation();
                if (enemyLoc != null) {
                    Nav.moveToward(enemyLoc);
            } else {
                MapLocation enemyArchonLoc = Comms.getEnemyArchonLocation();
                if (enemyArchonLoc != null) {
                    Nav.moveToward(enemyArchonLoc);
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
        // Prioritize lowest health for focus fire
        RobotInfo lowestHealth = Utils.findLowestHealthTarget(enemies);
        if (lowestHealth != null) {
            return lowestHealth;
        }
        // Fallback to type priorities if tie
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.TANK) {
                return enemy;
            }
        }
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                return enemy;
            }
        }
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER) {
                return enemy;
            }
        }
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SCOUT) {
                return enemy;
            }
        }
        return null;
    }

    static void tryShoot(RobotInfo target, RobotInfo[] enemies) throws GameActionException {
        if (target != null && rc.canFireSingleShot()) rc.fireSingleShot(rc.getLocation().directionTo(target.location));
    }
}
