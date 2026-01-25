package gemini_flash_3;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int scoutCount = rc.readBroadcast(2);
        int gardenerCount = rc.readBroadcast(3);

        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = RobotPlayer.randomDirection();
            
            // Key Change: Build exactly 1 gardener initially to start Scout production
            if (gardenerCount < 1) {
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    rc.broadcast(3, gardenerCount + 1);
                }
            } else if (scoutCount >= 5) {
                // After 5 scouts are harvesting/built, we can hire more gardeners to expand the tree economy
                if (rc.getTeamBullets() > 150 && rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    rc.broadcast(3, gardenerCount + 1);
                }
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        
        if (rc.getType() == RobotType.GARDENER) {
            // Movement: Move away from Archon to find space for trees
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

            Direction dir = RobotPlayer.randomDirection();
            if (scoutCount < 5) {
                // Key Change: Build exactly 5 SCOUT units before any other spending occurs
                if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    rc.broadcast(2, scoutCount + 1);
                }
            } else {
                // Key Change: Switch to 100% Tree planting once the 5 scouts are built
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                }
            }
            
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Always donate if we can win immediately
        if (bullets >= (1000 - rc.getTeamVictoryPoints()) * cost) {
            return bullets;
        }

        // Key Change: Trigger donation as soon as bullets exceed 200, regardless of the round number
        if (bullets > 200) {
            float donateAmount = bullets - 200;
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                return pointsToBuy * cost;
            }
        }
        return 0f;
    }
}
