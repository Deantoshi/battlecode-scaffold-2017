package copy_bot;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
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
        }
    }

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        while (true) {
            try {
                BulletSpending.spendPolicy();

                // Archon stays relatively still but can move if hit
                if (rc.getHealth() < 400) {
                   tryMove(randomDirection());
                }

                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");
        while (true) {
            try {
                // 1. Water nearby trees
                TreeInfo[] trees = rc.senseNearbyTrees(1.5f, rc.getTeam());
                if (trees.length > 0) {
                    TreeInfo lowestHealthTree = null;
                    for (TreeInfo t : trees) {
                        if (lowestHealthTree == null || t.health < lowestHealthTree.health) {
                            lowestHealthTree = t;
                        }
                    }
                    if (lowestHealthTree != null && rc.canWater(lowestHealthTree.ID)) {
                        rc.water(lowestHealthTree.ID);
                    }
                }

                // 2. Spend policy (plant/build/donate)
                BulletSpending.spendPolicy();

                // 3. Movement
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos, yPos);
                
                // Move away from archon to spread across map
                Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
                if (awayFromArchon == null) awayFromArchon = randomDirection();
                
                if (rc.getLocation().distanceTo(archonLoc) < 15) {
                    tryMove(awayFromArchon);
                } else {
                    // Just keep moving to spread out
                    tryMove(randomDirection());
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                // Prioritize shaking trees for bullets (Archetype requirement)
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo t : neutralTrees) {
                    if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                        rc.shake(t.ID);
                        break;
                    }
                }

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo target = null;
                
                if (enemies.length > 0) {
                    // Prioritize Gardeners (Archetype requirement)
                    for (RobotInfo r : enemies) {
                        if (r.type == RobotType.GARDENER) {
                            target = r;
                            break;
                        }
                    }
                    if (target == null) target = enemies[0];

                    // Combat behavior: Kite combat units, pursue gardeners
                    Direction toTarget = rc.getLocation().directionTo(target.location);
                    boolean isCombatUnit = (target.type == RobotType.SOLDIER || target.type == RobotType.LUMBERJACK || target.type == RobotType.TANK || target.type == RobotType.SCOUT);
                    
                    if (isCombatUnit) {
                        // Kite: move away while shooting
                        tryMove(toTarget.opposite());
                    } else {
                        // Pursue: move closer to gardener/archon
                        tryMove(toTarget);
                    }

                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                } else {
                    // Explore: search for neutral trees or enemies
                    if (neutralTrees.length > 0) {
                        tryMove(rc.getLocation().directionTo(neutralTrees[0].location));
                    } else {
                        tryMove(randomDirection());
                    }
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                if (enemies.length > 0) {
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(enemies[0].location));
                    }
                    tryMove(rc.getLocation().directionTo(enemies[0].location));
                } else {
                    tryMove(randomDirection());
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
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
                        tryMove(randomDirection());
                    }
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
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
