package claudebot;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team enemy;
    static Team myTeam;

    // Broadcast channels
    static final int ARCHON_X_CHANNEL = 0;
    static final int ARCHON_Y_CHANNEL = 1;
    static final int ENEMY_ARCHON_X_CHANNEL = 2;
    static final int ENEMY_ARCHON_Y_CHANNEL = 3;
    static final int SOLDIER_COUNT_CHANNEL = 4;
    static final int GARDENER_COUNT_CHANNEL = 5;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        RobotPlayer.enemy = rc.getTeam().opponent();
        RobotPlayer.myTeam = rc.getTeam();

        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
        }
    }

    static void runArchon() throws GameActionException {
        System.out.println("ClaudeBot Archon online!");

        while (true) {
            try {
                // Broadcast my location
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(ARCHON_X_CHANNEL, (int) myLocation.x);
                rc.broadcast(ARCHON_Y_CHANNEL, (int) myLocation.y);

                // Detect and broadcast enemy archon location
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                for (RobotInfo r : enemies) {
                    if (r.type == RobotType.ARCHON) {
                        rc.broadcast(ENEMY_ARCHON_X_CHANNEL, (int) r.location.x);
                        rc.broadcast(ENEMY_ARCHON_Y_CHANNEL, (int) r.location.y);
                    }
                }

                // Hire gardeners more aggressively
                Direction dir = randomDirection();
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = dir.rotateLeftDegrees(45 * i);
                    if (rc.canHireGardener(tryDir)) {
                        rc.hireGardener(tryDir);
                        break;
                    }
                }

                // Move away from enemies if any are nearby
                if (enemies.length > 0) {
                    MapLocation enemyLoc = enemies[0].location;
                    Direction awayFromEnemy = enemyLoc.directionTo(myLocation);
                    tryMove(awayFromEnemy);
                } else {
                    // Wander but stay away from map edges
                    tryMove(randomDirection());
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        System.out.println("ClaudeBot Gardener online!");
        int treesPlanted = 0;

        while (true) {
            try {
                // Water nearby trees that need it
                TreeInfo[] trees = rc.senseNearbyTrees(2, myTeam);
                for (TreeInfo tree : trees) {
                    if (rc.canWater(tree.ID) && tree.health < tree.maxHealth - 5) {
                        rc.water(tree.ID);
                        break;
                    }
                }

                // Build strategy: mostly soldiers, some lumberjacks
                Direction buildDir = randomDirection();

                // Find a valid build direction
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = buildDir.rotateLeftDegrees(45 * i);

                    // 70% soldiers, 30% lumberjacks
                    if (Math.random() < 0.7) {
                        if (rc.canBuildRobot(RobotType.SOLDIER, tryDir)) {
                            rc.buildRobot(RobotType.SOLDIER, tryDir);
                            break;
                        }
                    } else {
                        if (rc.canBuildRobot(RobotType.LUMBERJACK, tryDir)) {
                            rc.buildRobot(RobotType.LUMBERJACK, tryDir);
                            break;
                        }
                    }
                }

                // Plant trees if we haven't planted many yet (for bullet income)
                if (treesPlanted < 3) {
                    for (int i = 0; i < 6; i++) {
                        Direction plantDir = new Direction((float) (i * Math.PI / 3));
                        if (rc.canPlantTree(plantDir)) {
                            rc.plantTree(plantDir);
                            treesPlanted++;
                            break;
                        }
                    }
                }

                // Move a bit to spread out
                if (!rc.hasMoved()) {
                    tryMove(randomDirection());
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("ClaudeBot Soldier online!");

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Dodge bullets first
                dodgeBullets();

                // Find enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                if (enemies.length > 0) {
                    // Prioritize targets: Archon > Gardener > others
                    RobotInfo target = prioritizeTarget(enemies);
                    Direction toEnemy = myLocation.directionTo(target.location);
                    float distance = myLocation.distanceTo(target.location);

                    // Fire based on distance and number of enemies
                    if (rc.canFirePentadShot() && enemies.length >= 3 && distance < 4) {
                        rc.firePentadShot(toEnemy);
                    } else if (rc.canFireTriadShot() && distance < 5) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }

                    // Move toward enemy if far, strafe if close
                    if (distance > 4) {
                        tryMove(toEnemy);
                    } else {
                        // Strafe perpendicular to enemy
                        tryMove(toEnemy.rotateLeftDegrees(90));
                    }
                } else {
                    // No enemies visible - move toward broadcasted enemy archon location
                    int enemyX = rc.readBroadcast(ENEMY_ARCHON_X_CHANNEL);
                    int enemyY = rc.readBroadcast(ENEMY_ARCHON_Y_CHANNEL);

                    if (enemyX != 0 || enemyY != 0) {
                        MapLocation enemyArchon = new MapLocation(enemyX, enemyY);
                        tryMove(myLocation.directionTo(enemyArchon));
                    } else {
                        // Explore randomly
                        tryMove(randomDirection());
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("ClaudeBot Lumberjack online!");

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Check for enemies in strike range
                RobotInfo[] enemiesInRange = rc.senseNearbyRobots(
                    RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                // Also check we won't hit too many allies
                RobotInfo[] alliesInRange = rc.senseNearbyRobots(
                    RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, myTeam);

                if (enemiesInRange.length > 0 && enemiesInRange.length >= alliesInRange.length && !rc.hasAttacked()) {
                    rc.strike();
                } else {
                    // Look for trees to chop (prioritize neutral trees with goodies)
                    TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    TreeInfo bestTree = null;
                    float bestScore = -1;

                    for (TreeInfo tree : neutralTrees) {
                        float score = tree.containedBullets;
                        if (tree.containedRobot != null) {
                            score += 100; // High priority for trees with robots
                        }
                        float dist = myLocation.distanceTo(tree.location);
                        score = score / (dist + 1); // Closer is better

                        if (score > bestScore) {
                            bestScore = score;
                            bestTree = tree;
                        }
                    }

                    if (bestTree != null) {
                        // Move toward and chop the tree
                        if (rc.canChop(bestTree.ID)) {
                            rc.chop(bestTree.ID);
                        } else {
                            tryMove(myLocation.directionTo(bestTree.location));
                        }
                    } else {
                        // No good trees, look for enemies
                        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                        if (enemies.length > 0) {
                            tryMove(myLocation.directionTo(enemies[0].location));
                        } else {
                            tryMove(randomDirection());
                        }
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("ClaudeBot Tank online!");
        // Similar to soldier but tankier
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    Direction toEnemy = rc.getLocation().directionTo(enemies[0].location);
                    if (rc.canFirePentadShot()) {
                        rc.firePentadShot(toEnemy);
                    }
                    tryMove(toEnemy);
                } else {
                    tryMove(randomDirection());
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("ClaudeBot Scout online!");
        while (true) {
            try {
                // Scouts are fast - use them to find enemy archon
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                for (RobotInfo r : enemies) {
                    if (r.type == RobotType.ARCHON) {
                        rc.broadcast(ENEMY_ARCHON_X_CHANNEL, (int) r.location.x);
                        rc.broadcast(ENEMY_ARCHON_Y_CHANNEL, (int) r.location.y);
                    }
                }

                if (enemies.length > 0 && rc.canFireSingleShot()) {
                    rc.fireSingleShot(rc.getLocation().directionTo(enemies[0].location));
                }

                tryMove(randomDirection());
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Helper: Prioritize targets (Archon > Gardener > low health > others)
    static RobotInfo prioritizeTarget(RobotInfo[] enemies) {
        RobotInfo best = enemies[0];
        int bestPriority = getTargetPriority(best);

        for (RobotInfo r : enemies) {
            int priority = getTargetPriority(r);
            if (priority > bestPriority ||
                (priority == bestPriority && r.health < best.health)) {
                best = r;
                bestPriority = priority;
            }
        }
        return best;
    }

    static int getTargetPriority(RobotInfo r) {
        switch (r.type) {
            case ARCHON: return 100;
            case GARDENER: return 80;
            case SOLDIER: return 50;
            case TANK: return 40;
            case LUMBERJACK: return 30;
            case SCOUT: return 20;
            default: return 10;
        }
    }

    // Helper: Try to dodge incoming bullets
    static void dodgeBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets(3);
        for (BulletInfo bullet : bullets) {
            if (willCollideWithMe(bullet)) {
                // Move perpendicular to bullet direction
                Direction bulletDir = bullet.dir;
                Direction dodge = bulletDir.rotateLeftDegrees(90);
                if (rc.canMove(dodge)) {
                    rc.move(dodge);
                    return;
                }
                dodge = bulletDir.rotateRightDegrees(90);
                if (rc.canMove(dodge)) {
                    rc.move(dodge);
                    return;
                }
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 5);
    }

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.hasMoved()) {
            return false;
        }

        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        int currentCheck = 1;
        while (currentCheck <= checksPerSide) {
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            currentCheck++;
        }

        return false;
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}
