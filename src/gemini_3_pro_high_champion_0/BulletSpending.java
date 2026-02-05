package gemini_3_pro_high_champion_0;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 50f; // Lower reserve to pump units

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Archetype: Scorched Earth
        // Priority: Lumberjack > Tank.
        // No planting.

        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Higher chance to hire gardener to ensure we have production capacity
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            
            // Minimal donation for now, focus on elimination
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }

        if (rc.getType() == RobotType.GARDENER) {
            Direction dir = randomDirection();
            
            // NO TREE PLANTING - Scorched Earth
            
            // Build Units
            // Priority: Tank (if rich) > Lumberjack > Scout (rarely)
            
            if (rc.getTeamBullets() >= 300 && Math.random() < 0.6) {
                // Try to build Tank
                if (rc.canBuildRobot(RobotType.TANK, dir)) {
                    rc.buildRobot(RobotType.TANK, dir);
                }
            } else {
                // Try to build Lumberjack
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                } else if (Math.random() < 0.1 && rc.canBuildRobot(RobotType.SCOUT, dir)) {
                     // Occasional scout for vision
                     rc.buildRobot(RobotType.SCOUT, dir);
                }
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Cap gardeners to avoid clogging, but ensure we have some
        // Since we don't plant trees, gardeners are just factories.
        // We need enough factories to spend our income (which comes from chopping).
        // Check how many gardeners are nearby
        int nearbyGardeners = 0;
        RobotInfo[] friends = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo r : friends) {
            if(r.type == RobotType.GARDENER) nearbyGardeners++;
        }
        
        // If we have few gardeners, hire more aggressively
        if (nearbyGardeners < 2) {
             return rc.canHireGardener(dir) && Math.random() < 0.2;
        }
        return rc.canHireGardener(dir) && Math.random() < 0.05;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Win condition is Elimination, but if we have excess bullets, buy VP to not waste them
        // Cap at 1000 VP just in case
        float donateAmount = bullets - 500; // Keep 500 for tanks
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            return pointsToBuy * cost;
        }
        
        // End game rush
        if (rc.getRoundNum() > 2800) {
             donateAmount = bullets - 10;
             if (donateAmount >= cost) return donateAmount;
        }
        
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
