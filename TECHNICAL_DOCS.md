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
*   **Strategy:** Hide, spread out to avoid AoE, hire gardeners safely.

### **Gardener** (Builder/Worker)
*   **Role:** Economy & Production.
*   **Stats:** Low HP, cannot attack.
*   **Key Actions:**
    *   `rc.plantTree(Direction dir)`: Creates Bullet Tree.
    *   `rc.water(treeID)`: Heals trees (essential for Bullet Trees).
    *   `rc.buildRobot(RobotType type, Direction dir)`: Builds Soldier, Tank, Scout, Lumberjack.
*   **Strategy:** Find open space. Build a "farm" (hexagonal packing of trees with space for the gardener in the center). Don't block your own movement.

### **Soldier** (Main Combat)
*   **Role:** Ranged DPS.
*   **Stats:** Balanced HP, damage, and range.
*   **Key Actions:** `rc.fireSingleShot(dir)`, `rc.fireTriadShot(dir)`, `rc.firePentadShot(dir)`.
*   **Strategy:** Kite enemies (move back while shooting). Micro-manage movement to dodge bullets.

### **Lumberjack** (Melee/Utility)
*   **Role:** Tree clearing & Area-of-Effect (AoE) damage.
*   **Stats:** Tanky, short range.
*   **Key Actions:**
    *   `rc.chop(treeID)`: Destroys trees.
    *   `rc.strike()`: Deals damage to ALL units (friend or foe) within radius 2.
*   **Strategy:** Clear Neutral Trees to make space for Gardeners. Rush Archons/Gardeners (melee deals high damage).

### **Scout** (Recon/Harass)
*   **Role:** Vision & Harassment.
*   **Stats:** Very fast, huge vision radius, extremely low HP.
*   **Key Actions:** `rc.shake(id)`.
*   **Strategy:** Find enemy Gardener farms and shoot them from safe range. Shake neutral trees for early game economy. Report enemy positions via Broadcast.

### **Tank** (Heavy Combat)
*   **Role:** Siege unit.
*   **Stats:** High HP, High Damage, expensive.
*   **Strategy:** Late-game steamroller. Body slams destroy trees.

## 5. Key API Methods (`RobotController rc`)

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

## 6. Code Structure Template
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

## 7. Implementation Requirements for Competence

1.  **Movement Engine:** Do not just `rc.move(dir)`. Implement a `tryMove(dir)` helper that checks `rc.canMove(dir)` and tries rotated angles if blocked (simple obstacle avoidance).
2.  **Combat Micro:**
    *   **Dodging:** Before moving, check `rc.senseNearbyBullets()`. If a bullet will hit the robot next turn, move perpendicular to its path.
    *   **Targeting:** Focus fire on the enemy with the lowest HP (use `robotInfo.health`).
3.  **Macro Strategy:**
    *   **Early Game:** Archons hire 1 Gardener. Gardener builds 1 Scout (for bullets/scouting) then Lumberjacks (to clear space).
    *   **Mid Game:** Gardeners settle in open areas and plant trees (leave 1 spot open to build units).
    *   **Late Game:** Spam Soldiers/Tanks. Donate excess bullets to VP if victory is near or bullet cap is reached.
4.  **Gardener Logic (Crucial):**
    *   Gardeners must not block their own build direction.
    *   Pattern: Build trees in a circle around the gardener.
    *   **Watering:** Always prioritize `rc.water()` on the tree with lowest health within range.

## 8. Common Pitfalls to Avoid
*   **Friendly Fire:** `rc.strike()` hits allies. `rc.fire...()` hits allies if they are in the line of fire. Check line of sight before shooting.
*   **Bytecode Limit:** Avoid heavy loops (like pathfinding across the whole map) in a single turn.
*   **Stuck Units:** Gardeners often trap themselves with trees. Ensure they keep a "door" open or leave space to move.

## 9. Example "Smart" Move Helper
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
