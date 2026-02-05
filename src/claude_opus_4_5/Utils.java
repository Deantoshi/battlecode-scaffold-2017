package claude_opus_4_5;
import battlecode.common.*;

public strictfp class Utils {
    
    /**
     * Find the enemy with lowest health from an array of enemies.
     */
    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        RobotInfo lowest = enemies[0];
        for (RobotInfo enemy : enemies) {
            if (enemy.health < lowest.health) {
                lowest = enemy;
            }
        }
        return lowest;
    }
    
    /**
     * Find the tree with lowest health from an array of trees.
     */
    public static TreeInfo findLowestHealthTree(TreeInfo[] trees) {
        if (trees == null || trees.length == 0) {
            return null;
        }
        TreeInfo lowest = trees[0];
        for (TreeInfo tree : trees) {
            if (tree.health < lowest.health) {
                lowest = tree;
            }
        }
        return lowest;
    }
    
    /**
     * Find the closest enemy to the robot.
     */
    public static RobotInfo findClosestEnemy(RobotController rc, RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        MapLocation myLoc = rc.getLocation();
        RobotInfo closest = enemies[0];
        float closestDist = myLoc.distanceTo(closest.location);
        for (RobotInfo enemy : enemies) {
            float dist = myLoc.distanceTo(enemy.location);
            if (dist < closestDist) {
                closest = enemy;
                closestDist = dist;
            }
        }
        return closest;
    }
    
    /**
     * Find priority target based on: Gardeners > low HP > Scouts > Soldiers
     */
    public static RobotInfo findPriorityTarget(RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        RobotInfo best = enemies[0];
        int bestPriority = getTargetPriority(best);
        for (RobotInfo enemy : enemies) {
            int priority = getTargetPriority(enemy);
            if (priority > bestPriority) {
                best = enemy;
                bestPriority = priority;
            } else if (priority == bestPriority && enemy.health < best.health) {
                best = enemy;
            }
        }
        return best;
    }
    
    /**
     * Get priority value for a robot type.
     * Higher = more important to kill.
     */
    public static int getTargetPriority(RobotInfo robot) {
        switch (robot.type) {
            case GARDENER:   return 100;  // Highest priority - economy
            case ARCHON:     return 90;   // High priority - leader
            case SCOUT:      return 50;   // Medium priority - annoying
            case SOLDIER:    return 40;   // Combat unit
            case LUMBERJACK: return 35;   // Melee unit
            case TANK:       return 30;   // Slow but dangerous
            default:         return 0;
        }
    }
    
    /**
     * Check if a location is safe from enemy fire.
     */
    public static boolean isLocationSafe(RobotController rc, MapLocation loc, RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER || enemy.type == RobotType.TANK) {
                if (loc.distanceTo(enemy.location) < 5.0f) {
                    return false;
                }
            }
            if (enemy.type == RobotType.LUMBERJACK) {
                if (loc.distanceTo(enemy.location) < 3.0f) {
                    return false;
                }
            }
        }
        return true;
    }
}
