package gemini_flash_3;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
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
            case SCOUT:
                runScout();
                break;
            case TANK:
                runTank();
                break;
        }
    }

    static void runArchon() throws GameActionException {
        while (true) {
            try {
                BulletSpending.spendPolicy();
                if (rc.getHealth() < 400) {
                   tryMove(randomDirection());
                }
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
        while (true) {
            try {
                // Key Change: Prioritize watering existing trees to ensure bullet income for expensive Tanks.
                TreeInfo[] trees = rc.senseNearbyTrees(1.5f, rc.getTeam());
                if (trees.length > 0) {
                    TreeInfo lowestHealthTree = null;
                    for (TreeInfo t : trees) {
                        if (t.health < 50f) {
                            if (lowestHealthTree == null || t.health < lowestHealthTree.health) {
                                lowestHealthTree = t;
                            }
                        }
                    }
                    if (lowestHealthTree != null && rc.canWater(lowestHealthTree.ID)) {
                        rc.water(lowestHealthTree.ID);
                    }
                }

                BulletSpending.spendPolicy();

                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos, yPos);
                Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
                if (awayFromArchon == null) awayFromArchon = randomDirection();
                
                if (rc.getLocation().distanceTo(archonLoc) < 10) {
                    tryMove(awayFromArchon);
                } else {
                    // Try to find a good spot to plant trees (less crowded)
                    if (rc.senseNearbyTrees(2f).length > 2) {
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
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                // Key Change: Implement logic to prioritize moving through dense neutral forests to clear paths.
                // Engagement Style: Frontal assault; push through obstacles and use high health to soak damage.
                
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                
                if (enemies.length > 0) {
                    // Attack closest enemy
                    Direction toEnemy = rc.getLocation().directionTo(enemies[0].location);
                    tryMove(toEnemy);
                    
                    if (rc.canFirePentadShot()) {
                        rc.firePentadShot(toEnemy);
                    } else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(toEnemy);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                } else if (neutralTrees.length > 0) {
                    // Move towards and through neutral trees to clear them
                    Direction toTree = rc.getLocation().directionTo(neutralTrees[0].location);
                    tryMove(toTree);
                } else {
                    // Search for enemies - head towards enemy archon locations
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        tryMove(rc.getLocation().directionTo(enemyArchons[0]));
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
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo t : neutralTrees) {
                    if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                        rc.shake(t.ID);
                        break;
                    }
                }
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    tryMove(rc.getLocation().directionTo(enemies[0].location).opposite());
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(enemies[0].location));
                    }
                } else {
                    tryMove(randomDirection());
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    Direction toEnemy = rc.getLocation().directionTo(enemies[0].location);
                    tryMove(toEnemy);
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                } else {
                    tryMove(randomDirection());
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
                if(enemies.length > 0 && !rc.hasAttacked()) {
                    rc.strike();
                } else {
                    enemies = rc.senseNearbyRobots(-1, enemy);
                    if(enemies.length > 0) {
                        tryMove(rc.getLocation().directionTo(enemies[0].location));
                    } else {
                        TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                        if (trees.length > 0) {
                            if (rc.canChop(trees[0].ID)) {
                                rc.chop(trees[0].ID);
                            } else {
                                tryMove(rc.getLocation().directionTo(trees[0].location));
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
