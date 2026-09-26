#!/usr/bin/env bash
# The crank against the local validator (quickstart §5a), from Git Bash on
# Windows — the crank's JVM — with the keys read from WSL.
#
#   bash scripts/localnet-crank.sh <crank command…>   one command, e.g. status
#   bash scripts/localnet-crank.sh --loop             crank run + scripted run every 30 s
#
# Local runs fill the test cohorts with scripted members (--floor-test 8) and
# leave the judged leagues empty, so the seven-day wallets are free for them.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYS="${HOLDOUT_WSL_KEYDIR:-//wsl.localhost/Ubuntu/home/$USERNAME/.config/solana/holdout}"
export HOLDOUT_RPC_URL="${HOLDOUT_RPC_URL:-http://127.0.0.1:8899}"
export HOLDOUT_PROGRAM_ID=Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4
export HOLDOUT_ADMIN_KEYPAIR="$KEYS/admin.json"
export HOLDOUT_OPERATOR_KEYPAIR="$KEYS/operator.json"
export HOLDOUT_PAYER_KEYPAIR="$KEYS/operator.json"
export HOLDOUT_SCRIPTED_KEYS_DIR="$KEYS/scripted"
JAR="$ROOT/kotlin/crank/build/libs/crank.jar"

crank() { java -jar "$JAR" "$@" 2>/dev/null; }

if [[ "${1:-}" == "--loop" ]]; then
  while true; do
    echo "=== $(date -u +%FT%TZ)"
    crank run
    echo
    crank scripted run --floor-7d 0 --floor-21d 0 --floor-test 8
    echo
    sleep "${CRANK_INTERVAL_SECONDS:-30}"
  done
fi
crank "$@"
