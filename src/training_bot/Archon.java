package training_bot;

import battlecode.common.*;

/**
 * Archon - Mobile headquarters that hires Gardeners.
 * Key responsibilities:
 * - Hire Gardeners for economy and unit production
 * - Stay safe and mobile
 * - Donate bullets for victory points when ahead
 */
public strictfp class Archon {
    private static RobotController rc;
    private static int gardenersHired = 0;
    private static Direction spawnDirection = Direction.NORTH;

    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
        Navigation.init(rc);

        // Try to hire a Gardener
        tryHireGardener();

        // Move away from enemies if threatened
        evadeEnemies();

        // Donate bullets if we have excess and are winning
        tryDonate();
    }

    /**
     * Attempts to hire a Gardener in an available direction.
     */
    private static void tryHireGardener() throws GameActionException {
        // Limit gardeners based on round number for economy balance
        int maxGardeners = 1 + (rc.getRoundNum() / 200);
        maxGardeners = Math.min(maxGardeners, 5);

        if (gardenersHired >= maxGardeners) {
            return;
        }

        // Try to hire in different directions
        for (int i = 0; i < 8; i++) {
            Direction dir = spawnDirection.rotateLeftDegrees(i * 45);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                gardenersHired++;
                // Rotate spawn direction for next time
                spawnDirection = spawnDirection.rotateRightDegrees(90);
                Comms.incrementCounter(Comms.CHANNEL_GARDENER_COUNT);
                return;
            }
        }
    }

    /**
     * Move away from nearby enemies to stay safe.
     */
    private static void evadeEnemies() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length > 0) {
            // Find average enemy position
            float avgX = 0, avgY = 0;
            for (RobotInfo enemy : enemies) {
                avgX += enemy.location.x;
                avgY += enemy.location.y;

                // Report enemy Archon if spotted
                if (enemy.type == RobotType.ARCHON) {
                    Comms.reportEnemyArchon(enemy.location);
                }
            }
            avgX /= enemies.length;
            avgY /= enemies.length;

            MapLocation enemyCenter = new MapLocation(avgX, avgY);
            Navigation.moveAwayFrom(enemyCenter);
        } else {
            // Wander slowly to avoid getting cornered
            if (Math.random() < 0.1) {
                Navigation.wander();
            }
        }
    }

    /**
     * Donate bullets for victory points when we have excess.
     */
    private static void tryDonate() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();

        // Donate if we have lots of bullets or can win
        int vpNeeded = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();

        // Win condition check
        if (bullets >= vpNeeded * vpCost) {
            rc.donate(vpNeeded * vpCost);
            return;
        }

        // Donate excess bullets late game
        if (rc.getRoundNum() > 2500 && bullets > 500) {
            rc.donate(bullets - 200);
        } else if (bullets > 1000) {
            // Donate some excess
            rc.donate(100);
        }
    }
}
