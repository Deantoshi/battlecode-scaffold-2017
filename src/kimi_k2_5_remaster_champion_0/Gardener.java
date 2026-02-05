package kimi_k2_5_remaster_champion_0;
import battlecode.common.*;

/**
 * Gardener - Economy unit.
 * - Water lowest health tree
 * - Position away from Archon
 * - Plants trees and builds units via BulletSpending
 */
public strictfp class Gardener {
    static RobotController rc;
    static final float SPACE_FROM_ARCHON = 7.0f;
    static final float WATER_RADIUS = 2.5f;
    
    static MapLocation archonLoc;
    static boolean hasPositioned = false;
    
    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
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
        // Update archon location
        archonLoc = Comms.getArchonLocation();
        
        // Water trees - prioritize lowest health
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(WATER_RADIUS, rc.getTeam());
        if (nearbyTrees.length > 0) {
            TreeInfo lowestHealthTree = Utils.findLowestHealthTree(nearbyTrees);
            if (lowestHealthTree != null && rc.canWater(lowestHealthTree.getID())) {
                rc.water(lowestHealthTree.getID());
            }
        }
        
        // Position away from Archon initially
        if (!hasPositioned && archonLoc != null) {
            float distToArchon = rc.getLocation().distanceTo(archonLoc);
            if (distToArchon < SPACE_FROM_ARCHON) {
                Direction away = archonLoc.directionTo(rc.getLocation());
                Nav.tryMove(away);
            } else {
                hasPositioned = true;
            }
        }
        
        // Check for enemies and flee if necessary
        RobotInfo[] enemies = rc.senseNearbyRobots(7.0f, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Nav.moveAwayFrom(closest.location);
            }
        }
        
        // Handle spending (planting trees and building units)
        BulletSpending.spendPolicy(RobotType.GARDENER);
        
        // Move randomly if stuck
        if (!rc.hasMoved() && !hasPositioned) {
            Nav.tryMove(Nav.randomDirection());
        }
    }
}
