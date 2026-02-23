package claude_sonet_4_6_high;
import battlecode.common.*;

/**
 * ALL bullet-spending actions live here and are called only from spendPolicy().
 * Forbidden elsewhere: rc.donate, rc.hireGardener, rc.buildRobot, rc.plantTree.
 */
public class BulletSpending {
    static RobotController rc;

    // Communication channels
    static final int CH_GARDENER_COUNT = 4;  // how many gardeners we've hired total

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        switch (rc.getType()) {
            case ARCHON:   archonSpend();   break;
            case GARDENER: gardenerSpend(); break;
            default: break;
        }
    }

    // ---- ARCHON -------------------------------------------------------
    private static void archonSpend() throws GameActionException {
        int gardenerCount = rc.readBroadcast(CH_GARDENER_COUNT);
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();

        // Hire gardeners: target 2 gardeners early game, up to 3 by mid-game
        int maxGardeners = round < 200 ? 2 : 3;
        if (gardenerCount < maxGardeners && bullets >= 100f) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction((float) i * (float) Math.PI / 4);
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    rc.broadcast(CH_GARDENER_COUNT, gardenerCount + 1);
                    break;
                }
            }
        }

        // Donate excess bullets to VP (keep a reserve)
        donateExcess(150f);
    }

    // ---- GARDENER -----------------------------------------------------
    private static void gardenerSpend() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int round = rc.getRoundNum();

        // Count nearby ally robots to gauge local army size
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int lumberjacks = 0, soldiers = 0, scouts = 0;
        for (RobotInfo r : allies) {
            if (r.type == RobotType.LUMBERJACK) lumberjacks++;
            else if (r.type == RobotType.SOLDIER) soldiers++;
            else if (r.type == RobotType.SCOUT) scouts++;
        }

        // Count nearby team trees
        TreeInfo[] myTrees = rc.senseNearbyTrees(-1, rc.getTeam());
        int treeCount = myTrees.length;

        // Plant a tree if fewer than 5 trees nearby
        if (treeCount < 5) {
            for (int i = 0; i < 6; i++) {
                Direction dir = new Direction((float) i * (float) Math.PI / 3);
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    break;
                }
            }
        }

        // Build units: 1 lumberjack first, then prioritize soldiers
        if (lumberjacks == 0 && round < 150) {
            buildRobot(RobotType.LUMBERJACK);
        } else if (soldiers < 6) {
            buildRobot(RobotType.SOLDIER);
        } else if (scouts < 1) {
            buildRobot(RobotType.SCOUT);
        } else {
            buildRobot(RobotType.SOLDIER);
        }

        // Donate excess
        donateExcess(200f);
    }

    private static boolean buildRobot(RobotType type) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction((float) i * (float) Math.PI / 4);
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                return true;
            }
        }
        return false;
    }

    private static void donateExcess(float reserve) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        int round = rc.getRoundNum();

        // In very late game, donate almost everything
        if (round > 2500) reserve = 50f;
        else if (round > 2000) reserve = Math.min(reserve, 100f);

        float available = bullets - reserve;
        if (available >= cost && cost > 0) {
            int pts = (int) (available / cost);
            if (pts > 0) {
                rc.donate(pts * cost);
            }
        }
    }
}
