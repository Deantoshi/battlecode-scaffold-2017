package gemini_3_pro_high_champion_3;
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

        // Archetype: Lumberjack Rush
        // We need gardeners to build lumberjacks, but not too many that they clog the map.
        // 2-3 gardeners should be enough to pump out lumberjacks continuously if we have the bullets.
        int gardenerCap = 3;
        
        boolean needGardener = nearbyGardeners < gardenerCap;
        
        // Safety buffer: don't hire if very low on bullets, save for lumberjacks
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
        
        // Archetype: Cap trees at 3 (high mobility needed)
        boolean buildTrees = treeCount < 3; 
        
        // Strategy: Build Lumberjacks Primarily
        // If we have enough bullets for a Lumberjack, try to build one.
        // Trees are secondary priority here, mainly for some income.
        
        boolean builtUnit = false;
        
        // Priority 1: Build Lumberjacks
        // Maintain a swarm.
        if (rc.getTeamBullets() >= RobotType.LUMBERJACK.bulletCost) {
            for (int i = 0; i < 8; i++) {
                Direction d = randomDirection();
                if (rc.canBuildRobot(RobotType.LUMBERJACK, d)) {
                    rc.buildRobot(RobotType.LUMBERJACK, d);
                    builtUnit = true;
                    break;
                }
            }
        }
        
        // Priority 2: Plant Trees (if we didn't build a unit and need trees)
        // Or if we have excess bullets.
        // But we want to prioritize unit cap.
        if (!builtUnit && buildTrees) {
            for (int i = 0; i < 5; i++) {
                Direction d = randomDirection();
                if (rc.canPlantTree(d)) {
                    rc.plantTree(d);
                    break;
                }
            }
        }

        donateForVP();
    }

    private static void donateForVP() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Archetype: Elimination
        // Focus on units. Only donate if we are overflowing with bullets.
        float floatCap = 1000; 

        if (bullets > floatCap) {
            float toDonate = bullets - floatCap;
            if (toDonate >= cost) {
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
