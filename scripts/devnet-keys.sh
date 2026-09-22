#!/usr/bin/env bash
# The four operational devnet keypairs, created once and never overwritten.
# Task T010; the roles are fixed by research R14 and the `crank init` arguments
# in quickstart §6.
#
#   admin      signs `initialize`, `create_league` and `set_flags`. It must also
#              be the program's upgrade authority — `initialize` verifies that
#              against the ProgramData account and stores the signer as
#              `Config.admin` (R14). Deploy with this key, or hand the authority
#              to it with `solana program set-upgrade-authority` BEFORE running
#              `crank init`: revoking or moving it afterwards is fine, revoking
#              it first leaves the program permanently uninitialisable.
#   operator   signs `publish_chip_set`, and nothing else.
#   sponsor    pays the fee for `claim_faucet`. Its secret SHIPS IN THE APK
#              (README known weakness), so it holds a 0.05 SOL float and nothing
#              else. Treat it as public from the moment it exists.
#   treasury   owns the SKR fee destination token account — the 20 % forfeit
#              fee, the no-finisher half and rounding remainders.
#
# Nothing is written inside the repository: `.gitignore` excludes keypairs, and
# this keeps them excluded by construction. Run it in WSL2, where the Solana CLI
# lives (quickstart §1).

set -euo pipefail

KEYDIR="${HOLDOUT_KEYDIR:-$HOME/.config/solana/holdout}"
ROLES=(admin operator sponsor treasury)

if ! command -v solana-keygen >/dev/null 2>&1; then
  echo "devnet-keys: solana-keygen is not on PATH." >&2
  echo "Run this inside WSL2 with the Solana CLI 3.1.10 installed (quickstart §1)." >&2
  exit 1
fi

mkdir -p "$KEYDIR"
chmod 700 "$KEYDIR"

created=0
for role in "${ROLES[@]}"; do
  path="$KEYDIR/$role.json"
  if [[ -f "$path" ]]; then
    continue
  fi
  # --no-bip39-passphrase: these are devnet operational keys, backed up as files.
  solana-keygen new --no-bip39-passphrase --silent --outfile "$path" >/dev/null
  chmod 600 "$path"
  created=$((created + 1))
  echo "devnet-keys: created $role"
done

if [[ $created -eq 0 ]]; then
  echo "devnet-keys: all four already exist in $KEYDIR — nothing created"
fi

echo
printf '%-10s %-46s %s\n' ROLE PUBKEY FILE
for role in "${ROLES[@]}"; do
  path="$KEYDIR/$role.json"
  printf '%-10s %-46s %s\n' "$role" "$(solana-keygen pubkey "$path")" "$path"
done

cat <<'NOTE'

Back these up somewhere outside this machine. Losing `admin` after the 28
September freeze means the judged deployment can no longer be upgraded or
governed; losing `treasury` strands the fees. Losing `sponsor` costs 0.05 SOL.

Next: scripts/devnet-status.sh, to see what each one still needs funding to.
NOTE
