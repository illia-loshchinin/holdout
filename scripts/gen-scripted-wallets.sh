#!/usr/bin/env bash
# The 56 scripted-member wallets, generated once (research R12). Task T010.
#
#   20  7-day league   — the same 20 play every 7-day cohort, each holding a
#                        lobby place in the next while in the current (FR-006a)
#   36  21-day league  — three groups of 12 taking turns every three weeks, so
#                        no wallet is ever in two cohorts at once (FR-006b)
#
# Secrets land in $HOLDOUT_KEYDIR/scripted/ — outside the repository, like every
# other key here. Public keys land in kotlin/core/src/main/resources/, committed,
# bundled into the app and copied into the README: constitution XII and the
# README's known weaknesses both require every scripted member to be labelled
# wherever it appears, and that list is what does the labelling.
#
# Scripted members are real wallets under the same contract rules. Their
# forfeits fund real prizes; their completed days are NOT camera-verified. The
# contract never learns which wallets these are — the floors live in the crank's
# configuration alone (FR-043a).
#
# Idempotent: an existing keypair is kept, and the JSON is rewritten from
# whatever is on disk. Run it once, commit the JSON, and never run it again
# against a different keydir — a regenerated list strands the funded wallets.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYDIR="${HOLDOUT_KEYDIR:-$HOME/.config/solana/holdout}"
SECRETS="$KEYDIR/scripted"
OUT="$ROOT/kotlin/core/src/main/resources/scripted-wallets.json"

if ! command -v solana-keygen >/dev/null 2>&1; then
  echo "gen-scripted-wallets: solana-keygen is not on PATH — run this inside WSL2." >&2
  exit 1
fi

mkdir -p "$SECRETS" "$(dirname "$OUT")"
chmod 700 "$SECRETS"

# group:count — the name is what the crank's rotation reads, and what the JSON
# carries so a reader can see which wallets can be in a cohort together.
GROUPS=("7d:20" "21d-a:12" "21d-b:12" "21d-c:12")

created=0
for spec in "${GROUPS[@]}"; do
  group="${spec%%:*}"
  count="${spec##*:}"
  for ((i = 0; i < count; i++)); do
    path="$(printf '%s/%s-%02d.json' "$SECRETS" "$group" "$i")"
    [[ -f "$path" ]] && continue
    solana-keygen new --no-bip39-passphrase --silent --outfile "$path" >/dev/null
    chmod 600 "$path"
    created=$((created + 1))
  done
done

{
  echo '{'
  echo '  "note": "Scripted members: real wallets under the same contract rules as anyone else. They top a cohort up to its floor only after real members have joined (FR-043). Their forfeits fund real prizes; their completed days are not camera-verified. The contract does not know which wallets these are.",'
  echo '  "cluster": "devnet",'
  echo '  "wallets": ['
  first=1
  for spec in "${GROUPS[@]}"; do
    group="${spec%%:*}"
    count="${spec##*:}"
    league="${group%%-*}"
    for ((i = 0; i < count; i++)); do
      path="$(printf '%s/%s-%02d.json' "$SECRETS" "$group" "$i")"
      pk="$(solana-keygen pubkey "$path")"
      [[ $first -eq 1 ]] || echo ','
      first=0
      printf '    { "pubkey": "%s", "league": "%s", "group": "%s", "index": %d }' \
        "$pk" "$league" "$group" "$i"
    done
  done
  echo
  echo '  ]'
  echo '}'
} > "$OUT"

total="$(grep -c '"pubkey"' "$OUT")"
echo "gen-scripted-wallets: $created new, $total listed"
echo "  secrets: $SECRETS  (never committed, never shared)"
echo "  public:  ${OUT#"$ROOT/"}  (commit this)"
echo
echo "Each wallet needs one 0.02 SOL top-up before its first join — ~1.12 SOL in"
echo "total, from the operator key. They pay their own fees and rent afterwards,"
echo "so they never draw on the faucet's SOL budget (R12, R13)."
