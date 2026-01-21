package training_bot;

import battlecode.common.*;

/**
 * Lumberjack - Melee combat and tree clearing unit.
 * Key responsibilities:
 * - Clear trees to open pathways
 * - Collect bullets from neutral trees (shake)
 * - AoE damage with strike in combat
 * - Destroy enemy Bullet Trees
 */
public strictfp class Lumberjack {
    private static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
        Navigation.init(rc);

        MapLocation myLoc = rc.getLocation();

        // Priority 1: Check for enemies to strike
        RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
        RobotInfo[] friendlies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

        // Strike if enemies in range and no friendly fire
        if (enemies.length > 0 && friendlies.length == 0) {
            if (rc.canStrike()) {
                rc.strike();
            }
        }

        // Priority 2: Shake neutral trees for bullets
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(2f, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                break;
            }
        }

        // Priority 3: Chop trees
        boolean chopped = tryChopTree();

        // Priority 4: Move toward enemies or trees
        if (!rc.hasMoved()) {
            if (enemies.length > 0) {
                // Chase enemies
                RobotInfo nearest = findNearest(enemies);
                Navigation.moveToward(nearest.location);
            } else {
                // Find trees to clear
                moveTowardTrees();
            }
        }
    }

    /**
     * Attempts to chop a nearby tree.
     * Priority: Enemy trees > Neutral trees blocking path > Any neutral tree
     */
    private static boolean tryChopTree() throws GameActionException {
        // First, try enemy trees (denies economy)
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(2f, rc.getTeam().opponent());
        for (TreeInfo tree : enemyTrees) {
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return true;
            }
        }

        // Then neutral trees
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(2f, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
            // Shake first to collect bullets
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
            }
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return true;
            }
        }

        return false;
    }

    /**
     * Moves toward trees that need clearing.
     */
    private static void moveTowardTrees() throws GameActionException {
        // Look for trees in sensor range
        TreeInfo[] allTrees = rc.senseNearbyTrees();

        // Prioritize: Enemy trees > Neutral with bullets > Any neutral
        TreeInfo targetTree = null;
        float bestScore = Float.MAX_VALUE;

        for (TreeInfo tree : allTrees) {
            if (tree.team == rc.getTeam()) {
                continue; // Don't target our own trees
            }

            float score = rc.getLocation().distanceTo(tree.location);

            // Prioritize enemy trees
            if (tree.team == rc.getTeam().opponent()) {
                score -= 100;
            }

            // Prioritize trees with bullets
            if (tree.containedBullets > 0) {
                score -= tree.containedBullets;
            }

            if (score < bestScore) {
                bestScore = score;
                targetTree = tree;
            }
        }

        if (targetTree != null) {
            Navigation.moveToward(targetTree.location);
        } else {
            // Move toward enemy base to find trees/enemies
            Direction toEnemy = Navigation.towardEnemyArchons();
            Navigation.tryMove(toEnemy);
        }
    }

    /**
     * Finds the nearest robot in an array.
     */
    private static RobotInfo findNearest(RobotInfo[] robots) {
        RobotInfo nearest = null;
        float minDist = Float.MAX_VALUE;

        for (RobotInfo robot : robots) {
            float dist = rc.getLocation().distanceTo(robot.location);
            if (dist < minDist) {
                minDist = dist;
                nearest = robot;
            }
        }

        return nearest;
    }
}
