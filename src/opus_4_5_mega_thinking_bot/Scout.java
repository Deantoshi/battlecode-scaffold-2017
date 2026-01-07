package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Scout - Fast reconnaissance and harassment unit.
 * Key responsibilities:
 * - Shake trees for bullets (economy)
 * - Explore toward enemy spawn
 * - Harass enemy gardeners from safe distance
 * - Report enemy sightings
 * - Flee from combat threats
 */
public strictfp class Scout {
    private static RobotController rc;

    // Safe harassment distance
    private static final float SAFE_DIST = 5.5f;
    private static final float FLEE_DIST = 8f;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        while (true) {
            try {
                Utils.updatePerTurn();

                // Priority 1: Dodge bullets (scouts are fragile!)
                BulletInfo[] bullets = rc.senseNearbyBullets(RobotType.SCOUT.bulletSightRadius);
                if (bullets.length > 0) {
                    Nav.dodgeBullets();
                }

                // Priority 2: Shake trees for bullets
                shakeTrees();

                // Check for enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);
                reportEnemies(enemies);

                // Priority 3: Harass gardeners if safe
                if (harassGardener(enemies)) {
                    // Harassment in progress
                }
                // Priority 4: Flee from dangerous enemies
                else if (isDangerous(enemies)) {
                    fleeFromDanger(enemies);
                }
                // Priority 5: Explore
                else {
                    explore();
                }

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Shake trees for bullets.
     */
    private static void shakeTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        TreeInfo bestTree = null;
        float bestValue = 0;

        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                if (tree.containedBullets > bestValue) {
                    bestValue = tree.containedBullets;
                    bestTree = tree;
                }
            }
        }

        if (bestTree != null) {
            rc.shake(bestTree.ID);
        }
    }

    /**
     * Harass enemy gardener from safe distance.
     */
    private static boolean harassGardener(RobotInfo[] enemies) throws GameActionException {
        RobotInfo gardener = null;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                gardener = enemy;
                break;
            }
        }

        if (gardener == null) return false;

        float dist = Utils.myLoc.distanceTo(gardener.location);
        Direction toGardener = Utils.myLoc.directionTo(gardener.location);

        // Check for nearby threats (soldiers, tanks, lumberjacks)
        boolean hasThreat = false;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER ||
                    enemy.type == RobotType.TANK ||
                    enemy.type == RobotType.LUMBERJACK) {
                float threatDist = Utils.myLoc.distanceTo(enemy.location);
                if (threatDist < FLEE_DIST) {
                    hasThreat = true;
                    break;
                }
            }
        }

        if (hasThreat) {
            // Keep max distance while still shooting
            if (dist < SAFE_DIST + 1) {
                Nav.tryMove(toGardener.opposite());
            }
        } else {
            // Move to optimal harassment range
            if (dist > SAFE_DIST + 1) {
                Nav.safeMoveToward(gardener.location);
            } else if (dist < SAFE_DIST - 1) {
                Nav.tryMove(toGardener.opposite());
            }
        }

        // Shoot if we have a clear shot
        if (rc.canFireSingleShot()) {
            if (hasClearShot(toGardener)) {
                rc.fireSingleShot(toGardener);
            }
        }

        return true;
    }

    /**
     * Check if there are dangerous enemies nearby.
     */
    private static boolean isDangerous(RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            float dist = Utils.myLoc.distanceTo(enemy.location);
            if (enemy.type == RobotType.SOLDIER && dist < FLEE_DIST) return true;
            if (enemy.type == RobotType.TANK && dist < FLEE_DIST) return true;
            if (enemy.type == RobotType.LUMBERJACK && dist < FLEE_DIST - 2) return true;
        }
        return false;
    }

    /**
     * Flee from dangerous enemies.
     */
    private static void fleeFromDanger(RobotInfo[] enemies) throws GameActionException {
        if (rc.hasMoved()) return;

        // Find average position of threats
        float totalX = 0, totalY = 0;
        int count = 0;

        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER ||
                    enemy.type == RobotType.TANK ||
                    enemy.type == RobotType.LUMBERJACK) {
                totalX += enemy.location.x;
                totalY += enemy.location.y;
                count++;
            }
        }

        if (count > 0) {
            MapLocation threatCenter = new MapLocation(totalX / count, totalY / count);
            Nav.moveAway(threatCenter);
        }
    }

    /**
     * Explore toward enemy spawn.
     */
    private static void explore() throws GameActionException {
        if (rc.hasMoved()) return;

        // Look for trees with bullets to shake
        TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        TreeInfo bestTree = null;
        float bestValue = 0;

        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0) {
                float value = tree.containedBullets / (Utils.myLoc.distanceTo(tree.location) + 1);
                if (value > bestValue) {
                    bestValue = value;
                    bestTree = tree;
                }
            }
        }

        if (bestTree != null && bestValue > 0.5f) {
            Nav.moveTo(bestTree.location);
            return;
        }

        // Move toward enemy spawn
        MapLocation[] enemyArchons = Utils.getEnemyInitialArchonLocs();
        if (enemyArchons.length > 0) {
            // Don't go too close
            if (Utils.myLoc.distanceTo(enemyArchons[0]) > 10) {
                Nav.moveTo(enemyArchons[0]);
                return;
            }
        }

        // Random exploration
        Nav.tryMove(Utils.randomDirection());
    }

    /**
     * Check if we have a clear shot (no allies in the way).
     */
    private static boolean hasClearShot(Direction toTarget) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(6f, Utils.myTeam);

        for (RobotInfo ally : allies) {
            Direction toAlly = Utils.myLoc.directionTo(ally.location);
            float angleDiff = Math.abs(toTarget.radiansBetween(toAlly));
            float distToAlly = Utils.myLoc.distanceTo(ally.location);

            if (distToAlly < 5f && angleDiff < Math.PI / 8) {
                return false;
            }
        }
        return true;
    }

    /**
     * Report enemy sightings.
     */
    private static void reportEnemies(RobotInfo[] enemies) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                Comms.reportEnemyArchon(enemy.location);
            } else if (enemy.type == RobotType.GARDENER) {
                Comms.reportEnemyGardener(enemy.location);
            }
        }
    }
}
