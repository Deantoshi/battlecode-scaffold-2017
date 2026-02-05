package claude_opus_4_5_champion_2;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    
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
        // Broadcast our position for other units
        Comms.broadcastLocation(Comms.ARCHON_X, Comms.ARCHON_Y, rc.getLocation());
        
        // Reset counts at start of each round (first Archon only)
        if (rc.getRoundNum() % 10 == 0) {
            Comms.resetCounts();
        }
        
        // Check for nearby enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Flee from enemies!
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Nav.moveAway(closest.location);
            }
        } else {
            // No enemies, move randomly if possible
            if (!rc.hasMoved()) {
                Nav.tryMove(Nav.randomDirection());
            }
        }
        
        // Spend bullets (hire gardeners)
        BulletSpending.spendPolicy();
    }
}
