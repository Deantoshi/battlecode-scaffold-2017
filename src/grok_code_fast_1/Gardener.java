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
  // MILITARY-FIRST STRATEGY - prioritize building soldiers for fast elimination
        if (tryBuildUnit()) {
            return;
        }
        tryPlantTree();  // Plant trees after building units
        if (!rc.hasMoved()) {
            RobotInfo[] allies = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (allies.length > 5) {
                MapLocation allyCentroid = Utils.calculateCentroid(allies);
                Direction away = rc.getLocation().directionTo(allyCentroid).opposite();
                Nav.tryMove(away);
                return;
            }
            MapLocation target = Comms.getEnemyArchonLocation();
            if (target != null) {
                Nav.moveToward(target);
            } else {
                Nav.tryMove(Nav.randomDirection());
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
        // Military strategy: build soldiers aggressively for fast win
        int currentTurn = rc.getRoundNum();
        Direction[] dirs = Utils.getDirections();
        
        // Build soldiers early and often
        if (currentTurn > 50) {
            for (Direction dir : dirs) {
                if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    buildCount++;
                    return true;
                }
            }
        }
        
        // Build lumberjacks if needed for tree clearing on MagicWood
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(4.0f);
        if (nearbyTrees.length > 8) {
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
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(plantLoc, 2.5f, null);  // Even tighter spacing
                RobotInfo[] ownRobots = rc.senseNearbyRobots(1.5f, rc.getTeam());  // Minimal spacing
                if (nearbyTrees.length <= 2 && ownRobots.length <= 1) {  // Allow clustering for max VP
                    rc.plantTree(dir);
                    return true;
                }
            }
        }
        return false;
    }
}