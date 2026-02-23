package claude_sonet_4_6_high;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    // ---- Communication channels ----
    static final int CH_ARCHON_X    = 0;  // archon x * 100
    static final int CH_ARCHON_Y    = 1;  // archon y * 100
    static final int CH_ENEMY_X     = 2;  // last seen enemy x * 100
    static final int CH_ENEMY_Y     = 3;  // last seen enemy y * 100
    // CH_GARDENER_COUNT = 4 (owned by BulletSpending)

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        BulletSpending.init(rc);

        switch (rc.getType()) {
            case ARCHON:     runArchon();     break;
            case GARDENER:   runGardener();   break;
            case SOLDIER:    runSoldier();    break;
            case LUMBERJACK: runLumberjack(); break;
            case SCOUT:      runScout();      break;
            case TANK:       runTank();       break;
            default: while (true) { Clock.yield(); }
        }
    }

    // ====================================================================
    // ARCHON
    // ====================================================================
    static void runArchon() throws GameActionException {
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Broadcast our location
                rc.broadcast(CH_ARCHON_X, (int)(myLoc.x * 100));
                rc.broadcast(CH_ARCHON_Y, (int)(myLoc.y * 100));

                // Move away from visible enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    Direction awayFromEnemy = myLoc.directionTo(enemies[0].location).opposite();
                    tryMove(awayFromEnemy, 20, 8);
                } else {
                    // Wander slowly
                    tryMove(randomDirection(), 20, 3);
                }

                // Spend bullets (hire gardeners, donate VP)
                BulletSpending.spendPolicy();

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // GARDENER
    // ====================================================================
    static void runGardener() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        Direction exploreDir = randomDirection();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Water lowest-health team tree within range
                if (rc.canWater()) {
                    TreeInfo[] myTrees = rc.senseNearbyTrees(-1, rc.getTeam());
                    TreeInfo lowestHP = null;
                    for (TreeInfo t : myTrees) {
                        if (rc.canWater(t.ID)) {
                            if (lowestHP == null || t.health < lowestHP.health) {
                                lowestHP = t;
                            }
                        }
                    }
                    if (lowestHP != null) {
                        rc.water(lowestHP.ID);
                    }
                }

                // Spend bullets (plant trees, build units, donate)
                BulletSpending.spendPolicy();

                // Move away from enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    Direction awayFromEnemy = myLoc.directionTo(enemies[0].location).opposite();
                    if (!tryMove(awayFromEnemy, 20, 8)) {
                        tryMove(randomDirection(), 20, 3);
                    }
                } else {
                    // Explore until we find a good spot
                    if (!tryMove(exploreDir, 15, 4)) {
                        exploreDir = randomDirection();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // SOLDIER
    // ====================================================================
    static void runSoldier() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        Direction exploreDir = randomDirection();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // 1. Dodge incoming bullets
                if (!rc.hasMoved()) {
                    BulletInfo[] bullets = rc.senseNearbyBullets();
                    dodgeBullets(bullets);
                }

                // 2. Find and attack enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo target = getBestTarget(enemies);

                if (target != null) {
                    Direction toEnemy = myLoc.directionTo(target.location);
                    float dist = myLoc.distanceTo(target.location);

                    // Shoot
                    if (!rc.hasAttacked()) {
                        if (enemies.length >= 3 && rc.canFirePentadShot()) {
                            rc.firePentadShot(toEnemy);
                        } else if (enemies.length >= 2 && rc.canFireTriadShot()) {
                            rc.fireTriadShot(toEnemy);
                        } else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(toEnemy);
                        }
                    }

                    // Broadcast enemy location
                    rc.broadcast(CH_ENEMY_X, (int)(target.location.x * 100));
                    rc.broadcast(CH_ENEMY_Y, (int)(target.location.y * 100));

                    // Move: close in if far, retreat if too close
                    if (!rc.hasMoved()) {
                        if (dist > 6f) {
                            tryMove(toEnemy, 15, 5);
                        } else if (dist < 3.5f) {
                            tryMove(toEnemy.opposite(), 15, 5);
                        }
                    }
                } else {
                    // Navigate toward last broadcast enemy location or enemy archon start
                    int ex = rc.readBroadcast(CH_ENEMY_X);
                    int ey = rc.readBroadcast(CH_ENEMY_Y);
                    if (ex != 0 && ey != 0) {
                        MapLocation knownEnemy = new MapLocation(ex / 100f, ey / 100f);
                        if (!rc.hasMoved()) {
                            if (!tryMove(myLoc.directionTo(knownEnemy), 15, 5)) {
                                exploreDir = randomDirection();
                                tryMove(exploreDir, 15, 5);
                            }
                        }
                    } else {
                        // Head toward enemy archon initial location
                        MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                        if (!rc.hasMoved()) {
                            if (enemyArchons.length > 0) {
                                tryMove(myLoc.directionTo(enemyArchons[0]), 15, 5);
                            } else {
                                if (!tryMove(exploreDir, 15, 5)) {
                                    exploreDir = randomDirection();
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // LUMBERJACK
    // ====================================================================
    static void runLumberjack() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        Direction exploreDir = randomDirection();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Strike if enemies in range (no friendly fire check for simplicity - lumberjacks are aggressive)
                float strikeRadius = RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
                RobotInfo[] closeEnemies = rc.senseNearbyRobots(strikeRadius, enemy);
                if (closeEnemies.length > 0 && rc.canStrike()) {
                    // Check we won't hit too many allies
                    RobotInfo[] closeFriends = rc.senseNearbyRobots(strikeRadius, rc.getTeam());
                    if (closeFriends.length == 0 || closeEnemies.length > closeFriends.length) {
                        rc.strike();
                    }
                }

                // Sense all enemies and chase the best target
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    RobotInfo target = getBestTarget(enemies);
                    if (!rc.hasMoved()) {
                        tryMove(myLoc.directionTo(target.location), 15, 5);
                    }
                } else {
                    // Chop neutral trees for bullets (shake first)
                    TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    boolean acted = false;
                    for (TreeInfo t : neutralTrees) {
                        if (!rc.hasAttacked() && rc.canChop(t.ID)) {
                            // Shake first if bullets available
                            if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                                rc.shake(t.ID);
                            }
                            rc.chop(t.ID);
                            acted = true;
                            break;
                        }
                    }
                    if (!acted && !rc.hasMoved()) {
                        // Head toward enemy archon start
                        int ex = rc.readBroadcast(CH_ENEMY_X);
                        int ey = rc.readBroadcast(CH_ENEMY_Y);
                        if (ex != 0 && ey != 0) {
                            tryMove(myLoc.directionTo(new MapLocation(ex / 100f, ey / 100f)), 15, 5);
                        } else {
                            MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                            if (enemyArchons.length > 0) {
                                tryMove(myLoc.directionTo(enemyArchons[0]), 15, 5);
                            } else {
                                if (!tryMove(exploreDir, 15, 5)) {
                                    exploreDir = randomDirection();
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // SCOUT
    // ====================================================================
    static void runScout() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        Direction exploreDir = randomDirection();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Shake trees for bullets
                TreeInfo[] allTrees = rc.senseNearbyTrees();
                for (TreeInfo t : allTrees) {
                    if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                        rc.shake(t.ID);
                        break;
                    }
                }

                // Attack nearest enemy (scouts have low attack but can harass)
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    RobotInfo target = getBestTarget(enemies);
                    // Broadcast
                    rc.broadcast(CH_ENEMY_X, (int)(target.location.x * 100));
                    rc.broadcast(CH_ENEMY_Y, (int)(target.location.y * 100));
                    // Fire if possible
                    if (!rc.hasAttacked() && rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLoc.directionTo(target.location));
                    }
                    // Move toward target (scouts move through trees)
                    if (!rc.hasMoved()) {
                        tryMove(myLoc.directionTo(target.location), 15, 8);
                    }
                } else {
                    // Head toward enemy archon
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (!rc.hasMoved()) {
                        if (enemyArchons.length > 0) {
                            if (!tryMove(myLoc.directionTo(enemyArchons[0]), 15, 8)) {
                                exploreDir = randomDirection();
                                tryMove(exploreDir, 15, 8);
                            }
                        } else {
                            if (!tryMove(exploreDir, 15, 8)) {
                                exploreDir = randomDirection();
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // TANK
    // ====================================================================
    static void runTank() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        Direction exploreDir = randomDirection();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Dodge bullets
                if (!rc.hasMoved()) {
                    dodgeBullets(rc.senseNearbyBullets());
                }

                // Find and attack enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo target = getBestTarget(enemies);

                if (target != null) {
                    Direction toEnemy = myLoc.directionTo(target.location);

                    if (!rc.hasAttacked()) {
                        if (enemies.length >= 3 && rc.canFirePentadShot()) {
                            rc.firePentadShot(toEnemy);
                        } else if (enemies.length >= 2 && rc.canFireTriadShot()) {
                            rc.fireTriadShot(toEnemy);
                        } else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(toEnemy);
                        }
                    }

                    rc.broadcast(CH_ENEMY_X, (int)(target.location.x * 100));
                    rc.broadcast(CH_ENEMY_Y, (int)(target.location.y * 100));

                    if (!rc.hasMoved()) {
                        tryMove(toEnemy, 15, 5);
                    }
                } else {
                    int ex = rc.readBroadcast(CH_ENEMY_X);
                    int ey = rc.readBroadcast(CH_ENEMY_Y);
                    if (!rc.hasMoved()) {
                        if (ex != 0 && ey != 0) {
                            tryMove(myLoc.directionTo(new MapLocation(ex / 100f, ey / 100f)), 15, 5);
                        } else {
                            MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                            if (enemyArchons.length > 0) {
                                tryMove(myLoc.directionTo(enemyArchons[0]), 15, 5);
                            } else {
                                if (!tryMove(exploreDir, 15, 5)) {
                                    exploreDir = randomDirection();
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /** Prioritize: Archon > Gardener > others; then by lowest HP. */
    static RobotInfo getBestTarget(RobotInfo[] enemies) {
        if (enemies.length == 0) return null;
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RobotInfo r : enemies) {
            int score;
            if (r.type == RobotType.ARCHON)   score = 10000;
            else if (r.type == RobotType.GARDENER) score = 8000;
            else score = 3000;
            score -= (int) r.health; // lower HP = easier kill
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    /**
     * Attempt to sidestep bullets that will hit us this turn.
     * Only moves if we haven't moved yet.
     */
    static void dodgeBullets(BulletInfo[] bullets) throws GameActionException {
        if (rc.hasMoved()) return;
        for (BulletInfo b : bullets) {
            if (willCollideWithMe(b)) {
                Direction bulletDir = b.dir;
                Direction perpLeft  = bulletDir.rotateLeftDegrees(90);
                Direction perpRight = bulletDir.rotateRightDegrees(90);
                if (rc.canMove(perpLeft)) {
                    rc.move(perpLeft);
                    return;
                } else if (rc.canMove(perpRight)) {
                    rc.move(perpRight);
                    return;
                } else {
                    // Try moving away from bullet origin
                    Direction away = b.location.directionTo(rc.getLocation());
                    if (rc.canMove(away)) {
                        rc.move(away);
                        return;
                    }
                }
            }
        }
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLoc = rc.getLocation();
        Direction propagationDir = bullet.dir;
        MapLocation bulletLoc    = bullet.location;
        Direction dirToRobot     = bulletLoc.directionTo(myLoc);
        float distToRobot        = bulletLoc.distanceTo(myLoc);
        float theta              = propagationDir.radiansBetween(dirToRobot);
        if (Math.abs(theta) > Math.PI / 2) return false;
        float perpDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return perpDist <= rc.getType().bodyRadius;
    }

    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide)
            throws GameActionException {
        if (rc.hasMoved()) return false;
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        for (int i = 1; i <= checksPerSide; i++) {
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * i))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * i));
                return true;
            }
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * i))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * i));
                return true;
            }
        }
        return false;
    }
}
