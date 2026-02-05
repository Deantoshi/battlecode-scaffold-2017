package claude_opus_4_5_champion_0;
import battlecode.common.*;

/**
 * Scout Swarm Harass Strategy - Centralized Bullet Spending
 * 
 * Philosophy: Mass cheap scouts to harass enemy gardeners and economy
 * while collecting bullets from neutral trees. Win through economic
 * disruption and opportunistic VP pushes.
 */
public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 200f;  // Lower reserve for faster spending

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: donate -> hire gardener -> plant tree -> build scout
        if (rc.getType() == RobotType.ARCHON) {
            // Moderate VP donation when bullets > 500
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            
            Direction dir = findBuildDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Priority: build scouts for harassment, then plant some trees
            Direction dir = findBuildDirection();
            if (shouldBuildScout(dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
                return; // Scout built, done for this turn
            }
            
            dir = findBuildDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            
            // Moderate VP donation when bullets > 500 (gardeners can also donate)
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) throws GameActionException {
        if (!rc.canHireGardener(dir)) {
            return false;
        }
        // Count gardeners on team
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int gardenerCount = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.GARDENER) {
                gardenerCount++;
            }
        }
        // Cap at 3 gardeners for light economy - scouts collect from neutral trees
        if (gardenerCount < 3) {
            float bullets = rc.getTeamBullets();
            return bullets > 140f && Math.random() < 0.4;
        }
        return false;
    }

    private static boolean shouldPlantTree(Direction dir) throws GameActionException {
        if (!rc.canPlantTree(dir)) {
            return false;
        }
        // Count trees planted by this gardener
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        int treeCount = trees.length;
        // Reduce tree target to 4 - scouts collect bullets from neutral trees
        if (treeCount < 4) {
            return Math.random() < 0.6;  // Lower priority than base, scouts get bullets from neutral
        }
        return false;
    }

    /**
     * Build scouts as primary military unit for harassment
     * Scouts cost 80 bullets (cheaper than soldiers at 100)
     */
    private static boolean shouldBuildScout(Direction dir) throws GameActionException {
        if (!rc.canBuildRobot(RobotType.SCOUT, dir)) {
            return false;
        }
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        
        // Lower build threshold to bullets > 120 for frequent scout production
        // Early game: spam scouts for harassment
        if (bullets > 120f) {
            // High probability for frequent scout building
            return Math.random() < 0.7;
        }
        // Mid-late game: keep producing scouts
        if (round > 300 && bullets > 100f) {
            return Math.random() < 0.5;
        }
        return false;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        // Moderate VP donation when bullets > 500 (opportunistic VP pushes)
        if (bullets > 500f) {
            float donateAmount = bullets - BULLET_RESERVE;
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                return pointsToBuy * cost;
            }
        }
        return 0f;
    }

    /**
     * Find a direction to build in (tries multiple directions)
     */
    private static Direction findBuildDirection() throws GameActionException {
        Direction dir = new Direction((float)Math.random() * 2 * (float)Math.PI);
        // Try 8 directions
        for (int i = 0; i < 8; i++) {
            Direction tryDir = dir.rotateLeftDegrees(45 * i);
            if (rc.canBuildRobot(RobotType.SCOUT, tryDir) || rc.canHireGardener(tryDir) || rc.canPlantTree(tryDir)) {
                return tryDir;
            }
        }
        return dir;
    }
}
