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
            RobotInfo target = Utils.findLowestHealthTarget(enemies);
            if (tryShoot(target)) {
                return;
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            if (Nav.moveToward(enemyArchon)) {
                return;
            }
        }

        Nav.tryMove(Nav.randomDirection());
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
            if (enemy.type == RobotType.ARCHON) score -= 100;
            if (enemy.type == RobotType.GARDENER) score -= 50;
            if (score < bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    static boolean tryShoot(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
            return true;
        }
        return false;
    }
}
