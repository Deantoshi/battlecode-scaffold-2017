package gemini_3_pro_high_champion_0;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team myTeam;
    static Team enemyTeam;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        RobotPlayer.myTeam = rc.getTeam();
        RobotPlayer.enemyTeam = rc.getTeam().opponent();
        
        BulletSpending.init(rc);

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
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
        }
    }

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        while (true) {
            try {
                BulletSpending.spendPolicy();
                tryMove(randomDirection());
                
                // Broadcast location
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);
                
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");
        while (true) {
            try {
                BulletSpending.spendPolicy();
                tryMove(randomDirection());
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemyTeam);

                if (robots.length > 0) {
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(robots[0].location));
                    }
                }
                tryMove(randomDirection());
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack! Scorched Earth Mode.");
        while (true) {
            try {
                // Priority:
                // 1. Strike Enemy Robots
                // 2. Chop Trees (Enemy or Neutral)
                // 3. Move to Trees
                // 4. Move to Enemies
                
                RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyTeam);
                TreeInfo[] trees = rc.senseNearbyTrees(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS);
                
                boolean attacked = false;

                // COMBAT / CHOPPING
                if (enemies.length > 0) {
                    if (rc.canStrike()) {
                        rc.strike();
                        attacked = true;
                    }
                } 
                
                if (!attacked && trees.length > 0) {
                    // Filter for trees we can chop (not our own, though we shouldn't have any)
                    // Actually, we can chop our own trees if we want, but let's prioritize non-team trees
                    // But for Scorched Earth, we destroy everything.
                    
                    int bestTreeId = -1;
                    for (TreeInfo t : trees) {
                        if (rc.canChop(t.ID)) {
                            bestTreeId = t.ID;
                            break;
                        }
                    }
                    
                    if (bestTreeId != -1) {
                        rc.chop(bestTreeId);
                        attacked = true;
                    } else if (!attacked && rc.canStrike()) {
                        // If we can't chop specific one (maybe cooldown?), strike area
                        rc.strike();
                         attacked = true;
                    }
                }

                // MOVEMENT
                if (!rc.hasMoved()) {
                    // Find nearest tree to destroy
                    TreeInfo[] allTrees = rc.senseNearbyTrees(-1);
                    TreeInfo targetTree = null;
                    float minDist = 9999f;
                    
                    for (TreeInfo t : allTrees) {
                        if (t.team == myTeam) continue; // Don't target own trees intentionally unless stuck?
                        float d = t.location.distanceTo(rc.getLocation());
                        if (d < minDist) {
                            minDist = d;
                            targetTree = t;
                        }
                    }

                    if (targetTree != null) {
                        tryMove(rc.getLocation().directionTo(targetTree.location));
                    } else {
                        // No trees, hunt enemies
                         RobotInfo[] allEnemies = rc.senseNearbyRobots(-1, enemyTeam);
                         if (allEnemies.length > 0) {
                             tryMove(rc.getLocation().directionTo(allEnemies[0].location));
                         } else {
                             tryMove(randomDirection());
                         }
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    static void runTank() throws GameActionException {
        System.out.println("I'm a Tank! Bulldozer Mode.");
        while (true) {
            try {
                // Tanks can move through trees (causing damage).
                // Target enemies.
                
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                if (enemies.length > 0) {
                    MapLocation enemyLoc = enemies[0].location;
                    Direction toEnemy = rc.getLocation().directionTo(enemyLoc);
                    
                    // Fire logic
                    if (rc.canFirePentadShot() && enemies.length > 1) {
                        rc.firePentadShot(toEnemy);
                    } else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                    
                    // Move towards enemy aggressively (bulldozing trees)
                    // Tank mechanics: ignores trees for collision check, damages them if it hits.
                    // So we can just tryMove towards enemy.
                    if (rc.canMove(toEnemy)) {
                        rc.move(toEnemy);
                    } else {
                        tryMove(toEnemy);
                    }
                } else {
                     // Patrol / Hunt
                     // Maybe move towards center or random
                     tryMove(randomDirection());
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
         System.out.println("I'm a scout!");
         while(true) {
             try {
                 tryMove(randomDirection());
                 RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                 if (enemies.length > 0 && rc.canFireSingleShot()) {
                     rc.fireSingleShot(rc.getLocation().directionTo(enemies[0].location));
                 }
                 Clock.yield();
             } catch (Exception e) {
                 e.printStackTrace();
             }
         }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        boolean moved = false;
        int currentCheck = 1;
        while(currentCheck<=checksPerSide) {
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            currentCheck++;
        }
        return false;
    }
}
