package minimax_2_1;
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
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5.0f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            Comms.broadcastEnemySpotted(enemies[0].location);
            RobotInfo target = findArchonTarget(enemies);
            if (tryShoot(target)) {
                return;
            }
            target = findGardenerTarget(enemies);
            if (tryShoot(target)) {
                return;
            }
            target = Utils.findLowestHealthTarget(enemies);
            if (tryShoot(target)) {
                return;
            }
        } else {
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else {
                MapLocation lastSighting = Comms.getLastEnemySighting();
                if (lastSighting != null) {
                    Nav.moveToward(lastSighting);
                } else {
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
    }

    static boolean tryMoveToAttack(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        float dist = rc.getLocation().distanceTo(target.location);
        if (dist > 2.0f) {
            if (Nav.moveToward(target.location)) {
                return true;
            }
            MapLocation loc = target.location;
            for (int i = 0; i < 8; i++) {
                Direction d = new Direction((float)(i * Math.PI / 4));
                MapLocation nearby = loc.add(d, 2.0f);
                if (Nav.moveToward(nearby)) {
                    return true;
                }
            }
            Nav.tryMove(Nav.randomDirection());
            return true;
        }
        return false;
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;
        
        RobotInfo best = null;
        float bestScore = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.location);
            float health = enemy.health;
            float score = health + dist * 0.5f;
            if (enemy.type == RobotType.ARCHON) score -= 200;
            else if (enemy.type == RobotType.GARDENER) score -= 100;
            if (score < bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    static RobotInfo findArchonTarget(RobotInfo[] enemies) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        return null;
    }
    
    static RobotInfo findGardenerTarget(RobotInfo[] enemies) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                return enemy;
            }
        }
        return null;
    }
    
    static boolean tryShoot(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        
        if (rc.canFirePentadShot()) {
            rc.firePentadShot(dir);
            return true;
        }
        if (rc.canFireTriadShot()) {
            rc.fireTriadShot(dir);
            return true;
        }
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
            return true;
        }
        return false;
    }
}
