#!/usr/bin/env bash
set -euo pipefail

scripts/run-self-improve-match.sh grok_code_fast_1 examplefuncsplayer MagicWood | rg -q 'GOAL_MET=YES'
