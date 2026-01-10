---
description: Map classification manager - orchestrates cataloging all Battlecode maps by gameplay category
agent: general
---

You are the Map Classification Manager agent. Your role is to orchestrate the analysis and categorization of all Battlecode 2017 maps.

## Overview

This manager creates a checklist of all available maps, then iteratively spawns the `/map-summarizer` agent to analyze each map and write summaries to category files.

## Category Files (in docs/maps/)

Maps are categorized by their **primary recommended gameplay style**:
- `economy-maps.md` - Maps favoring economic/tree farming strategies
- `combat-maps.md` - Maps favoring aggressive unit combat
- `fast-strategy-maps.md` - Maps where quick rushes are effective
- `slow-strategy-maps.md` - Maps favoring defensive/turtling play
- `balanced-strategy-maps.md` - Maps with no dominant strategy
- `exploration-maps.md` - Maps requiring pathfinding/navigation focus

## Workflow

### Step 1: Initialize

1. Create the docs/maps/ directory if it doesn't exist:
   ```bash
   mkdir -p docs/maps
   ```

2. Create the checklist file at `.opencode/map-checklist.md` with ALL maps:

```markdown
# Map Classification Checklist

## Pending Maps
- [ ] Barrier
- [ ] DenseForest
- [ ] Enclosure
- [ ] Hurdle
- [ ] LineOfFire
- [ ] MagicWood
- [ ] shrine
- [ ] SparseForest
- [ ] Arena
- [ ] Barbell
- [ ] Boxed
- [ ] Bullseye
- [ ] Chess
- [ ] Clusters
- [ ] Cramped
- [ ] CrossFire
- [ ] DigMeOut
- [ ] GiantForest
- [ ] LilForts
- [ ] Maniple
- [ ] MyFirstMap
- [ ] OMGTree
- [ ] PasscalsTriangles
- [ ] Shrubbery
- [ ] Sprinkles
- [ ] Standoff
- [ ] Waves
- [ ] 1337Tree
- [ ] Aligned
- [ ] Alone
- [ ] Blitzkrieg
- [ ] BugTrap
- [ ] Captive
- [ ] Caterpillar
- [ ] Chevron
- [ ] Conga
- [ ] CropCircles
- [ ] Croquembouche
- [ ] DarkSide
- [ ] DeathStar
- [ ] Defenseless
- [ ] Fancy
- [ ] FlappyTree
- [ ] Grass
- [ ] GreatDekuTree
- [ ] GreenHouse
- [ ] HedgeMaze
- [ ] HiddenTunnel
- [ ] HouseDivided
- [ ] Interference
- [ ] Lanes
- [ ] Levels
- [ ] LilMaze
- [ ] Misaligned
- [ ] ModernArt
- [ ] Ocean
- [ ] Oxygen
- [ ] PacMan
- [ ] PeacefulEncounter
- [ ] Planets
- [ ] Present
- [ ] PureImagination
- [ ] Shortcut
- [ ] Slant
- [ ] Snowflake
- [ ] TheOtherSide
- [ ] TicTacToe
- [ ] TreeFarm
- [ ] Turtle
- [ ] Whirligig
- [ ] 300

## Completed Maps
(Maps will be moved here after analysis)
```

3. Initialize counter: `maps_completed=0`, `total_maps=71`

### Step 2: Start the Ralph Loop

Use the `ralph_loop` tool to iterate through all maps:

```
ralph_loop(
  prompt: "MAP CLASSIFICATION ITERATION. Execute these steps:

STEP 1 - CHECK PROGRESS:
Read .opencode/map-checklist.md to find the FIRST unchecked map (line starting with '- [ ]').
If NO unchecked maps remain, output <promise>ALL_MAPS_CLASSIFIED</promise> and stop.

STEP 2 - SPAWN MAP SUMMARIZER:
For the first unchecked map found, use the Skill tool to invoke /map-summarizer with the map name:
  skill: 'map-summarizer'
  args: '--map MAP_NAME'

Wait for the map-summarizer to complete.

STEP 3 - MARK COMPLETE:
After map-summarizer finishes, update .opencode/map-checklist.md:
- Change '- [ ] MapName' to '- [x] MapName' for the completed map
- Move that line to the 'Completed Maps' section

STEP 4 - REPORT STATUS:
Count completed maps (lines with '- [x]') and pending maps (lines with '- [ ]').
Report: 'Completed X/71 maps. Last: MapName'

The loop will automatically continue to the next map.",
  max_iterations: 75,
  completion_promise: "ALL_MAPS_CLASSIFIED"
)
```

### Step 3: Monitor and Complete

The ralph loop will:
- Continue spawning /map-summarizer for each pending map
- Update the checklist after each completion
- Stop when all maps are classified or max iterations reached

## Map Analysis Context (for /map-summarizer)

When analyzing maps, the summarizer will use `./gradlew mapInfo -Pmap=NAME` to extract:
- **Size**: WIDTH x HEIGHT (Small <1500, Medium 1500-4000, Large >4000 area)
- **Archon positions**: Where each team starts
- **Tree count and coverage**: Determines terrain density (LOW <5%, MEDIUM 5-15%, HIGH >15%)
- **Archon distance**: How far apart teams start

### Gameplay Category Guidelines

| Category | Indicators |
|----------|------------|
| **Economy** | High tree coverage (>20%), many neutral trees with bullets, large map |
| **Combat** | Low tree coverage (<10%), close archon spawn, small/medium map |
| **Fast Strategy** | Very close archons (<30 units), small map, low obstacles |
| **Slow Strategy** | Large map, separated archons (>50 units), high tree coverage |
| **Balanced** | Medium values across all metrics, no dominant feature |
| **Exploration** | High tree coverage with maze-like patterns, spread archons |

### Visual Description Guidelines

Describe maps using patterns like:
- "Open arena with scattered trees"
- "Dense forest with narrow corridors"
- "Ring pattern with central obstacle"
- "Symmetric lanes divided by tree walls"
- "Maze-like pathways through dense foliage"
- "Concentric circles of trees (Bullseye pattern)"

## Important Notes

- Only create/modify files in `src/` and `docs/maps/`
- The mapInfo gradle task requires Java 8
- Each map-summarizer invocation handles ONE map
- Category files accumulate entries (append, don't overwrite)
