# Battlecode 2017 Bot Generation Guide

## 1. Project Overview & Constraints
*   **Language:** Java 8 (StrictFP).
*   **Entry Point:** `RobotPlayer.java` in any package (e.g., `team01`).
*   **Main Method:** `public static void run(RobotController rc)`.
*   **Execution Model:** The `run` method is called once when the robot spawns. It **must** enter a `while(true)` loop. Inside the loop, `Clock.yield()` must be called at the end of every turn to pass execution to the next round.
*   **Bytecode Limit:** Each robot has a limited amount of computation per turn (`Clock.getBytecodesLeft()`). Exceeding it causes the robot to freeze for the turn.
*   **Exceptions:** Unhandled exceptions cause the robot to explode (die). Always wrap logic in `try-catch` blocks.

## 2. Victory Conditions
1.  **Victory Points (VP):** First team to reach **1000 VP** wins instantly.
    *   Earned by donating bullets via `rc.donate(float bullets)`.
    *   Cost starts at 7.5 bullets/VP and increases over time.
2.  **Destruction:** Destroy all opposing Archons and units (excluding trees).
3.  **Tiebreakers:** If round limit (3000) is reached: highest VP > most bullet trees > most resources.

## 3. Economy System
*   **Bullets:** The universal currency. Used for building units, attacking, and buying VP.
*   **Income Sources:**
    *   **Archons:** generate small passive income.
    *   **Bullet Trees:** Planted by Gardeners. Main source of income. Must be watered.
    *   **Shaking:** Units can `rc.shake(treeID)` neutral or enemy trees to steal contained bullets.

## 4. Robot Types & Roles

### **Archon** (HQ)
*   **Role:** Mobile base. Hires Gardeners.
*   **Stats:** High HP, cannot attack.
*   **Key Actions:** `rc.hireGardener(Direction dir)`.

### **Gardener** (Builder/Worker)
*   **Role:** Economy & Production.
*   **Stats:** Low HP, cannot attack.
*   **Key Actions:**
    *   `rc.plantTree(Direction dir)`: Creates Bullet Tree.
    *   `rc.water(treeID)`: Heals trees (essential for Bullet Trees).
    *   `rc.buildRobot(RobotType type, Direction dir)`: Builds Soldier, Tank, Scout, Lumberjack.

### **Soldier** (Main Combat)
*   **Role:** Ranged DPS.
*   **Stats:** Balanced HP, damage, and range.
*   **Key Actions:** `rc.fireSingleShot(dir)`, `rc.fireTriadShot(dir)`, `rc.firePentadShot(dir)`.

### **Lumberjack** (Melee/Utility)
*   **Role:** Tree clearing & Area-of-Effect (AoE) damage.
*   **Stats:** Tanky, short range.
*   **Key Actions:**
    *   `rc.chop(treeID)`: Destroys trees.
    *   `rc.strike()`: Deals damage to ALL units (friend or foe) within radius 2.

### **Scout** (Recon/Harass)
*   **Role:** Vision & Harassment.
*   **Stats:** Very fast, huge vision radius, extremely low HP.
*   **Key Actions:** `rc.shake(id)`.

### **Tank** (Heavy Combat)
*   **Role:** Siege unit.
*   **Stats:** High HP, High Damage, expensive.

## 5. Trees as Map Obstacles

Trees are physical obstacles that block movement and line-of-sight for bullets. Understanding how to clear trees is essential for navigation and combat.

### **Tree Types**
*   **Neutral Trees:** Pre-placed on map. Often contain bullets that can be shaken.
*   **Bullet Trees:** Planted by Gardeners. Generate income for the owning team.
*   **Team Trees:** Belong to a team. Destroying enemy trees hurts their economy.

### **Why Clear Trees?**
*   Open pathways for unit movement
*   Create firing lanes for ranged units
*   Deny enemy economy (destroy their Bullet Trees)
*   Collect bullets from neutral trees before destroying

### **Methods to Destroy Trees**

#### **1. Lumberjack Chopping (Most Efficient)**
Lumberjacks deal **5 damage per chop** to a single tree. This is the most bytecode-efficient and intentional method.

```java
// In Lumberjack logic
TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL); // or Team.B for enemy
if (trees.length > 0) {
    TreeInfo target = trees[0];
    // Shake first to collect bullets (if neutral)
    if (rc.canShake(target.ID) && target.containedBullets > 0) {
        rc.shake(target.ID);
    }
    // Then chop
    if (rc.canChop(target.ID)) {
        rc.chop(target.ID);
    }
}
```

#### **2. Soldier/Tank Shooting**
Bullets that hit trees deal damage. Soldiers can clear trees at range but waste ammo.

```java
// In Soldier logic - shooting at a tree blocking path
TreeInfo[] blockingTrees = rc.senseNearbyTrees(3.0f, Team.NEUTRAL);
for (TreeInfo tree : blockingTrees) {
    Direction toTree = rc.getLocation().directionTo(tree.location);
    if (rc.canFireSingleShot()) {
        rc.fireSingleShot(toTree);
        break;
    }
}
```

#### **3. Tank Trampling (Unique Ability)**
Tanks can **move through trees**, destroying them on contact. This makes Tanks excellent for clearing dense forests.

```java
// In Tank logic - intentionally path through trees
TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
if (trees.length > 0) {
    Direction toTree = rc.getLocation().directionTo(trees[0].location);
    // Tanks can move into trees - the tree is destroyed
    if (rc.canMove(toTree)) {
        rc.move(toTree);
    }
}
```

> **Note:** Tank trampling is instant and doesn't cost bullets, but Tanks are expensive (300 bullets). Use for strategic forest clearing.

#### **4. Lumberjack Strike (AoE Tree Damage)**
`rc.strike()` damages ALL trees (and units) within radius 2, dealing **2 damage** to each.

```java
// Clear multiple trees at once (careful of friendly fire!)
TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f);
RobotInfo[] nearbyFriendlies = rc.senseNearbyRobots(2.0f, rc.getTeam());
// Only strike if trees present and no friendlies in range
if (nearbyTrees.length > 2 && nearbyFriendlies.length == 0) {
    if (rc.canStrike()) {
        rc.strike();
    }
}
```

### **Tree Health Reference**
| Tree Type | Starting Health |
|-----------|-----------------|
| Small Neutral | ~20-50 HP |
| Large Neutral | ~100-300 HP |
| Bullet Tree | 100 HP (max when fully grown) |

## 6. Key API Methods (`RobotController rc`)

### **Sensing**
*   `rc.senseNearbyRobots(radius, team)`: Returns `RobotInfo[]`.
*   `rc.senseNearbyTrees(radius, team)`: Returns `TreeInfo[]`.
*   `rc.senseNearbyBullets(radius)`: Returns `BulletInfo[]` (crucial for dodging).
*   `rc.getLocation()`: Returns current `MapLocation`.

### **Movement**
*   `rc.canMove(Direction)`: Checks physics (terrain, units, trees).
*   `rc.move(Direction)`: Moves the robot.
*   **Pathfinding:** Use "Bug 0" or simple obstacle avoidance (try desired dir, then +/- degrees).

### **Communication (Broadcasting)**
*   `rc.broadcast(int channel, int data)`: Writes to a shared array (channels 0-9999).
*   `rc.readBroadcast(int channel)`: Reads from the array.
*   **Usage:** Archons broadcast their location so Gardeners know where to spawn. Scouts broadcast enemy locations.

## 7. Code Structure Template
The generated bot **must** follow this pattern:

```java
package team01;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        
        // Main Loop
        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:      runArchon();      break;
                    case GARDENER:    runGardener();    break;
                    case SOLDIER:     runSoldier();     break;
                    case LUMBERJACK:  runLumberjack();  break;
                    case SCOUT:       runScout();       break;
                    case TANK:        runTank();        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // REQUIRED: End turn
                Clock.yield();
            }
        }
    }
    // ... Implement runArchon, runGardener, etc.
}
```

## 8. File Structure Recommendations (Top Team Practices)

Top teams almost always used multiple files for maintainability and iteration speed. A very common pattern was:

### RobotPlayer.java as a Dispatcher
```java
public static void run(RobotController rc) {
    switch (rc.getType()) {
        case ARCHON:     Archon.run(rc);     break;
        case GARDENER:   Gardener.run(rc);   break;
        case SOLDIER:    Soldier.run(rc);    break;
        case LUMBERJACK: Lumberjack.run(rc); break;
        case SCOUT:      Scout.run(rc);      break;
        case TANK:       Tank.run(rc);       break;
    }
}
```

### Supporting Classes
- **Navigation/Pathfinding** - Bug navigation, obstacle avoidance
- **Combat Micro** - Targeting, dodging, kiting logic
- **Broadcast/Communication** - Channel encoding, message protocols
- **Map Analysis** - Terrain evaluation, enemy tracking
- **Utilities** - Bit packing, geometry helpers, caching, constants

### Typical 2017 Project Layout
```
src/<yourbotpackage>/
├── RobotPlayer.java   # Required entry point (dispatcher)
├── Archon.java        # Archon-specific logic
├── Gardener.java      # Gardener-specific logic
├── Soldier.java       # Soldier-specific logic
├── Lumberjack.java    # Lumberjack-specific logic
├── Scout.java         # Scout-specific logic
├── Tank.java          # Tank-specific logic
├── Nav.java           # Navigation/pathfinding utilities
├── Comms.java         # Broadcast communication helpers
└── Utils.java         # Shared utility functions
```

> **Note:** While you can technically put multiple classes in one `.java` file (non-public helper classes), this is uncommon among strong teams because it slows development and makes iteration harder.

## 9. Implementation Requirements for Competence

1.  **Movement Engine:** Do not just `rc.move(dir)`. Implement a `tryMove(dir)` helper that checks `rc.canMove(dir)` and tries rotated angles if blocked (simple obstacle avoidance).
2.  **Combat Micro:**
    *   **Dodging:** Before moving, check `rc.senseNearbyBullets()`. If a bullet will hit the robot next turn, move perpendicular to its path.
    *   **Targeting:** Focus fire on the enemy with the lowest HP (use `robotInfo.health`).
3.  **Gardener Logic (Crucial):**
    *   Gardeners must not block their own build direction.
    *   Pattern: Build trees in a circle around the gardener.
    *   **Watering:** Always prioritize `rc.water()` on the tree with lowest health within range.

## 10. Engine References (Under the Hood)
*   `engine/battlecode/common/`: Bot-facing types and constants (e.g., `RobotType`, `GameConstants`, `MapLocation`, `Direction`).
*   `engine/battlecode/world/`: Core simulation (robots, bullets, trees, collisions, `RobotControllerImpl`).
    *   `engine/battlecode/world/GameWorld.java`: Main simulation loop and world state updates.
    *   `engine/battlecode/world/RobotControllerImpl.java`: Actual implementation behind the `RobotController` API.
    *   `engine/battlecode/world/InternalRobot.java`: Per-robot state and turn execution.
    *   `engine/battlecode/world/InternalBullet.java`: Bullet movement and collision handling.
    *   `engine/battlecode/world/InternalTree.java`: Tree state, growth, and interactions.
*   `engine/battlecode/server/`: Match orchestration, round loop, win conditions, logging.
    *   `engine/battlecode/server/GameRunner.java`: Match lifecycle and round progression.
*   `engine/battlecode/schema/`: Serialized match state and replay data.
*   `engine/battlecode/doc/`: Engine notes and documentation (if present).

## 11. Example "Smart" Move Helper
```java
static void tryMove(Direction dir) throws GameActionException {
    if (rc.canMove(dir)) {
        rc.move(dir);
    } else {
        // Simple "hug" navigation
        for (int i = 0; i < 3; i++) {
             // Try left and right at increasing angles
             // ... implementation
        }
    }
}
```
