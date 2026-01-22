# Iteration History

| 212 | LOSS | 948 | N/A | 1.25 | 0.0 | 4 | 34 |
| 213 | LOSS | 948 | Gardener killed early without units | Added tree avoidance movement | 1.25 | 0.0 | 4 | 34 |
| 214 | LOSS | 876 | N/A | 0.75 | 0.0 | 4 | 40 |
| 215 | LOSS | 876 | N/A | 0.75 | 0.0 | 4 | 34 |
| 216 | LOSS | 876 | No army was built; archons killed early without protection, and the single gardener died at round 640 without producing any combat units. | Implemented Greedy Economy: Hire 3 gardeners early, prioritize tree planting until 10 trees, then build army; keep archons near spawn until 2 gardeners hired. | 0.75 | 0.0 | 4 | 34 |
| 217 | LOSS | 651 | No army was built; archons killed early without protection, gardener died at R372 without producing combat units. | Switched to Tank Push strategy: removed tree planting logic, prioritize building tanks if bullets > 250, else build soldiers. | 0.25 | 0.0 | 4 | 36 |
| 218 | LOSS | 641 | N/A | 0.75 | 0.0 | 4 | 35 |
| 219 | LOSS | 641 | No army was built; the single gardener died early at round 357 with exceptions, leaving archons unprotected and unable to produce combat units. | Implemented Scout Swarm strategy: prioritize building scouts if less than 5 local scouts, else build soldiers; removed tank logic. | 0.75 | 1.33 | 4 | 35 |
---
## Final Status

**RESULT:** LOSS in 641 rounds
**GOAL:** Win in ≤1500 rounds
**STATUS:** NOT ACHIEVED after 7 iterations

### Summary of Changes
The bot started with a baseline strategy but consistently failed to build an army, leading to early losses. Various approaches were tried including tree avoidance, greedy economy focusing on gardeners and trees, tank push strategy, and scout swarm, but none succeeded in creating a viable combat force.

### Key Improvements
1. Added tree avoidance movement to prevent collisions
2. Implemented Greedy Economy: Hire 3 gardeners early, prioritize tree planting until 10 trees, then build army; keep archons near spawn until 2 gardeners hired
3. Switched to Tank Push strategy: removed tree planting logic, prioritize building tanks if bullets > 250, else build soldiers
4. Implemented Scout Swarm strategy: prioritize building scouts if less than 5 local scouts, else build soldiers; removed tank logic
