# Iteration History

## Iterations

| Iteration | Outcome | Rounds | Problem | Change |
| 1 | LOSS | 2999 | Low probability for building soldiers and lumberjacks | Increased soldier build probability to 0.1, decreased lumberjack to 0.05 |
| 2 | LOSS | 2999 | Insufficient economic units (fewer gardeners and lumberjacks produced: 3G/4LJ vs 4G/10LJ) | Increased gardener hire probability to 0.1, lumberjack build probability to 0.1 |
| 3 | WIN | 2999 | Units stuck in SW quadrant, not engaging enemy effectively | Implement soldier movement towards visible enemies and increase soldier production probability |
| 4 | WIN | 2999 | Units concentrated in SW quadrant, not engaging enemy effectively | Implement soldier movement away from archon when no enemies visible for better exploration |
| 5 | WIN | 2999 | Units concentrated in SW quadrant and not engaging enemy effectively, leading to slow victory by tiebreaker | Made soldiers explore away from the spawn when no enemies are visible, instead of random movement |
| 6 | WIN | 2999 | Units concentrated in SW quadrant and not engaging enemy effectively, leading to slow victory by tiebreaker | Changed archon broadcast to use initial spawn location instead of current location for soldier exploration away from spawn |
| 7 | WIN | 2999 | Units concentrated in SW quadrant and not engaging enemy effectively, leading to slow victory by tiebreaker | Changed soldier exploration to move directly towards enemy archon location when no enemies visible |
| 8 | WIN | 2999 | Insufficient early aggression; soldiers are produced at moderate rates but not enough to overwhelm the opponent quickly. | Changed soldier build probability to 0.2 and lumberjack to 0.05 |
| 9 | WIN | 2999 | units remaining concentrated in SW quadrant, not effectively engaging the enemy | Increased soldier production probability to 0.3 and eliminated lumberjack production |
| 10 | WIN | 2999 | Units concentrated in SW quadrant, not engaging enemy effectively | Added movement logic to avoid allied units and promote spreading out |