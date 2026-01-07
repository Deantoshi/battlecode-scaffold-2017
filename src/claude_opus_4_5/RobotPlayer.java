package claude_opus_4_5;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team myTeam;
    static Team enemy;

    // Broadcast channels
    static final int ARCHON_X_CHANNEL = 0;
    static final int ARCHON_Y_CHANNEL = 1;
    static final int ENEMY_ARCHON_X_CHANNEL = 2;
    static final int ENEMY_ARCHON_Y_CHANNEL = 3;
    static final int GARDENER_COUNT_CHANNEL = 4;
    static final int SOLDIER_COUNT_CHANNEL = 5;
    static final int LUMBERJACK_COUNT_CHANNEL = 6;

    // Build directions for hexagonal tree pattern
    static final Direction[] HEX_DIRECTIONS = {
        Direction.getNorth(),
        Direction.getNorth().rotateRightDegrees(60),
        Direction.getNorth().rotateRightDegrees(120),
        Direction.getSouth(),
        Direction.getSouth().rotateRightDegrees(60),
        Direction.getSouth().rotateRightDegrees(120)
    };

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        myTeam = rc.getTeam();
        enemy = myTeam.opponent();

        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:     runArchon();     break;
                    case GARDENER:   runGardener();   break;
                    case SOLDIER:    runSoldier();    break;
                    case LUMBERJACK: runLumberjack(); break;
                    case SCOUT:      runScout();      break;
                    case TANK:       runTank();       break;
                }
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // ==================== ARCHON ====================
    static void runArchon() throws GameActionException {
        // Broadcast location
        MapLocation myLoc = rc.getLocation();
        rc.broadcast(ARCHON_X_CHANNEL, (int) myLoc.x);
        rc.broadcast(ARCHON_Y_CHANNEL, (int) myLoc.y);

        // Dodge bullets first
        tryDodgeBullets();

        // Hire gardeners - more aggressive early game
        int roundNum = rc.getRoundNum();
        int gardenerCount = rc.readBroadcast(GARDENER_COUNT_CHANNEL);

        // Hire gardener if we can afford it and don't have too many
        boolean shouldHire = (roundNum < 50 && gardenerCount < 2) ||
                            (roundNum >= 50 && gardenerCount < 4 && rc.getTeamBullets() > 150);

        if (shouldHire) {
            for (Direction dir : HEX_DIRECTIONS) {
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    rc.broadcast(GARDENER_COUNT_CHANNEL, gardenerCount + 1);
                    break;
                }
            }
        }

        // Move away from enemies
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, enemy);
        if (nearbyEnemies.length > 0) {
            Direction awayFromEnemy = nearbyEnemies[0].location.directionTo(myLoc);
            tryMove(awayFromEnemy);
        } else {
            // Wander slowly
            if (Math.random() < 0.1) {
                tryMove(randomDirection());
            }
        }

        // Donate bullets if we have excess (late game)
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();

        if (bullets > 400 && rc.getRoundNum() > 500) {
            float toDonate = bullets - 300;
            // Must donate in multiples of the VP cost
            toDonate = ((int)(toDonate / vpCost)) * vpCost;
            if (toDonate >= vpCost) {
                rc.donate(toDonate);
            }
        }
        // Victory push - if we're close to winning
        bullets = rc.getTeamBullets();
        if (rc.getTeamVictoryPoints() > 900 && bullets >= vpCost) {
            float toDonate = ((int)(bullets / vpCost)) * vpCost;
            if (toDonate >= vpCost) {
                rc.donate(toDonate);
            }
        }
    }

    // ==================== GARDENER ====================
    static int treesPlanted = 0;
    static boolean settled = false;
    static int settleAttempts = 0;

    static void runGardener() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // First, try to find a good spot to settle (away from other gardeners/trees)
        if (!settled && settleAttempts < 30) {
            TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3f, myTeam);
            RobotInfo[] nearbyGardeners = rc.senseNearbyRobots(5f, myTeam);
            int gardenerCount = 0;
            for (RobotInfo r : nearbyGardeners) {
                if (r.type == RobotType.GARDENER) gardenerCount++;
            }

            if (nearbyTrees.length == 0 && gardenerCount <= 1 && hasOpenSpace()) {
                settled = true;
            } else {
                // Move away from archon and other gardeners
                int archonX = rc.readBroadcast(ARCHON_X_CHANNEL);
                int archonY = rc.readBroadcast(ARCHON_Y_CHANNEL);
                MapLocation archonLoc = new MapLocation(archonX, archonY);
                Direction awayFromArchon = archonLoc.directionTo(myLoc);
                tryMove(awayFromArchon);
                settleAttempts++;
            }
            return;
        }

        settled = true; // Force settle after 30 attempts

        // Water the lowest health tree first
        TreeInfo[] myTrees = rc.senseNearbyTrees(2f, myTeam);
        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : myTrees) {
            if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                lowestTree = tree;
            }
        }
        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }

        // Build units strategically
        int roundNum = rc.getRoundNum();
        int soldierCount = rc.readBroadcast(SOLDIER_COUNT_CHANNEL);
        int lumberjackCount = rc.readBroadcast(LUMBERJACK_COUNT_CHANNEL);

        // Early game: build a scout or lumberjack
        if (roundNum < 100 && treesPlanted < 1) {
            // First build a lumberjack to clear space
            if (lumberjackCount < 2) {
                for (Direction dir : HEX_DIRECTIONS) {
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(LUMBERJACK_COUNT_CHANNEL, lumberjackCount + 1);
                        return;
                    }
                }
            }
        }

        // Plant trees (leave one direction open for building)
        if (treesPlanted < 5) {
            for (int i = 0; i < HEX_DIRECTIONS.length - 1; i++) {
                Direction dir = HEX_DIRECTIONS[i];
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    treesPlanted++;
                    return;
                }
            }
        }

        // Build combat units with the remaining open slot
        Direction buildDir = HEX_DIRECTIONS[HEX_DIRECTIONS.length - 1];
        // Alternate between soldiers and tanks late game
        if (rc.getTeamBullets() > 300 && roundNum > 300) {
            if (rc.canBuildRobot(RobotType.TANK, buildDir)) {
                rc.buildRobot(RobotType.TANK, buildDir);
                return;
            }
        }

        if (soldierCount < 10 || rc.getTeamBullets() > 150) {
            for (Direction dir : HEX_DIRECTIONS) {
                if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    rc.broadcast(SOLDIER_COUNT_CHANNEL, soldierCount + 1);
                    return;
                }
            }
        }
    }

    static boolean hasOpenSpace() throws GameActionException {
        int openDirs = 0;
        for (Direction dir : HEX_DIRECTIONS) {
            if (rc.canPlantTree(dir) || rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                openDirs++;
            }
        }
        return openDirs >= 4;
    }

    // ==================== SOLDIER ====================
    static void runSoldier() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Dodge bullets first!
        tryDodgeBullets();

        // Find enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

        if (enemies.length > 0) {
            // Find lowest HP enemy (focus fire)
            RobotInfo target = getLowestHpEnemy(enemies);
            Direction toEnemy = myLoc.directionTo(target.location);
            float distToEnemy = myLoc.distanceTo(target.location);

            // Check if we have a clear shot (no friendly fire)
            if (hasClearShot(myLoc, toEnemy)) {
                // Fire based on distance and bullet count
                if (rc.canFirePentadShot() && distToEnemy < 4) {
                    rc.firePentadShot(toEnemy);
                } else if (rc.canFireTriadShot() && distToEnemy < 5) {
                    rc.fireTriadShot(toEnemy);
                } else if (rc.canFireSingleShot()) {
                    rc.fireSingleShot(toEnemy);
                }
            }

            // Kiting: maintain optimal distance
            if (distToEnemy < 3) {
                // Too close, back up
                tryMove(toEnemy.opposite());
            } else if (distToEnemy > 5) {
                // Too far, move closer
                tryMove(toEnemy);
            } else {
                // Strafe sideways
                if (Math.random() < 0.5) {
                    tryMove(toEnemy.rotateLeftDegrees(90));
                } else {
                    tryMove(toEnemy.rotateRightDegrees(90));
                }
            }

            // Broadcast enemy archon location
            if (target.type == RobotType.ARCHON) {
                rc.broadcast(ENEMY_ARCHON_X_CHANNEL, (int) target.location.x);
                rc.broadcast(ENEMY_ARCHON_Y_CHANNEL, (int) target.location.y);
            }
        } else {
            // No enemies visible - hunt for them
            int enemyArchonX = rc.readBroadcast(ENEMY_ARCHON_X_CHANNEL);
            int enemyArchonY = rc.readBroadcast(ENEMY_ARCHON_Y_CHANNEL);

            if (enemyArchonX != 0 || enemyArchonY != 0) {
                // Move towards last known enemy archon position
                MapLocation enemyLoc = new MapLocation(enemyArchonX, enemyArchonY);
                tryMove(myLoc.directionTo(enemyLoc));
            } else {
                // Explore - head towards enemy spawn (opposite side of map)
                MapLocation[] initialArchons = rc.getInitialArchonLocations(enemy);
                if (initialArchons.length > 0) {
                    tryMove(myLoc.directionTo(initialArchons[0]));
                } else {
                    tryMove(randomDirection());
                }
            }
        }
    }

    static RobotInfo getLowestHpEnemy(RobotInfo[] enemies) {
        RobotInfo lowest = enemies[0];
        for (RobotInfo r : enemies) {
            // Prioritize archons and gardeners
            if (r.type == RobotType.ARCHON || r.type == RobotType.GARDENER) {
                if (lowest.type != RobotType.ARCHON && lowest.type != RobotType.GARDENER) {
                    lowest = r;
                } else if (r.health < lowest.health) {
                    lowest = r;
                }
            } else if (lowest.type != RobotType.ARCHON && lowest.type != RobotType.GARDENER) {
                if (r.health < lowest.health) {
                    lowest = r;
                }
            }
        }
        return lowest;
    }

    static boolean hasClearShot(MapLocation from, Direction dir) throws GameActionException {
        // Check if any allies are in the line of fire
        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        for (RobotInfo ally : allies) {
            Direction toAlly = from.directionTo(ally.location);
            float angle = Math.abs(dir.radiansBetween(toAlly));
            float dist = from.distanceTo(ally.location);
            // If ally is close and roughly in the direction we're shooting
            if (dist < 6 && angle < Math.PI / 6) {
                return false;
            }
        }
        return true;
    }

    // ==================== LUMBERJACK ====================
    static void runLumberjack() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Check for enemies in strike range
        RobotInfo[] enemiesInRange = rc.senseNearbyRobots(
            RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
        RobotInfo[] alliesInRange = rc.senseNearbyRobots(
            RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, myTeam);

        // Strike only if there are more enemies than allies nearby
        if (enemiesInRange.length > 0 && enemiesInRange.length >= alliesInRange.length && rc.canStrike()) {
            rc.strike();
            return;
        }

        // Find enemies to chase
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
        if (enemies.length > 0) {
            RobotInfo target = getLowestHpEnemy(enemies);
            tryMove(myLoc.directionTo(target.location));
            return;
        }

        // Chop neutral trees for space and bullets
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        if (neutralTrees.length > 0) {
            // Find tree with most bullets or closest
            TreeInfo bestTree = neutralTrees[0];
            for (TreeInfo tree : neutralTrees) {
                if (tree.containedBullets > bestTree.containedBullets) {
                    bestTree = tree;
                }
            }

            if (rc.canChop(bestTree.ID)) {
                rc.chop(bestTree.ID);
            } else {
                tryMove(myLoc.directionTo(bestTree.location));
            }
            return;
        }

        // Chop enemy trees
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, enemy);
        if (enemyTrees.length > 0) {
            TreeInfo closest = enemyTrees[0];
            for (TreeInfo tree : enemyTrees) {
                if (myLoc.distanceTo(tree.location) < myLoc.distanceTo(closest.location)) {
                    closest = tree;
                }
            }
            if (rc.canChop(closest.ID)) {
                rc.chop(closest.ID);
            } else {
                tryMove(myLoc.directionTo(closest.location));
            }
            return;
        }

        // Move towards enemy base
        MapLocation[] initialArchons = rc.getInitialArchonLocations(enemy);
        if (initialArchons.length > 0) {
            tryMove(myLoc.directionTo(initialArchons[0]));
        } else {
            tryMove(randomDirection());
        }
    }

    // ==================== SCOUT ====================
    static void runScout() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Dodge bullets (scouts are fragile!)
        tryDodgeBullets();

        // Shake nearby neutral trees for bullets
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                break;
            }
        }

        // Find enemy gardeners to harass
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
        RobotInfo targetGardener = null;
        for (RobotInfo r : enemies) {
            if (r.type == RobotType.GARDENER) {
                targetGardener = r;
                break;
            }
        }

        if (targetGardener != null) {
            Direction toTarget = myLoc.directionTo(targetGardener.location);
            float dist = myLoc.distanceTo(targetGardener.location);

            // Shoot if in range and clear shot
            if (rc.canFireSingleShot() && hasClearShot(myLoc, toTarget)) {
                rc.fireSingleShot(toTarget);
            }

            // Maintain safe distance (scouts die fast)
            if (dist < 4) {
                tryMove(toTarget.opposite());
            } else if (dist > 7) {
                tryMove(toTarget);
            }
        } else if (enemies.length > 0) {
            // Run from combat units
            Direction awayFromEnemy = enemies[0].location.directionTo(myLoc);
            tryMove(awayFromEnemy);

            // Broadcast enemy location
            rc.broadcast(ENEMY_ARCHON_X_CHANNEL, (int) enemies[0].location.x);
            rc.broadcast(ENEMY_ARCHON_Y_CHANNEL, (int) enemies[0].location.y);
        } else {
            // Explore towards enemy spawn
            MapLocation[] initialArchons = rc.getInitialArchonLocations(enemy);
            if (initialArchons.length > 0) {
                tryMove(myLoc.directionTo(initialArchons[0]));
            } else {
                tryMove(randomDirection());
            }
        }
    }

    // ==================== TANK ====================
    static void runTank() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Tanks are slow, focus on shooting
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

        if (enemies.length > 0) {
            RobotInfo target = getLowestHpEnemy(enemies);
            Direction toEnemy = myLoc.directionTo(target.location);

            // Fire if we have a clear shot
            if (hasClearShot(myLoc, toEnemy)) {
                if (rc.canFirePentadShot()) {
                    rc.firePentadShot(toEnemy);
                } else if (rc.canFireTriadShot()) {
                    rc.fireTriadShot(toEnemy);
                } else if (rc.canFireSingleShot()) {
                    rc.fireSingleShot(toEnemy);
                }
            }

            // Move towards enemy (tanks are tanky)
            tryMove(toEnemy);
        } else {
            // March towards enemy base
            MapLocation[] initialArchons = rc.getInitialArchonLocations(enemy);
            if (initialArchons.length > 0) {
                tryMove(myLoc.directionTo(initialArchons[0]));
            } else {
                tryMove(randomDirection());
            }
        }
    }

    // ==================== UTILITY METHODS ====================

    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 5);
    }

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (dir == null) return false;

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Try rotating left and right
        int currentCheck = 1;
        while (currentCheck <= checksPerSide) {
            Direction left = dir.rotateLeftDegrees(degreeOffset * currentCheck);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(degreeOffset * currentCheck);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
            currentCheck++;
        }

        return false;
    }

    static void tryDodgeBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets(5f);
        if (bullets.length == 0) return;

        MapLocation myLoc = rc.getLocation();
        float bodyRadius = rc.getType().bodyRadius;

        for (BulletInfo bullet : bullets) {
            if (willCollideWithMe(bullet)) {
                // Move perpendicular to bullet direction
                Direction bulletDir = bullet.dir;
                Direction dodgeLeft = bulletDir.rotateLeftDegrees(90);
                Direction dodgeRight = bulletDir.rotateRightDegrees(90);

                // Check which dodge direction is safer
                MapLocation leftPos = myLoc.add(dodgeLeft, rc.getType().strideRadius);
                MapLocation rightPos = myLoc.add(dodgeRight, rc.getType().strideRadius);

                // Prefer the direction away from bullet origin
                Direction fromBullet = bullet.location.directionTo(myLoc);
                if (Math.abs(dodgeLeft.radiansBetween(fromBullet)) < Math.abs(dodgeRight.radiansBetween(fromBullet))) {
                    if (rc.canMove(dodgeLeft)) {
                        rc.move(dodgeLeft);
                        return;
                    }
                }
                if (rc.canMove(dodgeRight)) {
                    rc.move(dodgeRight);
                    return;
                }
                if (rc.canMove(dodgeLeft)) {
                    rc.move(dodgeLeft);
                    return;
                }
            }
        }
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If bullet is traveling away from us
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // Calculate perpendicular distance
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));

        return perpendicularDist <= rc.getType().bodyRadius;
    }
}
