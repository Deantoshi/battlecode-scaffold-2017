package big_pickle;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team us;
    static Team them;

    public static Random rand;

    static MapLocation[] initialEnemyArchons;
    static Direction wanderDirection;

    // Gardener-local state (static is per robot instance in Battlecode runtime).
    public static boolean gardenerSettled = false;
    public static Direction gardenerPlantDirection = null;

    @SuppressWarnings("unused")
    public static void run(RobotController controller) throws GameActionException {
        rc = controller;
        us = rc.getTeam();
        them = us.opponent();
        rand = new Random(rc.getID() * 17L + rc.getRoundNum() * 131L);
        initialEnemyArchons = rc.getInitialArchonLocations(them);
        wanderDirection = Utils.randomDirection(rand);

        Comms.init(rc);
        Nav.init(rc, rand);
        BulletSpending.init(rc);

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
            case SCOUT:
                runScout();
                break;
            case TANK:
                runTank();
                break;
            default:
                runSoldier();
        }
    }

    private static void runArchon() throws GameActionException {
        while (true) {
            try {
                commonTurnStart();

                RobotInfo[] closeEnemies = rc.senseNearbyRobots(9f, them);
                if (closeEnemies.length > 0) {
                    RobotInfo threat = Utils.pickBestEnemy(closeEnemies, rc.getLocation());
                    Comms.reportEnemy(threat);
                    if (!rc.hasMoved()) {
                        Direction retreat = threat.location.directionTo(rc.getLocation());
                        Nav.moveInDirection(retreat, true);
                    }
                } else {
                    if (rand.nextFloat() < 0.17f) {
                        wanderDirection = wanderDirection.rotateLeftDegrees(30f - rand.nextFloat() * 60f);
                    }
                    if (!rc.hasMoved()) {
                        Nav.wander(wanderDirection);
                    }
                }

                BulletSpending.spendPolicy();
            } catch (Exception e) {
                System.out.println("Archon exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void runGardener() throws GameActionException {
        gardenerSettled = false;
        gardenerPlantDirection = Utils.randomDirection(rand);

        while (true) {
            try {
                commonTurnStart();

                if (!gardenerSettled && canSettleHere()) {
                    gardenerSettled = true;
                }

                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(7f, them);
                if (nearbyEnemies.length > 0 && !rc.hasMoved()) {
                    RobotInfo threat = Utils.pickBestEnemy(nearbyEnemies, rc.getLocation());
                    Direction retreat = threat.location.directionTo(rc.getLocation());
                    Nav.moveInDirection(retreat, true);
                }

                if (!gardenerSettled && !rc.hasMoved()) {
                    MapLocation settleTarget = pickSettleTarget();
                    if (settleTarget != null) {
                        Nav.moveToward(settleTarget, true);
                    } else {
                        Nav.wander(wanderDirection);
                    }
                    if (canSettleHere()) {
                        gardenerSettled = true;
                    }
                }

                waterLowestHealthTree();
                BulletSpending.spendPolicy();
            } catch (Exception e) {
                System.out.println("Gardener exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void runSoldier() throws GameActionException {
        while (true) {
            try {
                commonTurnStart();

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                if (enemies.length > 0) {
                    RobotInfo target = Utils.pickBestEnemy(enemies, rc.getLocation());
                    Comms.reportEnemy(target);
                    soldierMove(target);
                } else {
                    moveToIntelOrExplore();
                }

                soldierFire();
            } catch (Exception e) {
                System.out.println("Soldier exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void runTank() throws GameActionException {
        while (true) {
            try {
                commonTurnStart();

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                if (enemies.length > 0) {
                    RobotInfo target = Utils.pickBestEnemy(enemies, rc.getLocation());
                    Comms.reportEnemy(target);

                    if (!rc.hasMoved()) {
                        float dist = rc.getLocation().distanceTo(target.location);
                        if (dist > 4.1f) {
                            Nav.moveToward(target.location, true);
                        } else {
                            Direction strafe = rc.getLocation().directionTo(target.location)
                                    .rotateLeftDegrees(rand.nextBoolean() ? 60f : -60f);
                            Nav.moveInDirection(strafe, true);
                        }
                    }
                } else {
                    moveToIntelOrExplore();
                }

                tankFire();
            } catch (Exception e) {
                System.out.println("Tank exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void runLumberjack() throws GameActionException {
        while (true) {
            try {
                commonTurnStart();

                float strikeRadius = RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
                RobotInfo[] enemiesInStrike = rc.senseNearbyRobots(strikeRadius, them);
                if (enemiesInStrike.length > 0 && rc.canStrike() && shouldStrike()) {
                    rc.strike();
                } else {
                    TreeInfo tree = pickBestTreeTarget();
                    RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);

                    if (enemies.length > 0) {
                        RobotInfo target = Utils.pickBestEnemy(enemies, rc.getLocation());
                        Comms.reportEnemy(target);
                        if (!rc.hasMoved()) {
                            Nav.moveToward(target.location, true);
                        }
                    } else if (tree != null) {
                        if (rc.canChop(tree.ID)) {
                            rc.chop(tree.ID);
                        } else if (!rc.hasMoved()) {
                            Nav.moveToward(tree.location, true);
                        }
                    } else {
                        moveToIntelOrExplore();
                    }
                }
            } catch (Exception e) {
                System.out.println("Lumberjack exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void runScout() throws GameActionException {
        while (true) {
            try {
                commonTurnStart();

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                if (enemies.length > 0) {
                    RobotInfo target = pickScoutTarget(enemies);
                    Comms.reportEnemy(target);

                    float dist = rc.getLocation().distanceTo(target.location);
                    if (!rc.hasMoved()) {
                        if (isDangerous(target.type) && dist < 5.5f) {
                            Direction away = target.location.directionTo(rc.getLocation());
                            Nav.moveInDirection(away, true);
                        } else if (dist > 4f) {
                            Nav.moveToward(target.location, true);
                        } else {
                            Direction strafe = rc.getLocation().directionTo(target.location)
                                    .rotateLeftDegrees(rand.nextBoolean() ? 85f : -85f);
                            Nav.moveInDirection(strafe, true);
                        }
                    }

                    if (rc.canFireSingleShot()) {
                        Direction fire = rc.getLocation().directionTo(target.location);
                        float distance = rc.getLocation().distanceTo(target.location);
                        if (isSafeToFire(fire, distance, 1)) {
                            rc.fireSingleShot(fire);
                        }
                    }
                } else {
                    TreeInfo shakeTree = pickTreeToShake();
                    if (shakeTree != null && rc.canShake(shakeTree.ID)) {
                        rc.shake(shakeTree.ID);
                    } else {
                        moveToIntelOrExplore();
                    }
                }
            } catch (Exception e) {
                System.out.println("Scout exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    private static void commonTurnStart() throws GameActionException {
        Comms.reportUnitCount(rc.getType());
        shakeBestTreeIfPossible();

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
        if (enemies.length > 0) {
            Comms.reportEnemy(Utils.pickBestEnemy(enemies, rc.getLocation()));
        }
    }

    private static void moveToIntelOrExplore() throws GameActionException {
        if (rc.hasMoved()) {
            return;
        }

        MapLocation intel = Comms.getEnemyLocation();
        if (intel == null) {
            intel = pickClosestInitialEnemyArchon();
        }

        if (intel != null) {
            if (!Nav.moveToward(intel, true)) {
                Nav.wander(wanderDirection);
            }
        } else {
            Nav.wander(wanderDirection);
        }
    }

    private static void soldierMove(RobotInfo target) throws GameActionException {
        if (rc.hasMoved()) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        float dist = myLoc.distanceTo(target.location);
        Direction toTarget = myLoc.directionTo(target.location);

        if (target.type == RobotType.LUMBERJACK && dist < 3.8f) {
            Nav.moveInDirection(toTarget.opposite(), true);
            return;
        }

        if (dist > 4.6f) {
            Nav.moveToward(target.location, true);
            return;
        }

        Direction strafe = toTarget.rotateLeftDegrees(rand.nextBoolean() ? 70f : -70f);
        Nav.moveInDirection(strafe, true);
    }

    private static void soldierFire() throws GameActionException {
        if (rc.hasAttacked()) {
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
        if (enemies.length == 0) {
            return;
        }

        RobotInfo target = Utils.pickBestEnemy(enemies, rc.getLocation());
        Direction fireDir = rc.getLocation().directionTo(target.location);
        float dist = rc.getLocation().distanceTo(target.location);
        int nearbyEnemyCount = countEnemiesNear(target.location, 2.6f, enemies);

        if (rc.canFirePentadShot()
                && rc.getTeamBullets() > 80f
                && dist < 3.3f
                && nearbyEnemyCount >= 2
                && isSafeToFire(fireDir, dist, 3)) {
            rc.firePentadShot(fireDir);
            return;
        }

        if (rc.canFireTriadShot()
                && rc.getTeamBullets() > 30f
                && dist < 5.2f
                && (nearbyEnemyCount >= 2 || target.type == RobotType.TANK)
                && isSafeToFire(fireDir, dist, 2)) {
            rc.fireTriadShot(fireDir);
            return;
        }

        if (rc.canFireSingleShot() && isSafeToFire(fireDir, dist, 1)) {
            rc.fireSingleShot(fireDir);
        }
    }

    private static void tankFire() throws GameActionException {
        if (rc.hasAttacked()) {
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
        if (enemies.length == 0) {
            return;
        }

        RobotInfo target = Utils.pickBestEnemy(enemies, rc.getLocation());
        Direction fireDir = rc.getLocation().directionTo(target.location);
        float dist = rc.getLocation().distanceTo(target.location);

        if (rc.canFirePentadShot() && dist < 5.4f && isSafeToFire(fireDir, dist, 3)) {
            rc.firePentadShot(fireDir);
            return;
        }

        if (rc.canFireTriadShot() && isSafeToFire(fireDir, dist, 2)) {
            rc.fireTriadShot(fireDir);
            return;
        }

        if (rc.canFireSingleShot() && isSafeToFire(fireDir, dist, 1)) {
            rc.fireSingleShot(fireDir);
        }
    }

    private static boolean isSafeToFire(Direction dir, float targetDistance, int pattern) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        float[] offsets;
        if (pattern == 3) {
            offsets = new float[]{-30f, -15f, 0f, 15f, 30f};
        } else if (pattern == 2) {
            offsets = new float[]{-20f, 0f, 20f};
        } else {
            offsets = new float[]{0f};
        }

        RobotInfo[] allies = rc.senseNearbyRobots(targetDistance + 1.5f, us);
        TreeInfo[] trees = rc.senseNearbyTrees(targetDistance + 1.5f);

        for (float offset : offsets) {
            Direction shotDir = offset == 0f
                    ? dir
                    : (offset > 0f ? dir.rotateLeftDegrees(offset) : dir.rotateRightDegrees(-offset));

            for (RobotInfo ally : allies) {
                if (ally.ID == rc.getID()) {
                    continue;
                }
                float allyDist = myLoc.distanceTo(ally.location);
                if (allyDist >= targetDistance - 0.05f) {
                    continue;
                }
                if (Utils.rayIntersectsCircle(myLoc, shotDir, ally.location, ally.type.bodyRadius, targetDistance)) {
                    return false;
                }
            }

            for (TreeInfo tree : trees) {
                float treeDist = myLoc.distanceTo(tree.location);
                if (treeDist >= targetDistance - 0.05f) {
                    continue;
                }
                if (Utils.rayIntersectsCircle(myLoc, shotDir, tree.location, tree.radius, targetDistance)) {
                    if (tree.team == us || tree.team == Team.NEUTRAL) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static int countEnemiesNear(MapLocation center, float radius, RobotInfo[] enemies) {
        int count = 0;
        for (RobotInfo enemy : enemies) {
            if (center.distanceTo(enemy.location) <= radius) {
                count++;
            }
        }
        return count;
    }

    private static boolean shouldStrike() throws GameActionException {
        float strikeRadius = RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
        RobotInfo[] enemies = rc.senseNearbyRobots(strikeRadius, them);
        RobotInfo[] allies = rc.senseNearbyRobots(strikeRadius, us);

        int allyCount = 0;
        for (RobotInfo ally : allies) {
            if (ally.ID != rc.getID()) {
                allyCount++;
            }
        }

        return enemies.length > allyCount;
    }

    private static TreeInfo pickBestTreeTarget() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1);
        TreeInfo best = null;
        float bestScore = -99999f;

        for (TreeInfo tree : trees) {
            if (tree.team == us) {
                continue;
            }

            float score = 0f;
            if (tree.containedRobot != null) {
                score += 80f;
            }
            score += tree.containedBullets * 3f;
            if (tree.team == them) {
                score += 45f;
            }
            score -= rc.getLocation().distanceTo(tree.location) * 1.4f;

            if (score > bestScore) {
                bestScore = score;
                best = tree;
            }
        }

        return best;
    }

    private static TreeInfo pickTreeToShake() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1);
        TreeInfo best = null;
        float bestDist = 9999f;

        for (TreeInfo tree : trees) {
            if (tree.containedBullets <= 0) {
                continue;
            }
            float dist = rc.getLocation().distanceTo(tree.location);
            if (dist < bestDist) {
                bestDist = dist;
                best = tree;
            }
        }

        return best;
    }

    private static void waterLowestHealthTree() throws GameActionException {
        if (rc.getType() != RobotType.GARDENER) {
            return;
        }

        TreeInfo[] trees = rc.senseNearbyTrees(2.5f, us);
        TreeInfo toWater = null;
        float minHealth = 99999f;

        for (TreeInfo tree : trees) {
            if (!rc.canWater(tree.ID)) {
                continue;
            }
            if (tree.health < minHealth) {
                minHealth = tree.health;
                toWater = tree;
            }
        }

        if (toWater != null) {
            rc.water(toWater.ID);
        }
    }

    private static void shakeBestTreeIfPossible() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.5f);
        TreeInfo best = null;
        int bullets = 0;

        for (TreeInfo tree : trees) {
            if (tree.containedBullets > bullets && rc.canShake(tree.ID)) {
                bullets = tree.containedBullets;
                best = tree;
            }
        }

        if (best != null) {
            rc.shake(best.ID);
        }
    }

    private static boolean canSettleHere() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.senseNearbyRobots(6f, them).length > 0) {
            return false;
        }

        int openSpots = countOpenPlantSpots(myLoc);
        if (openSpots < 4) {
            return false;
        }

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(6f, us);
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == RobotType.GARDENER && ally.ID != rc.getID()) {
                return false;
            }
        }

        return true;
    }

    private static MapLocation pickSettleTarget() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        float bestScore = -99999f;

        for (int i = 0; i < 12; i++) {
            Direction dir = new Direction((float) (i * Math.PI / 6f));
            MapLocation candidate = myLoc.add(dir, 2.2f);

            if (!rc.onTheMap(candidate, 1f)) {
                continue;
            }

            int openSpots = countOpenPlantSpots(candidate);
            float score = openSpots * 8f;

            RobotInfo[] closeAllies = rc.senseNearbyRobots(candidate, 4.5f, us);
            for (RobotInfo ally : closeAllies) {
                if (ally.type == RobotType.GARDENER) {
                    score -= 10f;
                }
            }

            RobotInfo[] closeEnemies = rc.senseNearbyRobots(candidate, 6f, them);
            score -= closeEnemies.length * 14f;

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static int countOpenPlantSpots(MapLocation center) throws GameActionException {
        int open = 0;
        float dist = RobotType.GARDENER.bodyRadius + GameConstants.GENERAL_SPAWN_OFFSET + GameConstants.BULLET_TREE_RADIUS;

        for (int i = 0; i < 6; i++) {
            Direction dir = gardenerPlantDirection.rotateLeftDegrees(i * 60f);
            MapLocation spot = center.add(dir, dist);
            if (!rc.onTheMap(spot, GameConstants.BULLET_TREE_RADIUS)) {
                continue;
            }
            if (!rc.isCircleOccupiedExceptByThisRobot(spot, GameConstants.BULLET_TREE_RADIUS)) {
                open++;
            }
        }

        return open;
    }

    private static MapLocation pickClosestInitialEnemyArchon() {
        if (initialEnemyArchons == null || initialEnemyArchons.length == 0) {
            return null;
        }

        MapLocation myLoc = rc.getLocation();
        MapLocation best = initialEnemyArchons[0];
        float bestDist = myLoc.distanceTo(best);

        for (int i = 1; i < initialEnemyArchons.length; i++) {
            float dist = myLoc.distanceTo(initialEnemyArchons[i]);
            if (dist < bestDist) {
                bestDist = dist;
                best = initialEnemyArchons[i];
            }
        }

        return best;
    }

    private static RobotInfo pickScoutTarget(RobotInfo[] enemies) {
        RobotInfo best = enemies[0];
        float bestScore = -99999f;

        MapLocation myLoc = rc.getLocation();
        for (RobotInfo enemy : enemies) {
            float score;
            switch (enemy.type) {
                case GARDENER:
                    score = 70f;
                    break;
                case SCOUT:
                    score = 55f;
                    break;
                case SOLDIER:
                    score = 35f;
                    break;
                case LUMBERJACK:
                    score = 20f;
                    break;
                case ARCHON:
                    score = 25f;
                    break;
                case TANK:
                    score = 15f;
                    break;
                default:
                    score = 10f;
            }

            score -= myLoc.distanceTo(enemy.location) * 2f;
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }

        return best;
    }

    private static boolean isDangerous(RobotType type) {
        return type == RobotType.SOLDIER || type == RobotType.LUMBERJACK || type == RobotType.TANK;
    }
}
