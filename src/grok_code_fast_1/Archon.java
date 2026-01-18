package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int turnCounter = 0;
    static int hireCounter = 0;
    static int gardenersHired = 0;

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
 // DYNAMIC STRATEGY - adapt based on game progress
        int priority;
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f);
        
        if (turnCounter < 500) {
            priority = 2;  // Military focus early
        } else if (nearbyTrees.length > 12) {
            priority = 0;  // Clear trees if blocked
        } else {
            priority = 1;  // Balanced mid-late game
        }
        Comms.broadcastProductionPriority(priority);
        
        // VP-OPTIMIZED STRATEGY - maximize bullet generation through trees
        int maxGardeners = 5;  // More gardeners for maximum tree planting
        
        // Count actual gardeners from nearby robots
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        int actualGardenerCount = 0;
        for (RobotInfo robot : nearbyRobots) {
            if (robot.type == RobotType.GARDENER) {
                actualGardenerCount++;
            }
        }
        Comms.broadcastUnitCount(actualGardenerCount * 6); // Estimate total units
        
         // Hire minimal gardeners for VP strategy
         if (actualGardenerCount < maxGardeners && rc.getTeamBullets() > 100) {
            if (tryHireGardener()) {
                gardenersHired++;
            }
        }
        Comms.broadcastVictoryPoints((int)rc.getTeamVictoryPoints()); // New broadcast for VP tracking
        Comms.broadcastTreePlantingThreshold(10);  // New broadcast for planting threshold
        if (enemies.length > 0 && !rc.hasMoved()) {
            MapLocation centroid = Utils.calculateCentroid(enemies);
            Direction away = rc.getLocation().directionTo(centroid).opposite();
            Nav.tryMove(away);
        }




 // ULTRA-AGGRESSIVE VP STRATEGY - maximize VP generation for sub-1500 rounds
        float bullets = rc.getTeamBullets();
        
        // Always donate everything except minimum needed for gardener production
        if (bullets > 35) {  // Just enough to hire one gardener
            rc.donate(bullets - 35);
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