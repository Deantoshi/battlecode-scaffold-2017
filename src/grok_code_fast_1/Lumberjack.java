package grok_code_fast_1;
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
        if (tryStrike()) {
            // Already struck
        } else if (tryChopTree()) {
            // Chopped
        } else if (!rc.hasMoved()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0) {
                RobotInfo target = null;
                for (RobotInfo enemy : enemies) {
                    if (enemy.type == RobotType.GARDENER) {
                        target = enemy;
                        break;
                    }
                }
                if (target == null) {
                    target = Utils.findClosestEnemy(rc, enemies);
                }
                if (!Nav.moveToward(target.location)) {
                    // If blocking, try chopping nearby trees
                    TreeInfo[] blockingTrees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 1.0f, Team.NEUTRAL);
                    for (TreeInfo tree : blockingTrees) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                            break; // Chop one per turn
                        }
                    }
                    // Retry movement after chopping
                    Nav.moveToward(target.location);
                }
            } else {
                if (!Nav.tryMove(Nav.randomDirection())) {
                    // If blocking, try chopping nearby trees
                    TreeInfo[] blockingTrees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 1.0f, Team.NEUTRAL);
                    for (TreeInfo tree : blockingTrees) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                            break; // Chop one per turn
                        }
                    }
                    // Retry movement after chopping
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
    }

    static boolean tryStrike() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
        if (enemies.length > 0 && allies.length == 0) {
            if (rc.canStrike()) {
                rc.strike();
                return true;
            }
        }
        return false;
    }

    static boolean tryChopTree() throws GameActionException {
        MapLocation archonLoc = Comms.readLocation(0, 1); // Friendly archon location
        if (archonLoc != null) {
            TreeInfo[] treesNearArchon = rc.senseNearbyTrees(archonLoc, 5.0f, Team.NEUTRAL);
            for (TreeInfo tree : treesNearArchon) {
                if (rc.canChop(tree.ID)) {
                    rc.chop(tree.ID);
                    return true;
                }
            }
        }
        // Fallback to original
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
