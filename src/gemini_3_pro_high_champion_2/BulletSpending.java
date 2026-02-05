package gemini_3_pro_high_champion_2;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        switch (rc.getType()) {
            case ARCHON:
                runArchonSpending();
                break;
            case GARDENER:
                runGardenerSpending();
                break;
            default:
                break;
        }
    }

    private static void runArchonSpending() throws GameActionException {
        int nearbyGardeners = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo r : allies) {
            if (r.type == RobotType.GARDENER) nearbyGardeners++;
        }

        // Win Condition: VP Rush via high tree count (20).
        // Need enough gardeners to support 20 trees.
        // Cap gardeners at 6 to be safe (6 * 4 trees = 24).
        boolean needGardener = nearbyGardeners < 6;
        
        // Safety buffer for bullets
        if (rc.getTeamBullets() < 100) needGardener = false;

        if (needGardener) {
            for (int i = 0; i < 8; i++) {
                Direction d = randomDirection();
                if (rc.canHireGardener(d)) {
                    rc.hireGardener(d);
                    break;
                }
            }
        }

        donateForVP();
    }

    private static void runGardenerSpending() throws GameActionException {
        int treeCount = rc.getTreeCount();
        
        // Maintain high tree count (20)
        boolean buildEconomy = treeCount < 20; 
        
        // Priority 1: Plant Trees
        if (buildEconomy) {
            for (int i = 0; i < 5; i++) {
                Direction d = randomDirection();
                if (rc.canPlantTree(d)) {
                    rc.plantTree(d);
                    return; 
                }
            }
        }
        
        // Priority 2: Build Soldiers for defense
        // Only build if we have excess bullets to ensure we feed the VP rush
        // But also need some defense.
        if (rc.getTeamBullets() > 200) {
            RobotType buildType = RobotType.SOLDIER;
            for (int i = 0; i < 8; i++) {
                Direction d = randomDirection();
                if (rc.canBuildRobot(buildType, d)) {
                    rc.buildRobot(buildType, d);
                    break;
                }
            }
        }

        donateForVP();
    }

    private static void donateForVP() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Archetype Change: Lower donation threshold to 100 bullets.
        if (bullets > 100) {
            float toDonate = bullets - 100;
            if (toDonate >= cost) {
                // Donate as much as we can while keeping 100 reserve
                // Note: rc.donate takes float. 
                // We want to buy integer number of VPs usually to be efficient?
                // The rules say "Donating bullets buys VP at current cost (no fractional VP; uses floor)"
                // So rc.donate(cost * 1.5) buys 1 VP and wastes 0.5 cost bullets?
                // Let's ensure we donate multiples of cost.
                
                int vps = (int)(toDonate / cost);
                if (vps > 0) {
                    rc.donate(vps * cost);
                }
            }
        }
        
        // Insta-win check
        int currentVP = rc.getTeamVictoryPoints();
        int needed = 1000 - currentVP;
        if (needed > 0 && rc.getTeamBullets() >= needed * cost) {
            rc.donate(needed * cost);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
