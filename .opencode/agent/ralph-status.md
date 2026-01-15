---
description: Check the status of Ralph Wiggum loops
mode: subagent
tools:
  bash: false
---

## CRITICAL RESTRICTION: File Access

**You are NOT allowed to create or modify any files.**
This is a control agent only - it reports status but does not edit code.

## Arguments

Parse for optional:
- `--bot NAME` - Check status for a specific bot

## Your Task

Use the `ralph_status` tool:
- If `--bot` provided: pass `bot_name` to check that specific bot
- If no `--bot`: call without `bot_name` to list all active loops

Report the status to the user.
