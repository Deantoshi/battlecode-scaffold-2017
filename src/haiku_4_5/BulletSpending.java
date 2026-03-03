package haiku_4_5;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 200f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        if (rc.getType() == RobotType.ARCHON) {
            // Hire gardener if possible
            int round = rc.getRoundNum();
            if (round < 500) {
                Direction dir = Utils.randomDirection();
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = dir.rotateLeftDegrees(45 * i);
                    if (rc.canHireGardener(tryDir)) {
                        rc.hireGardener(tryDir);
                        return;
                    }
                }
            }
            // Donate when we have excess
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }

        if (rc.getType() == RobotType.GARDENER) {
            int round = rc.getRoundNum();
            float bullets = rc.getTeamBullets();

            // Try to plant trees early for economy
            if (round < 400 && bullets > 150) {
                Direction dir = Utils.randomDirection();
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = dir.rotateLeftDegrees(45 * i);
                    if (rc.canPlantTree(tryDir)) {
                        rc.plantTree(tryDir);
                        return;
                    }
                }
            }

            // Build soldiers early, scouts later
            if (round < 600) {
                Direction dir = Utils.randomDirection();
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = dir.rotateLeftDegrees(45 * i);
                    if (rc.canBuildRobot(RobotType.SOLDIER, tryDir)) {
                        rc.buildRobot(RobotType.SOLDIER, tryDir);
                        return;
                    }
                }
            } else {
                Direction dir = Utils.randomDirection();
                for (int i = 0; i < 8; i++) {
                    Direction tryDir = dir.rotateLeftDegrees(45 * i);
                    if (rc.canBuildRobot(RobotType.SCOUT, tryDir)) {
                        rc.buildRobot(RobotType.SCOUT, tryDir);
                        return;
                    }
                }
            }

            // Donate excess
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        float donateAmount = bullets - BULLET_RESERVE;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            return pointsToBuy * cost;
        }
        return 0f;
    }
}
