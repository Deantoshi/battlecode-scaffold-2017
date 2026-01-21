package training_bot;

import battlecode.common.*;

/**
 * Training Bot - A comprehensive bot demonstrating all unit types and functionalities.
 * Entry point that dispatches to unit-specific logic.
 */
public strictfp class RobotPlayer {
    static RobotController rc;
    static int turnCount = 0;

    /**
     * Main entry point called once when robot spawns.
     * Must enter infinite loop and call Clock.yield() each turn.
     */
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;

        // Initialize communications
        Comms.init(rc);

        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case ARCHON:
                        Archon.run(rc);
                        break;
                    case GARDENER:
                        Gardener.run(rc);
                        break;
                    case SOLDIER:
                        Soldier.run(rc);
                        break;
                    case LUMBERJACK:
                        Lumberjack.run(rc);
                        break;
                    case SCOUT:
                        Scout.run(rc);
                        break;
                    case TANK:
                        Tank.run(rc);
                        break;
                }
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // REQUIRED: End turn
                Clock.yield();
            }
        }
    }
}
