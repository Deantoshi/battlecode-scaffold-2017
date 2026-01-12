package minimax_2_1;
import battlecode.common.*;

public strictfp class Lumberjack {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
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
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(10, rc.getTeam());
        
        if (enemies.length > 0) {
            if (enemies.length <= allies.length + 1) {
                RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
                if (closest != null && rc.getLocation().distanceTo(closest.location) <= GameConstants.LUMBERJACK_STRIKE_RADIUS) {
                    if (tryStrike()) {
                        return;
                    }
                }
                Nav.moveToward(closest.location);
                return;
            } else {
                RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
                Direction away = rc.getLocation().directionTo(closest.location).opposite();
                Nav.tryMove(away);
                return;
            }
        }

        if (tryChopTree()) {
            return;
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            Nav.moveToward(enemyArchon);
            return;
        }

        Nav.tryMove(Nav.randomDirection());
    }

    static boolean tryStrike() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS / 2, rc.getTeam());
        if (enemies.length > 0) {
            if (rc.canStrike()) {
                rc.strike();
                return true;
            }
        }
        return false;
    }

    static boolean tryChopTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
        if (trees.length > 0) {
            if (rc.canChop(trees[0].ID)) {
                rc.chop(trees[0].ID);
                return true;
            }
        }
        return false;
    }
}
