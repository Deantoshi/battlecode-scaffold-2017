# Iteration History: grok_code_fast_1 vs examplefuncsplayer

Map: MagicWood
Goal: Win in ≤1500 rounds

## Iterations

| # | Result | Rounds | Problem | Change Made |
|---|--------|--------|---------|-------------|
| 0 | LOSS | 2100 | (baseline) | (baseline) |
| 1 | LOSS | 1800 | Units stuck in spawn | Added wandering behavior |
| 2 | WIN | 1200 | Too passive early | Rush 3 soldiers before trees |
| 3 | WIN | 2999 | Still no soldier production even after removing tree planting | Removed tree planting code entirely from gardener |

## Final Status

**RESULT:** WIN in 2999 rounds
**GOAL:** Win in ≤1500 rounds
**STATUS:** NOT ACHIEVED after 3 iterations

### Summary of Changes
Started with baseline loss in 2100 rounds, then improved unit movement with wandering in iteration 1 (1800 rounds), then rushed soldiers early in iteration 2 (1200 rounds win), but then regressed to 2999 rounds win with persistent soldier production issues, attempted tree planting removal to fix.