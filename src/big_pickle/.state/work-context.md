# Objective Work Context

## YOUR CURRENT OBJECTIVE
{
  "name": "defend-trees-from-lumberjacks",
  "description": "Protect bullet trees from enemy lumberjack destruction",
  "blocking_issue": "We're losing 91% of our trees (235/257) to lumberjacks, destroying our economy and tanking K/D ratio",
  "how_this_helps": "Defending trees preserves economy for faster unit production, prevents unit loss penalties, and enables victory in ≤1500 rounds instead of 2999",
  "metric_path": "trees_at_round.500.A",
  "operator": ">=",
  "threshold": 15,
  "max_attempts": 4,
  "attempts": 3,
  "best_result": 0,
  "created_iteration": 5
}

## Latest Match Metrics
{
  "winner": "A",
  "outcome": "WIN",
  "won": "YES",
  "rounds": 2999,
  "target_rounds": 1500,
  "goal_met": "NO",
  "final": {
    "team_a": {
      "bullets": 42,
      "vp": 0
    },
    "team_b": {
      "bullets": 42,
      "vp": 0
    }
  }
}

Unit Summary:
[
  {
    "team": "A",
    "unit": "ARCHON",
    "produced": 3,
    "lost": 0,
    "alive": 3
  },
  {
    "team": "A",
    "unit": "GARDENER",
    "produced": 9,
    "lost": 0,
    "alive": 9
  },
  {
    "team": "A",
    "unit": "SOLDIER",
    "produced": 26,
    "lost": 1,
    "alive": 25
  },
  {
    "team": "A",
    "unit": "TREE",
    "produced": 281,
    "lost": 249,
    "alive": 32
  },
  {
    "team": "B",
    "unit": "ARCHON",
    "produced": 3,
    "lost": 0,
    "alive": 3
  },
  {
    "team": "B",
    "unit": "GARDENER",
    "produced": 10,
    "lost": 1,
    "alive": 9
  },
  {
    "team": "B",
    "unit": "SOLDIER",
    "produced": 16,
    "lost": 1,
    "alive": 15
  },
  {
    "team": "B",
    "unit": "LUMBERJACK",
    "produced": 16,
    "lost": 2,
    "alive": 14
  }
]

## Objective History
# Objective History

## Completed Objectives

| # | Objective | Metric | Target | Achieved | Iteration | Locked |
|---|-----------|--------|--------|----------|-----------|--------|
| 1 | establish-tree-economy | trees_at_round.500.A >= 3 | 10 | Yes | 1 | ✅ |
| 2 | build-soldiers | unit_produced.A.SOLDIER >= 3 | 34 | Yes | 2 | ✅ |
| 3 | deal-damage | damage.A.enemy_kills >= 10 | 10 | Yes | 3 | ✅ |

## Failed/Abandoned Objectives

| # | Objective | Metric | Target | Best Result | Reason | Iteration |
|---|-----------|--------|--------|-------------|--------|-----------|
| 4 | improve-combat-efficiency | damage.A.kd_ratio >= 0.5 | 0.0625/0.5 | 0.0625 | Tree destruction (89% loss rate) makes K/D ratio impossible - 170 trees lost sabotages combat metrics | 4 |

### Attempt 2 for "defend-trees-from-lumberjacks"

**Current Value:** 0
**Target:** trees_at_round.500.A >= 15
**Change Made:** Enhanced tree-defense system with massively increased lumberjack priority (5000+ base score vs 1000), increased threat detection radius from 6f to 8f, expanded tree scanning range from 8f to 15f, added more aggressive triad shot usage against lumberjacks (up to 6f range vs 4f), and added comprehensive debug logging to track tree defense effectiveness.
**File Modified:** RobotPlayer.java
**Rationale:** The previous tree-defense system wasn't aggressive enough - with 91% tree loss rate (235/257 trees), we need extreme priority on lumberjack elimination. By massively increasing priority scores, expanding detection ranges, and using more powerful triad shots against tree threats, soldiers should focus exclusively on protecting our economy.

## Current Session Log

### Iteration 1: New Objective Proposed

**Objective:** establish-tree-economy
**Metric:** trees_at_round.500.A >= 3
**Rationale:** We have 0 trees despite having 9 gardeners - no bullet tree economy → Trees generate bullets for faster unit production, enabling victory in ≤1500 rounds

### Attempt 4 for "establish-tree-economy"

**Current Value:** 0
**Target:** trees_at_round.500.A >= 3
**Change Made:** Added tree planting logic to runGardener() method - gardeners now plant trees when they have >50 bullets
**File Modified:** RobotPlayer.java
**Rationale:** The gardener code had no plantTree() calls, explaining why we had 9 gardeners but 0 trees

### Iteration 2: New Objective Proposed

**Objective:** build-soldiers
**Metric:** unit_produced.A.SOLDIER >= 3
**Rationale:** We have no army - 0 soldiers built despite having 18 gardeners and excellent tree economy → Soldiers can actively kill enemy units and archon, enabling victory in ≤1500 rounds instead of waiting 2999 rounds

### Attempt 2 for "build-soldiers"

**Current Value:** 0
**Target:** unit_produced.A.SOLDIER >= 3
**Change Made:** Restructured gardener build priority - soldiers are now built first when we have >=100 bullets, removing the random 0.01 probability check
**File Modified:** RobotPlayer.java
**Rationale:** The original soldier building code had only 1% chance and was behind tree planting in priority. By making soldiers the priority when we have enough bullets, we should consistently build soldiers.

### Iteration 3: New Objective Proposed

**Objective:** deal-damage
**Metric:** damage.A.enemy_kills >= 10
**Rationale:** Terrible K/D ratio of 0.012 - we lose 169 units for only 2 kills → Better combat effectiveness will lead to faster victories by eliminating enemies rather than just outlasting them

### Attempt 2 for "deal-damage"

**Current Value:** 0
**Target:** damage.A.enemy_kills >= 10
**Change Made:** Enhanced soldier AI with proper targeting, movement toward enemies, and bullet dodging. Soldiers now find closest enemy, move toward them if out of range, and prioritize combat over random movement.
**File Modified:** RobotPlayer.java
**Rationale:** The original soldier code only attacked the first enemy in array and moved randomly even when enemies were present. By implementing intelligent targeting and pursuit, soldiers should engage enemies more effectively and get more kills.

### Attempt 3 for "deal-damage"

**Current Value:** 0
**Target:** damage.A.enemy_kills >= 10
**Change Made:** Fixed soldier combat priority - soldiers now fire first before moving, removed duplicate condition check, and moved dodge logic to only trigger when no enemies are present. Attack is now the absolute priority.
**File Modified:** RobotPlayer.java
**Rationale:** The previous code moved before attacking, using up the turn and preventing firing. Also had a duplicate condition check. By prioritizing attacking over moving, soldiers should actually shoot at enemies instead of just running toward them.

### Attempt 5 for "deal-damage"

**Current Value:** 0
**Target:** damage.A.enemy_kills >= 10
**Change Made:** Added extensive debug logging and bullet checking to soldier combat logic. Soldiers now explicitly check for enough bullets (>=10) before firing and log when they see enemies, fire, or can't fire. Also reduced movement threshold to get soldiers closer to enemies.
**File Modified:** RobotPlayer.java
**Rationale:** Previous attempts may have failed because soldiers weren't actually firing due to insufficient bullets or unclear firing conditions. By adding debug output and explicit bullet checking, we can identify if soldiers are engaging enemies properly and ensure they have enough bullets to fire.

### Iteration 4: New Objective Proposed

**Objective:** improve-combat-efficiency
**Metric:** damage.A.kd_ratio >= 0.5
**Rationale:** Terrible K/D ratio of 0.0625 - we lose 160 units for only 10 kills, making matches last 2999 rounds instead of ≤1500 → Better combat effectiveness means faster enemy elimination, leading to quicker victories instead of winning by attrition

### Attempt 2 for "improve-combat-efficiency"

**Current Value:** 0.0625
**Target:** damage.A.kd_ratio >= 0.5
**Change Made:** Enhanced soldier targeting system and combat aggression. Soldiers now use intelligent target scoring that prioritizes low-health enemies (easier kills) and high-value targets (Archons > Gardeners > Lumberjacks > Soldiers). Added triad shot usage when close to enemies or against multiple targets. Reduced movement threshold from 1.5f to 0.8f to get soldiers much closer for better accuracy. Soldiers now continue moving even after firing and attempt angled approaches when direct movement fails.
**File Modified:** RobotPlayer.java
**Rationale:** The previous combat system was too passive - soldiers would fire once and stop moving, or stay too far from targets for accurate shots. By implementing smart target prioritization (finishing off wounded enemies, targeting economy units), using burst shots appropriately, and getting much closer to enemies, soldiers should get significantly more kills per unit, improving the overall K/D ratio.

### Attempt 4 for "improve-combat-efficiency"

**Current Value:** 0.0625
**Target:** damage.A.kd_ratio >= 0.5
**Change Made:** Simplified soldier combat logic for maximum aggression and accuracy. Reduced movement threshold to 0.3f (very aggressive positioning), changed combat order to move first then fire for better accuracy, simplified targeting to prioritize closest enemies for faster engagement, and emphasized single shots (10 bullets) over triad shots (25 bullets) for better bullet economy. Soldiers now get much closer to targets before firing for significantly improved accuracy and kill potential.
**File Modified:** RobotPlayer.java
**Rationale:** The previous complex targeting system was causing hesitation and poor bullet economy. By simplifying to closest-target priority, moving before firing for better accuracy, getting extremely close (0.3f vs 0.8f), and using efficient single shots instead of expensive triad shots, soldiers should get many more accurate hits and kills per bullet spent, dramatically improving the K/D ratio.

### Attempt 5 for "improve-combat-efficiency"

**Current Value:** 0.0625
**Target:** damage.A.kd_ratio >= 0.5
**Change Made:** Implemented intelligent tree-defense targeting system. Soldiers now prioritize lumberjacks near our trees (highest threat to K/D ratio) with massive priority scoring (1000+ points per tree-threatened). Added tree proximity scanning, triad shots for lumberjack elimination, and patrol behavior near trees when no enemies present. Soldiers now actively defend the economy that determines our victory speed.
**File Modified:** RobotPlayer.java
**Rationale:** The root cause of terrible K/D ratio was tree destruction - 191 trees produced, 170 lost. Each tree death hurts the ratio dramatically. Enemy lumberjacks were massacring our trees with no defense. By making soldiers prioritize tree-destroying lumberjacks above all else (using priority scoring: lumberjacks near trees > archons > gardeners > soldiers), using triad shots against high-value tree threats, and patrolling near trees when idle, soldiers should eliminate the primary cause of our poor K/D ratio and dramatically improve combat efficiency.

### Objective Reassessment: improve-combat-efficiency

**Status:** DECOMPOSED
**Best Result:** 0.0625 / 0.5
**Attempts Used:** 5
**Reason:** Tree destruction (89% loss rate, 170/191 trees) makes K/D ratio impossible - trees count as unit losses but can't fight back, sabotaging combat metrics
**Next Step:** Solve tree defense first with new objective "defend-trees-from-lumberjacks" (target: ≤50 trees lost)

### Iteration 5: New Objective Proposed

**Objective:** defend-trees-from-lumberjacks
**Metric:** trees_at_round.500.A >= 15
**Rationale:** We're losing 91% of our trees (235/257) to lumberjacks, destroying our economy and tanking K/D ratio → Defending trees preserves economy for faster unit production, prevents unit loss penalties, and enables victory in ≤1500 rounds instead of 2999
## Bot Code

### RobotPlayer.java
```java
package big_pickle;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
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
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && Math.random() < .01) {
                    rc.hireGardener(dir);
                }

                // Move randomly
                tryMove(randomDirection());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                // Prioritize building soldiers if we have enough bullets
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.getTeamBullets() >= 100) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    System.out.println("Built a soldier! Bullets: " + rc.getTeamBullets());
                }
                // Plant trees if we have enough bullets
                else if (rc.getTeamBullets() > 50 && rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    System.out.println("Planted a tree! Bullets: " + rc.getTeamBullets());
                }
                // Randomly attempt to build a lumberjack in this direction
                else if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < .01 && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                }

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
        Team enemy = rc.getTeam().opponent();
        Team myTeam = rc.getTeam();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Debug bullet count and tree status
                if (rc.getRoundNum() % 50 == 0) {
                    TreeInfo[] myTrees = rc.senseNearbyTrees(20f, myTeam);
                    System.out.println("Soldier bullets: " + rc.getTeamBullets() + ", Nearby trees: " + myTrees.length);
                }

                // If there are enemies...
                if (robots.length > 0) {
                    System.out.println("Soldier sees " + robots.length + " enemies!");
                    
                    // PRIORITY TARGETING: Lumberjacks near our trees are #1 priority (they destroy K/D ratio)
                    RobotInfo highestPriorityTarget = null;
                    float highestPriorityScore = -9999f;
                    
                    // Increased tree detection range for better threat assessment
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(15f, myTeam);
                    System.out.println("Tree defense scan: " + nearbyTrees.length + " trees in range");
                    
                    for (RobotInfo enemyRobot : robots) {
                        float score = 0f;
                        
                        // LUMBERJACKS get MASSIVE priority if near our trees
                        if (enemyRobot.type == RobotType.LUMBERJACK) {
                            // Count trees near this lumberjack - increased threat radius
                            int treesNearLumberjack = 0;
                            float minTreeDist = 999f;
                            for (TreeInfo tree : nearbyTrees) {
                                float distToTree = enemyRobot.location.distanceTo(tree.location);
                                if (distToTree < 8f) { // Increased threat radius from 6f to 8f
                                    treesNearLumberjack++;
                                    if (distToTree < minTreeDist) {
                                        minTreeDist = distToTree;
                                    }
                                }
                            }
                            // MASSIVELY increased priority score - lumberjacks near trees are CRITICAL threat
                            score += 5000f + (treesNearLumberjack * 2000f) - (minTreeDist * 100f);
                            System.out.println("CRITICAL: LUMBERJACK near " + treesNearLumberjack + " trees! Closest tree: " + minTreeDist + " Priority score: " + score);
                        }
                        
                        // ARCHONS get high priority (victory condition)
                        else if (enemyRobot.type == RobotType.ARCHON) {
                            score += 500f;
                        }
                        
                        // GARDENERS get good priority (economy)
                        else if (enemyRobot.type == RobotType.GARDENER) {
                            score += 200f;
                        }
                        
                        // SOLDIERS get base priority
                        else if (enemyRobot.type == RobotType.SOLDIER) {
                            score += 100f;
                        }
                        
                        // Distance factor (closer = higher priority)
                        float distance = myLocation.distanceTo(enemyRobot.location);
                        score -= distance * 10f;
                        
                        // Health factor (lower health = slightly higher priority - easier kill)
                        score += (100f - enemyRobot.health) * 0.5f;
                        
                        if (score > highestPriorityScore) {
                            highestPriorityScore = score;
                            highestPriorityTarget = enemyRobot;
                        }
                    }
                    
                    RobotInfo target = highestPriorityTarget;
                    float closestDist = myLocation.distanceTo(target.location);
                    Direction toEnemy = myLocation.directionTo(target.location);
                    
                    System.out.println("TARGETING: " + target.type + " with priority score: " + highestPriorityScore + " Distance: " + closestDist);
                    
                    // Get much closer for better accuracy - significantly reduced threshold
                    float moveThreshold = rc.getType().bodyRadius + 0.3f; // Very aggressive positioning
                    
                    // Move first to get closer, then fire (improved accuracy)
                    boolean hasMoved = false;
                    if (closestDist > moveThreshold) {
                        // Try multiple directions to get closer
                        if (tryMove(toEnemy)) {
                            hasMoved = true;
                            System.out.println("Soldier moved closer, distance: " + closestDist);
                        } else {
                            // If direct movement fails, try aggressive angled approaches
                            for (int i = 10; i <= 30; i += 10) {
                                if (tryMove(toEnemy.rotateLeftDegrees(i)) || tryMove(toEnemy.rotateRightDegrees(i))) {
                                    hasMoved = true;
                                    System.out.println("Soldier moved aggressively to engage");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // After moving (or if already close), fire if possible
                    // PRIORITIZE TRIAD SHOTS against lumberjacks (they're high-value tree-destroying targets)
                    // INCREASED triad usage - use at longer range and with more lenient bullet requirements
                    if (target.type == RobotType.LUMBERJACK && closestDist < 6f && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("Soldier FIRED TRIAD at LUMBERJACK threatening trees! Distance: " + closestDist + " Health: " + target.health);
                    }
                    // Also use triad against lumberjacks at even closer range
                    else if (target.type == RobotType.LUMBERJACK && closestDist < 3f && rc.canFireTriadShot() && rc.getTeamBullets() >= 20) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("Soldier FIRED CLOSE TRIAD at LUMBERJACK! Distance: " + closestDist + " Health: " + target.health);
                    }
                    // Otherwise use single shots for bullet economy
                    else if (rc.canFireSingleShot() && rc.getTeamBullets() >= 10) {
                        rc.fireSingleShot(toEnemy);
                        System.out.println("Soldier FIRED at " + target.type + "! Distance: " + closestDist + " Health: " + target.health);
                    }
                    // Use triad in crowded situations too
                    else if (closestDist < 3 && robots.length >= 3 && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("Soldier FIRED TRIAD (crowded) at " + target.type);
                    }
                    
                    // If we couldn't move and are still far, try flanking
                    if (!hasMoved && closestDist > moveThreshold + 0.5f) {
                        Direction flankDir = toEnemy.rotateLeftDegrees(90);
                        if (!tryMove(flankDir)) {
                            tryMove(toEnemy.rotateRightDegrees(90));
                        }
                    }
                } else {
                    // No enemies, patrol near our trees to protect them
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(15f, myTeam); // Increased patrol range
                    boolean dodged = false;
                    
                    // First check for bullets and try to dodge
                    BulletInfo[] bullets = rc.senseNearbyBullets();
                    if (bullets.length > 0) {
                        for (BulletInfo bullet : bullets) {
                            if (willCollideWithMe(bullet)) {
                                // Try to dodge the bullet
                                Direction dodgeDir = bullet.dir.rotateLeftDegrees(90);
                                if (!rc.canMove(dodgeDir)) {
                                    dodgeDir = bullet.dir.rotateRightDegrees(90);
                                }
                                if (rc.canMove(dodgeDir)) {
                                    tryMove(dodgeDir);
                                    dodged = true;
                                }
                                break;
                            }
                        }
                    }
                    
                    // If no bullets to dodge, move toward trees to protect them
                    if (!dodged) {
                        if (nearbyTrees.length > 0) {
                            // Move toward the furthest tree to spread out protection
                            TreeInfo furthestTree = nearbyTrees[0];
                            float maxDist = myLocation.distanceTo(furthestTree.location);
                            for (TreeInfo tree : nearbyTrees) {
                                float dist = myLocation.distanceTo(tree.location);
                                if (dist > maxDist) {
                                    maxDist = dist;
                                    furthestTree = tree;
                                }
                            }
                            Direction toTree = myLocation.directionTo(furthestTree.location);
                            tryMove(toTree);
                            System.out.println("Soldier patrolling to protect " + nearbyTrees.length + " trees");
                        } else {
                            // No trees nearby, move randomly
                            tryMove(randomDirection());
                        }
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
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

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}```
