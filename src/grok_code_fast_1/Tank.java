package grok_code_fast_1;
import battlecode.common.*;

public class Tank {
    static RobotController rc;

    public static void init(RobotController rc) {
        Tank.rc = rc;
    }

    public static void fire() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            // Prioritize archons
            RobotInfo target = null;
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    target = robot;
                    break;
                }
            }
            if (target == null) {
                target = robots[0];
            }
            MapLocation enemyLoc = target.location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Use area shots when close for efficiency, adapted for tank range
            if (dist <= 2 && rc.canFirePentadShot()) {
                rc.firePentadShot(dir);
            } else if (dist <= 3 && rc.canFireTriadShot()) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }

    public static void defensiveMove() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
        
        // If enemies nearby, stay and defend
        if (enemies.length > 0) {
            return;
        }
        
        // Read archon location
        int x = rc.readBroadcast(0);
        int y = rc.readBroadcast(1);
        MapLocation archonLoc = new MapLocation(x, y);
        
        MapLocation myLoc = rc.getLocation();
        
        // Calculate defensive position: tighter circle around archon
        int id = rc.getID();
        float angle = (id % 8) * 45; // 8 positions for tighter formation
        float radius = 5f; // tighter radius
        Direction dirFromArchon = Direction.getEast().rotateRightDegrees(angle);
        MapLocation defensivePos = archonLoc.add(dirFromArchon, radius);
        
        // Move towards defensive position
        Direction toDef = myLoc.directionTo(defensivePos);
        if (rc.canMove(toDef)) {
            rc.move(toDef);
        } else {
            // Try to move closer or adjust
            tryMove(toDef);
        }
    }
    
    private static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Try slight rotations
        for (int i = 1; i <= 3; i++) {
            Direction left = dir.rotateLeftDegrees(i * 45);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(i * 45);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }
}