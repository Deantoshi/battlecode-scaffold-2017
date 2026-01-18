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
 // PURE ELIMINATION FOCUS - military all the way
        int priority;
        TreeInfo[] nearbyTreesForPriority = rc.senseNearbyTrees(10.0f);
        
        if (turnCounter < 15 && !Comms.isEnemySpotted()) {
            priority = 2;  // Quick scout for intel, then all military
        } else if (nearbyTreesForPriority.length > 15 && turnCounter < 300) {
            priority = 0;  // Early lumberjacks to clear dense areas
        } else {
            priority = 1;  // Soldiers for elimination
        }
        Comms.broadcastProductionPriority(priority);
        Comms.broadcastUnitCount(gardenerCount * 6); // Estimate total units
        Comms.broadcastVictoryPoints((int)rc.getTeamVictoryPoints()); // New broadcast for VP tracking
        Comms.broadcastTreePlantingThreshold(10);  // New broadcast for planting threshold
        if (enemies.length > 0 && !rc.hasMoved()) {
            MapLocation centroid = Utils.calculateCentroid(enemies);
            Direction away = rc.getLocation().directionTo(centroid).opposite();
            Nav.tryMove(away);
        }

 // PURE ARCHON VP - absolutely NO gardeners, maximum bullets for VP
        int maxGardeners = 0;  // Zero gardeners for maximum VP accumulation
 // NO GARDENERS - all bullets saved for VP
        // No hiring logic

 // MAXIMUM VP RUSH - donate EVERYTHING possible from the start
        float vpCost = rc.getVictoryPointCost();
        float currentVP = rc.getTeamVictoryPoints();
        
        // Start donating from round 1 - no waiting
        if (turnCounter > 1 && currentVP < 1000) {
            // Donate ALL bullets except tiny emergency reserve
            int bulletsToKeep = 10;  // Almost no reserve
            int availableForVP = (int)(rc.getTeamBullets() - bulletsToKeep);
            
            if (availableForVP >= vpCost) {
                int donationsToMake = availableForVP / (int)vpCost;
                
                // Donate EVERYTHING possible
                for (int i = 0; i < donationsToMake; i++) {
                    if (rc.getTeamBullets() >= vpCost + bulletsToKeep) {
                        rc.donate(vpCost);
                    } else {
                        break;
                    }
                }
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