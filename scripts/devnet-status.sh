#!/usr/bin/env bash
# What is funded, what is not, against the schedule in research R12/R13 and the
# quickstart §1 prerequisites table. Task T010. Read-only: it moves nothing.
#
#   scripted wallets   ~1.12 SOL total, one 0.02 SOL top-up each, due 22 Sep
#   faucet vault       25 SOL by 8 Oct — from 9 October it must last to
#                      11 November with no top-up (FR-042a)
#   sponsor float      0.05 SOL, refilled permissionlessly by `top_up_sponsor`
#   deploy rent        measured at the rehearsal deploy, held by `admin`
#
# Run it in WSL2. `--url` overrides the cluster (default devnet).

set -euo pipefail

KEYDIR="${HOLDOUT_KEYDIR:-$HOME/.config/solana/holdout}"
URL="${HOLDOUT_RPC:-https://api.devnet.solana.com}"
PROGRAM_ID="Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url) URL="$2"; shift 2 ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "devnet-status: unknown argument $1" >&2; exit 2 ;;
  esac
done

if ! command -v solana >/dev/null 2>&1; then
  echo "devnet-status: the Solana CLI is not on PATH — run this inside WSL2." >&2
  exit 1
fi

balance_of() { # pubkey -> bare SOL figure, or "-" when the account does not exist
  solana balance "$1" --url "$URL" 2>/dev/null | awk '{print $1}' || echo "-"
}

echo "cluster: $URL"
echo

echo "Operational keys (scripts/devnet-keys.sh)"
printf '  %-10s %-46s %12s  %s\n' ROLE PUBKEY BALANCE TARGET
declare -A TARGET=(
  [admin]="deploy rent + fees"
  [operator]="~1.15 SOL, to fund the scripted wallets"
  [sponsor]="0.05 SOL float"
  [treasury]="a few fees only"
)
for role in admin operator sponsor treasury; do
  path="$KEYDIR/$role.json"
  if [[ ! -f "$path" ]]; then
    printf '  %-10s %-46s %12s  %s\n' "$role" "(no keypair — run devnet-keys.sh)" "-" "${TARGET[$role]}"
    continue
  fi
  pk="$(solana-keygen pubkey "$path")"
  printf '  %-10s %-46s %12s  %s\n' "$role" "$pk" "$(balance_of "$pk")" "${TARGET[$role]}"
done

echo
echo "Program $PROGRAM_ID"
if info="$(solana program show "$PROGRAM_ID" --url "$URL" 2>/dev/null)"; then
  printf '%s\n' "$info" | sed 's/^/  /'
else
  echo "  not deployed on this cluster yet"
fi

# The faucet vault is a PDA of the program, so it exists only after `initialize`.
if vault="$(solana find-program-derived-address "$PROGRAM_ID" string:faucet_vault --url "$URL" 2>/dev/null | awk '{print $1}')" && [[ -n "$vault" ]]; then
  echo
  echo "Faucet vault $vault"
  echo "  balance: $(balance_of "$vault")   target: 25 SOL by 8 Oct (R13)"
fi

echo
SCRIPTED="kotlin/core/src/main/resources/scripted-wallets.json"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/$SCRIPTED" ]]; then
  count="$(grep -c '"pubkey"' "$ROOT/$SCRIPTED" || true)"
  echo "Scripted wallets: $count listed in $SCRIPTED"
  echo "  each needs one 0.02 SOL top-up before its first join (R12); the crank"
  echo "  reports which are short — \`crank scripted --dry-run\`"
else
  echo "Scripted wallets: none generated yet — run scripts/gen-scripted-wallets.sh"
fi
