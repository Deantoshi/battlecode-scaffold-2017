package gemini_flash_3;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = RobotPlayer.randomDirection();
            // Archetype Change: Increase shouldHireGardener probability to 1.0 if bullets > 100.
            if (rc.getTeamBullets() > 100 && rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        
        if (rc.getType() == RobotType.GARDENER) {
            // Movement: Move away from Archon
            int xPos = rc.readBroadcast(0);
            int yPos = rc.readBroadcast(1);
            if (xPos != 0 || yPos != 0) {
                MapLocation currentArchonLoc = new MapLocation(xPos, yPos);
                Direction awayFromArchon = currentArchonLoc.directionTo(rc.getLocation());
                if (awayFromArchon == null) awayFromArchon = RobotPlayer.randomDirection();
                RobotPlayer.tryMove(awayFromArchon);
            } else {
                RobotPlayer.tryMove(RobotPlayer.randomDirection());
            }

            // Archetype Change: Prioritize building SCOUT for harassment.
            // Unit Priority: SCOUT, GARDENER (hired by Archon), SOLDIER
            Direction dir = RobotPlayer.randomDirection();
            if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            } else if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                // Secondary unit priority
                rc.buildRobot(RobotType.SOLDIER, dir);
            } else if (rc.canPlantTree(dir)) {
                // Planting trees is lower priority but good for economy if we can't build
                rc.plantTree(dir);
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        // Archetype Change: Set getDonateAmount() to always return 0 until round 2000.
        if (rc.getRoundNum() < 2000) {
            return 0f;
        }

        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Always donate if we can win
        if (bullets >= (1000 - rc.getTeamVictoryPoints()) * cost) {
            return bullets;
        }

        float reserve = BULLET_RESERVE;
        float donateAmount = bullets - reserve;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            return pointsToBuy * cost;
        }
        return 0f;
    }
}
