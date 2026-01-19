package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Gardener {
    RobotController rc;
    int treesPlanted = 0;
    Direction buildDirection = Direction.SOUTH;
    int buildCount = 0;
    MapLocation wateringTarget = null;
    MapLocation quadrantTarget = null;

    public static void run(RobotController rc) throws GameActionException {
        Gardener g = new Gardener();
        g.rc = rc;
        g.treesPlanted = 0;
        g.buildDirection = Direction.SOUTH;
        g.buildCount = 0;
        g.wateringTarget = null;
        g.quadrantTarget = null;
        Nav.init(rc);
        Comms.init(rc);
        MapLocation archonStart = rc.getInitialArchonLocations(rc.getTeam())[0];
        g.quadrantTarget = null;

        while (true) {
            try {
                g.doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    void doTurn() throws GameActionException {
        int priority = Comms.getProductionPriority();
        waterLowestHealthTree();
        // Move towards tree needing water
        if (wateringTarget == null || (rc.canSenseLocation(wateringTarget) && rc.senseTreeAtLocation(wateringTarget).health >= 50)) {
            TreeInfo[] allTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, rc.getTeam());
            TreeInfo targetTree = null;
            float minDist = Float.MAX_VALUE;
            for (TreeInfo tree : allTrees) {
                if (tree.health < 50 && rc.getLocation().distanceTo(tree.location) < minDist) {
                    minDist = rc.getLocation().distanceTo(tree.location);
                    targetTree = tree;
                }
            }
            wateringTarget = targetTree != null ? targetTree.location : null;
        }
        if (wateringTarget != null && !rc.hasMoved()) {
            Direction dirToTarget = rc.getLocation().directionTo(wateringTarget);
            Nav.tryMove(dirToTarget);
        }
        if (priority == 1) {
            // Balanced: plant up to 4 trees for economy, then soldiers
            if (tryBuildUnit()) {
                return;
            }
        } else {
            // VP strategy: plant trees first, then build units
            if (tryPlantTree()) {
                treesPlanted++;
                return;
            }
            if (tryBuildUnit()) {
                return;
        }

        if (!rc.hasMoved() && priority == 1) {
            // Move to center if couldn't build soldier
            MapLocation center = new MapLocation(50.0f, 50.0f);
            Nav.moveToward(center);
        }

        if (!rc.hasMoved()) {
            MapLocation target = Comms.getEnemyArchonLocation();
            if (target != null) {
                Nav.moveToward(target);
            } else {
                Nav.tryMove(Nav.randomDirection());
            }
        }
        }
    }

    void waterLowestHealthTree() throws GameActionException {
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

    boolean tryBuildUnit() throws GameActionException {
        int priority = Comms.getProductionPriority();
        Direction[] dirs = Utils.getDirections();

        if (priority == 1) {
            // Military strategy: build soldiers only on empty maps
            for (Direction dir : dirs) {
                if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    buildCount++;
                    return true;
                }
            }
        } else {
            // VP or lumberjack strategy: build lumberjacks for bullet generation
            for (Direction dir : dirs) {
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    buildCount++;
                    return true;
                }
            }
        }
        return false;
    }

    boolean tryPlantTree() throws GameActionException {
        Direction[] dirs = Utils.getDirections();
        for (Direction dir : dirs) {
            if (rc.canPlantTree(dir)) {
                // Maximum density planting for VP generation
                MapLocation plantLoc = rc.getLocation().add(dir, rc.getType().bodyRadius + GameConstants.BULLET_TREE_RADIUS);
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(plantLoc, 2.5f, null);  // Check for blocking trees
                RobotInfo[] ownRobots = rc.senseNearbyRobots(1.5f, rc.getTeam());  // Minimal spacing
                if (nearbyTrees.length <= 5 && ownRobots.length <= 1) {  // Allow more trees for dense planting
                    rc.plantTree(dir);
                    treesPlanted++;
                    return true;
                }
            }
        }
        return false;
    }
}