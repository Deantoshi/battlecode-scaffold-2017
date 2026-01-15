package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;

    // Quadrant patrols variables
    static int currentQuadrant = 0;
    static MapLocation[] quadrantCenters = {new MapLocation(20,20), new MapLocation(30,80), new MapLocation(80,80), new MapLocation(80,20), new MapLocation(50,50)};  // Added center
    static int maxQuadrants = 5;

    // Directed sweeps variables
    static MapLocation archonLoc = null;

    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
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
        tryShakeTree();

        // Broadcast map intel
        broadcastMapIntel();

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        // Retreat if low health or outnumbered
        MapLocation allyArchon = Comms.readLocation(0, 1);
        if (rc.getHealth() < 40 || (enemies.length > 2 && countLumberjacksSoldiers(enemies) > 2)) {
            if (allyArchon != null && !rc.hasMoved()) {
                Direction retreatDir = rc.getLocation().directionTo(allyArchon);
                Nav.tryMove(retreatDir);
                return;
            }
        }
        for (RobotInfo enemy : enemies) {
            reportEnemy(enemy);
            if ((enemy.type == RobotType.GARDENER || enemy.type == RobotType.ARCHON) && !rc.hasMoved()) {
                Nav.moveToward(enemy.location);
                return;
            } else if (enemy.type != RobotType.ARCHON && enemy.type != RobotType.GARDENER && rc.getHealth() > 20 && !rc.hasMoved()) {
                // Harass other units if safe
                Direction harassDir = rc.getLocation().directionTo(enemy.location);
                Nav.tryMove(harassDir);
            }
        }

        // Prioritize shooting gardeners/archons
        for (RobotInfo enemy : enemies) {
            if ((enemy.type == RobotType.GARDENER || enemy.type == RobotType.ARCHON) && rc.canFireSingleShot()) {
                Direction dir = rc.getLocation().directionTo(enemy.location);
                rc.fireSingleShot(dir);
                break;  // Prioritize these
            }
        }
        // Then shoot others if no priority targets
        for (RobotInfo enemy : enemies) {
            if (rc.canFireSingleShot()) {
                Direction dir = rc.getLocation().directionTo(enemy.location);
                rc.fireSingleShot(dir);
                break;
            }
        }

        // Clustering check
        if (!rc.hasMoved()) {
            RobotInfo[] allies = rc.senseNearbyRobots(5.0f, rc.getTeam());
            if (allies.length > 5) {
                MapLocation allyCentroid = Utils.calculateCentroid(allies);
                Direction away = rc.getLocation().directionTo(allyCentroid).opposite();
                Nav.tryMove(away);
                return;
            }
        }

        if (!rc.hasMoved()) {
            if (enemies.length == 0) {
                // Replace quadrant patrol with dynamic sweeps
                MapLocation archonLoc = Comms.readLocation(0, 1); // Friendly archon
                MapLocation enemyLoc = Comms.getEnemyArchonLocation();
                if (archonLoc != null && enemyLoc != null) {
                    float baseDist = archonLoc.distanceTo(enemyLoc);
                    float currentDist = archonLoc.distanceTo(rc.getLocation());
                    int spiralTurns = (int)(currentDist / 5.0f); // More systematic
                    Direction toEnemy = archonLoc.directionTo(enemyLoc);
                    Direction spiralDir = toEnemy.rotateLeftDegrees(spiralTurns * 45.0f); // Consistent 45-degree steps
                    Nav.tryMove(spiralDir);
                } else {
                    Nav.tryMove(Nav.randomDirection()); // Fallback
                }
            } else {
                // Expanded harassment: shoot more units
                for (RobotInfo enemy : enemies) {
                    if (rc.canFireSingleShot()) {
                        Direction dir = rc.getLocation().directionTo(enemy.location);
                        rc.fireSingleShot(dir);
                        break;
                    }
                }
            }
        }
    }

    static boolean tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(3.0f, Team.NEUTRAL);  // Increased from 2.0f to 3.0f
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return true;
            }
        }
        return false;
    }

    static void reportEnemy(RobotInfo enemy) throws GameActionException {
        if (enemy.type == RobotType.ARCHON) {
            Comms.broadcastLocation(2, 3, enemy.location);
            rc.broadcast(4, 1);
        } else if (enemy.type == RobotType.GARDENER) {
            Comms.broadcastLocation(9, 10, enemy.location); // New channels for gardeners
        } else if (enemy.type == RobotType.LUMBERJACK) {
            Comms.broadcastLumberjackDetected();
        } else {
            Comms.broadcastLocation(15, 16, enemy.location); // General enemies
        }
    }

    static void broadcastMapIntel() throws GameActionException {
        // Broadcast tree clusters (simplified: broadcast location of dense tree areas)
        TreeInfo[] trees = rc.senseNearbyTrees(5.0f, Team.NEUTRAL);
        if (trees.length > 5) {
            MapLocation treeCenter = rc.getLocation(); // Approximate center
            Comms.broadcastLocation(5, 6, treeCenter);
        }

        // Broadcast map edges if near boundary (enhanced: check multiple points)
        MapLocation loc = rc.getLocation();
        if (loc.x < 10 || loc.x > 90 || loc.y < 10 || loc.y > 90) {
            Comms.broadcastLocation(7, 8, loc);
        }
    }

    static int countLumberjacksSoldiers(RobotInfo[] enemies) {
        int count = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.LUMBERJACK || enemy.type == RobotType.SOLDIER) {
                count++;
            }
        }
        return count;
    }
}