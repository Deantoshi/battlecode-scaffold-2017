package claude_opus_4_5_low;
import battlecode.common.*;

/**
 * Balanced Army Archetype
 * Philosophy: Build a diverse army with multiple unit types for adaptation.
 * Win Condition: Hybrid (VP + Elimination)
 * Unit Priority: SOLDIER > LUMBERJACK > TANK
 */
public class BulletSpending {
    static RobotController rc;
    
    // Archetype: Balanced Army - moderate reserve for flexibility
    static final float BULLET_RESERVE = 150f;
    
    // Balanced Army donation threshold - donate excess above this
    static final float DONATION_THRESHOLD = 500f;
    
    // Balanced Army donation rate - donate 25% of excess
    static final float DONATION_RATE = 0.25f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Balanced Army: Centralized spend order
        // Archon: hire gardener -> donate
        // Gardener: plant tree -> build soldier/lumberjack/tank -> donate
        
        if (rc.getType() == RobotType.ARCHON) {
            // Try to hire gardeners with 20% rate
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            
            // Donate excess bullets
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees with 20% rate for moderate economy
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            
            // Balanced Army: Build diverse units
            // Priority: Soldier (30%) > Lumberjack (20%) > Tank (10%)
            dir = randomDirection();
            RobotType unitToBuild = selectUnitToBuild();
            if (unitToBuild != null && rc.canBuildRobot(unitToBuild, dir)) {
                rc.buildRobot(unitToBuild, dir);
            } else if (unitToBuild != null) {
                // Try other directions if first one blocked
                for (int i = 0; i < 8; i++) {
                    dir = dir.rotateRightDegrees(45);
                    if (rc.canBuildRobot(unitToBuild, dir)) {
                        rc.buildRobot(unitToBuild, dir);
                        break;
                    }
                }
            }
            
            // Donate excess bullets
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    /**
     * Balanced Army gardener hire rate: 20%
     */
    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < 0.20;
    }

    /**
     * Balanced Army tree plant rate: 20% for moderate economy
     */
    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < 0.20;
    }

    /**
     * Balanced Army unit selection with rotation:
     * - Soldiers: 30% (ranged engagement)
     * - Lumberjacks: 20% (tree clearing + melee)
     * - Tanks: 10% (heavy push)
     * Returns null if no unit should be built this turn
     */
    private static RobotType selectUnitToBuild() throws GameActionException {
        double roll = Math.random();
        
        // 30% chance for soldier (priority 1)
        if (roll < 0.30) {
            return RobotType.SOLDIER;
        }
        // 20% chance for lumberjack (priority 2)
        else if (roll < 0.50) {
            return RobotType.LUMBERJACK;
        }
        // 10% chance for tank (priority 3) - only if we have enough bullets
        else if (roll < 0.60) {
            // Tanks cost 300, so only build if we have enough
            if (rc.getTeamBullets() >= 300 + BULLET_RESERVE) {
                return RobotType.TANK;
            }
            // Fall back to soldier if not enough for tank
            return RobotType.SOLDIER;
        }
        
        // 40% of the time, don't build (save resources)
        return null;
    }

    /**
     * Balanced Army donation: donate 25% of excess bullets above 500
     */
    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Only donate if we have excess above threshold
        if (bullets <= DONATION_THRESHOLD) {
            return 0f;
        }
        
        // Donate 25% of excess above threshold
        float excess = bullets - DONATION_THRESHOLD;
        float donateAmount = excess * DONATION_RATE;
        
        // Make sure we donate in whole VP units
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
