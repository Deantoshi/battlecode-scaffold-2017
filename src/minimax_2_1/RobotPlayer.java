package minimax_2_1;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:      Archon.run();      break;
                    case GARDENER:    Gardener.run();    break;
                    case SOLDIER:     Soldier.run();     break;
                    case LUMBERJACK:  Lumberjack.run();  break;
                    case SCOUT:       Scout.run();       break;
                    case TANK:        Tank.run();        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
