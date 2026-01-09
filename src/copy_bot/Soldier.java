package copy_bot;
import battlecode.common.*;

public strictfp class Soldier {
    static boolean moved = false;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            moved = false;
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                BulletInfo[] bullets = rc.senseNearbyBullets();
                
                if (!tryDodgeBullets(rc, bullets)) {
                    if (enemies.length > 0) {
                        combatTurn(rc, enemies);
                    } else {
                        MapLocation enemyLoc = Comms.readEnemyLocation(rc);
                        if (enemyLoc != null) {
                            Nav.moveToward(rc, enemyLoc);
                        } else {
                            Direction dir = Nav.randomDirection();
                            for (int i = 0; i < 3; i++) {
                                if (Nav.tryMove(rc, dir)) break;
                                dir = Nav.randomDirection();
                            }
                        }
                    }
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static boolean tryDodgeBullets(RobotController rc, BulletInfo[] bullets) throws GameActionException {
        for (BulletInfo bullet : bullets) {
            if (Utils.willCollideWithMe(rc, bullet)) {
                Direction escape = bullet.dir.rotateLeftDegrees(90);
                if (Nav.tryMove(rc, escape)) {
                    moved = true;
                }
                return true;
            }
        }
        return false;
    }

    static void combatTurn(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo target = findHighestPriorityTarget(enemies);
        if (target == null) return;
        
        if (!moved) {
            float dist = rc.getLocation().distanceTo(target.location);
            if (dist > 4) {
                if (Nav.moveToward(rc, target.location)) {
                    moved = true;
                }
            } else if (dist < 2) {
                if (Nav.moveAway(rc, target.location)) {
                    moved = true;
                }
            }
        }
        
        if (rc.canFireSingleShot()) {
            int shotType = Utils.getShotType(rc, target.location);
            if (shotType == 3 && rc.canFirePentadShot()) {
                rc.firePentadShot(rc.getLocation().directionTo(target.location));
            } else if (shotType == 2 && rc.canFireTriadShot()) {
                rc.fireTriadShot(rc.getLocation().directionTo(target.location));
            } else if (rc.getTeamBullets() > 5) {
                rc.fireSingleShot(rc.getLocation().directionTo(target.location));
            }
        }
    }

    static RobotInfo findHighestPriorityTarget(RobotInfo[] enemies) {
        RobotInfo bestGardener = null;
        RobotInfo bestArchon = null;
        RobotInfo bestSoldier = null;
        RobotInfo bestOther = null;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                if (bestGardener == null || enemy.health < bestGardener.health) {
                    bestGardener = enemy;
                }
            } else if (enemy.type == RobotType.ARCHON) {
                if (bestArchon == null || enemy.health < bestArchon.health) {
                    bestArchon = enemy;
                }
            } else if (enemy.type == RobotType.SOLDIER) {
                if (bestSoldier == null || enemy.health < bestSoldier.health) {
                    bestSoldier = enemy;
                }
            } else {
                if (bestOther == null || enemy.health < bestOther.health) {
                    bestOther = enemy;
                }
            }
        }
        
        if (bestGardener != null) return bestGardener;
        if (bestSoldier != null && bestSoldier.health < 30) return bestSoldier;
        if (bestArchon != null && bestArchon.health < 150) return bestArchon;
        return bestSoldier != null ? bestSoldier : (bestOther != null ? bestOther : enemies[0]);
    }
}
