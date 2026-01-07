package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Archon - Mobile base unit that hires Gardeners.
 * Key responsibilities:
 * - Broadcast location for team coordination
 * - Analyze map on round 1 (set build strategy)
 * - Hire gardeners strategically
 * - Dodge bullets and flee from enemies
 * - Check for VP win condition
 */
public strictfp class Archon {
    private static RobotController rc;
    private static int myArchonIndex = -1;
    private static int gardenersHired = 0;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        // Initialize per-turn data first
        Utils.updatePerTurn();

        // Determine our archon index (for multi-archon maps)
        determineArchonIndex();

        while (true) {
            try {
                Utils.updatePerTurn();

                // Step 1: Broadcast my location
                broadcastLocation();

                // Step 2: Analyze map on round 1
                if (Utils.roundNum == 1) {
                    analyzeMapDensity();
                }

                // Step 3: Check for instant VP win
                if (Utils.tryWinByDonation()) {
                    Clock.yield();
                    continue;
                }

                // Step 4: Strategic VP donation in late game
                Utils.strategicDonate();

                // Step 5: Dodge bullets
                Nav.dodgeBullets();

                // Step 6: Flee from enemies
                fleeFromEnemies();

                // Step 7: Hire gardeners
                hireGardener();

                // Step 8: Move to safe position
                moveToSafePosition();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Determine this archon's index for broadcast coordination.
     */
    private static void determineArchonIndex() throws GameActionException {
        MapLocation[] ourArchons = Utils.getMyInitialArchonLocs();
        for (int i = 0; i < ourArchons.length; i++) {
            if (Utils.myLoc.distanceTo(ourArchons[i]) < 0.5f) {
                myArchonIndex = i;
                break;
            }
        }
        if (myArchonIndex == -1) myArchonIndex = 0;

        // Set archon count on round 1
        if (Utils.roundNum <= 1) {
            Comms.setArchonCount(ourArchons.length);
        }
    }

    /**
     * Broadcast our location so team can coordinate.
     */
    private static void broadcastLocation() throws GameActionException {
        Comms.broadcastArchonLocation(myArchonIndex, Utils.myLoc);
    }

    /**
     * Analyze map density on round 1 to determine build strategy.
     */
    private static void analyzeMapDensity() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        int treeCount = nearbyTrees.length;

        // Calculate density based on tree count in sensor range
        int mapType;
        int density;

        if (treeCount >= 8) {
            mapType = Utils.MAP_DENSE;
            density = 80;
        } else if (treeCount >= 3) {
            mapType = Utils.MAP_MEDIUM;
            density = 50;
        } else {
            mapType = Utils.MAP_OPEN;
            density = 20;
        }

        Comms.setMapType(mapType);
        Comms.setTreeDensity(density);
    }

    /**
     * Hire gardeners based on game phase and economy.
     */
    private static void hireGardener() throws GameActionException {
        // Don't hire if we can't afford it
        if (Utils.teamBullets < RobotType.GARDENER.bulletCost) return;

        // Hiring schedule
        boolean shouldHire = false;

        if (Utils.roundNum <= 1 && gardenersHired < 1) {
            // Always hire first gardener immediately
            shouldHire = true;
        } else if (Utils.roundNum <= 50 && gardenersHired < 2) {
            // Second gardener by round 50
            shouldHire = Utils.teamBullets > 120;
        } else if (Utils.roundNum <= 200 && gardenersHired < 4) {
            // Up to 4 gardeners by round 200
            shouldHire = Utils.teamBullets > 180;
        } else if (Utils.roundNum > 200) {
            // Late game: hire based on settled gardeners
            int settledGardeners = Comms.getSettledGardenerCount();
            if (settledGardeners >= gardenersHired && Utils.teamBullets > 250) {
                shouldHire = true;
            }
        }

        if (shouldHire) {
            // Try all directions to find a valid hire direction
            for (Direction dir : Utils.HEX_DIRS) {
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    gardenersHired++;
                    Comms.incrementGardenerCount();
                    return;
                }
            }
            // Also try random directions
            for (int i = 0; i < 5; i++) {
                Direction dir = Utils.randomDirection();
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    gardenersHired++;
                    Comms.incrementGardenerCount();
                    return;
                }
            }
        }
    }

    /**
     * Flee from nearby enemies.
     */
    private static void fleeFromEnemies() throws GameActionException {
        if (rc.hasMoved()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);
        if (enemies.length == 0) return;

        // Report enemy sightings
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                Comms.reportEnemyArchon(enemy.location);
            } else if (enemy.type == RobotType.GARDENER) {
                Comms.reportEnemyGardener(enemy.location);
            }
        }

        // Find center of enemy mass
        float totalX = 0, totalY = 0;
        for (RobotInfo enemy : enemies) {
            totalX += enemy.location.x;
            totalY += enemy.location.y;
        }
        MapLocation enemyCenter = new MapLocation(totalX / enemies.length, totalY / enemies.length);

        // Flee away from enemy center
        Nav.moveAway(enemyCenter);
    }

    /**
     * Move toward a safe position (center of allies, away from frontline).
     */
    private static void moveToSafePosition() throws GameActionException {
        if (rc.hasMoved()) return;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, Utils.myTeam);

        if (allies.length > 0) {
            // Move toward center of allies
            float totalX = 0, totalY = 0;
            int count = 0;
            for (RobotInfo ally : allies) {
                if (ally.type == RobotType.GARDENER || ally.type == RobotType.SOLDIER) {
                    totalX += ally.location.x;
                    totalY += ally.location.y;
                    count++;
                }
            }

            if (count > 0) {
                MapLocation allyCenter = new MapLocation(totalX / count, totalY / count);
                if (Utils.myLoc.distanceTo(allyCenter) > 3) {
                    Nav.moveTo(allyCenter);
                    return;
                }
            }
        }

        // If no specific target, move randomly but avoid enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);
        if (enemies.length == 0) {
            Nav.tryMove(Utils.randomDirection());
        }
    }
}
