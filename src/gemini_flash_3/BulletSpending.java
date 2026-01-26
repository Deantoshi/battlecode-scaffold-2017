package gemini_flash_3;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> build units -> donate.
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Priority 1: Build SCOUTs above all other combat units (Archetype requirement)
            if (rc.getTeamBullets() >= RobotType.SCOUT.bulletCost) {
                Direction scoutDir = randomDirection();
                if (rc.canBuildRobot(RobotType.SCOUT, scoutDir)) {
                    rc.buildRobot(RobotType.SCOUT, scoutDir);
                    return; // Consumes turn action
                }
            }

            // Priority 2: Plant trees (Map saturation)
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
                return; // Consumes turn action
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        if (!rc.canHireGardener(dir)) return false;
        // Archetype: hire Gardeners as fast as cooldown allows
        // Limit to a reasonable number of gardeners so we have bullets for scouts
        return rc.getRobotCount() < 20; 
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Favor planting trees whenever possible for map saturation
        return rc.canPlantTree(dir) && Math.random() < 0.8;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Always donate if we can win
        if (bullets >= (1000 - rc.getTeamVictoryPoints()) * cost) {
            return bullets;
        }

        // Archetype: Donate 100% of bullets above 100 to VP.
        float reserve = BULLET_RESERVE;
        float donateAmount = bullets - reserve;
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
