package training_bot;

import battlecode.common.*;

/**
 * Scout - Fast recon and harassment unit.
 * Key responsibilities:
 * - Explore map and report enemy positions
 * - Shake trees for bullets (economic harass)
 * - Harass enemy Gardeners
 * - Kite enemies with superior speed
 */
public strictfp class Scout {
    private static RobotController rc;
    private static MapLocation targetLocation = null;
    private static boolean exploringEnemySide = false;

    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
        Navigation.init(rc);

        // Priority 1: Dodge bullets (scouts are fragile)
        Navigation.tryDodgeBullets();

        // Priority 2: Shake trees for bullets
        shakeTrees();

        // Priority 3: Report and harass enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            handleEnemies(enemies);
        } else {
            // Explore toward enemy
            explore();
        }
    }

    /**
     * Shakes nearby trees to collect bullets.
     */
    private static void shakeTrees() throws GameActionException {
        // Shake neutral trees
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        // Shake enemy trees too
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        for (TreeInfo tree : enemyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }
    }

    /**
     * Handles enemy sightings - report and harass.
     */
    private static void handleEnemies(RobotInfo[] enemies) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Report enemy Archon locations
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                Comms.reportEnemyArchon(enemy.location);
            }
        }

        // Find closest threat and target
        RobotInfo nearestThreat = null;
        RobotInfo harassTarget = null;
        float minThreatDist = Float.MAX_VALUE;
        float minTargetDist = Float.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            float dist = myLoc.distanceTo(enemy.location);

            // Identify threats (units that can kill us)
            if (enemy.type == RobotType.SOLDIER || enemy.type == RobotType.TANK ||
                enemy.type == RobotType.LUMBERJACK) {
                if (dist < minThreatDist) {
                    minThreatDist = dist;
                    nearestThreat = enemy;
                }
            }

            // Identify harass targets (Gardeners, Archons)
            if (enemy.type == RobotType.GARDENER || enemy.type == RobotType.ARCHON) {
                if (dist < minTargetDist) {
                    minTargetDist = dist;
                    harassTarget = enemy;
                }
            }
        }

        // Flee from threats if too close
        if (nearestThreat != null && minThreatDist < 6f) {
            Navigation.moveAwayFrom(nearestThreat.location);
            return;
        }

        // Harass Gardeners by orbiting and being annoying
        if (harassTarget != null) {
            harassEnemy(harassTarget);
        } else if (!rc.hasMoved()) {
            // Orbit threats at safe distance
            if (nearestThreat != null) {
                orbitEnemy(nearestThreat);
            }
        }
    }

    /**
     * Harasses a Gardener or Archon by staying close but safe.
     */
    private static void harassEnemy(RobotInfo target) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        float distance = myLoc.distanceTo(target.location);

        // Scouts can shoot too (single shots)
        if (rc.canFireSingleShot() && distance < 5f) {
            Direction toTarget = myLoc.directionTo(target.location);
            rc.fireSingleShot(toTarget);
        }

        // Maintain harassment distance
        if (distance < 3f) {
            Navigation.moveAwayFrom(target.location);
        } else if (distance > 5f) {
            Navigation.moveToward(target.location);
        } else {
            // Strafe around
            Direction toTarget = myLoc.directionTo(target.location);
            Direction strafe = toTarget.rotateLeftDegrees(90);
            Navigation.tryMove(strafe);
        }
    }

    /**
     * Orbits an enemy at safe distance for info gathering.
     */
    private static void orbitEnemy(RobotInfo enemy) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction toEnemy = myLoc.directionTo(enemy.location);
        float distance = myLoc.distanceTo(enemy.location);

        if (distance < 7f) {
            // Orbit perpendicular
            Direction orbit = toEnemy.rotateLeftDegrees(90);
            Navigation.tryMove(orbit);
        } else {
            Navigation.moveToward(enemy.location);
        }
    }

    /**
     * Explores the map, prioritizing enemy side.
     */
    private static void explore() throws GameActionException {
        if (!exploringEnemySide) {
            // Head toward enemy base
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            if (enemyArchons.length > 0) {
                targetLocation = enemyArchons[(int)(Math.random() * enemyArchons.length)];
                exploringEnemySide = true;
            }
        }

        if (targetLocation != null) {
            float dist = rc.getLocation().distanceTo(targetLocation);
            if (dist < 5f) {
                // Reached target, pick new one
                targetLocation = null;
                exploringEnemySide = false;
            } else {
                Navigation.moveToward(targetLocation);
                return;
            }
        }

        // Random exploration
        Navigation.wander();
    }
}
