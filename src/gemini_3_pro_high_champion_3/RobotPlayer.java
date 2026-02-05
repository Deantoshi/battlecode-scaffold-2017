package gemini_3_pro_high_champion_3;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Team myTeam;
    static Team enemyTeam;

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
                
                // Defensive movement: avoid enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                if (enemies.length > 0) {
                     Direction away = rc.getLocation().directionTo(enemies[0].location).opposite();
                     tryMove(away);
                } else {
                     tryMove(randomDirection());
                }
                
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
                
                // Water trees
                TreeInfo[] trees = rc.senseNearbyTrees(2.0f, myTeam);
                for(TreeInfo t : trees){
                    if(t.health < t.maxHealth - 5 && rc.canWater(t.ID)){
                        rc.water(t.ID);
                        break;
                    }
                }
                
                // Movement: Spread out for trees
                if (!rc.hasMoved()) {
                    TreeInfo[] allTrees = rc.senseNearbyTrees(3.0f);
                    RobotInfo[] robots = rc.senseNearbyRobots(3.0f, myTeam);
                    
                    if (allTrees.length > 2 || robots.length > 2) {
                        tryMove(randomDirection());
                    }
                }
                
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier! Patrol Mode.");
        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                
                if (enemies.length > 0) {
                    // Combat
                    RobotInfo target = enemies[0];
                    Direction toEnemy = myLoc.directionTo(target.location);
                    float dist = myLoc.distanceTo(target.location);
                    
                    if (rc.canFireTriadShot() && dist < 4) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                    
                    // Defensive kiting
                    if (dist < 5) {
                        tryMove(toEnemy.opposite());
                    } else if (dist > 7) {
                        // Stay within patrol range preferably
                    } else {
                        tryMove(toEnemy); // Engage
                    }
                } else {
                    // Patrol around Archon
                    MapLocation anchor = null;
                    RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
                    for (RobotInfo r : allies) {
                        if (r.type == RobotType.ARCHON) {
                            anchor = r.location;
                            break;
                        }
                    }
                    if (anchor == null) {
                        MapLocation[] archonLocs = rc.getInitialArchonLocations(myTeam);
                        if (archonLocs.length > 0) anchor = archonLocs[0];
                    }
                    
                    if (anchor != null) {
                        float dist = myLoc.distanceTo(anchor);
                        if (dist > 10) {
                            tryMove(myLoc.directionTo(anchor));
                        } else if (dist < 5) {
                            tryMove(randomDirection()); // Spread out a bit
                        } else {
                            // Orbit
                            tryMove(myLoc.directionTo(anchor).rotateLeftDegrees(90));
                        }
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

    static void runTank() throws GameActionException {
        // Tank behaves like Soldier
        runSoldier();
    }
    
    static void runLumberjack() throws GameActionException {
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemyTeam);
        MapLocation target = null;
        if (enemyArchons.length > 0) {
            target = enemyArchons[rc.getID() % enemyArchons.length];
        }

        while (true) {
            try {
                // 1. Strike/Chop logic (Combat)
                RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyTeam);
                TreeInfo[] trees = rc.senseNearbyTrees(GameConstants.LUMBERJACK_STRIKE_RADIUS);
                
                boolean attacked = false;
                if (enemies.length > 0) {
                    if (rc.canStrike()) {
                        rc.strike();
                        attacked = true;
                    }
                } 
                
                if (!attacked && trees.length > 0) {
                    for (TreeInfo t : trees) {
                        if (t.team != myTeam && rc.canChop(t.ID)) {
                            rc.chop(t.ID);
                            break;
                        }
                    }
                }
                
                // 2. Movement logic (Aggressive Rush)
                if (!rc.hasMoved()) {
                    RobotInfo[] farEnemies = rc.senseNearbyRobots(-1, enemyTeam);
                    if (farEnemies.length > 0) {
                        // Move towards visible enemy
                        tryMove(rc.getLocation().directionTo(farEnemies[0].location));
                    } else if (target != null) {
                        // Move towards initial enemy archon spawn
                        if (rc.getLocation().distanceTo(target) < 4) {
                            tryMove(randomDirection()); // We are there, hunt around
                        } else {
                            tryMove(rc.getLocation().directionTo(target));
                        }
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
