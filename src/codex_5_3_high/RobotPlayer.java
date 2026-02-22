package codex_5_3_high;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team us;
    static Team them;
    static MapLocation[] initialEnemyArchons;
    static MapLocation[] initialFriendlyArchons;
    static java.util.Random rng;

    @SuppressWarnings("unused")
    public static void run(RobotController controller) throws GameActionException {
        rc = controller;
        us = rc.getTeam();
        them = us.opponent();
        rng = new java.util.Random(0xC0D35L ^ rc.getID());

        initialEnemyArchons = rc.getInitialArchonLocations(them);
        initialFriendlyArchons = rc.getInitialArchonLocations(us);

        Comms.init(rc, initialEnemyArchons);
        Nav.init(rc, rng);
        Combat.init(rc);

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
                while (true) {
                    Clock.yield();
                }
        }
    }

    static void runArchon() {
        while (true) {
            try {
                turnPreamble();

                RobotInfo[] enemies = rc.senseNearbyRobots(10f, them);
                if (enemies.length > 0) {
                    Comms.reportEnemy(enemies[0]);
                    moveAwayFromEnemies(enemies);
                } else {
                    archonDrift();
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void runGardener() {
        while (true) {
            try {
                turnPreamble();

                waterWeakestTree();

                RobotInfo[] enemies = rc.senseNearbyRobots(8f, them);
                if (enemies.length > 0) {
                    Comms.reportEnemy(enemies[0]);
                    moveAwayFromEnemies(enemies);
                } else if (!isGoodGardenSpot()) {
                    roamForGardenSpot();
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void runSoldier() {
        while (true) {
            try {
                turnPreamble();

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                BulletInfo[] bullets = rc.senseNearbyBullets();

                if (enemies.length > 0) {
                    RobotInfo target = Combat.pickTarget(enemies);
                    Comms.reportEnemy(target);
                    soldierMove(target, enemies, bullets);
                    Combat.tryShoot(target);
                } else {
                    patrolToObjective(bullets);
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void runLumberjack() {
        while (true) {
            try {
                turnPreamble();

                RobotInfo[] closeEnemies = rc.senseNearbyRobots(
                        RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS,
                        them);
                RobotInfo[] closeAllies = rc.senseNearbyRobots(
                        RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS,
                        us);

                if (closeEnemies.length > 0 && (closeAllies.length == 0 || closeEnemies.length > closeAllies.length + 1)) {
                    if (rc.canStrike()) {
                        rc.strike();
                    }
                } else {
                    TreeInfo chop = pickChopTarget();
                    if (chop != null && rc.canChop(chop.ID)) {
                        rc.chop(chop.ID);
                    } else {
                        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                        if (enemies.length > 0) {
                            RobotInfo target = Combat.pickTarget(enemies);
                            Comms.reportEnemy(target);
                            Nav.tryMoveToward(target.location);
                        } else if (chop != null) {
                            Nav.tryMoveToward(chop.location);
                        } else {
                            patrolToObjective(rc.senseNearbyBullets());
                        }
                    }
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void runScout() {
        while (true) {
            try {
                turnPreamble();

                TreeInfo shake = bestShakeTree();
                if (shake != null && rc.canShake(shake.ID)) {
                    rc.shake(shake.ID);
                }

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                BulletInfo[] bullets = rc.senseNearbyBullets();

                if (enemies.length > 0) {
                    RobotInfo target = Combat.pickScoutTarget(enemies);
                    Comms.reportEnemy(target);
                    scoutMove(target, bullets);
                    Combat.tryShoot(target);
                } else if (shake != null) {
                    Nav.tryMoveToward(shake.location);
                } else {
                    patrolToObjective(bullets);
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void runTank() {
        while (true) {
            try {
                turnPreamble();

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);
                BulletInfo[] bullets = rc.senseNearbyBullets();

                if (enemies.length > 0) {
                    RobotInfo target = Combat.pickTarget(enemies);
                    Comms.reportEnemy(target);
                    tankMove(target, bullets);
                    Combat.tryShoot(target);
                } else {
                    patrolToObjective(bullets);
                }

                BulletSpending.spendPolicy();

            } catch (Exception ignored) {
            }
            Clock.yield();
        }
    }

    static void turnPreamble() throws GameActionException {
        Comms.beginRound();
        Comms.reportAlive(rc.getType());

        TreeInfo shake = bestShakeTree();
        if (shake != null && rc.canShake(shake.ID)) {
            rc.shake(shake.ID);
        }
    }

    static void archonDrift() throws GameActionException {
        MapLocation my = rc.getLocation();
        Direction toEnemy = my.directionTo(Comms.getObjective(my));

        RobotInfo[] allies = rc.senseNearbyRobots(4f, us);
        float vx = 0f;
        float vy = 0f;
        for (int i = 0; i < allies.length; i++) {
            if (allies[i].type == RobotType.ARCHON || allies[i].type == RobotType.GARDENER) {
                float dx = my.x - allies[i].location.x;
                float dy = my.y - allies[i].location.y;
                float d2 = dx * dx + dy * dy + 0.01f;
                vx += dx / d2;
                vy += dy / d2;
            }
        }
        vx += 0.35f * toEnemy.getDeltaX(1f);
        vy += 0.35f * toEnemy.getDeltaY(1f);
        Direction drift = Nav.directionFromVector(vx, vy, toEnemy);
        Nav.tryMove(drift);
    }

    static void roamForGardenSpot() throws GameActionException {
        MapLocation my = rc.getLocation();
        MapLocation objective = Comms.getObjective(my);
        Direction dir = my.directionTo(objective);

        RobotInfo[] allies = rc.senseNearbyRobots(5f, us);
        float vx = 0f;
        float vy = 0f;
        for (int i = 0; i < allies.length; i++) {
            if (allies[i].type == RobotType.GARDENER || allies[i].type == RobotType.ARCHON) {
                float dx = my.x - allies[i].location.x;
                float dy = my.y - allies[i].location.y;
                float d2 = dx * dx + dy * dy + 0.1f;
                vx += 1.2f * dx / d2;
                vy += 1.2f * dy / d2;
            }
        }

        vx += 0.45f * dir.getDeltaX(1f);
        vy += 0.45f * dir.getDeltaY(1f);

        Nav.tryMove(Nav.directionFromVector(vx, vy, dir));
    }

    static boolean isGoodGardenSpot() throws GameActionException {
        int free = 0;
        Direction toEnemy = rc.getLocation().directionTo(Comms.getObjective(rc.getLocation()));
        for (int i = 0; i < 6; i++) {
            Direction d = toEnemy.rotateLeftDegrees(i * 60f);
            if (rc.canPlantTree(d)) {
                free++;
            }
        }
        return free >= 3;
    }

    static void soldierMove(RobotInfo target, RobotInfo[] enemies, BulletInfo[] bullets) throws GameActionException {
        MapLocation my = rc.getLocation();
        float dist = my.distanceTo(target.location);

        float vx = 0f;
        float vy = 0f;

        if (dist > 4.5f) {
            Direction d = my.directionTo(target.location);
            vx += d.getDeltaX(1f);
            vy += d.getDeltaY(1f);
        } else if (dist < 3.0f || target.type == RobotType.LUMBERJACK && dist < 4.5f) {
            Direction d = target.location.directionTo(my);
            vx += d.getDeltaX(1.3f);
            vy += d.getDeltaY(1.3f);
        }

        for (int i = 0; i < enemies.length; i++) {
            if (enemies[i].type == RobotType.LUMBERJACK) {
                float d = my.distanceTo(enemies[i].location);
                if (d < 5.5f) {
                    Direction away = enemies[i].location.directionTo(my);
                    float w = (5.8f - d) * 0.9f;
                    vx += away.getDeltaX(w);
                    vy += away.getDeltaY(w);
                }
            }
        }

        Nav.addBulletDodgeVector(bullets, my, rc.getType().bodyRadius, rc.getType().strideRadius, VectorHolder.INSTANCE);
        vx += VectorHolder.INSTANCE.x;
        vy += VectorHolder.INSTANCE.y;

        Direction best = Nav.directionFromVector(vx, vy, my.directionTo(target.location));
        Nav.tryMove(best);
    }

    static void scoutMove(RobotInfo target, BulletInfo[] bullets) throws GameActionException {
        MapLocation my = rc.getLocation();
        float dist = my.distanceTo(target.location);

        float vx = 0f;
        float vy = 0f;

        Direction to = my.directionTo(target.location);
        if (target.type == RobotType.GARDENER || target.type == RobotType.SCOUT) {
            if (dist > 2.6f) {
                vx += to.getDeltaX(1.0f);
                vy += to.getDeltaY(1.0f);
            } else if (dist < 1.7f) {
                Direction away = target.location.directionTo(my);
                vx += away.getDeltaX(1.1f);
                vy += away.getDeltaY(1.1f);
            }
        } else {
            if (dist < 5.5f) {
                Direction away = target.location.directionTo(my);
                vx += away.getDeltaX(1.2f);
                vy += away.getDeltaY(1.2f);
            }
        }

        Nav.addBulletDodgeVector(bullets, my, rc.getType().bodyRadius, rc.getType().strideRadius, VectorHolder.INSTANCE);
        vx += VectorHolder.INSTANCE.x;
        vy += VectorHolder.INSTANCE.y;

        Direction circling = to.rotateLeftDegrees(70f);
        vx += 0.5f * circling.getDeltaX(1f);
        vy += 0.5f * circling.getDeltaY(1f);

        Nav.tryMove(Nav.directionFromVector(vx, vy, to));
    }

    static void tankMove(RobotInfo target, BulletInfo[] bullets) throws GameActionException {
        MapLocation my = rc.getLocation();
        Direction to = my.directionTo(target.location);
        float dist = my.distanceTo(target.location);

        float vx = 0f;
        float vy = 0f;

        if (dist > 3.3f) {
            vx += to.getDeltaX(1f);
            vy += to.getDeltaY(1f);
        }

        Nav.addBulletDodgeVector(bullets, my, rc.getType().bodyRadius, rc.getType().strideRadius, VectorHolder.INSTANCE);
        vx += 0.6f * VectorHolder.INSTANCE.x;
        vy += 0.6f * VectorHolder.INSTANCE.y;

        Nav.tryMove(Nav.directionFromVector(vx, vy, to));
    }

    static void patrolToObjective(BulletInfo[] bullets) throws GameActionException {
        MapLocation my = rc.getLocation();
        MapLocation objective = Comms.getObjective(my);
        Direction to = my.directionTo(objective);

        Nav.addBulletDodgeVector(bullets, my, rc.getType().bodyRadius, rc.getType().strideRadius, VectorHolder.INSTANCE);
        float vx = 0.7f * to.getDeltaX(1f) + VectorHolder.INSTANCE.x;
        float vy = 0.7f * to.getDeltaY(1f) + VectorHolder.INSTANCE.y;

        if (!Nav.tryMove(Nav.directionFromVector(vx, vy, to))) {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static void moveAwayFromEnemies(RobotInfo[] enemies) throws GameActionException {
        MapLocation my = rc.getLocation();
        float vx = 0f;
        float vy = 0f;

        for (int i = 0; i < enemies.length; i++) {
            float dx = my.x - enemies[i].location.x;
            float dy = my.y - enemies[i].location.y;
            float d2 = dx * dx + dy * dy + 0.1f;
            float w = 1.8f / d2;
            if (enemies[i].type == RobotType.LUMBERJACK) {
                w *= 1.8f;
            }
            vx += dx * w;
            vy += dy * w;
        }

        Nav.tryMove(Nav.directionFromVector(vx, vy, Nav.randomDirection()));
    }

    static void waterWeakestTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(3.1f, us);
        TreeInfo weakest = null;
        for (int i = 0; i < trees.length; i++) {
            if (rc.canWater(trees[i].ID)) {
                if (weakest == null || trees[i].health < weakest.health) {
                    weakest = trees[i];
                }
            }
        }
        if (weakest != null) {
            rc.water(weakest.ID);
        }
    }

    static TreeInfo pickChopTarget() {
        try {
            MapLocation my = rc.getLocation();
            TreeInfo best = null;
            float bestScore = -9999f;

            TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, them);
            for (int i = 0; i < enemyTrees.length; i++) {
                float dist = my.distanceTo(enemyTrees[i].location);
                float score = 55f - 0.7f * dist - 0.02f * enemyTrees[i].health;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemyTrees[i];
                }
            }

            TreeInfo[] neutral = rc.senseNearbyTrees(-1, Team.NEUTRAL);
            for (int i = 0; i < neutral.length; i++) {
                float dist = my.distanceTo(neutral[i].location);
                float score = 0f;
                if (neutral[i].containedRobot != null) {
                    score += 60f;
                }
                score += neutral[i].containedBullets * 0.6f;
                score -= neutral[i].health * 0.03f;
                score -= 0.45f * dist;
                if (score > bestScore) {
                    bestScore = score;
                    best = neutral[i];
                }
            }

            return best;
        } catch (Exception ignored) {
            return null;
        }
    }

    static TreeInfo bestShakeTree() {
        try {
            TreeInfo[] neutral = rc.senseNearbyTrees(-1, Team.NEUTRAL);
            TreeInfo best = null;
            float bestScore = 0f;
            MapLocation my = rc.getLocation();
            for (int i = 0; i < neutral.length; i++) {
                if (neutral[i].containedBullets <= 0) {
                    continue;
                }
                float dist = my.distanceTo(neutral[i].location);
                float score = neutral[i].containedBullets - 0.35f * dist;
                if (score > bestScore) {
                    bestScore = score;
                    best = neutral[i];
                }
            }
            return best;
        } catch (Exception ignored) {
            return null;
        }
    }

    static final class VectorHolder {
        static final VectorHolder INSTANCE = new VectorHolder();
        float x;
        float y;
    }
}
