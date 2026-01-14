package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;

    // Spiral exploration variables
    static int spiralStep = 0;
    static Direction spiralDir = Direction.NORTH;
    static int stepsInDir = 1;
    static int stepsTaken = 0;

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

        // Add shooting
        for (RobotInfo enemy : enemies) {
            if (enemy.type != RobotType.ARCHON && enemy.type != RobotType.TANK && rc.canFireSingleShot()) {
                Direction dir = rc.getLocation().directionTo(enemy.location);
                rc.fireSingleShot(dir);
                break; // One shot per turn
            }
        }

        if (!rc.hasMoved()) {
            // Spiral exploration
            if (stepsTaken >= stepsInDir) {
                spiralDir = spiralDir.rotateRightDegrees(90);
                stepsTaken = 0;
                if (spiralStep % 2 == 1) {
                    stepsInDir++;
                }
                spiralStep++;
            }
            if (Nav.tryMove(spiralDir)) {
                stepsTaken++;
            } else {
                // Blocked, try different direction
                Nav.tryMove(spiralDir.rotateLeftDegrees(45));
            }
        }
    }

    static boolean tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
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

        // Broadcast map edges if near boundary (simplified: if location is extreme)
        // Note: Map dimensions not directly accessible, using approximation
        if (rc.getLocation().x < 5 || rc.getLocation().x > 95 ||
            rc.getLocation().y < 5 || rc.getLocation().y > 95) {
            Comms.broadcastLocation(7, 8, rc.getLocation());
        }
    }
}
