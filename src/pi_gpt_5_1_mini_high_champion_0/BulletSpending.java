package pi_gpt_5_1_mini_high_champion_0;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;
    private static final float SAFE_ARC_DEGREES = 120f;
    private static final float SAFE_LOOKAHEAD_DISTANCE = 6f;
    private static final float HIRE_DIRECTION_STEP_DEGREES = 10f;

    private static Direction cachedSafeHireDirection;
    private static int cachedSafeHireRound = -1;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static Direction computeSafeHireDirection() throws GameActionException {
        int currentRound = rc.getRoundNum();
        if (currentRound == cachedSafeHireRound) {
            return cachedSafeHireDirection;
        }
        cachedSafeHireDirection = scanForSafeHireDirection();
        cachedSafeHireRound = currentRound;
        return cachedSafeHireDirection;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire soldier -> donate.
        if (rc.getType() == RobotType.ARCHON) {
            Direction safeDir = computeSafeHireDirection();
            if (safeDir != null && shouldHireGardener(safeDir)) {
                rc.hireGardener(safeDir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            dir = randomDirection();
            if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static Direction scanForSafeHireDirection() throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(SAFE_LOOKAHEAD_DISTANCE);
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(SAFE_LOOKAHEAD_DISTANCE);
        int steps = (int)(360f / HIRE_DIRECTION_STEP_DEGREES);
        float halfArcRadians = (float)Math.toRadians(SAFE_ARC_DEGREES / 2f);

        for (int i = 0; i < steps; i++) {
            Direction candidate = new Direction((float)Math.toRadians(i * HIRE_DIRECTION_STEP_DEGREES));
            if (!rc.canHireGardener(candidate)) {
                continue;
            }
            if (isArcBlocked(candidate, myLocation, nearbyRobots, nearbyTrees, halfArcRadians)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean isArcBlocked(Direction candidate, MapLocation origin, RobotInfo[] robots, TreeInfo[] trees, float halfArcRadians) {
        for (RobotInfo robot : robots) {
            if (robot.getID() == rc.getID()) {
                continue;
            }
            if (isObstacleInArc(origin, robot.getLocation(), candidate, halfArcRadians)) {
                return true;
            }
        }
        for (TreeInfo tree : trees) {
            if (isObstacleInArc(origin, tree.getLocation(), candidate, halfArcRadians)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isObstacleInArc(MapLocation origin, MapLocation obstacleLocation, Direction candidate, float halfArcRadians) {
        Direction toObstacle = origin.directionTo(obstacleLocation);
        float angleDiff = Math.abs(candidate.radiansBetween(toObstacle));
        if (angleDiff > halfArcRadians) {
            return false;
        }
        float distance = origin.distanceTo(obstacleLocation);
        return distance <= SAFE_LOOKAHEAD_DISTANCE;
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < .01;
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < .01;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .01;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        float donateAmount = bullets - BULLET_RESERVE;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            return pointsToBuy * cost;
        }
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
