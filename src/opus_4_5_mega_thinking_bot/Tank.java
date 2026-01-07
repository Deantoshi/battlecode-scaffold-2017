package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Tank - Heavy late-game combat unit.
 * Key responsibilities:
 * - Push forward aggressively (high HP)
 * - Fire at priority targets
 * - Destroy trees by body contact
 * - March toward enemy base
 */
public strictfp class Tank {
    private static RobotController rc;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        while (true) {
            try {
                Utils.updatePerTurn();

                // Check for VP win condition (tanks are expensive, so we should check)
                Utils.tryWinByDonation();

                // Sense enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);

                if (enemies.length > 0) {
                    // Report enemies
                    reportEnemies(enemies);

                    // Combat mode
                    RobotInfo target = selectTarget(enemies);
                    if (target != null) {
                        // Fire at target
                        Direction toTarget = Utils.myLoc.directionTo(target.location);
                        if (hasClearShot(toTarget)) {
                            fireAtTarget(target);
                        }

                        // Push toward target (tanks are tanky)
                        Nav.moveTo(target.location);
                    }
                } else {
                    // March toward enemy base
                    marchToEnemy();
                }

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Select target with priority scoring.
     */
    private static RobotInfo selectTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            float score = Utils.myLoc.distanceTo(enemy.location);

            // Priority modifiers
            switch (enemy.type) {
                case ARCHON:
                    score *= 0.3f;  // Highest priority for tank
                    break;
                case GARDENER:
                    score *= 0.4f;
                    break;
                case SCOUT:
                    score *= 1.0f;
                    break;
                case SOLDIER:
                    score *= 0.8f;
                    break;
                case LUMBERJACK:
                    score *= 0.9f;
                    break;
                case TANK:
                    score *= 1.2f;  // Tank vs tank is expensive
                    break;
            }

            // Low health bonus
            float healthRatio = enemy.health / enemy.type.maxHealth;
            score *= (0.5f + healthRatio * 0.5f);

            if (score < bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        return bestTarget;
    }

    /**
     * Fire at target with appropriate shot type.
     */
    private static void fireAtTarget(RobotInfo target) throws GameActionException {
        Direction toTarget = Utils.myLoc.directionTo(target.location);
        float dist = Utils.myLoc.distanceTo(target.location);

        // Choose shot type based on distance and bullet count
        if (dist < 4 && Utils.teamBullets > 50 && rc.canFirePentadShot()) {
            rc.firePentadShot(toTarget);
        } else if (dist < 5 && Utils.teamBullets > 30 && rc.canFireTriadShot()) {
            rc.fireTriadShot(toTarget);
        } else if (rc.canFireSingleShot()) {
            rc.fireSingleShot(toTarget);
        }
    }

    /**
     * Check if we have a clear shot.
     */
    private static boolean hasClearShot(Direction toTarget) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(6f, Utils.myTeam);

        for (RobotInfo ally : allies) {
            Direction toAlly = Utils.myLoc.directionTo(ally.location);
            float angleDiff = Math.abs(toTarget.radiansBetween(toAlly));
            float distToAlly = Utils.myLoc.distanceTo(ally.location);

            // Wide cone for pentad shot
            float maxAngle = (float)(Math.PI / 6);

            if (distToAlly < 6f && angleDiff < maxAngle) {
                return false;
            }
        }
        return true;
    }

    /**
     * March toward enemy base.
     */
    private static void marchToEnemy() throws GameActionException {
        if (rc.hasMoved()) return;

        // Check for broadcast enemy locations
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

        // Move toward enemy initial spawn
        MapLocation[] enemyArchons = Utils.getEnemyInitialArchonLocs();
        if (enemyArchons.length > 0) {
            Nav.moveTo(enemyArchons[0]);
            return;
        }

        // Random movement
        Nav.tryMove(Utils.randomDirection());
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
