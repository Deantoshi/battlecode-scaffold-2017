package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Lumberjack - Tree clearing and melee combat unit.
 * Key responsibilities:
 * - Strike enemies when beneficial (smart strike)
 * - Chase down enemy units
 * - Chop trees (prioritize trees with bullets, then enemy trees)
 * - Clear paths for gardeners
 */
public strictfp class Lumberjack {
    private static RobotController rc;

    // Strike radius
    private static final float STRIKE_RADIUS = RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;

    public static void run(RobotController robotController) throws GameActionException {
        rc = robotController;

        while (true) {
            try {
                Utils.updatePerTurn();

                // Priority 1: Check for strike opportunity
                if (shouldStrike()) {
                    rc.strike();
                }

                // Priority 2: Chase enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, Utils.enemyTeam);
                if (enemies.length > 0) {
                    chaseEnemy(selectTarget(enemies));
                }
                // Priority 3: Chop trees
                else if (chopTrees()) {
                    // Chopping in progress
                }
                // Priority 4: Move toward objective
                else {
                    moveTowardObjective();
                }

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    /**
     * Determine if we should strike (smart strike logic).
     */
    private static boolean shouldStrike() throws GameActionException {
        if (!rc.canStrike()) return false;

        RobotInfo[] enemiesInRange = rc.senseNearbyRobots(STRIKE_RADIUS, Utils.enemyTeam);
        RobotInfo[] alliesInRange = rc.senseNearbyRobots(STRIKE_RADIUS, Utils.myTeam);

        // No enemies in range, don't strike
        if (enemiesInRange.length == 0) return false;

        // Calculate damage dealt vs damage to allies
        float enemyDamage = 0;
        float allyDamage = 0;
        float attackPower = RobotType.LUMBERJACK.attackPower;

        for (RobotInfo enemy : enemiesInRange) {
            enemyDamage += Math.min(enemy.health, attackPower);
        }

        for (RobotInfo ally : alliesInRange) {
            float damage = Math.min(ally.health, attackPower);
            allyDamage += damage;

            // Extra weight for valuable allies
            if (ally.type == RobotType.ARCHON) {
                allyDamage += 50;
            } else if (ally.type == RobotType.GARDENER) {
                allyDamage += 20;
            }
        }

        // Only strike if net positive (enemy damage significantly outweighs ally damage)
        return enemyDamage > allyDamage * 1.5f;
    }

    /**
     * Select the best target to chase.
     */
    private static RobotInfo selectTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            float score = Utils.myLoc.distanceTo(enemy.location);

            // Priority modifiers (lower = higher priority)
            switch (enemy.type) {
                case GARDENER:
                    score *= 0.4f;
                    break;
                case ARCHON:
                    score *= 0.5f;
                    break;
                case SCOUT:
                    score *= 0.7f;
                    break;
                case SOLDIER:
                    score *= 1.0f;
                    break;
                case LUMBERJACK:
                    score *= 1.1f;
                    break;
                case TANK:
                    score *= 1.5f;
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
     * Chase an enemy unit.
     */
    private static void chaseEnemy(RobotInfo target) throws GameActionException {
        if (target == null) return;
        if (rc.hasMoved()) return;

        // Report enemy sightings
        if (target.type == RobotType.ARCHON) {
            Comms.reportEnemyArchon(target.location);
        } else if (target.type == RobotType.GARDENER) {
            Comms.reportEnemyGardener(target.location);
        }

        // Move toward enemy
        Nav.moveTo(target.location);
    }

    /**
     * Chop trees with priority.
     * Returns true if we're engaged in chopping.
     */
    private static boolean chopTrees() throws GameActionException {
        TreeInfo[] allTrees = rc.senseNearbyTrees(-1, null);
        if (allTrees.length == 0) return false;

        TreeInfo bestTree = null;
        float bestScore = Float.MAX_VALUE;

        for (TreeInfo tree : allTrees) {
            // Never chop own trees
            if (tree.team == Utils.myTeam) continue;

            float score = Utils.myLoc.distanceTo(tree.location);

            // Bonus for trees with bullets
            if (tree.containedBullets > 0) {
                score -= tree.containedBullets * 2;
            }

            // Bonus for enemy trees
            if (tree.team == Utils.enemyTeam) {
                score -= 10;
            }

            // Prefer smaller trees (faster to chop)
            score += tree.health / 10;

            if (score < bestScore) {
                bestScore = score;
                bestTree = tree;
            }
        }

        if (bestTree != null) {
            // First shake if it has bullets
            if (bestTree.containedBullets > 0 && rc.canShake(bestTree.ID)) {
                rc.shake(bestTree.ID);
            }

            // Chop if in range
            if (rc.canChop(bestTree.ID)) {
                rc.chop(bestTree.ID);
                return true;
            }

            // Move toward tree
            if (!rc.hasMoved()) {
                Nav.moveTo(bestTree.location);
                return true;
            }
        }

        return false;
    }

    /**
     * Move toward enemy base or rally point.
     */
    private static void moveTowardObjective() throws GameActionException {
        if (rc.hasMoved()) return;

        // Check for broadcast enemy locations
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

        // Random exploration
        Nav.tryMove(Utils.randomDirection());
    }
}
