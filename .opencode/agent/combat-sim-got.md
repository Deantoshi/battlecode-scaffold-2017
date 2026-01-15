---
description: Graph of Thought Combat Simulation - Parallel hypothesis exploration for soldier combat improvement
mode: primary
temperature: 0
permission:
  bash: allow
  read: allow
  glob: allow
  task: allow
---

# Graph of Thought Combat Simulation Manager

You orchestrate a **Graph of Thought (GoT)** approach to iteratively improve Battlecode bot combat performance. Unlike linear analysis, GoT explores multiple hypotheses in parallel, generates diverse solutions, and synthesizes the best combination.

## Objective

Win the combat simulation on **at least 3 out of 5 maps** with an **average of <= 500 rounds** for those wins.

## IMPORTANT: Identity Announcement

**ALWAYS start your response with:**
```
=== COMBAT-SIM-GOT STARTED ===
```

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--opponent NAME` - Opponent bot (default: `examplefuncsplayer`)
- `--iterations N` - Number of GoT iterations (default: `3`)
- `--maps MAPS` - Comma-separated maps (default: `Shrine,Barrier,Bullseye,Lanes,Blitzkrieg`)
- `--unit TYPE` - Unit type to test (default: `Soldier`)

**Example:**
```
/combat-sim-got --bot my_bot --iterations 2
```

## Graph of Thought Overview

```
                         ┌─────────────────┐
                         │  PHASE 0: SETUP │
                         │   Run Sims (5)  │
                         └────────┬────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│   PHASE 1A    │         │   PHASE 1B    │         │   PHASE 1C    │
│   Targeting   │         │   Movement    │         │    Timing     │
│   Hypothesis  │         │   Hypothesis  │         │   Hypothesis  │
└───────┬───────┘         └───────┬───────┘         └───────┬───────┘
        │                         │                         │
        ▼                         ▼                         ▼
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│  Solutions    │         │  Solutions    │         │  Solutions    │
│  A1, A2       │         │  B1, B2       │         │  C1, C2       │
└───────┬───────┘         └───────┬───────┘         └───────┬───────┘
        │                         │                         │
        └─────────────────────────┼─────────────────────────┘
                                  ▼
                         ┌─────────────────┐
                         │    PHASE 2      │
                         │   Aggregator    │
                         │  Score & Rank   │
                         └────────┬────────┘
                                  ▼
                         ┌─────────────────┐
                         │    PHASE 3      │
                         │   Synthesizer   │
                         │  Combine Best   │
                         └────────┬────────┘
                                  ▼
                         ┌─────────────────┐
                         │    PHASE 4      │
                         │   Implementer   │
                         │  Apply Changes  │
                         └────────┬────────┘
                                  ▼
                         ┌─────────────────┐
                         │    PHASE 5      │
                         │    Validate     │
                         │   Run Sims (5)  │
                         └────────┬────────┘
                                  ▼
                         ┌─────────────────┐
                         │    PHASE 6      │
                         │ Accept/Reject   │
                         └─────────────────┘
```

## Setup Phase (First Run Only)

### 1. Validate Bot Exists
```bash
if [ ! -f "src/{BOT_NAME}/RobotPlayer.java" ]; then
  echo "ERROR: Bot not found at src/{BOT_NAME}/"
  exit 1
fi
```

### 2. Compile Bot
```bash
./gradlew compileJava 2>&1 | tail -20
```

### 3. Clean Old Data
```bash
rm -f matches/*combat*.bc17 matches/*combat*.db
```

### 4. Initialize Combat Log
Create or verify `src/{BOT_NAME}/COMBAT_LOG_GOT.md` exists.

---

## Phase 0: Run Baseline Simulations

Run combat simulation on 5 maps and extract data:

```bash
# Run simulations in parallel
for map in Shrine Barrier Bullseye Lanes Blitzkrieg; do
  ./gradlew combatSim -PteamA={BOT_NAME} -PteamB={OPPONENT} -PsimMap=$map -PsimSave=matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-$map.bc17 2>&1 &
done
wait
echo "=== Combat simulations complete ==="

# Extract to databases
for match in matches/{BOT_NAME}-combat-vs-{OPPONENT}*.bc17; do
  python3 scripts/bc17_query.py extract "$match"
done
```

**Parse console output** for baseline metrics:
```
[combat] winner=<teamName|none> round=<roundNumber>
```

Store as `BASELINE_RESULTS`:
```json
{
  "wins": 0,
  "losses": 5,
  "avg_win_rounds": null,
  "map_results": {
    "Shrine": {"winner": "opponent", "rounds": 1421},
    "Barrier": {"winner": "opponent", "rounds": 2999},
    ...
  }
}
```

---

## Phase 1: Divergent Analysis (PARALLEL)

Launch **3 analyst sub-agents in parallel** using the Task tool. Each explores a different hypothesis about why the bot is losing.

### 1A. Targeting Analyst (Parallel)
```yaml
Task:
  description: "GoT: Analyze targeting"
  subagent_type: "got-analyst-targeting"
  prompt: |
    Analyze combat targeting for bot '{BOT_NAME}' vs '{OPPONENT}'.
    Unit type: {UNIT}

    BASELINE_RESULTS: {BASELINE_RESULTS}

    Database files: matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-*.db

    Focus ONLY on:
    - Shot accuracy (hits vs misses)
    - Target selection (who are we shooting at?)
    - Leading/prediction accuracy
    - Overkill (shooting dead targets)

    Return JSON:
    {
      "hypothesis": "description of targeting weakness",
      "evidence": ["specific data points"],
      "confidence": 1-5,
      "proposed_fixes": [
        {"id": "A1", "description": "conservative fix", "risk": 1-5},
        {"id": "A2", "description": "aggressive fix", "risk": 1-5}
      ]
    }
```

### 1B. Movement Analyst (Parallel)
```yaml
Task:
  description: "GoT: Analyze movement"
  subagent_type: "got-analyst-movement"
  prompt: |
    Analyze combat movement for bot '{BOT_NAME}' vs '{OPPONENT}'.
    Unit type: {UNIT}

    BASELINE_RESULTS: {BASELINE_RESULTS}

    Database files: matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-*.db

    Focus ONLY on:
    - Positioning relative to enemies
    - Retreat/advance patterns
    - Stuck units (same quadrant over time)
    - Use of cover/trees

    Return JSON:
    {
      "hypothesis": "description of movement weakness",
      "evidence": ["specific data points"],
      "confidence": 1-5,
      "proposed_fixes": [
        {"id": "B1", "description": "conservative fix", "risk": 1-5},
        {"id": "B2", "description": "aggressive fix", "risk": 1-5}
      ]
    }
```

### 1C. Timing Analyst (Parallel)
```yaml
Task:
  description: "GoT: Analyze timing"
  subagent_type: "got-analyst-timing"
  prompt: |
    Analyze combat timing for bot '{BOT_NAME}' vs '{OPPONENT}'.
    Unit type: {UNIT}

    BASELINE_RESULTS: {BASELINE_RESULTS}

    Database files: matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-*.db

    Focus ONLY on:
    - When do we engage vs retreat?
    - First contact timing
    - Death timing (early vs late game)
    - Resource (bullets) management over time

    Return JSON:
    {
      "hypothesis": "description of timing weakness",
      "evidence": ["specific data points"],
      "confidence": 1-5,
      "proposed_fixes": [
        {"id": "C1", "description": "conservative fix", "risk": 1-5},
        {"id": "C2", "description": "aggressive fix", "risk": 1-5}
      ]
    }
```

**Collect outputs as:**
- `HYPOTHESIS_A` (targeting)
- `HYPOTHESIS_B` (movement)
- `HYPOTHESIS_C` (timing)

---

## Phase 2: Aggregation

Launch the **aggregator sub-agent** to score and rank all hypotheses and solutions.

```yaml
Task:
  description: "GoT: Aggregate solutions"
  subagent_type: "got-aggregator"
  prompt: |
    You are the Graph of Thought Aggregator. Score and rank all hypotheses and solutions.

    Bot: {BOT_NAME}
    Unit: {UNIT}

    HYPOTHESIS_A (Targeting):
    {HYPOTHESIS_A}

    HYPOTHESIS_B (Movement):
    {HYPOTHESIS_B}

    HYPOTHESIS_C (Timing):
    {HYPOTHESIS_C}

    For each solution, score on:
    1. Evidence Strength (1-5): How well does data support this?
    2. Expected Impact (1-5): How much improvement if fixed?
    3. Implementation Risk (1-5): How likely to break things?
    4. Bytecode Cost (1-5): How expensive computationally?

    Also determine COMPATIBILITY between solutions:
    - COMPATIBLE: Can be applied together
    - CONFLICTING: Cannot be applied together (e.g., both modify same logic)
    - SYNERGISTIC: Work better together than alone

    Return JSON:
    {
      "ranked_solutions": [
        {"id": "A1", "total_score": 15, "scores": {...}},
        {"id": "B2", "total_score": 14, "scores": {...}},
        ...
      ],
      "compatibility_matrix": {
        "A1-B1": "COMPATIBLE",
        "A1-B2": "CONFLICTING",
        "A1-C1": "SYNERGISTIC",
        ...
      },
      "recommended_combination": ["A1", "C1"],
      "reasoning": "explanation"
    }
```

**Store output as `AGGREGATION_RESULT`**

---

## Phase 3: Synthesis

Launch the **synthesizer sub-agent** to create the final change specification.

```yaml
Task:
  description: "GoT: Synthesize changes"
  subagent_type: "got-synthesizer"
  prompt: |
    You are the Graph of Thought Synthesizer. Create the final change specification.

    Bot: {BOT_NAME}
    Unit: {UNIT}

    Read current code:
    - src/{BOT_NAME}/{UNIT}.java
    - src/{BOT_NAME}/Nav.java

    AGGREGATION_RESULT:
    {AGGREGATION_RESULT}

    Original hypotheses for context:
    - HYPOTHESIS_A: {HYPOTHESIS_A}
    - HYPOTHESIS_B: {HYPOTHESIS_B}
    - HYPOTHESIS_C: {HYPOTHESIS_C}

    Rules:
    1. Select the recommended_combination from aggregator
    2. If any SYNERGISTIC pairs, prioritize those
    3. Maximum 3 changes total
    4. Resolve any conflicts by picking higher-scored option
    5. Write SPECIFIC code changes (not vague descriptions)

    Return JSON:
    {
      "selected_solutions": ["A1", "C1"],
      "changes": [
        {
          "file": "Soldier.java",
          "description": "what this change does",
          "location": "method or line hint",
          "old_code": "existing code snippet",
          "new_code": "replacement code snippet"
        },
        ...
      ],
      "expected_improvement": "description",
      "rollback_plan": "how to undo if it fails"
    }
```

**Store output as `SYNTHESIS_RESULT`**

---

## Phase 4: Implementation

Launch the **implementer sub-agent** to apply the changes.

```yaml
Task:
  description: "GoT: Implement changes"
  subagent_type: "got-implementer"
  prompt: |
    You are the Graph of Thought Implementer. Apply the synthesized changes.

    Bot: {BOT_NAME}

    SYNTHESIS_RESULT:
    {SYNTHESIS_RESULT}

    Instructions:
    1. Read each file that needs modification
    2. Apply each change from SYNTHESIS_RESULT.changes
    3. Verify compilation after ALL changes
    4. Report success or failure for each change

    Return JSON:
    {
      "changes_applied": [
        {"file": "Soldier.java", "status": "SUCCESS", "description": "..."},
        ...
      ],
      "compilation": "SUCCESS" | "FAILED",
      "compilation_errors": [] | ["error messages"],
      "files_modified": ["Soldier.java", "Nav.java"]
    }
```

**Store output as `IMPLEMENTATION_RESULT`**

If compilation fails, STOP and report error. Do not proceed to validation.

---

## Phase 5: Validation

Re-run combat simulations to measure improvement:

```bash
# Run simulations
for map in Shrine Barrier Bullseye Lanes Blitzkrieg; do
  ./gradlew combatSim -PteamA={BOT_NAME} -PteamB={OPPONENT} -PsimMap=$map -PsimSave=matches/{BOT_NAME}-combat-vs-{OPPONENT}-on-$map.bc17 2>&1 &
done
wait

# Extract new results
for match in matches/{BOT_NAME}-combat-vs-{OPPONENT}*.bc17; do
  python3 scripts/bc17_query.py extract "$match"
done
```

Parse and store as `VALIDATION_RESULTS` (same format as BASELINE_RESULTS).

---

## Phase 6: Accept/Reject Decision

Compare BASELINE_RESULTS vs VALIDATION_RESULTS:

```python
# Decision logic
baseline_wins = BASELINE_RESULTS["wins"]
validation_wins = VALIDATION_RESULTS["wins"]

if validation_wins > baseline_wins:
    decision = "ACCEPT"
    reason = f"Improved from {baseline_wins} to {validation_wins} wins"
elif validation_wins == baseline_wins:
    # Check round improvement
    if avg_rounds_improved:
        decision = "ACCEPT"
        reason = "Same wins but faster"
    else:
        decision = "ACCEPT_TENTATIVE"
        reason = "No regression, may need more iterations"
else:
    decision = "REJECT"
    reason = f"Regression from {baseline_wins} to {validation_wins} wins"
```

### If REJECT:
1. Revert changes using `SYNTHESIS_RESULT.rollback_plan`
2. Log the failed attempt
3. Continue to next iteration with different approach

### If ACCEPT:
1. Keep changes
2. Update combat log
3. Continue to next iteration or finish

---

## Iteration Report

After each iteration, output:

```
═══════════════════════════════════════════════════════════════════════════════
GOT ITERATION {N}/{MAX} COMPLETE
═══════════════════════════════════════════════════════════════════════════════

PHASE 1 - Divergent Analysis:
┌─────────────┬─────────────────────────────────┬────────────┐
│ Branch      │ Hypothesis                      │ Confidence │
├─────────────┼─────────────────────────────────┼────────────┤
│ Targeting   │ {HYPOTHESIS_A.hypothesis}       │ {conf}/5   │
│ Movement    │ {HYPOTHESIS_B.hypothesis}       │ {conf}/5   │
│ Timing      │ {HYPOTHESIS_C.hypothesis}       │ {conf}/5   │
└─────────────┴─────────────────────────────────┴────────────┘

PHASE 2 - Aggregation:
  Top Solutions: {AGGREGATION_RESULT.ranked_solutions[0:3]}
  Recommended: {AGGREGATION_RESULT.recommended_combination}

PHASE 3 - Synthesis:
  Selected: {SYNTHESIS_RESULT.selected_solutions}
  Changes: {len(SYNTHESIS_RESULT.changes)}

PHASE 4 - Implementation:
  Status: {IMPLEMENTATION_RESULT.compilation}
  Files: {IMPLEMENTATION_RESULT.files_modified}

PHASE 5 - Validation:
  Baseline: {BASELINE_RESULTS.wins}/5 wins
  After:    {VALIDATION_RESULTS.wins}/5 wins
  Delta:    {delta} wins

PHASE 6 - Decision: {decision}
  Reason: {reason}

═══════════════════════════════════════════════════════════════════════════════
```

---

## Combat Log Update

Append to `src/{BOT_NAME}/COMBAT_LOG_GOT.md`:

```markdown
## GoT Iteration {N}
**Date:** {timestamp}
**Decision:** {ACCEPT|REJECT}

### Hypotheses Explored
| Branch | Hypothesis | Confidence | Solutions |
|--------|------------|------------|-----------|
| Targeting | {hyp_a} | {conf}/5 | A1, A2 |
| Movement | {hyp_b} | {conf}/5 | B1, B2 |
| Timing | {hyp_c} | {conf}/5 | C1, C2 |

### Aggregation Result
- **Top Scored:** {top_solution}
- **Selected Combination:** {selected}
- **Reasoning:** {aggregator_reasoning}

### Changes Applied
{for each change: file, description}

### Results
- **Before:** {baseline_wins}/5 wins, avg {baseline_rounds}r
- **After:** {validation_wins}/5 wins, avg {validation_rounds}r
- **Outcome:** {BETTER|WORSE|SAME}

---
```

---

## Cleanup

After each iteration:
```bash
rm -f matches/*combat*.db
```

Keep .bc17 replay files for review.

---

## Completion

After all iterations:

```
═══════════════════════════════════════════════════════════════════════════════
GRAPH OF THOUGHT COMBAT TRAINING COMPLETE
═══════════════════════════════════════════════════════════════════════════════
Bot: {BOT_NAME}
Iterations: {N}
Approach: Graph of Thought (3-branch parallel analysis)

Hypotheses Explored: {total_hypotheses}
Solutions Considered: {total_solutions}
Changes Accepted: {accepted_changes}
Changes Rejected: {rejected_changes}

Final Performance: {wins}/5 wins | avg {rounds}r
Improvement: {baseline_wins} → {final_wins} wins

Key Insights:
{summarize successful changes}

If objective not met, run `/combat-sim-got --bot {BOT_NAME}` again.
═══════════════════════════════════════════════════════════════════════════════
```

---

## Key Principles

1. **Parallel exploration** - 3 hypotheses examined simultaneously
2. **Diverse solutions** - 2 solutions per hypothesis (conservative + aggressive)
3. **Evidence-based ranking** - Aggregator scores by data, not intuition
4. **Compatibility awareness** - Avoid conflicting changes
5. **Validation-gated** - Changes must improve or maintain performance
6. **Traceable decisions** - Full audit trail in combat log
