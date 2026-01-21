package training_bot;

import battlecode.common.*;

/**
 * Tank - Heavy siege unit.
 * Key responsibilities:
 * - High damage ranged attacks
 * - Clear forests by trampling through trees
 * - Push into enemy territory
 * - Destroy enemy structures and units
 */
public strictfp class Tank {
    private static RobotController rc;
    private static MapLocation targetLocation = null;

    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
        Navigation.init(rc);

        // Priority 1: Engage enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length > 0) {
            engageCombat(enemies);
        } else {
            // No enemies - advance and clear path
            advanceAndClear();
        }
    }

    /**
     * Engages enemies with tank firepower.
     */
    private static void engageCombat(RobotInfo[] enemies) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Find priority target
        RobotInfo target = selectTarget(enemies);

        if (target == null) {
            return;
        }

        MapLocation targetLoc = target.location;
        Direction toTarget = myLoc.directionTo(targetLoc);
        float distance = myLoc.distanceTo(targetLoc);

        // Report Archon
        if (target.type == RobotType.ARCHON) {
            Comms.reportEnemyArchon(targetLoc);
        }

        // Tanks have powerful shots - use them
        if (rc.canFirePentadShot() && enemies.length >= 2) {
            rc.firePentadShot(toTarget);
        } else if (rc.canFireTriadShot()) {
            rc.fireTriadShot(toTarget);
        } else if (rc.canFireSingleShot()) {
            rc.fireSingleShot(toTarget);
        }

        // Movement - tanks are slow but powerful, advance steadily
        if (!rc.hasMoved()) {
            if (distance > 5f) {
                // Close distance, trampling trees
                advanceToward(targetLoc);
            } else if (distance < 3f && target.type == RobotType.LUMBERJACK) {
                // Back off from melee threats
                Navigation.moveAwayFrom(targetLoc);
            }
            // Else stay at good range
        }
    }

    /**
     * Selects the best target - prioritize high value.
     */
    private static RobotInfo selectTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            float score = enemy.health;

            // High priority targets
            if (enemy.type == RobotType.ARCHON) {
                score -= 2000;
            } else if (enemy.type == RobotType.GARDENER) {
                score -= 1000;
            } else if (enemy.type == RobotType.TANK) {
                score -= 500; // Counter tanks
            }

            score += rc.getLocation().distanceTo(enemy.location) * 5;

            if (score < bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        return bestTarget;
    }

    /**
     * Advances toward enemy, clearing trees by trampling.
     * Tanks can move through trees, destroying them on contact.
     */
    private static void advanceAndClear() throws GameActionException {
        // Get target location
        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            targetLocation = enemyArchon;
        }

        if (targetLocation == null) {
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            if (enemyArchons.length > 0) {
                targetLocation = enemyArchons[0];
            }
        }

        if (targetLocation != null) {
            advanceToward(targetLocation);
        } else {
            Navigation.wander();
        }
    }

    /**
     * Advances toward a location, intentionally clearing trees.
     * Uses tank's unique ability to trample through trees.
     */
    private static void advanceToward(MapLocation target) throws GameActionException {
        if (rc.hasMoved()) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        Direction toTarget = myLoc.directionTo(target);

        // First try direct movement
        if (rc.canMove(toTarget)) {
            rc.move(toTarget);
            return;
        }

        // Check for trees in the way - tanks can trample them
        TreeInfo[] treesInWay = rc.senseNearbyTrees(2f);
        for (TreeInfo tree : treesInWay) {
            if (tree.team != rc.getTeam()) {
                Direction toTree = myLoc.directionTo(tree.location);
                // Move into tree to destroy it
                if (rc.canMove(toTree)) {
                    rc.move(toTree);
                    return;
                }
            }
        }

        // Standard pathfinding if no trees to trample
        Navigation.tryMove(toTarget);
    }
}
