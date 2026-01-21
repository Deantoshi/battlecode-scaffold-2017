package training_bot;

import battlecode.common.*;

/**
 * Lumberjack - Melee combat and tree clearing unit (SHOWCASE MODE).
 *
 * MECHANICS DEMONSTRATED:
 * - rc.chop(treeID) - Deals 5 damage to a tree
 * - rc.shake(treeID) - Collects bullets from trees
 * - rc.strike() - AoE damage to ALL units/trees within radius 2
 *
 * SHOWCASE BEHAVIOR: Actively moves toward enemy base, clearing trees
 * and engaging enemies along the way.
 */
public strictfp class Lumberjack {
    private static RobotController rc;
    private static MapLocation targetLocation = null;

    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
        Navigation.init(rc);

        MapLocation myLoc = rc.getLocation();

        // Set target to enemy Archon location
        if (targetLocation == null) {
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            if (enemyArchons.length > 0) {
                targetLocation = enemyArchons[0];
            }
        }

        // Check for nearby enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(
            RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS,
            rc.getTeam().opponent()
        );
        RobotInfo[] nearbyFriendlies = rc.senseNearbyRobots(
            RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS,
            rc.getTeam()
        );

        // PRIORITY 1: Strike if enemies in melee range and no friendly fire
        if (nearbyEnemies.length > 0 && nearbyFriendlies.length == 0) {
            if (rc.canStrike()) {
                rc.strike();
                System.out.println("LUMBERJACK: STRIKE! Hit " + nearbyEnemies.length + " enemies");
            }
        }

        // PRIORITY 2: Chase visible enemies
        if (enemies.length > 0) {
            RobotInfo nearest = findNearest(enemies);
            if (nearest != null) {
                System.out.println("LUMBERJACK: Chasing " + nearest.type);
                Navigation.moveToward(nearest.location);
                // Try to chop any tree in range while chasing
                tryChopTree();
                return;
            }
        }

        // PRIORITY 3: Shake trees for bullets while moving
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                System.out.println("LUMBERJACK: Shook tree for " + tree.containedBullets + " bullets");
                break;
            }
        }

        // PRIORITY 4: Move toward enemy base, chopping blocking trees
        moveTowardEnemy();

        // Chop any tree we're next to
        tryChopTree();
    }

    /**
     * Moves toward enemy base, the main objective.
     * Will path around or through trees.
     */
    private static void moveTowardEnemy() throws GameActionException {
        if (rc.hasMoved()) {
            return;
        }

        // Update target from broadcasts
        MapLocation broadcastedEnemy = Comms.getEnemyArchonLocation();
        if (broadcastedEnemy != null) {
            targetLocation = broadcastedEnemy;
        }

        if (targetLocation == null) {
            Navigation.wander();
            return;
        }

        Direction toTarget = rc.getLocation().directionTo(targetLocation);

        // Try to move toward target
        if (Navigation.tryMove(toTarget)) {
            return;
        }

        // If blocked, check if a tree is in the way - chop it!
        TreeInfo[] blockingTrees = rc.senseNearbyTrees(2f);
        for (TreeInfo tree : blockingTrees) {
            if (tree.team != rc.getTeam()) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                float angleDiff = Math.abs(toTarget.degreesBetween(toTree));
                // Tree is roughly in our path
                if (angleDiff < 45 && rc.canChop(tree.ID)) {
                    rc.chop(tree.ID);
                    System.out.println("LUMBERJACK: Chopping blocking tree");
                    return;
                }
            }
        }

        // Try any direction if stuck
        Navigation.wander();
    }

    /**
     * Attempts to chop a nearby tree.
     * Priority: Enemy trees > Neutral trees with bullets > Any neutral tree
     */
    private static boolean tryChopTree() throws GameActionException {
        // First, try enemy trees (denies economy)
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(2f, rc.getTeam().opponent());
        for (TreeInfo tree : enemyTrees) {
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                System.out.println("LUMBERJACK: Chopping ENEMY tree!");
                return true;
            }
        }

        // Then neutral trees (shake first for bullets)
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(2f, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
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
