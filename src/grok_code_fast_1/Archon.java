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
        tryShakeTree();
        Comms.broadcastLocation(0, 1, rc.getLocation());

        // Check for nearby enemies - if any, move away from closest, considering multiple threats
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] ownRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        Comms.broadcastTreeDensity(rc.senseNearbyTrees(10.0f).length); // New method in Comms
        if (enemies.length > 0) {
            Comms.broadcastEnemyThreats(enemies.length); // New method in Comms
        }
    // MILITARY STRATEGY - focus on unit elimination with optimal gardeners
        int priority = 1;  // Military focus
        Comms.broadcastProductionPriority(priority);

           // MILITARY STRATEGY - hire optimal gardeners for soldier production
           int maxGardeners = 8;  // More gardeners for more units

        // Count actual gardeners from nearby robots
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        int actualGardenerCount = 0;
        for (RobotInfo robot : nearbyRobots) {
            if (robot.type == RobotType.GARDENER) {
                actualGardenerCount++;
            }
        }
        Comms.broadcastUnitCount(actualGardenerCount * 6); // Estimate total units

          // Hire gardeners for military strategy - hire as many as possible
          while (actualGardenerCount < maxGardeners && rc.getTeamBullets() >= 20) {
             if (tryHireGardener()) {
                 gardenersHired++;
                 actualGardenerCount++;  // Update count
             } else {
                 break;  // Can't hire more
             }
         }
        Comms.broadcastVictoryPoints((int)rc.getTeamVictoryPoints()); // New broadcast for VP tracking
        Comms.broadcastTreePlantingThreshold(10);  // New broadcast for planting threshold
        if (enemies.length > 0 && !rc.hasMoved()) {
            MapLocation centroid = Utils.calculateCentroid(enemies);
            Direction toward = rc.getLocation().directionTo(centroid);
            Nav.tryMove(toward);
        }




       // VP STRATEGY - donate bullets for victory points aggressively but keep reserve for production
        if (priority == 0 && rc.getTeamBullets() >= 50) {  // Keep reserve for building units
            int donateAmount = (int)(rc.getTeamBullets() - 50);
            if (donateAmount > 0) {
                rc.donate(donateAmount);
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
            if (rc.canHireGardener(dir)) {
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

    static boolean tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(3.0f, Team.NEUTRAL);
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return true;
            }
        }
        return false;
    }
}