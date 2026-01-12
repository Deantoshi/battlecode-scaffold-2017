# Battlecode 2017 Map Creation & Editing Guide

This guide provides technical details and workflows for creating and editing Battlecode 2017 maps (.map17 files).

## Table of Contents
- [Overview](#overview)
- [Map File Format](#map-file-format)
- [Available Gradle Tasks](#available-gradle-tasks)
- [Java Utilities](#java-utilities)
- [Creating New Maps](#creating-new-maps)
- [Editing Existing Maps](#editing-existing-maps)
- [Map Design Patterns](#map-design-patterns)
- [Technical Details](#technical-details)

---

## Overview

Battlecode 2017 maps use the **FlatBuffer** binary format (.map17) containing:
- Map dimensions and origin
- Initial robot positions (Archons)
- Neutral tree positions (with radius, health, bullets)
- Random seed for game

### Key Constraints
- **Map size**: Typically 80x100 to 100x100 units
- **Tree radius**: Default 2.5 units (5 units diameter)
- **Recommended tree spacing**: 6.0 units (2.5 + 1.0 + 2.5) to prevent overlap
- **Archon count**: 1-3 per team (affects starting economy)

---

## Map File Format

Maps are stored in FlatBuffer format defined in `engine/battlecode/schema/`. The Java serializer/deserializer is in `engine/battlecode/world/GameMapIO.java`.

### Main Components

#### 1. Map Metadata
```java
float width, height;           // Map dimensions
MapLocation origin;            // Usually (0,0)
String mapName;               // Filename without .map17
int seed;                     // Random seed
```

#### 2. Robots (SpawnedBodyTable)
```java
int[] robotIDs;
int[] teamIDs;                // Team.A=0, Team.B=1
byte[] types;                 // BodyType enum
float[] xs, ys;               // Coordinates
```

#### 3. Neutral Trees (NeutralTreeTable)
```java
int[] robotIDs;               // Unique tree IDs
float[] xs, ys;              // Coordinates
float[] radii;                // Tree radius (typically 2.5)
int[] containedBullets;        // Bullets in tree (0-100)
byte[] containedBodies;       // Robot type inside (0=none)
float[] healths, maxHealths;  // Tree health
```

---

## Available Gradle Tasks

All map-related tasks are in `build.gradle`:

### Map Information
```bash
./gradlew mapInfo -Pmap=<mapName>
```
- Prints detailed map statistics
- Shows tree density, coverage, archon positions
- Example: `./gradlew mapInfo -Pmap=300`

### Tree Operations
```bash
# Clean overlapping trees (reduce tree count, maintain coverage)
./gradlew cleanMapTrees -Pmap=<mapName> [-Poutput=<newMapName>]

# Regenerate trees with pattern
./gradlew regenerateMapTrees -Pmap=<mapName> [-Poutput=<newMapName>]

# Dump all tree positions with symmetry check
./gradlew dumpMapTrees -Pmap=<mapName>

# Visualize map as ASCII grid
./gradlew visualizeMap -Pmap=<mapName>
```

### List Available Maps
```bash
./gradlew listMaps
```

---

## Java Utilities

### Location: `engine/battlecode/util/`

#### 1. MapInfoPrinter.java
**Purpose**: Display comprehensive map statistics

**Usage**:
```bash
java battlecode.util.MapInfoPrinter <mapName>
```

**Output**:
- Map dimensions, area, origin, seed
- Archon positions and distances
- Neutral tree count and density
- Tree coverage percentage
- Terrain level (LOW/MEDIUM/HIGH)

#### 2. MapTreeCleaner.java
**Purpose**: Remove overlapping trees while maintaining coverage

**Algorithm**:
1. Sort trees by priority (bullets > radius > ID)
2. Greedy selection: keep tree if it doesn't overlap with already kept trees
3. Overlap defined as: `distance < radiusA + radiusB - 0.01`

**Usage**:
```bash
java battlecode.util.MapTreeCleaner <mapName> [outputMapName]
```

#### 3. MapTreeRegenerator.java
**Purpose**: Create symmetrical tree patterns

**Current Pattern**: Vertical wedges (^ and inverted ^)
- Top/bottom wedges with trees spanning full width at center
- Neat 6.0 unit grid spacing
- Perfect left-right and top-bottom symmetry

**Key Parameters** (can be modified):
```java
float treeRadius = 2.5f;
float treeSpacing = 6.0f;  // radius * 2 + gap
float centerLineX = mapWidth / 2.0f;
float centerLineY = mapHeight / 2.0f;
```

**Usage**:
```bash
java battlecode.util.MapTreeRegenerator <mapName> [outputMapName]
```

#### 4. MapTreeDumper.java
**Purpose**: List all tree positions and verify symmetry

**Usage**:
```bash
java battlecode.util.MapTreeDumper <mapName>
```

**Output**:
- All tree coordinates sorted by (x, y)
- Symmetry verification (checks for missing mirrors)
- Missing mirror report

#### 5. MapVisualizer.java
**Purpose**: ASCII grid visualization

**Usage**:
```bash
java battlecode.util.MapVisualizer <mapName>
```

**Legend**:
- `#` = Tree
- `1/2/3` = Team A Archons (numbered)
- `a/b/c` = Team B Archons (lettered)
- `.` = Empty space

---

## Creating New Maps

### Method 1: Copy and Edit Existing Map
```bash
# 1. Copy existing map
cp maps/300.map17 maps/my_new_map.map17

# 2. Edit using regenerator utility
./gradlew regenerateMapTrees -Pmap=my_new_map -Poutput=my_new_map

# 3. Verify
./gradlew visualizeMap -Pmap=my_new_map
```

### Method 2: Create New Utility Class

1. Create new file: `engine/battlecode/util/MyMapGenerator.java`
2. Extend base pattern from `MapTreeRegenerator.java`
3. Add to build.gradle:
```gradle
task generateMyMap(type: JavaExec, dependsOn: 'compileEngineJava') {
    description 'Generate custom map pattern.'
    group 'battlecode'
    main = 'battlecode.util.MyMapGenerator'
    classpath = sourceSets.engine.runtimeClasspath
    args = ['myNewMap']
}
```
4. Run: `./gradlew generateMyMap`

---

## Editing Existing Maps

### Workflow 1: Reduce Tree Count (Performance)
```bash
# 1. Check current tree count
./gradlew mapInfo -Pmap=300

# 2. Clean overlapping trees
./gradlew cleanMapTrees -Pmap=300

# 3. Verify improvement
./gradlew mapInfo -Pmap=300
```

**Typical results**:
- Original 300.map17: 860 trees, 208% coverage (massive overlap)
- After cleaning: 236 trees, 57% coverage

### Workflow 2: Change Tree Pattern
```bash
# Edit MapTreeRegenerator.java pattern logic
# Then regenerate
./gradlew regenerateMapTrees -Pmap=300
```

### Workflow 3: Add Archons
Edit `MapTreeRegenerator.java` in `regenerateTrees()`:
```java
// Add Team A archons
newBodies.add(createArchon(nextRobotId++, Team.A, 15.0f, 45.0f));
newBodies.add(createArchon(nextRobotId++, Team.A, 20.0f, 55.0f));

// Add Team B archons
newBodies.add(createArchon(nextRobotId++, Team.B, 75.0f, 45.0f));
newBodies.add(createArchon(nextRobotId++, Team.B, 80.0f, 55.0f));
```

### Workflow 4: Add Bullets to Trees
```java
private static TreeInfo createTreeWithBullets(int id, float x, float y, float radius, int bullets) {
    return new TreeInfo(
        id,
        Team.NEUTRAL,
        new MapLocation(x, y),
        radius,
        radius * GameConstants.NEUTRAL_TREE_HEALTH_RATE,
        bullets,  // 0-100 bullets
        null
    );
}
```

---

## Map Design Patterns

### Pattern 1: Symmetrical Wedges (Current 300.map17)
**Purpose**: Creates clear attack lanes

**Structure**:
```
# # # # # # # # # # # # # # #  (top - widest)
  # # # # # # # # # # # #
    # # # # # # # # # #
      # # # # # # #
        # # # #
         A   B              (archons)
        # # # #
      # # # # # # #
    # # # # # # # # # #
  # # # # # # # # # # # #
# # # # # # # # # # # # # # #  (bottom - widest)
```

**Implementation** (from `MapTreeRegenerator.java`):
```java
for (float y = startY; y <= endY; y += treeSpacing) {
    float normalizedDist = (y - startY) / (endY - startY);
    float maxSpreadFromCenter = (mapWidth / 2.0f) * (1.0f - normalizedDist * 0.7f);

    List<Float> xPositions = new ArrayList<>();
    for (float x = startX; x <= centerLineX + 0.5f; x += treeSpacing) {
        if (Math.abs(x - centerLineX) <= maxSpreadFromCenter) {
            xPositions.add(x);
        }
    }

    // Mirror left-right and top-bottom
    for (float x : xPositions) {
        newBodies.add(createTree(nextTreeId++, x, y, treeRadius));
        if (Math.abs(x - centerLineX) > 0.1f) {
            newBodies.add(createTree(nextTreeId++, mapWidth - x, y, treeRadius));
        }
        newBodies.add(createTree(nextTreeId++, x, mapHeight - y, treeRadius));
        if (Math.abs(x - centerLineX) > 0.1f) {
            newBodies.add(createTree(nextTreeId++, mapWidth - x, mapHeight - y, treeRadius));
        }
    }
}
```

### Pattern 2: Grid Layout
**Purpose**: Maximum coverage, simple navigation

```java
float startX = 3.0f;
float endX = mapWidth - 3.0f;
float startY = 3.0f;
float endY = mapHeight - 3.0f;

for (float x = startX; x <= endX; x += 6.0f) {
    for (float y = startY; y <= endY; y += 6.0f) {
        newBodies.add(createTree(nextTreeId++, x, y, 2.5f));
    }
}
```

### Pattern 3: Forest Edges
**Purpose**: Walls on borders, open center

```java
float treeRadius = 2.5f;
float treeSpacing = 6.0f;

// Left edge
for (float y = treeRadius; y <= mapHeight - treeRadius; y += treeSpacing) {
    newBodies.add(createTree(nextTreeId++, treeRadius, y, treeRadius));
}

// Right edge
for (float y = treeRadius; y <= mapHeight - treeRadius; y += treeSpacing) {
    newBodies.add(createTree(nextTreeId++, mapWidth - treeRadius, y, treeRadius));
}

// Top edge
for (float x = treeRadius; x <= mapWidth - treeRadius; x += treeSpacing) {
    newBodies.add(createTree(nextTreeId++, x, treeRadius, treeRadius));
}

// Bottom edge
for (float x = treeRadius; x <= mapWidth - treeRadius; x += treeSpacing) {
    newBodies.add(createTree(nextTreeId++, x, mapHeight - treeRadius, treeRadius));
}
```

### Pattern 4: Clusters
**Purpose**: Resource-rich areas

```java
// Create 4 tree clusters
float[][] clusterCenters = {
    {20.0f, 20.0f},  // top-left
    {70.0f, 20.0f},  // top-right
    {20.0f, 70.0f},  // bottom-left
    {70.0f, 70.0f}   // bottom-right
};

for (float[] center : clusterCenters) {
    float cx = center[0];
    float cy = center[1];

    // Create cluster in hex pattern
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            float x = cx + i * 6.0f;
            float y = cy + j * 6.0f;
            if (x > 2.5f && x < mapWidth - 2.5f && y > 2.5f && y < mapHeight - 2.5f) {
                newBodies.add(createTree(nextTreeId++, x, y, 2.5f));
            }
        }
    }
}
```

---

## Technical Details

### Tree Overlap Detection
```java
private static boolean treesOverlap(TreeInfo a, TreeInfo b) {
    float dx = a.location.x - b.location.x;
    float dy = a.location.y - b.location.y;
    float distance = (float) Math.sqrt(dx * dx + dy * dy);
    return distance < (a.radius + b.radius - 0.01f);
}
```

### Mirror Generation (Symmetry)
**For a tree at (x, y) on a map of (width, height)**:

- **Left-right mirror**: `(width - x, y)`
- **Top-bottom mirror**: `(x, height - y)`
- **Both mirrors**: `(width - x, height - y)`

**Avoid duplicates on center lines**:
```java
if (Math.abs(x - centerLineX) > 0.1f) {
    // Add mirror (skip if on center line)
}
```

### Recommended Tree Coverage
- **Low**: < 5% (open maps, fast units)
- **Medium**: 5-15% (balanced maps)
- **High**: > 15% (chokepoints, strategic)

**Current 300.map17**: 33.45% coverage

### Archon Placement Strategy
**1 Archon per team**:
- Simple, focused economy
- Archon = critical target

**2 Archons per team**:
- Redundancy, distributed production
- Can lose 1 and still be competitive

**3 Archons per team** (current 300.map17):
- Maximum starting economy
- Harder to defend all positions
- Fast army building

**Recommended positions**:
```java
// Vertical spread (good for left/right team split)
float archonX = teamA ? 12.0f : 78.0f;
float[] archonYs = {35.0f, 45.0f, 55.0f};

for (float y : archonYs) {
    newBodies.add(createArchon(id++, team, archonX, y));
}
```

### Bullet Allocation in Trees
Trees can contain bullets (0-100) that are harvested by units:

```java
// Resource-rich trees (scout targets)
newBodies.add(createTreeWithBullets(id++, x, y, 2.5f, 100));

// Standard trees
newBodies.add(createTreeWithBullets(id++, x, y, 2.5f, 0));
```

**Strategy**: Place bullet trees near center or behind enemy lines to encourage scouting.

---

## Common Issues & Solutions

### Issue: "Map causes performance issues"
**Cause**: Too many overlapping trees

**Solution**:
```bash
./gradlew cleanMapTrees -Pmap=<mapName>
```

### Issue: "Trees don't look symmetrical"
**Cause**: Incorrect mirror logic or center line handling

**Solution**:
```bash
./gradlew dumpMapTrees -Pmap=<mapName>
# Look for "Missing mirror" errors
```

### Issue: "Archons trapped by trees"
**Cause**: Trees placed too close to archon spawn

**Solution**: Ensure 3-5 unit buffer around archons:
```java
float safeZone = 5.0f;
for (float x = startX; x <= endX; x += treeSpacing) {
    for (float y = startY; y <= endY; y += treeSpacing) {
        // Skip if too close to any archon
        boolean tooClose = false;
        for (RobotInfo archon : archons) {
            float dx = x - archon.location.x;
            float dy = y - archon.location.y;
            if (Math.sqrt(dx*dx + dy*dy) < safeZone) {
                tooClose = true;
                break;
            }
        }
        if (!tooClose) {
            newBodies.add(createTree(id++, x, y, 2.5f));
        }
    }
}
```

### Issue: "Can't build on this map"
**Cause**: Invalid map dimensions or tree placement outside bounds

**Solution**:
```bash
./gradlew mapInfo -Pmap=<mapName>
# Check:
# - WIDTH, HEIGHT are reasonable (80-100)
# - TREE_SPREAD_X, TREE_SPREAD_Y are within bounds
```

---

## Quick Reference

### File Locations
- **Map files**: `maps/<name>.map17`
- **Java utilities**: `engine/battlecode/util/Map*.java`
- **Serializer**: `engine/battlecode/world/GameMapIO.java`
- **Schema**: `engine/battlecode/schema/GameMap.fbs`
- **Gradle tasks**: `build.gradle` (lines 338-400)

### Common Commands
```bash
# View map info
./gradlew mapInfo -Pmap=300

# Visualize map
./gradlew visualizeMap -Pmap=300

# Clean overlapping trees
./gradlew cleanMapTrees -Pmap=300

# Regenerate with new pattern
./gradlew regenerateMapTrees -Pmap=300

# Check symmetry
./gradlew dumpMapTrees -Pmap=300

# List all maps
./gradlew listMaps
```

### Code Templates

**Add a single tree**:
```java
newBodies.add(createTree(id++, x, y, 2.5f));
```

**Add tree with bullets**:
```java
newBodies.add(new TreeInfo(
    id++,
    Team.NEUTRAL,
    new MapLocation(x, y),
    2.5f,  // radius
    2.5f * GameConstants.NEUTRAL_TREE_HEALTH_RATE,  // health
    50,   // bullets
    null  // contained robot
));
```

**Add archon**:
```java
newBodies.add(new RobotInfo(
    id++,
    Team.A,
    RobotType.ARCHON,
    new MapLocation(x, y),
    RobotType.ARCHON.getStartingHealth(),
    0,  // round spawned
    0   // bytecodes used
));
```

---

## Best Practices

1. **Always verify after editing**:
   ```bash
   ./gradlew visualizeMap -Pmap=<mapName>
   ./gradlew dumpMapTrees -Pmap=<mapName>
   ```

2. **Keep maps symmetrical** - Ensures fair gameplay

3. **Avoid massive overlap** - Causes performance issues
   - Use `cleanMapTrees` if tree coverage > 150%

4. **Maintain archon escape routes** - Don't block all paths

5. **Test balance** - Run games with `examplefuncsplayer` vs `examplefuncsplayer`:
   ```bash
   ./gradlew runWithSummary -PteamA=examplefuncsplayer -PteamB=examplefuncsplayer -Pmaps=300
   ```

6. **Document changes** - Update this file when adding new patterns or utilities

---

## Additional Resources

- **Built-in maps**: `engine/battlecode/world/resources/*.map17`
- **Map schema**: `engine/battlecode/schema/GameMap.fbs`
- **Client editor**: Run `./gradlew clientWatch` → Open http://localhost:8080
- **Technical docs**: `TECHNICAL_DOCS.md`

---

## Version History

- **2025**: Initial map creation guide
  - Added 5 utility classes
  - Symmetrical wedge pattern
  - 3-archon-per-team configuration
  - Tree overlap cleaning algorithm

---

*This document should be updated when new map utilities or patterns are added.*
