package big_pickle;

import battlecode.common.*;

public class BulletSpending {
    private static RobotController rc;

    private static final float EARLY_GAME_RESERVE = 140f;
    private static final float MID_GAME_RESERVE = 220f;

    public static void init(RobotController controller) {
        rc = controller;
    }

    public static void spendPolicy() throws GameActionException {
        donateIfWinningOrEndgame();

        if (rc.getType() == RobotType.ARCHON) {
            archonSpend();
            return;
        }

        if (rc.getType() == RobotType.GARDENER) {
            gardenerSpend();
            return;
        }

        donateExcess();
    }

    private static void archonSpend() throws GameActionException {
        int round = rc.getRoundNum();
        int gardeners = Comms.getUnitCount(RobotType.GARDENER);
        int archons = Math.max(1, Comms.getUnitCount(RobotType.ARCHON));
        int soldiers = Comms.getUnitCount(RobotType.SOLDIER);

        int desiredGardeners;
        if (round < 250) {
            desiredGardeners = archons;
        } else if (round < 900) {
            desiredGardeners = archons + 1;
        } else {
            desiredGardeners = archons + 2;
        }

        desiredGardeners = Math.min(desiredGardeners, 6);

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(8f, rc.getTeam().opponent());
        boolean inDanger = nearbyEnemies.length > 0;

        boolean armyTooSmall = soldiers < Math.max(2, gardeners);
        boolean shouldHoldForArmy = gardeners >= archons && armyTooSmall && round > 120;

        if ((gardeners < desiredGardeners || gardeners == 0)
                && !shouldHoldForArmy
                && rc.getTeamBullets() >= RobotType.GARDENER.bulletCost
                && (!inDanger || gardeners == 0)) {
            Direction dir = findBestHireDirection();
            if (dir != null && rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        }

        donateExcess();
    }

    private static void gardenerSpend() throws GameActionException {
        int round = rc.getRoundNum();
        int soldiers = Comms.getUnitCount(RobotType.SOLDIER);
        int lumberjacks = Comms.getUnitCount(RobotType.LUMBERJACK);
        int scouts = Comms.getUnitCount(RobotType.SCOUT);
        int tanks = Comms.getUnitCount(RobotType.TANK);

        RobotInfo[] enemies = rc.senseNearbyRobots(7f, rc.getTeam().opponent());
        if (enemies.length > 0) {
            if (tryBuild(RobotType.SOLDIER)) {
                return;
            }
            if (tryBuild(RobotType.LUMBERJACK)) {
                return;
            }
        }

        TreeInfo[] neutralTrees = rc.senseNearbyTrees(6f, Team.NEUTRAL);
        int desiredLumberjacks = neutralTrees.length >= 2 ? Math.max(1, soldiers / 3) : 0;

        if (round < 90 && scouts < 1 && neutralTrees.length > 0 && tryBuild(RobotType.SCOUT)) {
            return;
        }

        int desiredSoldiers = 3 + round / 90;
        if (round > 180) {
            desiredSoldiers = Math.max(desiredSoldiers, gardenersForArmySupport() * 2);
        }

        if (soldiers < desiredSoldiers && tryBuild(RobotType.SOLDIER)) {
            return;
        }

        if (lumberjacks < desiredLumberjacks && rc.getTeamBullets() >= RobotType.LUMBERJACK.bulletCost + 20f) {
            if (tryBuild(RobotType.LUMBERJACK)) {
                return;
            }
        }

        if (round > 500 && rc.getTeamBullets() > 350f && tanks < round / 450) {
            if (tryBuild(RobotType.TANK)) {
                return;
            }
        }

        if (RobotPlayer.gardenerSettled) {
            int treeCount = rc.getTreeCount();
            int desiredTrees = round < 350 ? 2 : 4;

            if (treeCount < desiredTrees
                    && soldiers >= 4
                    && rc.getTeamBullets() >= 150f
                    && tryPlantTree()) {
                return;
            }
        }

        donateExcess();
    }

    private static int gardenersForArmySupport() throws GameActionException {
        int gardeners = Comms.getUnitCount(RobotType.GARDENER);
        return Math.max(1, gardeners);
    }

    private static void donateIfWinningOrEndgame() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        float currentVP = rc.getTeamVictoryPoints();
        float neededVP = GameConstants.VICTORY_POINTS_TO_WIN - currentVP;

        if (neededVP > 0f) {
            float bulletsToWin = (float) Math.ceil(neededVP) * vpCost;
            if (bullets >= bulletsToWin) {
                float donate = (float) Math.floor(bullets / vpCost) * vpCost;
                if (donate >= vpCost) {
                    rc.donate(donate);
                    return;
                }
            }
        }

        if (rc.getRoundNum() > GameConstants.GAME_DEFAULT_ROUNDS - 20) {
            float donate = (float) Math.floor(bullets / vpCost) * vpCost;
            if (donate >= vpCost) {
                rc.donate(donate);
            }
        }
    }

    private static void donateExcess() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve;
        if (round < 350) {
            reserve = EARLY_GAME_RESERVE;
        } else if (round < 1200) {
            reserve = MID_GAME_RESERVE;
        } else if (round < 2200) {
            reserve = 150f;
        } else {
            reserve = 80f;
        }

        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        float excess = bullets - reserve;

        if (excess < vpCost) {
            return;
        }

        float donate = (float) Math.floor(excess / vpCost) * vpCost;
        if (donate >= vpCost) {
            rc.donate(donate);
        }
    }

    private static boolean tryBuild(RobotType type) throws GameActionException {
        if (rc.getTeamBullets() < type.bulletCost) {
            return false;
        }

        MapLocation enemy = Comms.getEnemyLocation();
        Direction base = enemy != null
                ? rc.getLocation().directionTo(enemy)
                : Utils.randomDirection(RobotPlayer.rand);

        for (int i = 0; i < 24; i++) {
            float degrees = i * 15f;
            Direction dir = (i % 2 == 0)
                    ? base.rotateLeftDegrees(degrees)
                    : base.rotateRightDegrees(degrees);
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                return true;
            }
        }

        return false;
    }

    private static boolean tryPlantTree() throws GameActionException {
        if (rc.getTeamBullets() < GameConstants.BULLET_TREE_COST) {
            return false;
        }

        Direction base = RobotPlayer.gardenerPlantDirection;
        if (base == null) {
            base = Utils.randomDirection(RobotPlayer.rand);
        }

        for (int i = 0; i < 6; i++) {
            Direction dir = base.rotateLeftDegrees(i * 60f);
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                return true;
            }
        }

        return false;
    }

    private static Direction findBestHireDirection() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction towardEnemy = Utils.randomDirection(RobotPlayer.rand);
        MapLocation enemy = Comms.getEnemyLocation();
        if (enemy != null && myLoc.distanceTo(enemy) > 0.1f) {
            towardEnemy = myLoc.directionTo(enemy);
        }

        Direction bestDir = null;
        float bestScore = -99999f;

        for (int i = 0; i < 24; i++) {
            float degrees = i * 15f;
            Direction dir = (i % 2 == 0)
                    ? towardEnemy.rotateLeftDegrees(degrees)
                    : towardEnemy.rotateRightDegrees(degrees);

            if (!rc.canHireGardener(dir)) {
                continue;
            }

            MapLocation spawnLoc = myLoc.add(dir, rc.getType().bodyRadius + GameConstants.GENERAL_SPAWN_OFFSET + RobotType.GARDENER.bodyRadius);
            float score = scoreSpawnOpenArea(spawnLoc);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static float scoreSpawnOpenArea(MapLocation center) throws GameActionException {
        float score = 0f;

        for (int i = 0; i < 8; i++) {
            Direction d = new Direction((float) (i * Math.PI / 4f));
            MapLocation probe = center.add(d, 2.6f);
            if (!rc.onTheMap(probe, 1f)) {
                score -= 3f;
                continue;
            }
            if (rc.isCircleOccupiedExceptByThisRobot(probe, 1f)) {
                score -= 2f;
            } else {
                score += 1.5f;
            }
        }

        return score;
    }
}
