package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Gardener - Economy and production unit.
 * Key responsibilities:
 * - Find optimal settling spot
 * - Plant trees in hexagonal pattern (5 trees + 1 build slot)
 * - Water trees (prioritize lowest health)
 * - Build units based on map type and game phase
 */
public strictfp class Gardener {
    private static RobotController rc;

    // Hexagonal farm directions (60 degree spacing)
    // Direction 0 is reserved for building units
    private static final Direction[] FARM_DIRS = {
            new Direction(0),                           // BUILD SLOT (East)
            new Direction((float)(Math.PI / 3)),        // Tree 1 (60 degrees)
            new Direction((float)(2 * Math.PI / 3)),    // Tree 2 (120 degrees)
            new Direction((float)(Math.PI)),            // Tree 3 (180 degrees)
            new Direction((float)(4 * Math.PI / 3)),    // Tree 4 (240 degrees)
            new Direction((float)(5 * Math.PI / 3))     // Tree 5 (300 degrees)
    };

    private static boolean settled = false;
    private static int settlingAttempts = 0;
    private static int treesPlanted = 0;
    private static int unitsBuilt = 0;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        while (true) {
            try {
                Utils.updatePerTurn();

                if (!settled) {
                    // Phase 1: Find and move to settle spot
                    findSettleSpot();
                } else {
                    // Phase 2: Settled - manage farm and production

                    // First priority: Water trees
                    waterTrees();

                    // Second priority: Build units (early game)
                    if (unitsBuilt < 2 || Utils.teamBullets > 150) {
                        buildUnits();
                    }

                    // Third priority: Plant trees
                    plantTrees();

                    // If nothing else to do, build more units
                    buildUnits();
                }

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Find and move toward a good settling spot.
     */
    private static void findSettleSpot() throws GameActionException {
        // Force settle after too many attempts
        if (settlingAttempts > 40) {
            settled = true;
            Comms.incrementSettledGardeners();
            return;
        }

        // Check if current location is good
        int openDirs = Nav.countOpenDirections();
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(5f, Utils.myTeam);
        int nearbyGardeners = Utils.countType(nearbyAllies, RobotType.GARDENER);

        // Good spot: at least 4 open directions and no other gardeners within 5
        if (openDirs >= 4 && nearbyGardeners == 0) {
            // Check if we're not too close to map edge
            if (isValidSettleLocation()) {
                settled = true;
                Comms.incrementSettledGardeners();
                return;
            }
        }

        // Move to find better spot
        Direction moveDir = Nav.findSettlingDirection();
        Nav.tryMove(moveDir);
        settlingAttempts++;
    }

    /**
     * Check if current location is valid for settling.
     */
    private static boolean isValidSettleLocation() throws GameActionException {
        // Check distance from map edges
        // We need space for trees (radius 2.5 from center)
        for (Direction dir : FARM_DIRS) {
            MapLocation checkLoc = Utils.myLoc.add(dir, 2.5f);
            if (!rc.onTheMap(checkLoc)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Water the tree with lowest health.
     */
    private static void waterTrees() throws GameActionException {
        TreeInfo[] myTrees = rc.senseNearbyTrees(2f, Utils.myTeam);
        if (myTrees.length == 0) return;

        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;

        for (TreeInfo tree : myTrees) {
            if (rc.canWater(tree.ID)) {
                // Prioritize trees that need water most
                float healthRatio = tree.health / tree.maxHealth;
                if (healthRatio < 0.95f && tree.health < lowestHealth) {
                    lowestHealth = tree.health;
                    lowestTree = tree;
                }
            }
        }

        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }
    }

    /**
     * Plant trees in the hexagonal pattern.
     */
    private static void plantTrees() throws GameActionException {
        if (!rc.isBuildReady()) return;
        if (treesPlanted >= 5) return;  // Max 5 trees

        // Try to plant in each tree direction (skip build slot at index 0)
        for (int i = 1; i < FARM_DIRS.length; i++) {
            Direction dir = FARM_DIRS[i];
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                treesPlanted++;
                return;
            }
        }
    }

    /**
     * Build units based on map type and game phase.
     */
    private static void buildUnits() throws GameActionException {
        if (!rc.isBuildReady()) return;

        // Check if we have enough bullets
        if (Utils.teamBullets < 100) return;

        int mapType = Comms.getMapType();
        Direction buildDir = FARM_DIRS[0];  // Build slot

        // Check if build direction is clear
        if (!canBuildInDirection(buildDir)) {
            // Try other directions
            for (Direction dir : Utils.HEX_DIRS) {
                if (canBuildInDirection(dir)) {
                    buildDir = dir;
                    break;
                }
            }
        }

        // Early game build order
        if (Utils.isEarlyGame()) {
            // Dense maps: prioritize lumberjacks
            if (mapType == Utils.MAP_DENSE) {
                if (Comms.getUnitCount(RobotType.LUMBERJACK) < 3) {
                    tryBuild(RobotType.LUMBERJACK, buildDir);
                    return;
                }
            }
            // Open maps: early scout for economy
            else if (mapType == Utils.MAP_OPEN) {
                if (Comms.getUnitCount(RobotType.SCOUT) < 1) {
                    tryBuild(RobotType.SCOUT, buildDir);
                    return;
                }
            }

            // Default: build soldier for defense
            if (Comms.getUnitCount(RobotType.SOLDIER) < 3) {
                tryBuild(RobotType.SOLDIER, buildDir);
                return;
            }
        }

        // Mid game build order
        if (Utils.isMidGame()) {
            int soldiers = Comms.getUnitCount(RobotType.SOLDIER);
            int lumberjacks = Comms.getUnitCount(RobotType.LUMBERJACK);

            // Maintain 3:1 soldier to lumberjack ratio
            if (lumberjacks < 2) {
                tryBuild(RobotType.LUMBERJACK, buildDir);
            } else if (soldiers > lumberjacks * 3) {
                tryBuild(RobotType.LUMBERJACK, buildDir);
            } else {
                tryBuild(RobotType.SOLDIER, buildDir);
            }
            return;
        }

        // Late game build order
        if (Utils.isLateGame()) {
            // Build tanks if we have economy
            if (Utils.teamBullets > 350 && Comms.getUnitCount(RobotType.TANK) < 3) {
                tryBuild(RobotType.TANK, buildDir);
                return;
            }
            // Otherwise soldiers
            tryBuild(RobotType.SOLDIER, buildDir);
        }
    }

    /**
     * Check if we can build in a direction.
     */
    private static boolean canBuildInDirection(Direction dir) throws GameActionException {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) ||
                rc.canBuildRobot(RobotType.LUMBERJACK, dir) ||
                rc.canBuildRobot(RobotType.SCOUT, dir);
    }

    /**
     * Try to build a robot of the given type.
     */
    private static boolean tryBuild(RobotType type, Direction primaryDir) throws GameActionException {
        if (!rc.isBuildReady()) return false;
        if (Utils.teamBullets < type.bulletCost) return false;

        // Try primary direction
        if (rc.canBuildRobot(type, primaryDir)) {
            rc.buildRobot(type, primaryDir);
            Comms.incrementUnitCount(type);
            unitsBuilt++;
            return true;
        }

        // Try all hex directions
        for (Direction dir : Utils.HEX_DIRS) {
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                Comms.incrementUnitCount(type);
                unitsBuilt++;
                return true;
            }
        }

        // Try random directions
        for (int i = 0; i < 5; i++) {
            Direction dir = Utils.randomDirection();
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                Comms.incrementUnitCount(type);
                unitsBuilt++;
                return true;
            }
        }

        return false;
    }
}
