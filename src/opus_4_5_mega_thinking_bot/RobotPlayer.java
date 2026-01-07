package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Entry point for the Opus 4.5 Mega Thinking Bot.
 * Dispatches to robot-specific handlers.
 */
public strictfp class RobotPlayer {

    /**
     * run() is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     */
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Initialize shared utilities
        Utils.init(rc);
        Comms.init(rc);
        Nav.init(rc);

        // Dispatch to robot-specific handler
        // Each handler contains its own while(true) loop
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
    }
}
