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
        RobotInfo[] ownRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        Comms.broadcastTreeDensity(rc.senseNearbyTrees(10.0f).length); // New method in Comms
        if (enemies.length > 0) {
            Comms.broadcastEnemyThreats(enemies.length); // New method in Comms
        }
 // Extreme early military rush for fast victory
        int priority;
        TreeInfo[] nearbyTreesForPriority = rc.senseNearbyTrees(10.0f);
        if (turnCounter < 50 && !Comms.isEnemySpotted()) {
            priority = 2;  // Scouts first for immediate intel
        } else if (turnCounter < 300) {
            priority = 1;  // Soldiers for early aggression 
        } else if (nearbyTreesForPriority.length > 12) {
            priority = 0;  // Lumberjacks only if extremely dense
        } else if (Comms.getEnemyThreats() > 0) {
            priority = 1;  // Soldiers if enemies detected
        } else {
            priority = 1;  // Default to soldiers for aggression
        }
        Comms.broadcastProductionPriority(priority);
        Comms.broadcastUnitCount(gardenerCount * 6); // Estimate total units
        Comms.broadcastTreePlantingThreshold(10);  // New broadcast for planting threshold
        if (enemies.length > 0 && !rc.hasMoved()) {
            MapLocation centroid = Utils.calculateCentroid(enemies);
            Direction away = rc.getLocation().directionTo(centroid).opposite();
            Nav.tryMove(away);
        }

        // VP-focused: minimal gardeners, just enough for bullet generation
        int maxGardeners = 2;  // Minimized to save bullets for VP donation
        // Check density but be more aggressive about hiring
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f);
        if (nearbyTrees.length > 8) {
            // Very dense: prioritize moving to clear area but still hire
            if (!rc.hasMoved() && gardenersHired < 2) {
                // Move to less dense direction after initial gardeners
                Direction denseDir = rc.getLocation().directionTo(nearbyTrees[0].location);
                Direction awayDense = denseDir.opposite();
                Nav.tryMove(awayDense);
            }
            maxGardeners = 5;  // Even more gardeners in very dense areas
        } else if (nearbyTrees.length >= 3 && nearbyTrees.length <= 5) {
            // Moderately dense: standard hiring
            maxGardeners = 4;
        }
        if (rc.getTeamBullets() >= 50 && gardenersHired < maxGardeners) {
            if (tryHireGardener()) {
                gardenersHired++;
                gardenerCount++;
            }
        }

 // Ultra-extreme VP rush - donate as soon as possible for fastest win
        float vpCost = rc.getVictoryPointCost();
        if (turnCounter > 50 && rc.getTeamBullets() > vpCost * 1.05) {
            while (rc.getTeamBullets() >= vpCost && turnCounter < 1200) {
                rc.donate(vpCost);
            }
        }

        if (!rc.hasMoved()) {
            // Radial toward enemy archons
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Comms.broadcastRallyPoint(enemyArchon);
                if (enemies.length == 0 && ownRobots.length >= 5) {
                    Direction toEnemy = rc.getLocation().directionTo(enemyArchon);
                    Nav.tryMove(toEnemy);
                }
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