package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Soldier - Main combat unit with advanced micro.
 * Key responsibilities:
 * - Dodge bullets
 * - Select targets with priority scoring
 * - Kite enemies (maintain optimal distance)
 * - Coordinate focus fire with team
 * - Prevent friendly fire
 */
public strictfp class Soldier {
    private static RobotController rc;

    // Optimal combat range
    private static final float OPTIMAL_RANGE = 4.5f;

    // Last known enemy location for hunting
    private static MapLocation lastEnemyLoc = null;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        while (true) {
            try {
                Utils.updatePerTurn();

                // Sense surroundings
                BulletInfo[] bullets = rc.senseNearbyBullets(10f);
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);

                // Priority 1: Dodge bullets
                if (bullets.length > 0) {
                    Nav.dodgeBullets();
                }

                // Priority 2: Combat if enemies present
                if (enemies.length > 0) {
                    executeCombat(enemies);
                } else {
                    // Priority 3: Hunt for enemies
                    huntEnemies();
                }

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Execute combat against visible enemies.
     */
    private static void executeCombat(RobotInfo[] enemies) throws GameActionException {
        // Report enemy sightings
        reportEnemies(enemies);

        // Select target with priority scoring
        RobotInfo target = selectTarget(enemies);
        if (target == null) return;

        lastEnemyLoc = target.location;
        Direction toTarget = Utils.myLoc.directionTo(target.location);
        float dist = Utils.myLoc.distanceTo(target.location);

        // Attack if we have a clear shot
        if (rc.canFireSingleShot()) {
            if (hasClearShot(toTarget)) {
                // Choose shot type based on distance and bullet count
                if (dist < 3 && Utils.teamBullets > 50 && rc.canFirePentadShot()) {
                    rc.firePentadShot(toTarget);
                } else if (dist < 4 && Utils.teamBullets > 30 && rc.canFireTriadShot()) {
                    rc.fireTriadShot(toTarget);
                } else {
                    rc.fireSingleShot(toTarget);
                }
            }
        }

        // Micro movement
        microMovement(target);
    }

    /**
     * Select the best target using priority scoring.
     */
    private static RobotInfo selectTarget(RobotInfo[] enemies) throws GameActionException {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        // Check for focus fire target
        int focusId = Comms.getFocusTargetId();
        if (!Comms.isFocusTargetStale()) {
            for (RobotInfo enemy : enemies) {
                if (enemy.ID == focusId) {
                    return enemy;  // Prioritize team focus target
                }
            }
        }

        for (RobotInfo enemy : enemies) {
            float score = Utils.myLoc.distanceTo(enemy.location);

            // Priority modifiers (lower = higher priority)
            switch (enemy.type) {
                case ARCHON:
                    score *= 0.4f;
                    break;
                case GARDENER:
                    score *= 0.5f;
                    break;
                case SCOUT:
                    score *= 0.8f;
                    break;
                case SOLDIER:
                    score *= 1.0f;
                    break;
                case LUMBERJACK:
                    score *= 1.1f;
                    break;
                case TANK:
                    score *= 1.3f;
                    break;
            }

            // Health modifier: finish low HP targets
            float healthRatio = enemy.health / enemy.type.maxHealth;
            score *= (0.5f + healthRatio * 0.5f);

            if (score < bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        // Set as team focus target if it's high-value
        if (bestTarget != null &&
                (bestTarget.type == RobotType.ARCHON || bestTarget.type == RobotType.GARDENER)) {
            Comms.setFocusTarget(bestTarget);
        }

        return bestTarget;
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

            // Check if ally is in the cone of fire (30 degrees for pentad)
            float maxAngle = (float)(Math.PI / 6);  // 30 degrees

            if (distToAlly < 6f && angleDiff < maxAngle) {
                // Ally is in potential line of fire
                return false;
            }
        }
        return true;
    }

    /**
     * Kiting micro movement.
     */
    private static void microMovement(RobotInfo target) throws GameActionException {
        if (rc.hasMoved()) return;

        float dist = Utils.myLoc.distanceTo(target.location);
        Direction toEnemy = Utils.myLoc.directionTo(target.location);

        if (dist < 3.0f) {
            // Too close - back up
            Nav.tryMove(toEnemy.opposite());
        } else if (dist > 5.5f) {
            // Too far - close in
            Nav.safeMoveToward(target.location);
        } else {
            // Optimal range - strafe
            Nav.strafe(toEnemy);
        }
    }

    /**
     * Hunt for enemies when none are visible.
     */
    private static void huntEnemies() throws GameActionException {
        if (rc.hasMoved()) return;

        // Check broadcast for enemy locations
        if (Comms.isEnemyArchonRecent()) {
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Nav.moveTo(enemyArchon);
                return;
            }
        }

        if (Comms.isEnemyGardenerRecent()) {
            MapLocation enemyGardener = Comms.getEnemyGardenerLocation();
            if (enemyGardener != null) {
                Nav.moveTo(enemyGardener);
                return;
            }
        }

        // Go to last known enemy location
        if (lastEnemyLoc != null) {
            if (Utils.myLoc.distanceTo(lastEnemyLoc) > 3) {
                Nav.moveTo(lastEnemyLoc);
                return;
            } else {
                lastEnemyLoc = null;
            }
        }

        // Move toward enemy initial spawn
        MapLocation[] enemyArchons = Utils.getEnemyInitialArchonLocs();
        if (enemyArchons.length > 0) {
            Nav.moveTo(enemyArchons[0]);
            return;
        }

        // Random exploration
        Nav.tryMove(Utils.randomDirection());
    }

    /**
     * Report enemy sightings via broadcast.
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
