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
            // Clustering check
            RobotInfo[] allies = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (allies.length > 5) {
                MapLocation allyCentroid = Utils.calculateCentroid(allies);
                Direction away = rc.getLocation().directionTo(allyCentroid).opposite();
                Nav.tryMove(away);
                return;
            }
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0) {
                RobotInfo target = null;
                for (RobotInfo enemy : enemies) {
                    if (enemy.type == RobotType.ARCHON) {
                        target = enemy;
                        break;
                    }
                }
                if (target == null) {
                    for (RobotInfo enemy : enemies) {
                        if (enemy.type == RobotType.GARDENER) {
                            target = enemy;
                            break;
                        }
                    }
                }
                // ... existing clearing logic
                // Proactive clearing: check for blocking trees before moving
                TreeInfo[] blockingTrees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 6.0f, Team.NEUTRAL);  // Increased range
                if (blockingTrees.length > 0) {
                    for (TreeInfo tree : blockingTrees) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                            return; // Chop to clear path
                        }
                    }
                }
                if (!Nav.moveToward(target.location)) {
                    // If still blocking after move attempt, chop more
                    for (TreeInfo tree : blockingTrees) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                            break;
                        }
                    }
                    // Retry movement after chopping
                    Nav.moveToward(target.location);
                }
            } else {
                // Prioritize tree defense if enemy lumberjacks detected
                if (Comms.isLumberjackDetected()) {
                    MapLocation defendLoc = Comms.getTreeCluster();
                    if (defendLoc == null) defendLoc = Comms.readLocation(0, 1); // friendly archon
                    if (defendLoc != null) {
                        Nav.moveToward(defendLoc);
                        return;
                    }
                }
                // Explore aggressively with clearing
                TreeInfo[] blockingTrees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 6.0f, Team.NEUTRAL);  // Increased range
                if (blockingTrees.length > 0) {
                    for (TreeInfo tree : blockingTrees) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                            return; // Clear before random move
                        }
                    }
                }
                // If enemy detected, move to enemy archon to chop trees
                MapLocation enemyArchon = Comms.getEnemyArchonLocation();
                if (enemyArchon != null) {
                    Nav.moveToward(enemyArchon);
                } else {
                    // Else, rally
                    MapLocation rally = Comms.getRallyPoint();
                    if (rally != null) {
                        Nav.moveToward(rally);
                    } else {
                        // Move toward predicted enemy base or center
                        MapLocation friendlyArchon = Comms.readLocation(0, 1);
                        if (friendlyArchon != null) {
                            float oppX = 100.0f - friendlyArchon.x;
                            float oppY = 100.0f - friendlyArchon.y;
                            MapLocation predicted = new MapLocation(oppX, oppY);
                            Nav.moveToward(predicted);
                        } else {
                            MapLocation center = new MapLocation(50.0f, 50.0f);
                            Nav.moveToward(center);
                        }
                    }
                }
            }
        }
        // Add directional clearing: if moving toward enemy archon, prioritize chopping trees in path
        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            Direction toArchon = rc.getLocation().directionTo(enemyArchon);
            TreeInfo[] treesInPath = rc.senseNearbyTrees(10.0f, Team.NEUTRAL);
            for (TreeInfo tree : treesInPath) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                if (Math.abs(toArchon.degreesBetween(toTree)) < 45 && rc.canChop(tree.ID)) {
                    rc.chop(tree.ID);
                    return;
                }
            }
        }
    }

    static boolean tryStrike() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
        if (enemies.length >= 2 && allies.length <= 2) {  // Changed from >2 to >=2, and <=1 to <=2
            if (rc.canStrike()) {
                rc.strike();
                return true;
            }
        } else if (enemies.length > 0 && allies.length == 0) {  // Unchanged fallback
            if (rc.canStrike()) {
                rc.strike();
                return true;
            }
        }
        return false;
    }

    static boolean tryChopTree() throws GameActionException {
        // Prioritize enemy trees for harassment
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, rc.getTeam().opponent());
        if (enemyTrees.length > 0) {
            if (rc.canChop(enemyTrees[0].ID)) {
                rc.chop(enemyTrees[0].ID);
                return true;
            }
        }
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