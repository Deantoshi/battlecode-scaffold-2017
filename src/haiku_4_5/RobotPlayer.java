package haiku_4_5;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        BulletSpending.init(rc);
        Comms.init(rc);
        Nav.init(rc);

        switch (rc.getType()) {
            case ARCHON:
                Archon.run();
                break;
            case GARDENER:
                Gardener.run();
                break;
            case SOLDIER:
                Soldier.run();
                break;
            case LUMBERJACK:
                Lumberjack.run();
                break;
            case SCOUT:
                Scout.run();
                break;
            case TANK:
                Tank.run();
                break;
        }
    }

    static RobotController getRc() {
        return rc;
    }
}
