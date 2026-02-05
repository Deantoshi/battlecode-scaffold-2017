package gemini_3_pro_high_champion_1;
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
        int nearbyCombat = 0;
        
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo r : allies) {
            if (r.type == RobotType.GARDENER) nearbyGardeners++;
            else if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK || r.type == RobotType.LUMBERJACK) nearbyCombat++;
        }

        // Logic: Hire if we have 0 gardeners nearby, or if the ratio supports it.
        boolean needGardener = (nearbyGardeners == 0) || (nearbyGardeners * 3 < nearbyCombat);
        
        if (nearbyGardeners >= 3) needGardener = false;

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
        // 2. Plant trees only if bullet income < X (Proxy: Tree Count)
        // 3. Mix SOLDIER and TANK production.
        
        int treeCount = rc.getTreeCount();
        boolean buildEconomy = treeCount < 10; 
        
        if (rc.getTeamBullets() > 400) buildEconomy = false; 

        // Attempt to plant
        if (buildEconomy) {
            for (int i = 0; i < 5; i++) {
                Direction d = randomDirection();
                if (rc.canPlantTree(d)) {
                    rc.plantTree(d);
                    return; 
                }
            }
        }
        
        // Unit Build Logic
        RobotType buildType = RobotType.SOLDIER;
        
        // If rich, aim for Tank. 
        // If we select Tank but lack funds, canBuildRobot will be false, so we effectively save up.
        if (rc.getTeamBullets() > 250) {
            buildType = RobotType.TANK;
        } 
        
        // Attempt to build selected type
        boolean built = false;
        for (int i = 0; i < 8; i++) {
            Direction d = randomDirection();
            if (rc.canBuildRobot(buildType, d)) {
                rc.buildRobot(buildType, d);
                built = true;
                break;
            }
        }

        // Fallback: If we wanted TANK but couldn't (maybe space?), and we are overflowing with bullets,
        // force a soldier to keep production moving.
        if (!built && buildType == RobotType.TANK && rc.getTeamBullets() > 500) {
             for (int i = 0; i < 8; i++) {
                Direction d = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, d)) {
                    rc.buildRobot(RobotType.SOLDIER, d);
                    break;
                }
            }
        }
        
        donateForVP();
    }

    private static void donateForVP() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Donate excess
        if (bullets > 600) {
            float toDonate = bullets - 600;
            if (toDonate >= cost) {
                int vps = (int)(toDonate / cost);
                rc.donate(vps * cost);
            }
        }
        
        // Last minute rush
        if (rc.getRoundNum() > 2800) {
             if (bullets > cost) {
                 rc.donate(bullets - (bullets % cost));
             }
        }
        
        // Insta-win check
        int currentVP = rc.getTeamVictoryPoints();
        int needed = 1000 - currentVP;
        if (needed > 0 && bullets >= needed * cost) {
            rc.donate(needed * cost);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
