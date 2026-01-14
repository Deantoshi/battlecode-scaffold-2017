package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int turnCounter = 0;
    static int hireCounter = 0;
    static int gardenersHired = 0;
    static int gardenerCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
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
        turnCounter++;
        Comms.broadcastLocation(0, 1, rc.getLocation());

        // Check for nearby enemies - if any, move away from closest, considering multiple threats
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Comms.broadcastTreeDensity(rc.senseNearbyTrees(10.0f).length); // New method in Comms
        if (enemies.length > 0) {
            Comms.broadcastEnemyThreats(enemies.length); // New method in Comms
        }
        // Dynamic production broadcasts
        int priority;
        if (turnCounter < 400) {  // Extended from 300 to 400 for more soldiers early
            priority = 1;  // Soldiers
        } else if (turnCounter < 600) {
            priority = 2;  // Scouts for exploration
        } else if (turnCounter > 1000) {
            priority = 3;  // Tanks for late-game sieges
        } else {
            TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f);
            RobotInfo[] enemiesForPriority = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (nearbyTrees.length > 5 || enemiesForPriority.length > 2) {
                priority = 0;  // Lumberjacks for clearing/harassment
            } else {
                priority = 2;  // Scouts
            }
        }
        Comms.broadcastProductionPriority(priority);
        Comms.broadcastUnitCount(gardenerCount * 6); // Estimate total units
        Comms.broadcastTreePlantingThreshold(10);  // New broadcast for planting threshold
        if (enemies.length > 0 && !rc.hasMoved()) {
            MapLocation centroid = Utils.calculateCentroid(enemies);
            Direction away = rc.getLocation().directionTo(centroid).opposite();
            Nav.tryMove(away);
        }

        // Hire up to 10 gardeners early if bullets sufficient, prefer safe directions
        int maxGardeners = 8;  // Reduced from 10 to 8
        // Check density
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f);
        if (nearbyTrees.length > 5) {
            // Skip hiring or reposition
            if (!rc.hasMoved()) {
                // Move to less dense direction
                Direction denseDir = rc.getLocation().directionTo(nearbyTrees[0].location);
                Direction awayDense = denseDir.opposite();
                Nav.tryMove(awayDense);
            }
            return; // Skip hiring in dense areas
        } else if (nearbyTrees.length >= 3 && nearbyTrees.length <= 5) {
            // Moderately dense: hire lumberjacks for clearing
            maxGardeners = 6;  // Further reduced to 6 in moderately dense areas
            if (rc.getTeamBullets() >= 50 && gardenersHired < maxGardeners) {
                // Prioritize lumberjack in dense areas via tryHireGardener adjustment
            }
        }
        if (rc.getTeamBullets() >= 50 && gardenersHired < maxGardeners) {
            if (tryHireGardener()) {
                gardenersHired++;
                gardenerCount++;
            }
        }

        // Accelerate VP Donations
        int unitCount = Comms.getUnitCount();
        if ((unitCount >= 20 || turnCounter >= 600) && rc.getTeamBullets() > 50) {
            int donateAmount = (int)(rc.getTeamBullets() - 50);
            rc.donate(donateAmount);
        }

        if (!rc.hasMoved()) {
            // Radial toward enemy archons
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Comms.broadcastRallyPoint(enemyArchon);
                Direction toEnemy = rc.getLocation().directionTo(enemyArchon);
                Nav.tryMove(toEnemy);
            } else {
                // Fallback to center
                MapLocation center = new MapLocation(50.0f, 50.0f);
                Nav.tryMove(rc.getLocation().directionTo(center));
            }
        }
    }

    static boolean tryHireGardener() throws GameActionException {
        Direction[] dirs = {new Direction(0), new Direction((float)Math.PI/4), new Direction((float)Math.PI/2), new Direction(3*(float)Math.PI/4), new Direction((float)Math.PI), new Direction(5*(float)Math.PI/4), new Direction(3*(float)Math.PI/2), new Direction(7*(float)Math.PI/4)};
        // Prioritize directions away from enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(10.0f, rc.getTeam().opponent());
        MapLocation enemyCentroid = enemies.length > 0 ? Utils.calculateCentroid(enemies) : null;
        for (Direction dir : dirs) {
            MapLocation hireLoc = rc.getLocation().add(dir, 2.0f);
            if (enemyCentroid != null && hireLoc.distanceTo(enemyCentroid) < 5.0f) continue; // Avoid near enemies
            TreeInfo[] trees = rc.senseNearbyTrees(hireLoc, 2.0f, Team.NEUTRAL);
            if (trees.length > 0 && rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        return false;
    }
}