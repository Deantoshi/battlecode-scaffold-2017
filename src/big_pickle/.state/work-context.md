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
  "attempts": 4,
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
      "bullets": 67,
      "vp": 0
    },
    "team_b": {
      "bullets": 38,
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
    "produced": 6,
    "lost": 0,
    "alive": 6
  },
  {
    "team": "A",
    "unit": "SOLDIER",
    "produced": 39,
    "lost": 0,
    "alive": 39
  },
  {
    "team": "A",
    "unit": "TREE",
    "produced": 164,
    "lost": 151,
    "alive": 13
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
    "produced": 9,
    "lost": 0,
    "alive": 9
  },
  {
    "team": "B",
    "unit": "SOLDIER",
    "produced": 12,
    "lost": 0,
    "alive": 12
  },
  {
    "team": "B",
    "unit": "LUMBERJACK",
    "produced": 21,
    "lost": 0,
    "alive": 21
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

### Attempt 3 for "defend-trees-from-lumberjacks"

**Current Value:** 0
**Target:** trees_at_round.500.A >= 15
**Change Made:** Drastically improved soldier deployment speed and tree defense focus. Lowered soldier build threshold from 100 to 60 bullets for 40% faster deployment, made gardeners build soldiers aggressively when we have >3 trees to protect, simplified soldier targeting to focus purely on lumberjack threats (10000+ priority score for tree-threatening lumberjacks), improved patrol logic to protect vulnerable trees, and made soldiers use triad shots against high-value tree threats.
**File Modified:** RobotPlayer.java
**Rationale:** The root issue was that soldiers weren't being built fast enough to protect trees - with a 91% loss rate, we need immediate military response. By lowering the build cost threshold by 40%, prioritizing soldiers when we have trees to defend, and making soldiers laser-focused on lumberjack elimination, we should get defensive units deployed much faster and keep our tree economy intact.

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

                // Check how many trees we have to defend
                TreeInfo[] myTrees = rc.senseNearbyTrees(20f, rc.getTeam());
                boolean needTreeDefense = myTrees.length > 3; // Need defense if we have >3 trees

                // Generate a random direction
                Direction dir = randomDirection();

                // PRIORITY: Build soldiers faster for tree defense
                // Lower threshold and higher priority when we have trees to protect
                int soldierBuildCost = 60; // Reduced from 100 for faster deployment
                boolean canAffordSoldier = rc.getTeamBullets() >= soldierBuildCost;
                
                if (needTreeDefense && canAffordSoldier && rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    System.out.println("URGENT: Built soldier for tree defense! Trees: " + myTrees.length + " Bullets: " + rc.getTeamBullets());
                }
                // Normal soldier building when no urgent tree defense needed
                else if (canAffordSoldier && rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    System.out.println("Built a soldier! Bullets: " + rc.getTeamBullets());
                }
                // Plant trees if we have enough bullets and don't urgently need defense
                else if (rc.getTeamBullets() > 50 && rc.canPlantTree(dir) && !needTreeDefense) {
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

                // SIMPLIFIED TREE DEFENSE: Focus exclusively on lumberjack threats
                if (robots.length > 0) {
                    System.out.println("Soldier sees " + robots.length + " enemies!");
                    
                    // Find highest priority target: Lumberjacks near trees > everything else
                    RobotInfo highestPriorityTarget = null;
                    float highestPriorityScore = -9999f;
                    
                    // Scan for our trees to assess threats
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(15f, myTeam);
                    System.out.println("Tree defense scan: " + nearbyTrees.length + " trees in range");
                    
                    for (RobotInfo enemyRobot : robots) {
                        float score = 0f;
                        
                        // LUMBERJACKS get MASSIVE priority if near our trees (they destroy our economy)
                        if (enemyRobot.type == RobotType.LUMBERJACK) {
                            // Count trees this lumberjack threatens
                            int treesThreatened = 0;
                            float minTreeDist = 999f;
                            for (TreeInfo tree : nearbyTrees) {
                                float distToTree = enemyRobot.location.distanceTo(tree.location);
                                if (distToTree < 8f) { // Lumberjack attack range + buffer
                                    treesThreatened++;
                                    if (distToTree < minTreeDist) {
                                        minTreeDist = distToTree;
                                    }
                                }
                            }
                            // MASSIVE priority for tree-destroying lumberjacks
                            score += 10000f + (treesThreatened * 5000f) - (minTreeDist * 200f);
                            System.out.println("CRITICAL THREAT: LUMBERJACK threatening " + treesThreatened + " trees! Score: " + score);
                        }
                        
                        // ARCHONS get high priority (victory condition)
                        else if (enemyRobot.type == RobotType.ARCHON) {
                            score += 1000f;
                        }
                        
                        // GARDENERS get good priority (economy)
                        else if (enemyRobot.type == RobotType.GARDENER) {
                            score += 500f;
                        }
                        
                        // SOLDIERS get base priority
                        else if (enemyRobot.type == RobotType.SOLDIER) {
                            score += 200f;
                        }
                        
                        // Distance factor (closer = higher priority)
                        float distance = myLocation.distanceTo(enemyRobot.location);
                        score -= distance * 50f;
                        
                        if (score > highestPriorityScore) {
                            highestPriorityScore = score;
                            highestPriorityTarget = enemyRobot;
                        }
                    }
                    
                    RobotInfo target = highestPriorityTarget;
                    float closestDist = myLocation.distanceTo(target.location);
                    Direction toEnemy = myLocation.directionTo(target.location);
                    
                    System.out.println("TARGETING: " + target.type + " Priority: " + highestPriorityScore + " Distance: " + closestDist);
                    
                    // AGGRESSIVE positioning: Get very close for maximum accuracy
                    float moveThreshold = rc.getType().bodyRadius + 0.1f; // Extremely aggressive
                    
                    // Move first if not in optimal range
                    boolean hasMoved = false;
                    if (closestDist > moveThreshold) {
                        if (tryMove(toEnemy)) {
                            hasMoved = true;
                            System.out.println("Soldier moved aggressively, distance: " + closestDist);
                        } else {
                            // Try flanking if direct approach fails
                            for (int i = 15; i <= 45; i += 15) {
                                if (tryMove(toEnemy.rotateLeftDegrees(i)) || tryMove(toEnemy.rotateRightDegrees(i))) {
                                    hasMoved = true;
                                    System.out.println("Soldier flanked to engage");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Fire with priority: Use triad shots against high-value lumberjack threats
                    if (target.type == RobotType.LUMBERJACK && highestPriorityScore > 10000 && closestDist < 6f && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("URGENT: FIRED TRIAD at tree-threatening LUMBERJACK! Distance: " + closestDist);
                    }
                    // Regular single shots for normal combat
                    else if (rc.canFireSingleShot() && rc.getTeamBullets() >= 10) {
                        rc.fireSingleShot(toEnemy);
                        System.out.println("FIRED at " + target.type + "! Distance: " + closestDist);
                    }
                    // Triad shots for crowded combat
                    else if (closestDist < 4f && robots.length >= 3 && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("FIRED TRIAD (crowded) at " + target.type);
                    }
                } else {
                    // No enemies, prioritize tree protection patrol
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(20f, myTeam); // Increased patrol range
                    boolean dodged = false;
                    
                    // First check for bullets and try to dodge
                    BulletInfo[] bullets = rc.senseNearbyBullets();
                    if (bullets.length > 0) {
                        for (BulletInfo bullet : bullets) {
                            if (willCollideWithMe(bullet)) {
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
                    
                    // If no bullets to dodge, patrol strategically around trees
                    if (!dodged) {
                        if (nearbyTrees.length > 0) {
                            // Find the most isolated tree that needs protection
                            TreeInfo mostVulnerableTree = null;
                            float maxDistFromOthers = -1f;
                            
                            for (TreeInfo tree : nearbyTrees) {
                                float minDistToOtherTrees = 999f;
                                for (TreeInfo otherTree : nearbyTrees) {
                                    if (tree != otherTree) {
                                        float dist = tree.location.distanceTo(otherTree.location);
                                        if (dist < minDistToOtherTrees) {
                                            minDistToOtherTrees = dist;
                                        }
                                    }
                                }
                                if (minDistToOtherTrees > maxDistFromOthers) {
                                    maxDistFromOthers = minDistToOtherTrees;
                                    mostVulnerableTree = tree;
                                }
                            }
                            
                            if (mostVulnerableTree != null) {
                                Direction toVulnerableTree = myLocation.directionTo(mostVulnerableTree.location);
                                tryMove(toVulnerableTree);
                                System.out.println("Patrolling to protect vulnerable tree (" + nearbyTrees.length + " total)");
                            }
                        } else {
                            // No trees nearby, move toward likely tree locations
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
