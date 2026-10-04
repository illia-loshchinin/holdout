#!/usr/bin/env bash
# A local validator running the real program, for the local end-to-end run
# (quickstart §5a). Run inside WSL2, where the Solana CLI lives.
#
#   bash scripts/localnet.sh start    fresh ledger, holdout.so loaded as an upgradeable
#                                     program whose upgrade authority is the admin key,
#                                     so `crank init` passes the same check as on devnet
#   bash scripts/localnet.sh fund     local airdrops: admin, operator (the crank's payer),
#                                     the sponsor's 0.05 SOL float, and any extra wallet
#                                     given as an argument (your phone's wallet)
#   bash scripts/localnet.sh status   is it up, and what does each key hold
#   bash scripts/localnet.sh stop
#
# Build the program first — with test-leagues, as the devnet deploy is (T092):
#   NO_DNA=1 anchor build -- --features test-leagues
#
# The validator listens on 127.0.0.1:8899, which WSL2 forwards to Windows'
# localhost; a phone reaches it over USB after `adb reverse tcp:8899 tcp:8899`.
# Chain time is the wall clock here: there is no time travel, so the short test
# league's compressed day is how a whole cycle runs in minutes.
set -euo pipefail

export PATH="$HOME/.local/share/solana/install/active_release/bin:$PATH"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYDIR="${HOLDOUT_KEYDIR:-$HOME/.config/solana/holdout}"
LEDGER="${HOLDOUT_LOCAL_LEDGER:-$HOME/.holdout-localnet}"
PROGRAM_ID="Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4"
SO="$ROOT/target/deploy/holdout.so"
URL="http://127.0.0.1:8899"

pubkey() { solana-keygen pubkey "$KEYDIR/$1.json"; }

wait_up() {
  for _ in $(seq 1 60); do
    solana cluster-version --url "$URL" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "localnet: the validator did not come up; see $LEDGER/validator.log" >&2
  return 1
}

case "${1:-}" in
  start)
    [[ -f "$SO" ]] || { echo "localnet: $SO is missing — build it first (see the header)" >&2; exit 1; }
    [[ -f "$KEYDIR/admin.json" ]] || { echo "localnet: no admin key — run scripts/devnet-keys.sh first" >&2; exit 1; }
    if solana cluster-version --url "$URL" >/dev/null 2>&1; then
      echo "localnet: already running at $URL (stop it first for a fresh ledger)"
      exit 0
    fi
    mkdir -p "$LEDGER"
    # Detached from this shell entirely, so `wsl.exe … start` returns.
    setsid nohup solana-test-validator --reset --quiet --ledger "$LEDGER" \
      --rpc-port 8899 \
      --upgradeable-program "$PROGRAM_ID" "$SO" "$(pubkey admin)" \
      </dev/null >"$LEDGER/validator.log" 2>&1 &
    echo $! >"$LEDGER/validator.pid"
    wait_up
    echo "localnet: up at $URL, program $PROGRAM_ID, upgrade authority $(pubkey admin)"
    ;;
  fund)
    wait_up
    solana airdrop 100 "$(pubkey admin)" --url "$URL" >/dev/null
    solana airdrop 50 "$(pubkey operator)" --url "$URL" >/dev/null
    # The sponsor holds its float and nothing more, as on devnet (research R13).
    solana airdrop 0.05 "$(pubkey sponsor)" --url "$URL" >/dev/null
    shift
    for extra in "$@"; do solana airdrop 1 "$extra" --url "$URL" >/dev/null; done
    "$0" status
    ;;
  status)
    if ! solana cluster-version --url "$URL" >/dev/null 2>&1; then echo "localnet: not running"; exit 1; fi
    echo "localnet: up at $URL, slot $(solana slot --url "$URL")"
    for role in admin operator sponsor treasury; do
      printf '  %-9s %s  %s\n' "$role" "$(pubkey "$role")" "$(solana balance "$(pubkey "$role")" --url "$URL")"
    done
    ;;
  stop)
    if [[ -f "$LEDGER/validator.pid" ]]; then kill "$(cat "$LEDGER/validator.pid")" 2>/dev/null || true; rm -f "$LEDGER/validator.pid"; fi
    pkill -f "solana-test-validator.*$LEDGER" 2>/dev/null || true
    echo "localnet: stopped"
    ;;
  *)
    sed -n '2,20p' "$0"
    exit 1
    ;;
esac
