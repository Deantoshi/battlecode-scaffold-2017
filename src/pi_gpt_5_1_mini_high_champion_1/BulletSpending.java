package pi_gpt_5_1_mini_high_champion_1;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;
    private static final float SAFE_ARC_DEGREES = 120f;
    private static final float SAFE_LOOKAHEAD_DISTANCE = 6f;
    private static final float HIRE_DIRECTION_STEP_DEGREES = 10f;
    private static final int SAFE_ARC_STABILITY_ROUNDS = 3;
    private static final float DIRECTION_TOLERANCE_RADIANS = 0.01f;
    public static final int PENDING_GARDENER_SLOT_CHANNEL_DX = 4;
    public static final int PENDING_GARDENER_SLOT_CHANNEL_DY = 5;
    public static final int PENDING_GARDENER_SLOT_CHANNEL_FLAG = 6;

    private static Direction cachedSafeHireDirection;
    private static int cachedSafeHireRound = -1;
    private static final SafeArcTimer safeArcTimer = new SafeArcTimer(SAFE_ARC_STABILITY_ROUNDS);

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static Direction computeSafeHireDirection() throws GameActionException {
        int currentRound = rc.getRoundNum();
        if (currentRound == cachedSafeHireRound) {
            return cachedSafeHireDirection;
        }
        cachedSafeHireRound = currentRound;
        Direction candidate = scanForSafeHireDirection();
        safeArcTimer.tick(candidate);
        cachedSafeHireDirection = safeArcTimer.getStableDirection();
        return cachedSafeHireDirection;
    }

    public static Direction getPendingSafeHireDirection() {
        return safeArcTimer.getPendingDirection();
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire soldier -> donate.
        if (rc.getType() == RobotType.ARCHON) {
            Direction safeDir = computeSafeHireDirection();
            Direction pendingDir = readPendingGardenerDirectionBroadcast();
            if (safeDir != null && pendingDir != null && directionsMatch(safeDir, pendingDir) && shouldHireGardener(safeDir)) {
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

    private static Direction readPendingGardenerDirectionBroadcast() throws GameActionException {
        int flag = rc.readBroadcast(PENDING_GARDENER_SLOT_CHANNEL_FLAG);
        if (flag == 0) {
            return null;
        }
        float dx = Float.intBitsToFloat(rc.readBroadcast(PENDING_GARDENER_SLOT_CHANNEL_DX));
        float dy = Float.intBitsToFloat(rc.readBroadcast(PENDING_GARDENER_SLOT_CHANNEL_DY));
        return new Direction(dx, dy);
    }

    private static boolean directionsMatch(Direction a, Direction b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.radiansBetween(b)) < DIRECTION_TOLERANCE_RADIANS;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    private static class SafeArcTimer {
        private final int requiredRounds;
        private int consecutiveRounds;
        private Direction lastCandidate;
        private Direction pendingDirection;
        private Direction stableDirection;

        SafeArcTimer(int requiredRounds) {
            this.requiredRounds = requiredRounds;
        }

        void tick(Direction candidate) {
            pendingDirection = candidate;
            if (candidate == null) {
                lastCandidate = null;
                stableDirection = null;
                consecutiveRounds = 0;
                return;
            }
            if (lastCandidate != null && directionsMatch(lastCandidate, candidate)) {
                consecutiveRounds++;
            } else {
                lastCandidate = candidate;
                consecutiveRounds = 1;
                stableDirection = null;
            }
            if (consecutiveRounds >= requiredRounds) {
                stableDirection = candidate;
            }
        }

        Direction getPendingDirection() {
            return pendingDirection;
        }

        Direction getStableDirection() {
            return stableDirection;
        }
    }
}
