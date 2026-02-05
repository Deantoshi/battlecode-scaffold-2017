package gemini_3_pro_high_champion_1;
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
                // Archon Logic: Dodge, Donate/Hire (via BulletSpending)
                BulletSpending.spendPolicy();
                
                // Move away from enemies, random otherwise
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
                
                // Movement: Try to find open space for trees
                // If we are too close to other units or trees, move apart.
                if (!rc.hasMoved()) {
                    TreeInfo[] trees = rc.senseNearbyTrees(3.0f);
                    RobotInfo[] robots = rc.senseNearbyRobots(3.0f, myTeam);
                    
                    if (trees.length > 2 || robots.length > 2) {
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
        System.out.println("I'm a soldier! Squad Mode.");
        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                
                // Squad Logic
                int squadSize = 0;
                RobotInfo[] friends = rc.senseNearbyRobots(10, myTeam);
                for (RobotInfo r : friends) {
                    if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                        squadSize++;
                    }
                }
                // Include self
                squadSize++;

                boolean readyToPush = squadSize >= 3;

                // Combat
                if (enemies.length > 0) {
                    RobotInfo target = enemies[0];
                    Direction toEnemy = myLoc.directionTo(target.location);
                    float dist = myLoc.distanceTo(target.location);
                    
                    if (rc.canFireTriadShot() && dist < 4) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                    
                    // Movement in combat
                    if (readyToPush || dist < 5) {
                        // Engage
                        tryMove(toEnemy);
                    } else {
                        // Hold position / kite
                        if (dist < 6) tryMove(toEnemy.opposite());
                    }
                } else {
                    // No enemies
                    if (readyToPush) {
                        // Move to enemy spawn or random
                        MapLocation[] archonLocs = rc.getInitialArchonLocations(enemyTeam);
                        if (archonLocs.length > 0) {
                            tryMove(myLoc.directionTo(archonLocs[0]));
                        } else {
                            tryMove(randomDirection());
                        }
                    } else {
                        // Rally / Wait
                        // Move towards friendly archon to group up?
                        RobotInfo[] nearbyArchons = rc.senseNearbyRobots(-1, myTeam);
                        MapLocation rallyPoint = null;
                        for (RobotInfo r : nearbyArchons) {
                            if (r.type == RobotType.ARCHON) {
                                rallyPoint = r.location;
                                break;
                            }
                        }
                        
                        if (rallyPoint != null) {
                            if (myLoc.distanceTo(rallyPoint) > 5) {
                                tryMove(myLoc.directionTo(rallyPoint));
                            } else {
                                tryMove(randomDirection()); // Patrol near archon
                            }
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
        System.out.println("I'm a Tank!");
        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                
                 // Squad Logic (Tanks count as heavy squad members)
                int squadSize = 0;
                RobotInfo[] friends = rc.senseNearbyRobots(10, myTeam);
                for (RobotInfo r : friends) {
                    if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                        squadSize++;
                    }
                }
                squadSize++; // self

                boolean readyToPush = squadSize >= 2; // Tanks need fewer buddies

                if (enemies.length > 0) {
                    RobotInfo target = enemies[0];
                    Direction toEnemy = myLoc.directionTo(target.location);
                    
                    if (rc.canFirePentadShot()) {
                        rc.firePentadShot(toEnemy);
                    } else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                    
                    if (rc.canMove(toEnemy)) {
                        rc.move(toEnemy);
                    } else {
                        tryMove(toEnemy);
                    }
                } else {
                    if (readyToPush) {
                         MapLocation[] archonLocs = rc.getInitialArchonLocations(enemyTeam);
                        if (archonLocs.length > 0) {
                            tryMove(myLoc.directionTo(archonLocs[0]));
                        } else {
                            tryMove(randomDirection());
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
    
    static void runLumberjack() throws GameActionException {
        // Simple lumberjack for cleanup/defense
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyTeam);
                TreeInfo[] trees = rc.senseNearbyTrees(GameConstants.LUMBERJACK_STRIKE_RADIUS);
                
                if (enemies.length > 0) {
                    if (rc.canStrike()) rc.strike();
                } else if (trees.length > 0) {
                    // Chop neutral/enemy trees
                    for (TreeInfo t : trees) {
                        if (t.team != myTeam && rc.canChop(t.ID)) {
                            rc.chop(t.ID);
                            break;
                        }
                    }
                }
                
                if (!rc.hasMoved()) tryMove(randomDirection());
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
