package gemini_3_pro;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Random rand;

    // Broadcasting Channels
    static final int ARCHON_COUNT_CHANNEL = 0;
    static final int GARDENER_COUNT_CHANNEL = 1;
    static final int ENEMY_ARCHON_LOC_CHANNEL = 10; // 10, 11, 12...
    static final int TARGET_LOC_CHANNEL = 20;

    // Keep track of our circle
    static Direction[] buildDirections = new Direction[6];

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        rand = new Random(rc.getID());
        
        // Initialize constant directions (hexagonal)
        for (int i = 0; i < 6; i++) {
            buildDirections[i] = new Direction((float) (i * Math.PI / 3));
        }

        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case SCOUT:
                runScout();
                break;
            case TANK:
                runTank();
                break;
        }
    }

    static void runArchon() throws GameActionException {
        System.out.println("Archon online.");
        while (true) {
            try {
                // Strategic check: How many gardeners?
                int gardenerCount = rc.readBroadcast(GARDENER_COUNT_CHANNEL);
                
                // Reset gardener count every turn approximately? 
                // A better way is gardeners incrementing and maybe we clear it rarely, 
                // but for simplicity, let's just rely on rough estimates or probability.
                // Actually, let's just cap gardeners based on round number or bullets.
                
                // Simple strategy: Hire a gardener if we have money and fewer than X gardeners
                // Since we can't easily count live gardeners without complex signaling, 
                // we will use a probability based on bullet count.
                
                if (rc.isBuildReady() && rc.getTeamBullets() > 120) {
                     // Try to hire in any safe direction
                     for (int i = 0; i < 12; i++) {
                         Direction dir = new Direction((float) (i * Math.PI / 6));
                         if (rc.canHireGardener(dir)) {
                             rc.hireGardener(dir);
                             break;
                         }
                     }
                }

                // Dodge bullets / Move away from enemies
                avoidDangers();

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        System.out.println("Gardener online.");
        
        // Find a spot to settle?
        // For now, just settle immediately where we are or move a bit if crowded.
        
        // Build plan:
        // Slot 0: Build Unit
        // Slot 1-5: Trees
        
        int myLoopCounter = 0;

        while (true) {
            try {
                // 1. Water Trees (Highest Priority to keep them alive)
                if (rc.canWater()) {
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f, rc.getTeam());
                    TreeInfo lowestTree = null;
                    float lowestHealth = 9999f;
                    
                    for (TreeInfo t : nearbyTrees) {
                        if (rc.canWater(t.getID()) && t.health < t.maxHealth - 5) {
                            if (t.health < lowestHealth) {
                                lowestHealth = t.health;
                                lowestTree = t;
                            }
                        }
                    }
                    if (lowestTree != null) {
                        rc.water(lowestTree.getID());
                    }
                }

                // 2. Build Trees
                // We want to build trees in specific relative directions.
                // Let's use our buildDirections array.
                // Leave direction 0 open for units.
                for (int i = 1; i < 6; i++) {
                    if (rc.canPlantTree(buildDirections[i])) {
                        rc.plantTree(buildDirections[i]);
                        break; // Only one action per turn (plant OR build) usually limits us, but check cooldowns
                    }
                }

                // 3. Build Units
                if (rc.isBuildReady()) {
                    Direction buildDir = buildDirections[0];
                    if (rc.canBuildRobot(RobotType.SCOUT, buildDir) && Math.random() < 0.1 && rc.getTeamBullets() > 100) {
                         rc.buildRobot(RobotType.SCOUT, buildDir);
                    } else if (rc.canBuildRobot(RobotType.LUMBERJACK, buildDir) && Math.random() < 0.1 && rc.getTeamBullets() > 100) {
                         rc.buildRobot(RobotType.LUMBERJACK, buildDir);
                    } else if (rc.canBuildRobot(RobotType.SOLDIER, buildDir) && rc.getTeamBullets() > 100) {
                         rc.buildRobot(RobotType.SOLDIER, buildDir);
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("Soldier online.");
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                
                if (enemies.length > 0) {
                    RobotInfo target = enemies[0]; // Simplest target selection
                    
                    // Attack
                    if (rc.canFireSingleShot()) {
                         // Don't shoot teammates
                         Direction toEnemy = rc.getLocation().directionTo(target.location);
                         if (!willHitAlly(toEnemy)) {
                             rc.fireSingleShot(toEnemy);
                         }
                    }
                    
                    // Micro: Move
                    // If too close, back up. If too far, close in.
                    float dist = rc.getLocation().distanceTo(target.location);
                    if (dist < 4) {
                        tryMove(rc.getLocation().directionTo(target.location).opposite());
                    } else {
                        tryMove(rc.getLocation().directionTo(target.location));
                    }
                } else {
                    // No enemies? Explore or move to rally point.
                    // Simple random walk for now
                    tryMove(randomDirection());
                }
                
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("Lumberjack online.");
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
                TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);

                if (enemies.length > 0) {
                    // Strike if enemies are close!
                    // Be careful not to hit friends, but LJ is chaotic.
                    // Only strike if we hit more enemies than friends? 
                    // For now, just strike if enemy is there.
                    if (rc.canStrike()) {
                        rc.strike();
                    }
                } else if (trees.length > 0) {
                    // Chop trees
                    if (rc.canChop(trees[0].getID())) {
                        rc.chop(trees[0].getID());
                    }
                } else {
                    // Move towards enemies or trees
                     RobotInfo[] farEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                     if (farEnemies.length > 0) {
                         tryMove(rc.getLocation().directionTo(farEnemies[0].location));
                     } else {
                         tryMove(randomDirection());
                     }
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("Scout online.");
        while (true) {
            try {
                // Shake trees for bullets
                TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo t : trees) {
                    if (t.getContainedBullets() > 0 && rc.canShake(t.getID())) {
                        rc.shake(t.getID());
                    }
                }
                
                // Explore
                tryMove(randomDirection());
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        runSoldier(); // Same logic as soldier for now
    }

    // --- HELPER METHODS ---

    static boolean willHitAlly(Direction dir) throws GameActionException {
        // Crude check: cast a tiny ray or just trust we are good. 
        // Battlecode 2017 didn't have detailed raycast API in strict sense for all cases, 
        // but we can check nearby robots in that direction.
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] allies = rc.senseNearbyRobots(4, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (myLoc.directionTo(ally.location).degreesBetween(dir) < 15) {
                return true;
            }
        }
        return false;
    }

    static void avoidDangers() throws GameActionException {
        // Basic bullet dodging
        BulletInfo[] bullets = rc.senseNearbyBullets();
        MapLocation myLoc = rc.getLocation();
        
        Direction bestDir = null;
        float maxSafety = -9999;

        // Sample a few directions
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction((float)(i * Math.PI / 4));
            if (!rc.canMove(dir)) continue;

            float safety = 0;
            MapLocation nextLoc = myLoc.add(dir, rc.getType().strideRadius);
            
            // Check bullet distance
            for (BulletInfo b : bullets) {
                if (willCollideWithLoc(b, nextLoc)) {
                    safety -= b.damage;
                }
            }
            
            if (safety > maxSafety) {
                maxSafety = safety;
                bestDir = dir;
            }
        }
        
        if (bestDir != null && maxSafety > -10) { // If it's safer than getting hit
             rc.move(bestDir);
        }
    }
    
    static boolean willCollideWithLoc(BulletInfo bullet, MapLocation loc) {
        Direction directionToRobot = bullet.location.directionTo(loc);
        float distToRobot = bullet.location.distanceTo(loc);
        float theta = bullet.dir.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    static void tryMove(Direction dir) throws GameActionException {
        if (dir == null) return;
        if (rc.canMove(dir)) {
            rc.move(dir);
        } else {
            // Simple obstacle avoidance
            // Try left and right
            for (int i = 1; i <= 3; i++) {
                Direction left = dir.rotateLeftDegrees(20 * i);
                if (rc.canMove(left)) {
                    rc.move(left);
                    return;
                }
                Direction right = dir.rotateRightDegrees(20 * i);
                if (rc.canMove(right)) {
                    rc.move(right);
                    return;
                }
            }
        }
    }

    static Direction randomDirection() {
        return new Direction(rand.nextFloat() * 2 * (float) Math.PI);
    }

    // Pseudo-random generator (java.util.Random is allowed in 2017?) 
    // Docs say Java 8 StrictFP. Usually Battlecode allows Math.random().
    // I used java.util.Random above, but let's stick to Math.random to be safe as per example.
    // Actually, I'll define a simple wrapper if needed, but Math.random() is fine.
    static class Random {
        long seed;
        Random(int id) { this.seed = id; }
        float nextFloat() {
            return (float)Math.random();
        }
    }
}
