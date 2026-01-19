# Improvement Plan

## Iteration
4

## Match Result
- Outcome: WIN
- Rounds: 2999
- Goal Met: NO

## Analysis

### Primary Problem
Units concentrated in SW quadrant and not engaging enemy effectively, leading to slow victory by tiebreaker.

### Root Cause
Soldiers move randomly when no enemies are visible, keeping them clustered near the spawn area instead of exploring the map.

### Previous Attempts
Iteration 1: Increased soldier build probability to encourage more combat units.
Iteration 2: Increased gardener and lumberjack probabilities for better economy.
Iteration 3: Implemented soldier movement towards visible enemies and increased soldier production probability, but random movement when enemies not visible still causes clustering.

## Proposed Solution

### Strategy Change
Make soldiers explore away from the spawn when no enemies are visible, instead of random movement, to spread out and find enemies earlier.

### Implementation Details
- **File:** `src/grok_code_fast_1/RobotPlayer.java`
- **Location:** `runSoldier` method, around line 134-136
- **Change:** Replace `tryMove(randomDirection());` with logic to read archon location from broadcast channels 0 and 1, calculate direction away from archon, and `tryMove(awayDirection);`

### Expected Impact
Soldiers will spread out more effectively, leading to earlier enemy detection and engagement, reducing total rounds to win.

### Success Criteria
Win the match in ≤1500 rounds with units spread across multiple quadrants.