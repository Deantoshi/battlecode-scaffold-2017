# Improvement Plan

## Iteration
150

## Match Result
- Outcome: LOSS
- Rounds: 2999
- Goal Met: NO

## Analysis

### Primary Problem
Late soldier production (first soldier at round 339) and insufficient gardeners (only 2 produced), leading to out-production by opponent who had 16 soldiers and 5 gardeners.

### Root Cause
Gardener hiring probability too low (0.3 in previous iteration), resulting in slow unit production; build order still prioritizes lumberjacks over early soldiers.

### Previous Attempts
Iterations 143-149 attempted to accelerate soldier production and increase gardener hiring (to 0.3), but first soldier still late and gardener count low.

## Proposed Solution

### Strategy Change
Increase gardener hiring probability to 0.5 for early game (<300 rounds) and hire up to 4 gardeners to boost production capacity.

### Implementation Details
- **File:** `src/grok_code_fast_1/Archon.java`
- **Location:** Gardener hiring logic (probability and count check)
- **Change:** Change hiring probability from 0.3 to 0.5, max gardeners from 3 to 4 for rounds <300.

### Economy Projection

**Unit Costs (bullets):**
- Archon: spawns free | Gardener: 100 | Soldier: 100 | Lumberjack: 100

**Income Sources:**
- Base: 1.0 bullet/round (passive)
- Per Archon: +2.0 bullets/round

**Projection Table:**
| Round | Build Action | Cost | Cumulative Spent | Income/Round | Balance |
|-------|--------------|------|------------------|--------------|---------|
| 1     | (start)      | 0    | 0                | 3.0          | 300     |
| 10    | Gardener     | 100  | 100              | 3.0          | 200     |
| 20    | Gardener     | 100  | 200              | 3.0          | 100     |
| 30    | Gardener     | 100  | 300              | 3.0          | 0       |
| 40    | Gardener     | 100  | 400              | 3.0          | -100    |
| 50    | Gardener     | 100  | 500              | 5.0          | -100    |
| 60    | Soldier      | 100  | 600              | 5.0          | -200    |
| 70    | Soldier      | 100  | 700              | 5.0          | -300    |
| 80    | Soldier      | 100  | 800              | 5.0          | -400    |

**Key Milestones:**
- Round when first combat unit ready: ~80 (soldiers inactive first 20 rounds)
- Round when army size reaches 5: ~120
- Projected bullets at R500: ~500 (assuming more trees/income)

**Break-even Analysis:**
- Each additional gardener costs 100 but adds +2.0 income/round, breaks even at round ~50 after build.

### Expected Impact
More gardeners will enable faster production of soldiers and lumberjacks, leading to earlier combat engagement and better economy.

### Success Criteria
First soldier produced before round 100, achieve win in ≤1500 rounds.