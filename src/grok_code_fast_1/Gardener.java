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
        // PURE ECONOMY GARDENERS - no military, just water trees and save bullets
        // No unit building - save all bullets for VP donations by archon
        // Fallback: if round >50 and no units built, force build soldier
        if (rc.getRoundNum() > 50 && buildCount == 0) {
            tryBuildUnit();
        }
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
        RobotInfo[] enemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
        int lumberjackCount = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.LUMBERJACK) {
                lumberjackCount++;
            }
        }
        Comms.broadcastLumberjackThreat(lumberjackCount);
        int priority = Comms.getProductionPriority(); // Read from broadcast
        int turnCount = rc.getRoundNum();
        RobotType toBuild;
 // MAXIMUM MILITARY PRODUCTION - balanced force for elimination
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(6.0f);
        int minLumberjacks = (turnCount < 400) ? 4 : 2;  // More lumberjacks for tree clearing
        int minSoldiers = (turnCount < 400) ? 20 : 15;  // Massive soldier wave
        
        // Adapt based on tree density
        if (nearbyTrees.length > 12) {
            minLumberjacks = (turnCount < 400) ? 8 : 5;  // Even more lumberjacks in dense areas
        }
        
        if (Comms.getOurLumberjackCount() < minLumberjacks && nearbyTrees.length > 8) {
            toBuild = RobotType.LUMBERJACK;
        } else if (Comms.getOurSoldierCount() < minSoldiers) {
            toBuild = RobotType.SOLDIER;
        } else {
            if (turnCount > 200 && lumberjackCount == 0) {
                toBuild = RobotType.SOLDIER;
            } else if (turnCount < 100 && Comms.getOurSoldierCount() < 3) {
                toBuild = RobotType.SOLDIER;
            } else if (priority == 0) {
                toBuild = RobotType.LUMBERJACK;
            } else if (priority == 2) {
                toBuild = RobotType.SCOUT;
            } else {
                // Default to soldiers for aggression
                toBuild = RobotType.SOLDIER;
            }
        }
        Direction[] dirs = Utils.getDirections();
        for (Direction dir : dirs) {
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                buildCount++;
                if (toBuild == RobotType.LUMBERJACK) {
                    Comms.broadcastOurLumberjackCount(Comms.getOurLumberjackCount() + 1);
                }
                if (toBuild == RobotType.SOLDIER) {
                    Comms.broadcastOurSoldierCount(Comms.getOurSoldierCount() + 1);
                }
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
                        if (fallback == RobotType.LUMBERJACK) {
                            Comms.broadcastOurLumberjackCount(Comms.getOurLumberjackCount() + 1);
                        }
                        if (fallback == RobotType.SOLDIER) {
                            Comms.broadcastOurSoldierCount(Comms.getOurSoldierCount() + 1);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean tryPlantTree() throws GameActionException {
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