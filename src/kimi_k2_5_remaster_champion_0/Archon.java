package kimi_k2_5_remaster_champion_0;
import battlecode.common.*;

/**
 * Archon - Leader unit.
 * - Broadcasts position (channels 0,1)
 * - Flee from enemies
 * - Hires Gardeners via BulletSpending
 */
public strictfp class Archon {
    static RobotController rc;
    static final float FLEE_DISTANCE = 8.0f;
    
    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        BulletSpending.init(rc);
        
        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
    
    static void doTurn() throws GameActionException {
        // Broadcast our position
        Comms.broadcastArchonLocation(rc.getLocation());
        
        // Check for enemies and flee if necessary
        RobotInfo[] enemies = rc.senseNearbyRobots(FLEE_DISTANCE, rc.getTeam().opponent());
        if (enemies.length > 0) {
            // Run away from the closest enemy
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Nav.moveAwayFrom(closest.location);
                Comms.setEmergency(true);
            }
        } else {
            Comms.setEmergency(false);
        }
        
        // Handle spending (hiring Gardeners)
        BulletSpending.spendPolicy(RobotType.ARCHON);
        
        // Move randomly if we haven't moved yet
        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }
}
