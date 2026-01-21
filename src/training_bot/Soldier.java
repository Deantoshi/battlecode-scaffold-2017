package training_bot;

import battlecode.common.*;

/**
 * Soldier - Main ranged combat unit.
 * Key responsibilities:
 * - Engage and destroy enemy units
 * - Dodge incoming bullets
 * - Focus fire on low-health targets
 * - Support team pushes
 */
public strictfp class Soldier {
    private static RobotController rc;
    private static MapLocation targetLocation = null;

    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Navigation.init(rc);

        // Priority 1: Dodge bullets
        boolean dodged = Navigation.tryDodgeBullets();

        // Priority 2: Engage enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length > 0) {
            engageCombat(enemies, dodged);
        } else {
            // No enemies - move toward objective
            moveToObjective();
        }
    }

    /**
     * Engages enemies with shooting and micro.
     */
    private static void engageCombat(RobotInfo[] enemies, boolean alreadyMoved) throws GameActionException {
        // Find priority target (lowest HP or Archon)
        RobotInfo target = selectTarget(enemies);

        if (target == null) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        MapLocation targetLoc = target.location;
        Direction toTarget = myLoc.directionTo(targetLoc);
        float distance = myLoc.distanceTo(targetLoc);

        // Report enemy Archon
        if (target.type == RobotType.ARCHON) {
            Comms.reportEnemyArchon(targetLoc);
        }

        // Shooting logic
        if (rc.canFirePentadShot() && enemies.length >= 3 && distance < 4f) {
            // Use pentad for groups
            rc.firePentadShot(toTarget);
        } else if (rc.canFireTriadShot() && distance < 5f) {
            // Triad for medium range
            if (isClearShot(toTarget, distance)) {
                rc.fireTriadShot(toTarget);
            }
        } else if (rc.canFireSingleShot()) {
            // Single shot - more accurate
            if (isClearShot(toTarget, distance)) {
                rc.fireSingleShot(toTarget);
            }
        }

        // Movement micro (if we haven't dodged)
        if (!alreadyMoved) {
            microMovement(target, distance);
        }
    }

    /**
     * Selects the best target to attack.
     * Priority: Lowest HP, then Archons, then nearest.
     */
    private static RobotInfo selectTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            float score = enemy.health;

            // Prioritize Archons (high value target)
            if (enemy.type == RobotType.ARCHON) {
                score -= 1000;
            }

            // Prioritize Gardeners (economy)
            if (enemy.type == RobotType.GARDENER) {
                score -= 500;
            }

            // Prefer closer targets
            score += rc.getLocation().distanceTo(enemy.location) * 10;

            if (score < bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        return bestTarget;
    }

    /**
     * Checks if there's a clear shot to the target (no friendly fire).
     */
    private static boolean isClearShot(Direction dir, float distance) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Check for friendly units in the line of fire
        RobotInfo[] friendlies = rc.senseNearbyRobots(distance, rc.getTeam());
        for (RobotInfo friendly : friendlies) {
            // Check if friendly is in the firing cone
            Direction toFriendly = myLoc.directionTo(friendly.location);
            float angleDiff = Math.abs(dir.degreesBetween(toFriendly));
            if (angleDiff < 30 && myLoc.distanceTo(friendly.location) < distance) {
                return false;
            }
        }

        // Check for friendly trees
        TreeInfo[] friendlyTrees = rc.senseNearbyTrees(distance, rc.getTeam());
        for (TreeInfo tree : friendlyTrees) {
            Direction toTree = myLoc.directionTo(tree.location);
            float angleDiff = Math.abs(dir.degreesBetween(toTree));
            if (angleDiff < 15) {
                return false;
            }
        }

        return true;
    }

    /**
     * Micro movement - kite enemies and maintain optimal range.
     */
    private static void microMovement(RobotInfo target, float distance) throws GameActionException {
        float optimalRange = 4.0f;

        if (target.type == RobotType.LUMBERJACK || target.type == RobotType.SOLDIER) {
            optimalRange = 5.0f; // Stay further from threats
        }

        if (distance < optimalRange - 1) {
            // Too close - back up
            Navigation.moveAwayFrom(target.location);
        } else if (distance > optimalRange + 1) {
            // Too far - close in
            Navigation.moveToward(target.location);
        } else {
            // Good range - strafe
            Direction toTarget = rc.getLocation().directionTo(target.location);
            Direction strafe = toTarget.rotateLeftDegrees(90);
            if (Math.random() < 0.5) {
                strafe = strafe.opposite();
            }
            Navigation.tryMove(strafe);
        }
    }

    /**
     * Move toward objective when no enemies visible.
     */
    private static void moveToObjective() throws GameActionException {
        // Check for broadcasted enemy location
        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            targetLocation = enemyArchon;
        }

        // If no target, head toward enemy spawn
        if (targetLocation == null) {
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            if (enemyArchons.length > 0) {
                targetLocation = enemyArchons[0];
            }
        }

        if (targetLocation != null) {
            Navigation.moveToward(targetLocation);
        } else {
            Navigation.wander();
        }
    }
}
