package gemini_flash_3;
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
                // Broadcast location for gardeners to move away from
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                BulletSpending.spendPolicy();

                // Archon stays relatively still but can move if hit
                if (rc.getHealth() < 400) {
                   tryMove(randomDirection());
                }

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

                // 2. Spend policy (includes movement, planting, building, and donating)
                BulletSpending.spendPolicy();

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
                // 1. Shake trees for bullets
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo t : neutralTrees) {
                    if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                        rc.shake(t.ID);
                        break;
                    }
                }

                // 2. Harassment logic
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo target = null;
                
                // Prioritize Gardeners, then Archons
                for (RobotInfo r : enemies) {
                    if (r.type == RobotType.GARDENER) {
                        target = r;
                        break;
                    }
                }
                if (target == null) {
                    for (RobotInfo r : enemies) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break;
                        }
                    }
                }
                if (target == null && enemies.length > 0) {
                    target = enemies[0];
                }

                if (target != null) {
                    Direction toTarget = rc.getLocation().directionTo(target.location);
                    
                    // Hit and run engagement style
                    if (target.type == RobotType.GARDENER || target.type == RobotType.ARCHON) {
                        // Move towards economy units to harass
                        tryMove(toTarget);
                    } else {
                        // Avoid combat units (Soldiers, Lumberjacks, Tanks)
                        tryMove(toTarget.opposite());
                    }

                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                } else {
                    // No enemies, patrol
                    tryMove(randomDirection());
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
