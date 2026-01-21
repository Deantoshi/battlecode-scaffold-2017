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
    private static boolean earlyDonationDone = false; // Track early VP showcase

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
     * Attempts to hire exactly 1 Gardener at the start.
     * This bot is a showcase - one gardener will build all unit types.
     */
    private static void tryHireGardener() throws GameActionException {
        // SHOWCASE MODE: Only hire 1 gardener to demonstrate all mechanics
        if (gardenersHired >= 1) {
            return;
        }

        // Try to hire in different directions
        for (int i = 0; i < 8; i++) {
            Direction dir = spawnDirection.rotateLeftDegrees(i * 45);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                gardenersHired++;
                Comms.incrementCounter(Comms.CHANNEL_GARDENER_COUNT);
                System.out.println("ARCHON: Hired showcase Gardener to build all unit types");
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
     * Donate bullets for victory points.
     * MECHANIC: rc.donate(bullets) converts bullets to VP at current exchange rate.
     * Cost starts at 7.5 bullets/VP and increases over time.
     *
     * SHOWCASE: Donate a few VP early to demonstrate the mechanic!
     */
    private static void tryDonate() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        int round = rc.getRoundNum();
        int currentVP = rc.getTeamVictoryPoints();

        // Win condition check - go for the win!
        int vpNeeded = GameConstants.VICTORY_POINTS_TO_WIN - currentVP;
        if (bullets >= vpNeeded * vpCost) {
            rc.donate(vpNeeded * vpCost);
            System.out.println("ARCHON R" + round + ": Donating for VICTORY!");
            return;
        }

        // SHOWCASE: Early VP donation to demonstrate the mechanic
        // Donate a few points early when we have excess bullets
        if (!earlyDonationDone && round > 50 && bullets > 200) {
            // Buy 10 VP early to showcase the donation mechanic
            float donationAmount = 10 * vpCost;
            if (bullets >= donationAmount + 100) { // Keep 100 for unit building
                rc.donate(donationAmount);
                earlyDonationDone = true;
                System.out.println("ARCHON R" + round + ": SHOWCASE - Donated for 10 VP! " +
                    "(cost=" + (int)donationAmount + " bullets, rate=" + vpCost + "/VP)");
                return;
            }
        }

        // Periodic small donations when we have excess (after showcase units built)
        // This shows ongoing VP accumulation strategy
        if (round > 500 && bullets > 500 && round % 200 == 0) {
            float donationAmount = 5 * vpCost;
            rc.donate(donationAmount);
            System.out.println("ARCHON R" + round + ": Periodic donation for 5 VP (total VP: " +
                (currentVP + 5) + ")");
        }

        // Late game: more aggressive donations
        if (round > 2000 && bullets > 300) {
            float donationAmount = Math.min(bullets - 200, 20 * vpCost);
            if (donationAmount > vpCost) {
                rc.donate(donationAmount);
                int vpGained = (int)(donationAmount / vpCost);
                System.out.println("ARCHON R" + round + ": Late game donation for ~" + vpGained + " VP");
            }
        }
    }
}
