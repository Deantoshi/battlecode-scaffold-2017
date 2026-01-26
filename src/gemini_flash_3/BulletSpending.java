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
            // Archetype: Iron Juggernaut - Heavy economic investment to reach late-game Tank production.
            
            // Priority 1: Plant trees (Map saturation for income)
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
                return; // Consumes turn action
            }

            // Priority 2: Build units
            // unit_priority: ["GARDENER", "LUMBERJACK", "TANK", "TANK"]
            // Note: Gardeners are hired by Archons. Gardener builds Lumberjacks, Soldiers, Scouts, Tanks.
            
            int treeCount = rc.getTreeCount();
            
            // If we have 10+ trees, prioritize TANKS
            if (treeCount >= 10) {
                if (rc.getTeamBullets() >= RobotType.TANK.bulletCost) {
                    Direction buildDir = randomDirection();
                    if (rc.canBuildRobot(RobotType.TANK, buildDir)) {
                        rc.buildRobot(RobotType.TANK, buildDir);
                        return;
                    }
                }
            }

            // Otherwise, build Lumberjacks to clear space or for defense
            if (rc.getTeamBullets() >= RobotType.LUMBERJACK.bulletCost) {
                // Check if we already have some lumberjacks or if there are many trees
                if (rc.getRobotCount() < 20) { // Limit total robots to save for tanks
                    Direction buildDir = randomDirection();
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, buildDir)) {
                        rc.buildRobot(RobotType.LUMBERJACK, buildDir);
                        return;
                    }
                }
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        if (!rc.canHireGardener(dir)) return false;
        // Need gardeners to build the tree economy
        return rc.getRobotCount() < 30; 
    }

    private static boolean shouldPlantTree(Direction dir) {
        // High priority on planting trees for the "Iron Juggernaut" economy
        return rc.canPlantTree(dir);
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Always donate if we can win
        if (bullets >= (1000 - rc.getTeamVictoryPoints()) * cost) {
            return bullets;
        }

        // Donate surplus only if we have a very strong economy and enough for tanks
        if (rc.getTreeCount() >= 25) {
            float reserve = 500f; // Higher reserve for tanks
            float donateAmount = bullets - reserve;
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                return pointsToBuy * cost;
            }
        }
        
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
