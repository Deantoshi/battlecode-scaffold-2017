---
description: Initialize bot improvement session - reads code and creates state files
mode: primary
temperature: 0.7
permission:
  bash: allow
  read: allow
  unsafe-write: allow
  glob: allow
---

# Game Init

Initialize a new bot improvement session. This runs ONCE at the start.

## Arguments

Parse for:
- `--bot NAME` - **REQUIRED**: Bot folder in `src/NAME/`
- `--opponent NAME` - **REQUIRED**: Opponent folder in `src/NAME/`
- `--maps MAP` - Single map (default: `MagicWood`)

---

## Identity

**Start with:**
```
=== GAME-INIT STARTED ===
Bot: {BOT}
Opponent: {OPPONENT}
Map: {MAP}
```

---

## Step 1: Create State Directory

```bash
mkdir -p src/{BOT}/.state
```

---

## Step 2: Check if Already Initialized

Read `src/{BOT}/iteration-history.md`.

**If file exists:**
- Print: "History file exists. Skipping initialization."
- Write `SKIP` to `src/{BOT}/.state/init-status.txt`
- Exit

**If file does NOT exist:**
- Continue to Step 3

---

## Step 3: Read Technical Documentation

Read `HOW_TO_PLAY_BATTLE_CODE_2017.md` in the project root to understand:
- Victory conditions (VP vs elimination)
- Robot types and their roles
- Economy system
- Key API methods

---

## Step 4: Read Your Bot Code

Glob `src/{BOT}/*.java`, then Read each file.

Create analysis in your mind:
- What units do you build?
- What is your economy strategy?
- What are your combat tactics?
- What is your current win strategy?

---

## Step 5: Create Iteration History File

Write to `src/{BOT}/iteration-history.md`:

```markdown
# Iteration History: {BOT} vs {OPPONENT}

Map: {MAP}
Goal: Win in ≤1500 rounds

## Code Analysis

### Your Bot ({BOT})
- **Units:** {what you build}
- **Economy:** {tree/bullet strategy}
- **Combat:** {targeting/movement}
- **Win Strategy:** {current approach}

## Iterations

| # | Result | Rounds | Problem | Change Made |
|---|--------|--------|---------|-------------|
```

---

## Step 6: Write Init Status

Write `DONE` to `src/{BOT}/.state/init-status.txt`

---

## Step 7: Finish

Print:
```
=== GAME-INIT COMPLETE ===
Created: src/{BOT}/iteration-history.md
Created: src/{BOT}/.state/
Ready for iteration loop.
```
