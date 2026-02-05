package kimi_k2_5_remaster_champion_0;
import battlecode.common.*;

/**
 * Utility functions for targeting and analysis.
 */
public strictfp class Utils {
    
    /**
     * Find the enemy with the lowest health.
     */
    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        
        RobotInfo lowest = null;
        float minHealth = Float.MAX_VALUE;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.health < minHealth) {
                minHealth = enemy.health;
                lowest = enemy;
            }
        }
        
        return lowest;
    }
    
    /**
     * Find the closest enemy.
     */
    public static RobotInfo findClosestEnemy(RobotController rc, RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        
        RobotInfo closest = null;
        float minDist = Float.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        
        for (RobotInfo enemy : enemies) {
            float dist = myLoc.distanceTo(enemy.location);
            if (dist < minDist) {
                minDist = dist;
                closest = enemy;
            }
        }
        
        return closest;
    }
    
    /**
     * Find the lowest health tree.
     */
    public static TreeInfo findLowestHealthTree(TreeInfo[] trees) {
        if (trees == null || trees.length == 0) {
            return null;
        }
        
        TreeInfo lowest = null;
        float minHealth = Float.MAX_VALUE;
        
        for (TreeInfo tree : trees) {
            if (tree.health < minHealth) {
                minHealth = tree.health;
                lowest = tree;
            }
        }
        
        return lowest;
    }
    
    /**
     * Calculate priority score for targets.
     * Higher score = higher priority.
     * Priority: Gardener > Archon > Scout > Soldier > Lumberjack > Tank
     */
    public static float getTargetPriority(RobotType type) {
        switch (type) {
            case GARDENER:   return 1000f;
            case ARCHON:     return 900f;
            case SCOUT:      return 700f;
            case SOLDIER:    return 600f;
            case LUMBERJACK: return 500f;
            case TANK:       return 400f;
            default:         return 0f;
        }
    }
    
    /**
     * Find the best target based on priority and distance.
     */
    public static RobotInfo findBestTarget(RobotController rc, RobotInfo[] enemies) {
        if (enemies == null || enemies.length == 0) {
            return null;
        }
        
        RobotInfo best = null;
        float bestScore = Float.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        
        for (RobotInfo enemy : enemies) {
            float priority = getTargetPriority(enemy.type);
            float dist = myLoc.distanceTo(enemy.location);
            float score = priority - dist * 10; // Distance penalty
            
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        
        return best;
    }
    
    /**
     * Check if firing in a direction would hit allies.
     */
    public static boolean wouldHitAllies(RobotController rc, Direction dir, float maxDist, float angleThreshold) {
        RobotInfo[] allies = rc.senseNearbyRobots(maxDist, rc.getTeam());
        
        for (RobotInfo ally : allies) {
            Direction toAlly = rc.getLocation().directionTo(ally.location);
            float angle = Math.abs(dir.degreesBetween(toAlly));
            
            if (angle < angleThreshold) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Count nearby enemies.
     */
    public static int countNearbyEnemies(RobotController rc, float radius) {
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
        return enemies.length;
    }
    
    /**
     * Count nearby allies.
     */
    public static int countNearbyAllies(RobotController rc, float radius) {
        RobotInfo[] allies = rc.senseNearbyRobots(radius, rc.getTeam());
        return allies.length;
    }
    
    /**
     * Check if we have clear line of fire to target.
     */
    public static boolean hasClearShot(RobotController rc, MapLocation target) {
        MapLocation myLoc = rc.getLocation();
        Direction dir = myLoc.directionTo(target);
        float dist = myLoc.distanceTo(target);
        
        // Check for trees in the way
        TreeInfo[] trees = rc.senseNearbyTrees(dist / 2);
        for (TreeInfo tree : trees) {
            Direction toTree = myLoc.directionTo(tree.location);
            float angle = Math.abs(dir.degreesBetween(toTree));
            if (angle < 15 && myLoc.distanceTo(tree.location) < dist) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if enemy is within attack radius.
     */
    public static boolean isInAttackRange(RobotController rc, RobotInfo enemy) {
        float dist = rc.getLocation().distanceTo(enemy.location);
        return dist <= rc.getType().sensorRadius && 
               dist <= rc.getType().bulletSpeed; // Rough approximation
    }
}
