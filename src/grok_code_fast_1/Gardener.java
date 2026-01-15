package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static Direction buildDirection = Direction.SOUTH;
    static int buildCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
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
        waterLowestHealthTree();
        // Increase tree limits and add scouts
        if (treesPlanted < 1 && rc.getTeamBullets() >= 50) {
            if (tryPlantTree()) {
                treesPlanted++;
            }
        } else if (rc.getTeamBullets() >= 60) {
            tryBuildUnit();
        }
        // Fallback: if round >200 and no units built, force build soldier
        if (rc.getRoundNum() > 200 && buildCount == 0) {
            tryBuildUnit();
        }
        if (!rc.hasMoved()) {
            // Sense nearby robots for spacing
            RobotInfo[] nearbyRobots = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (nearbyRobots.length > 1) { // More than self
                Direction away = rc.getLocation().directionTo(nearbyRobots[0].location).opposite();
                Nav.tryMove(away);
            } else {
                // Existing away from archon
                Direction away = rc.getLocation().directionTo(Comms.readLocation(0,1)).opposite();
                Nav.tryMove(away);
            }
        }
    }

    static void waterLowestHealthTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, rc.getTeam());
        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : trees) {
            if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                lowestTree = tree;
            }
        }
        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }
    }

    static boolean tryBuildUnit() throws GameActionException {
        int priority = Comms.getProductionPriority(); // Read from broadcast
        RobotType toBuild;
        int turnCount = rc.getRoundNum();
        if (turnCount > 1000 && priority == 3) {
            toBuild = RobotType.TANK;
        } else if (priority == 0) {
            toBuild = RobotType.LUMBERJACK;
        } else {
            // Default to soldiers for priority 1 or unknown
            toBuild = RobotType.SOLDIER;
        }
        // Fallback: build soldiers early to ensure combat units
        if (toBuild != RobotType.SOLDIER && turnCount < 400) {
            toBuild = RobotType.SOLDIER;
        }
        Direction[] dirs = Utils.getDirections();
        for (Direction dir : dirs) {
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                buildCount++;
                return true;
            }
        }
        // Fallback to cheaper alternatives
        RobotType[] fallbacks = {RobotType.LUMBERJACK};
        for (RobotType fallback : fallbacks) {
            if (fallback != toBuild) {
                for (Direction dir : dirs) {
                    if (rc.canBuildRobot(fallback, dir)) {
                        rc.buildRobot(fallback, dir);
                        buildCount++;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean tryPlantTree() throws GameActionException {
        Direction[] dirs = Utils.getDirections();
        for (Direction dir : dirs) {
            if (rc.canPlantTree(dir)) {
                // Improve tree spacing
                MapLocation plantLoc = rc.getLocation().add(dir, rc.getType().bodyRadius + GameConstants.BULLET_TREE_RADIUS);
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(plantLoc, 4.0f, null);  // Check all teams for spacing
                if (nearbyTrees.length == 0) {  // Ensure at least 4.0f spacing
                    rc.plantTree(dir);
                    return true;
                }
            }
        }
        return false;
    }
}