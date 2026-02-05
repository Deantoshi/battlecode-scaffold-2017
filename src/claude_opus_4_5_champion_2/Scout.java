package claude_opus_4_5_champion_2;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;
    static Direction exploreDir = null;
    
    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        
        // Initialize explore direction toward enemy
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        if (enemyArchons.length > 0) {
            exploreDir = rc.getLocation().directionTo(enemyArchons[0]);
        } else {
            exploreDir = Nav.randomDirection();
        }
        
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
        // Report alive
        Comms.reportAlive();
        
        MapLocation myLoc = rc.getLocation();
        
        // Shake trees for bullets
        shakeTrees();
        
        // Check for dangerous enemies (Soldiers, Lumberjacks, Tanks)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        // Check for dangerous enemies nearby
        RobotInfo danger = null;
        RobotInfo gardenerTarget = null;
        RobotInfo archonTarget = null;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER || 
                enemy.type == RobotType.LUMBERJACK || 
                enemy.type == RobotType.TANK) {
                if (danger == null || myLoc.distanceTo(enemy.location) < myLoc.distanceTo(danger.location)) {
                    danger = enemy;
                }
            }
            if (enemy.type == RobotType.GARDENER) {
                gardenerTarget = enemy;
            }
            if (enemy.type == RobotType.ARCHON) {
                archonTarget = enemy;
                // Report enemy Archon location!
                Comms.broadcastEnemyArchon(enemy.location);
            }
        }
        
        // Flee from danger first
        if (danger != null) {
            float dist = myLoc.distanceTo(danger.location);
            if (dist < 6.0f) {
                Nav.moveAway(danger.location);
                return;
            }
        }
        
        // Harass Gardeners if safe
        if (gardenerTarget != null) {
            float dist = myLoc.distanceTo(gardenerTarget.location);
            
            // Attack if we can
            Direction toGardener = myLoc.directionTo(gardenerTarget.location);
            if (rc.canFireSingleShot() && dist < 6.0f) {
                rc.fireSingleShot(toGardener);
            }
            
            // Stay at range if no immediate danger
            if (danger == null) {
                if (dist > 5.0f && !rc.hasMoved()) {
                    Nav.moveToward(gardenerTarget.location);
                } else if (dist < 3.0f && !rc.hasMoved()) {
                    Nav.moveAway(gardenerTarget.location);
                }
            }
            return;
        }
        
        // Report Archon if found
        if (archonTarget != null) {
            Comms.broadcastEnemyArchon(archonTarget.location);
        }
        
        // Explore toward enemy base
        if (!rc.hasMoved()) {
            // Occasionally change direction
            if (Math.random() < 0.1) {
                exploreDir = exploreDir.rotateLeftDegrees(45);
            }
            
            if (!Nav.tryMove(exploreDir)) {
                // Hit obstacle, rotate
                exploreDir = Nav.randomDirection();
                Nav.tryMove(exploreDir);
            }
        }
    }
    
    /**
     * Shake nearby trees for bullets.
     */
    static void shakeTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }
    }
}
