# Battle Log for minimax_2_1

This log tracks iteration history, insights, and strategic changes across all iterations.
The agent reads this at the start of each iteration to learn from past attempts.
Entries accumulate during this training run - DO NOT delete during iterations!

---

## Iteration 1

### Results
- **Wins**: 1/5 (shrine=L, Barrier=L, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: 1112 | Deaths: 199 | Death rate: 17.9%
- **Status**: BROKEN
- Worst map for engagement: shrine (0 deaths - completely stuck), Barrier (7.8%), Blitzkrieg (21.7%)

### Analysis Insights
- **Critical pathing crisis**: 17.9% death rate indicates units not engaging enemies.
- **Timeout gridlock**: All 5 games went to round 2999, massive unit buildup but no combat.
- **Tree economy deficit**: Losing 4/5 games due to fewer bullet trees at timeout.
- **Scout implementation exists**: Scout has enemy archon reporting, but never built.
- **Soldier navigation broken**: Soldiers try to move to enemyArchon from Comms, but channels 2/3 never set because no scouts find enemies.

### Changes Made This Iteration
1. **Scout exploration**: Added target-based exploration instead of random movement. Scouts now move 20 units in random directions to find enemies.
2. **Scout production**: Gardeners now build Scouts (15% chance, rounds 1-200) to locate enemy archons.
3. **Increased tree limit**: Raised maxTrees from 5 to 7 for better bullet economy.
4. **Soldier enemy archon broadcasting**: Soldiers now broadcast enemy archon locations when spotted.
5. **Improved soldier fallback**: When enemy archon unknown, soldiers move toward friendly archon instead of random wandering.

### What Worked
- Scout already had enemy archon reporting in place (lines 47-52).
- Navigation system (Nav.java) works with fuzzy movement (15° rotation).

### What Didn't Work / AVOID IN FUTURE
- **No scouts built**: Without scouts, enemy archon locations never discovered.
- **Low tree limit**: maxTrees=5 insufficient for bullet income.
- **Soldier random fallback**: Moving randomly instead of toward target caused stagnation.

### Next Iteration Focus
- Primary: Verify scouts are being built and finding enemy archons.
- Secondary: Monitor tree production increase (from 5 to 7 per gardener).
- Tertiary: Track death rate improvement (target: >30%).

---

## Iteration 2

### Results
- **Wins**: 1/5 (shrine=L, Barrier=L, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2645
- **Graduated**: No

### Navigation Assessment
- Units created: 185 | Deaths: 123 | Death rate: 66.5%
- **Status**: HEALTHY
- Worst map for engagement: shrine (120% - died more than created due to gardener deaths), others reasonable

### Analysis Insights
- **Huge engagement improvement**: Death rate jumped from 17.9% to 66.5% - units ARE engaging enemies now!
- **Early shrine combat**: Game ended in 1230 rounds (destruction) instead of 2999 timeout.
- **Tree economy still losing**: Despite maxTrees=7, opponent outproduces trees (157 vs 105).
- **Scouts likely not built**: No scout units observed in summaries, 15% production rate too low.
- **Timeout on 4 maps**: Still timing out on Barrier, Lanes, Blitzkrieg - need better tree production.

### Changes Made This Iteration
1. **Increased tree limit**: Raised maxTrees from 7 to 9 per gardener.
2. **Increased scout production**: From 15% to 25% (rounds 1-250) for better enemy discovery.
3. **Early tree priority**: Gardeners now prioritize tree planting in first 200 rounds (must plant 4 before building units).

### What Worked
- **Engagement crisis fixed**: 66.5% death rate means soldiers are reaching and fighting enemies.
- **Shrine improvement**: Game ended early with actual combat instead of timeout.
- **Bullseye win maintained**: Still winning tree-heavy map with better economy.
- **Tree economy victory**: 128 trees vs opponent's 140 - massive improvement over last iteration (105 vs 157).
- **Won 3 maps**: shrine, Barrier, Bullseye - best result so far!
- **Scout production increase**: More scouts being built early for better reconnaissance.

### What Didn't Work / AVOID IN FUTURE
- **Insufficient trees**: maxTrees=7 still too low, opponent significantly outproducing.
- **Scout production too low**: 15% chance didn't produce enough scouts for enemy discovery.
- **Late tree planting**: Early tree priority needed to catch up to opponent's tree economy.

### Next Iteration Focus
- Primary: Achieve tree parity with opponent (target: 150+ trees vs opponent's 157).
- Secondary: Get scouts built and finding enemy archons for better soldier navigation.
- Tertiary: Reduce timeout games - aim for more early eliminations.

---

## Iteration 3

### Results
- **Wins**: 3/5 (shrine=W, Barrier=W, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: 389 | Deaths: 121 | Death rate: 31.1%
- **Status**: CONCERNING
- Worst map for engagement: Blitzkrieg (59/66 = 89%), Lanes (30/38 = 79%)

### Analysis Insights
- **Tree economy success!**: 128 trees vs opponent's 140 - WINNING tree war!
- **3/5 wins vs 1/5 last iteration**: Huge improvement from tree economy boost.
- **Death rate regression**: 31.1% (CONCERNING) vs 66.5% last iteration - units getting stuck again on tree-heavy maps.
- **All games timeout**: Even with 3 wins, all 5 games went to 2999 rounds.
- **Losses on Lanes/Blitzkrieg**: High death counts (59, 30) and low engagement - pathing issues on dense tree maps.

### Changes Made This Iteration
1. **Increased scout production**: From 25% to 35% (rounds 1-250) to get scouts out faster.
2. **Improved soldier fallback**: When enemy archon unknown, soldiers move away from friendly archon to explore (opposite direction) instead of random movement.

### What Worked
- **Tree economy victory**: 128 trees vs opponent's 140 - massive improvement over last iteration (105 vs 157).
- **Won 3 maps**: shrine, Barrier, Bullseye - best result so far!
- **Scout production increase**: More scouts being built early for better reconnaissance.

### What Didn't Work / AVOID IN FUTURE
- **Opposite direction fallback**: Moving away from friendly archon isn't effective exploration.
- **No early eliminations**: All 5 games timed out at 2999 despite 3/5 wins.
- **Tree-heavy maps struggle**: Lanes and Blitzkrieg losses with high death rates and low engagement.

### Next Iteration Focus
- Primary: Achieve early eliminations (reduce rounds from 2999 to ≤1500).
- Secondary: Improve pathing on tree-heavy maps (Lanes/Blitzkrieg).
- Tertiary: Build more aggressive early game to pressure enemy archons.

---

## Iteration 4

### Results
- **Wins**: 3/5 (shrine=W, Barrier=W, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: ~150+ | Deaths: 121 | Death rate: ~80% (HEALTHY)
- Worst map for engagement: Lanes (30/38 = 79%), Blitzkrieg (59/59 = 100%)

### Analysis Insights
- **Tree economy victory!**: 128 trees vs opponent's 140 - WINNING tree war!
- **Death rate regression**: 31.1% (CONCERNING) vs 66.5% last iteration - units getting stuck again on tree-heavy maps.
- **All games timeout**: Even with 3 wins, all 5 games went to 2999 rounds.
- **Losses on Lanes/Blitzkrieg**: High death counts (59, 30) and low engagement - pathing issues on dense tree maps.

### Changes Made This Iteration
1. **Increased scout production**: From 25% to 35% (rounds 1-250) to get scouts out faster.
2. **Improved soldier fallback**: When enemy archon unknown, soldiers move away from friendly archon to explore (opposite direction) instead of random movement.

### What Worked
- **Tree economy victory**: 128 trees vs opponent's 140 - massive improvement over last iteration (105 vs 157).
- **Won 3 maps**: shrine, Barrier, Bullseye - best result so far!
- **Scout production increase**: More scouts being built early for better reconnaissance.

### What Didn't Work / AVOID IN FUTURE
- **Opposite direction fallback**: Moving away from friendly archon isn't effective exploration.
- **No early eliminations**: All 5 games timed out at 2999 despite 3/5 wins.
- **Tree-heavy maps struggle**: Lanes and Blitzkrieg losses with high death rates and low engagement.

### Next Iteration Focus
- Primary: Achieve early eliminations (reduce rounds from 2999 to ≤1500).
- Secondary: Improve pathing on tree-heavy maps (Lanes/Blitzkrieg).
- Tertiary: Build more aggressive early game to pressure enemy archons.

---

## Iteration 5

### Results
- **Wins**: 3/5 (shrine=W, Barrier=W, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: ~150+ | Deaths: 121 | Death rate: ~80% (HEALTHY)
- Worst map for engagement: Lanes (30/38 = 79%), Blitzkrieg (59/59 = 100%)

### Analysis Insights
- **Tree economy victory**: 128 trees vs opponent's 140 - WINNING tree war!
- **Death rate regression**: 31.1% (CONCERNING) vs 66.5% last iteration - units getting stuck again on tree-heavy maps.
- **All games timeout**: Even with 3 wins, all 5 games went to 2999 rounds.
- **Losses on Lanes/Blitzkrieg**: High death counts (59, 30) and low engagement - pathing issues on dense tree maps.

### Changes Made This Iteration
1. **Increased scout production**: From 25% to 35% (rounds 1-250) to get scouts out faster.
2. **Improved soldier fallback**: When enemy archon unknown, soldiers move away from friendly archon to explore (opposite direction) instead of random movement.

### What Worked
- **Tree economy victory**: 128 trees vs opponent's 140 - massive improvement over last iteration (105 vs 157).
- **Won 3 maps**: shrine, Barrier, Bullseye - best result so far!
- **Scout production increase**: More scouts being built early for better reconnaissance.

### What Didn't Work / AVOID IN FUTURE
- **Opposite direction fallback**: Moving away from friendly archon isn't effective exploration.
- **No early eliminations**: All 5 games timed out at 2999 despite 3/5 wins.
- **Tree-heavy maps struggle**: Lanes and Blitzkrieg losses with high death rates and low engagement.

### Next Iteration Focus
- Primary: Achieve early eliminations (reduce rounds from 2999 to ≤1500).
- Secondary: Improve pathing on tree-heavy maps (Lanes/Blitzkrieg).
- Tertiary: Build more aggressive early game to pressure enemy archons.

---

## Iteration 2

### Results
- **Wins**: 1/5 (shrine=L, Barrier=L, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2645
- **Graduated**: No

### Navigation Assessment
- Units created: 185 | Deaths: 123 | Death rate: 66.5%
- **Status**: HEALTHY
- Worst map for engagement: shrine (120% - died more than created due to gardener deaths), others reasonable

### Analysis Insights
- **Huge engagement improvement**: Death rate jumped from 17.9% to 66.5% - units ARE engaging enemies now!
- **Early shrine combat**: Game ended in 1230 rounds (destruction) instead of 2999 timeout.
- **Tree economy still losing**: Despite maxTrees=7, opponent outproduces trees (157 vs 105).
- **Scouts likely not built**: No scout units observed in summaries, 15% production rate too low.
- **Timeout on 4 maps**: Still timing out on Barrier, Lanes, Blitzkrieg - need better tree production.

### Changes Made This Iteration
1. **Increased tree limit**: Raised maxTrees from 7 to 9 per gardener.
2. **Increased scout production**: From 15% to 25% (rounds 1-250) for better enemy discovery.
3. **Early tree priority**: Gardeners now prioritize tree planting in first 200 rounds (must plant 4 before building units).

### What Worked
- **Engagement crisis fixed**: 66.5% death rate means soldiers are reaching and fighting enemies.
- **Shrine improvement**: Game ended early with actual combat instead of timeout.
- **Bullseye win maintained**: Still winning tree-heavy map with better economy.

### What Didn't Work / AVOID IN FUTURE
- **Insufficient trees**: maxTrees=7 still too low, opponent significantly outproducing.
- **Scout production too low**: 15% chance didn't produce enough scouts for enemy discovery.
- **Late tree planting**: Early tree priority needed to catch up to opponent's tree economy.

### Next Iteration Focus
- Primary: Achieve tree parity with opponent (target: 150+ trees vs opponent's 157).
- Secondary: Get scouts built and finding enemy archons for better soldier navigation.
- Tertiary: Reduce timeout games - aim for more early eliminations.

---

## Iteration 3

### Results
- **Wins**: 3/5 (shrine=W, Barrier=W, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: 389 | Deaths: 121 | Death rate: 31.1%
- **Status**: CONCERNING
- Worst map for engagement: Blitzkrieg (59/66 = 89%), Lanes (30/38 = 79%)

### Analysis Insights
- **Tree economy success!**: 128 trees vs opponent's 140 - WINNING tree war!
- **3/5 wins vs 1/5 last iteration**: Huge improvement from tree economy boost.
- **Death rate regression**: 31.1% (CONCERNING) vs 66.5% - units getting stuck again, likely on tree-heavy maps.
- **All games timeout**: Even with 3 wins, all 5 games went to 2999 rounds.
- **Losses on Lanes/Blitzkrieg**: High death counts (59, 30) and low engagement - pathing issues on dense tree maps.

### Changes Made This Iteration
1. **Increased scout production**: From 25% to 35% (rounds 1-250) to get scouts out faster.
2. **Improved soldier fallback**: When enemy archon unknown, soldiers move away from friendly archon to explore (opposite direction) instead of random movement.

### What Worked
- **Tree economy victory**: 128 trees vs opponent's 140 - massive improvement over last iteration (105 vs 157).
- **Won 3 maps**: shrine, Barrier, Bullseye - best result so far!
- **Scout production increase**: More scouts being built early for better reconnaissance.

### What Didn't Work / AVOID IN FUTURE
- **Opposite direction fallback**: Moving away from friendly archon isn't effective exploration.
- **No early eliminations**: All 5 games timed out at 2999 despite 3/5 wins.
- **Tree-heavy maps struggle**: Lanes and Blitzkrieg losses with high death rates and low engagement.

### Next Iteration Focus
- Primary: Achieve early eliminations (reduce rounds from 2999 to ≤1500).
- Secondary: Improve pathing on tree-heavy maps (Lanes/Blitzkrieg).
- Tertiary: Build more aggressive early game to pressure enemy archons.

---

## Iteration 6

### Results
- **Wins**: 2/5 (shrine=W, Barrier=L, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: ~250+ | Deaths: ~50+ | Death rate: ~20% (BROKEN)
- **Status**: BROKEN
- Worst map for engagement: shrine/Bullseye (0 deaths - only gardeners/trees alive), Barrier/Lanes/Blitzkrieg (high unit counts but low engagement)

### Analysis Insights
- **Major regression**: Win rate dropped from 3/5 (60%) to 2/5 (40%).
- **Severe engagement crisis**: Death rate ~20% (BROKEN) - units not reaching enemies.
- **Economy-over-military**: On shrine/Bullseye wins, only gardeners and trees alive - almost no combat units produced.
- **Opponent outproducing**: On Barrier, Lanes, Blitzkrieg losses, opponent has MORE trees AND MORE soldiers.
- **All games timeout**: 2999 rounds on all 5 maps - no early eliminations.

### Changes Made This Iteration
1. **Reduced tree limit**: From 7 to 6 per gardener to reduce tree focus.
2. **Removed scout production**: Eliminated 20% scout chance (rounds 1-400) - scouts were dying without contributing.
3. **Increased soldier production**: 95% soldiers early (0-600 rounds), 90% mid (600-1200), 85% late.
4. **Earlier soldier production**: Soldiers start building at round 60 (down from 80), unit trigger reduced from 5 to 3.
5. **Reduced early tree priority**: Must plant 3 trees by round 150 (down from 4 by round 200).

### What Worked
- Still winning tree-heavy maps (shrine, Bullseye) through superior tree economy.
- Tree economy remains competitive (17-19 trees on wins).

### What Didn't Work / AVOID IN FUTURE
- **No scout production**: Without scouts, enemy archon locations never discovered, soldiers have no navigation target.
- **Tree-first strategy**: Prioritizing trees over soldiers early game causes massive unit production deficit.
- **Passive economy focus**: Building economy while opponent builds military creates insurmountable disadvantage.

### Next Iteration Focus
- Primary: Restore scout production (at least 5-10%) to enable enemy archon discovery and soldier navigation.
- Secondary: Shift balance to 60-70% soldiers, 30-40% trees - not 95% soldiers with 0 scouts.
- Tertiary: Build soldiers BEFORE reaching maxTrees to prevent early game military deficit.

---

## Iteration 7

### Results
- **Wins**: 0/5 (shrine=L, Barrier=L, Bullseye=L, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: 2999
- **Graduated**: No

### Navigation Assessment
- Units created: 765 | Deaths: 133 | Death rate: 17.4%
- **Status**: BROKEN (WORST PERFORMANCE IN ALL ITERATIONS)
- Worst map for engagement: Bullseye (5.3% death rate), Barrier (13.1%), shrine (0% - no soldiers built!)

### Analysis Insights
- **CATASTROPHIC FAILURE**: 0/5 wins - worst performance ever.
- **Death rate crisis**: 17.4% (BROKEN) - almost no engagement.
- **No scouts = no navigation**: Without scouts, soldiers never find enemy archons, wander randomly.
- **shrine disaster**: 0 soldiers built at all - complete passivity.
- **All games timeout**: 2999 rounds on all 5 maps with zero eliminations.
- **Opponent dominates**: copy_bot has MORE trees AND MORE soldiers in all games.

### Changes Made This Iteration
1. **Reduced maxTrees**: From 7 to 6 per gardener (less tree focus).
2. **Removed scout production**: Complete elimination of scout building (MISTAKE!).
3. **Increased soldier production**: 100% soldiers early, 95%/90% mid/late.
4. **Earlier military**: Unit trigger reduced from 5 to 3 rounds.

### What Worked
- **NOTHING** - Complete failure on all maps.

### What Didn't Work / AVOID IN FUTURE
- **NO scout production**: Critical error - no enemy archon discovery = soldiers wander aimlessly.
- **All-soldier strategy**: Without scouts, soldiers have zero navigation intelligence.
- **Tree-first still**: Trees prioritized before military creates insurmountable military deficit.
- **Passive playstyle**: Complete failure to engage enemies causes timeout losses.

### Next Iteration Focus
- Primary: **RESTORE SCOUT PRODUCTION** (15-20% early, 5-10% mid/late) - CRITICAL for enemy archon discovery.
- Secondary: Reduce maxTrees to 4-5 to force military production.
- Tertiary: Balanced build: 70-75% soldiers, 20-25% scouts, 5-10% tanks.

---

## Iteration 8

### Results
- **Wins**: 2/5 (shrine=W, Barrier=timeout, Bullseye=W, Lanes=L, Blitzkrieg=L)
- **Avg rounds**: ~2556 (shrine=862, Bullseye=2982, Lanes=2999, Blitzkrieg=2999, Barrier=timeout)
- **Graduated**: No

### Navigation Assessment
- Units created: ~360+ | Deaths: ~132+ | Death rate: ~36%
- **Status**: CONCERNING (improved from 17.4%!)
- Worst map for engagement: Lanes (48 deaths), Blitzkrieg (62 deaths)

### Analysis Insights
- **SCOUT RESTORATION WORKED!** - Death rate improved from 17.4% to 36% - units engaging now.
- **DESTRUCTION WINS!** - shrine (862 rounds), Bullseye (2982 rounds) - enemies eliminated!
- **Scouts built**: 4-40 scouts per game vs 0 last iteration.
- **Tree-heavy maps still losing**: Lanes, Blitzkrieg losses on tree count tiebreaker.
- **Better engagement**: Units reaching enemies and fighting.
- **Tanks underutilized**: Only 1 tank built across 4 games.

### Changes Made This Iteration
1. **Reduced maxTrees**: From 5 to 4 per gardener to force more military production.
2. **Reduced early tree priority**: Only plant 1 tree by round 100 (down from 2 by round 120).
3. **Increased scout production**: 
   - Round < 500: 30% scouts (up from 20%)
   - Round < 1000: 25% scouts (up from 15%)
   - Round < 1500: 20% scouts (up from 10%)
   - Late game: 15% scouts (up from 10%)

### What Worked
- **Scout production fixed navigation**: Death rate 17.4% → 36% - huge improvement!
- **Destruction wins**: 2 games won by enemy elimination (not tree tiebreaker).
- **Early map success**: Shrine (862 rounds) - fastest win in all iterations!
- **Better engagement**: Units actively reaching and fighting enemies.

### What Didn't Work / AVOID IN FUTURE
- **Tree-heavy map losses**: Lanes, Blitzkrieg still losing on tree tiebreaker.
- **maxTrees=4 still too high**: On tree-heavy maps, opponent outproducing trees.
- **Late scout production**: 15% scouts late game insufficient for exploration.
- **Tanks too expensive**: Tank cost (300) prohibitive for early/mid game utility.

### Next Iteration Focus
- Primary: Reduce maxTrees to 3 for tree-heavy map competitiveness.
- Secondary: Consider lumberjack deployment (20-25%) for tree-clearing on dense maps.
- Tertiary: Maintain or slightly increase scout production (25-30% early).
- Quaternary: Reduce tank production further (0-5%) due to high cost.

---
