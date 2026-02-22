package codex_5_3_high;

import battlecode.common.*;

public strictfp class BulletSpending {

    static final float BASE_RESERVE = 80f;

    public static void spendPolicy() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (rc == null) {
            return;
        }

        switch (rc.getType()) {
            case ARCHON:
                archonSpend(rc);
                break;
            case GARDENER:
                gardenerSpend(rc);
                break;
            default:
                break;
        }

        donatePolicy(rc);
    }

    static void archonSpend(RobotController rc) throws GameActionException {
        if (rc.getBuildCooldownTurns() > 0) {
            return;
        }

        int round = rc.getRoundNum();
        int archons = Math.max(1, Comms.getCount(RobotType.ARCHON));
        int gardeners = Comms.getCount(RobotType.GARDENER);
        int soldiers = Comms.getCount(RobotType.SOLDIER);

        int desiredGardeners = 1;
        if (round > 60) desiredGardeners = 2;
        if (round > 250) desiredGardeners = 3;
        if (round > 700) desiredGardeners = 4;
        desiredGardeners = Math.max(desiredGardeners, archons);

        RobotInfo[] enemies = rc.senseNearbyRobots(9f, rc.getTeam().opponent());
        if (enemies.length > 0 && soldiers < gardeners * 2) {
            desiredGardeners = Math.min(desiredGardeners, gardeners + 1);
        }

        if (gardeners < desiredGardeners && rc.getTeamBullets() >= RobotType.GARDENER.bulletCost + 5f) {
            Direction hireDir = bestHireDirection(rc);
            if (hireDir != null && rc.canHireGardener(hireDir)) {
                rc.hireGardener(hireDir);
            }
        }
    }

    static void gardenerSpend(RobotController rc) throws GameActionException {
        if (rc.getBuildCooldownTurns() > 0) {
            return;
        }

        int round = rc.getRoundNum();
        int gardeners = Math.max(1, Comms.getCount(RobotType.GARDENER));
        int soldiers = Comms.getCount(RobotType.SOLDIER);
        int lumber = Comms.getCount(RobotType.LUMBERJACK);
        int scouts = Comms.getCount(RobotType.SCOUT);
        int tanks = Comms.getCount(RobotType.TANK);

        RobotInfo[] enemies = rc.senseNearbyRobots(8f, rc.getTeam().opponent());
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(6f, Team.NEUTRAL);
        int nearbyOwnTrees = rc.senseNearbyTrees(3.1f, rc.getTeam()).length;

        if (enemies.length > 0) {
            RobotType emergency = RobotType.SOLDIER;
            if (neutralTrees.length > 5 && lumber < gardeners + 1) {
                emergency = RobotType.LUMBERJACK;
            }
            tryBuild(rc, emergency);
            return;
        }

        if (round < 120 && soldiers < 2) {
            if (tryBuild(rc, RobotType.SOLDIER)) return;
        }

        if (scouts < 1 && round < 220) {
            if (tryBuild(rc, RobotType.SCOUT)) return;
        }

        if (neutralTrees.length >= 6 && lumber < gardeners + 1) {
            if (tryBuild(rc, RobotType.LUMBERJACK)) return;
        }

        if (soldiers < gardeners * 3 + 1) {
            if (tryBuild(rc, RobotType.SOLDIER)) return;
        }

        if (round > 380 && rc.getTeamBullets() > 300f && tanks * 3 < soldiers + 1) {
            if (tryBuild(rc, RobotType.TANK)) return;
        }

        boolean enoughArmy = soldiers >= 2 || round > 300;
        if (enoughArmy && nearbyOwnTrees < 4 && rc.getTeamBullets() >= GameConstants.BULLET_TREE_COST + 10f) {
            tryPlantTree(rc);
        }
    }

    static void donatePolicy(RobotController rc) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();

        int currentVP = rc.getTeamVictoryPoints();
        int canBuy = (int) (bullets / cost);
        if (currentVP + canBuy >= GameConstants.VICTORY_POINTS_TO_WIN) {
            rc.donate(canBuy * cost);
            return;
        }

        int roundsLeft = rc.getRoundLimit() - rc.getRoundNum();
        if (roundsLeft < 40) {
            int points = (int) (bullets / cost);
            if (points > 0) {
                rc.donate(points * cost);
            }
            return;
        }

        float reserve = BASE_RESERVE;
        if (rc.getType() == RobotType.GARDENER) reserve = 120f;
        if (rc.getRoundNum() > 1500) reserve = 60f;

        float spare = bullets - reserve;
        if (spare >= cost * 4f) {
            int points = (int) (spare / cost);
            if (points > 0) {
                rc.donate(points * cost);
            }
        }
    }

    static boolean tryBuild(RobotController rc, RobotType type) throws GameActionException {
        if (rc.getTeamBullets() < type.bulletCost) {
            return false;
        }

        Direction best = bestBuildDirection(rc);
        if (best == null) {
            return false;
        }

        if (rc.canBuildRobot(type, best)) {
            rc.buildRobot(type, best);
            return true;
        }

        for (int i = 1; i <= 8; i++) {
            Direction left = best.rotateLeftDegrees(15f * i);
            if (rc.canBuildRobot(type, left)) {
                rc.buildRobot(type, left);
                return true;
            }
            Direction right = best.rotateRightDegrees(15f * i);
            if (rc.canBuildRobot(type, right)) {
                rc.buildRobot(type, right);
                return true;
            }
        }
        return false;
    }

    static void tryPlantTree(RobotController rc) throws GameActionException {
        Direction enemyDir = rc.getLocation().directionTo(Comms.getObjective(rc.getLocation()));

        for (int i = 0; i < 6; i++) {
            Direction d = enemyDir.rotateLeftDegrees(60f * i);
            if (Math.abs(enemyDir.degreesBetween(d)) < 18f) {
                continue;
            }
            if (rc.canPlantTree(d)) {
                rc.plantTree(d);
                return;
            }
        }

        for (int i = 0; i < 12; i++) {
            Direction d = enemyDir.rotateLeftDegrees(30f * i + 15f);
            if (rc.canPlantTree(d)) {
                rc.plantTree(d);
                return;
            }
        }
    }

    static Direction bestHireDirection(RobotController rc) {
        MapLocation my = rc.getLocation();
        MapLocation objective = Comms.getObjective(my);
        Direction base = my.directionTo(objective);

        for (int i = 0; i <= 12; i++) {
            if (i == 0) {
                if (rc.canHireGardener(base)) return base;
            } else {
                Direction left = base.rotateLeftDegrees(15f * i);
                if (rc.canHireGardener(left)) return left;
                Direction right = base.rotateRightDegrees(15f * i);
                if (rc.canHireGardener(right)) return right;
            }
        }

        for (int i = 0; i < 24; i++) {
            Direction d = new Direction((float) (Math.PI * 2f * i / 24f));
            if (rc.canHireGardener(d)) return d;
        }

        return null;
    }

    static Direction bestBuildDirection(RobotController rc) {
        MapLocation my = rc.getLocation();
        MapLocation objective = Comms.getObjective(my);
        Direction base = my.directionTo(objective);

        if (rc.canBuildRobot(RobotType.SOLDIER, base)
                || rc.canBuildRobot(RobotType.SCOUT, base)
                || rc.canBuildRobot(RobotType.LUMBERJACK, base)
                || rc.canBuildRobot(RobotType.TANK, base)) {
            return base;
        }

        for (int i = 1; i <= 12; i++) {
            Direction left = base.rotateLeftDegrees(15f * i);
            if (rc.canBuildRobot(RobotType.SOLDIER, left)
                    || rc.canBuildRobot(RobotType.SCOUT, left)
                    || rc.canBuildRobot(RobotType.LUMBERJACK, left)
                    || rc.canBuildRobot(RobotType.TANK, left)) {
                return left;
            }
            Direction right = base.rotateRightDegrees(15f * i);
            if (rc.canBuildRobot(RobotType.SOLDIER, right)
                    || rc.canBuildRobot(RobotType.SCOUT, right)
                    || rc.canBuildRobot(RobotType.LUMBERJACK, right)
                    || rc.canBuildRobot(RobotType.TANK, right)) {
                return right;
            }
        }

        return null;
    }
}
