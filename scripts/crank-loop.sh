#!/usr/bin/env bash
# The crank's schedule (T094, research R12): one automation pass, then scripted
# members, every CRANK_INTERVAL_SECONDS (default 600). Runs in the Fly machine
# and in the GitHub Actions fallback (with --once).
#
# Keys come from the environment, never from the image or the repository:
#   HOLDOUT_PAYER_KEY_JSON        the payer's Solana CLI keypair (a JSON array)
#   HOLDOUT_SCRIPTED_KEYS_TGZ_B64 base64 of a .tar.gz of the 56 scripted keypairs
#                                 (tar -czf - -C ~/.config/solana/holdout/scripted . | base64 -w0)
#                                 required: the loop exits 2 without it, unless
#                                 CRANK_WITHOUT_SCRIPTED=1 runs the automation alone
# plus HOLDOUT_RPC_URL and HOLDOUT_PROGRAM_ID (contracts/crank-cli.md).
#
# A failed pass is logged and the loop carries on: every step is idempotent, so
# the next pass finishes whatever this one could not (constitution VIII).
set -uo pipefail

JAR="${CRANK_JAR:-/crank/crank.jar}"
KEYS="$(mktemp -d)"
chmod 700 "$KEYS"

if [[ -n "${HOLDOUT_PAYER_KEY_JSON:-}" ]]; then
  printf '%s' "$HOLDOUT_PAYER_KEY_JSON" > "$KEYS/payer.json"
  export HOLDOUT_PAYER_KEYPAIR="$KEYS/payer.json"
fi
if [[ -n "${HOLDOUT_SCRIPTED_KEYS_TGZ_B64:-}" ]]; then
  mkdir -p "$KEYS/scripted"
  # A truncated or garbled secret fails here too: without `set -e` the pipeline's
  # failure would pass unnoticed until some cohort needed a key that is not there.
  if ! printf '%s' "$HOLDOUT_SCRIPTED_KEYS_TGZ_B64" | base64 -d | tar -xzf - -C "$KEYS/scripted" ||
    ! compgen -G "$KEYS/scripted/*.json" > /dev/null; then
    echo "crank-loop: HOLDOUT_SCRIPTED_KEYS_TGZ_B64 does not unpack to the scripted keypairs; build it again (see the top of this file)" >&2
    exit 2
  fi
  export HOLDOUT_SCRIPTED_KEYS_DIR="$KEYS/scripted"
elif [[ -z "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" && "${CRANK_WITHOUT_SCRIPTED:-}" != "1" ]]; then
  # A missing secret reads as empty (GitHub) or unset (Fly): without the keys no
  # scripted member ever joins or submits, and the cohort floors go unmet — so
  # the scheduler fails here rather than stay green. CRANK_WITHOUT_SCRIPTED=1
  # runs the automation alone, on purpose.
  echo "crank-loop: HOLDOUT_SCRIPTED_KEYS_TGZ_B64 is not set; set it, or CRANK_WITHOUT_SCRIPTED=1 to run without scripted members" >&2
  exit 2
fi
chmod 600 "$KEYS"/*.json "$KEYS"/scripted/*.json 2>/dev/null || true

pass() {
  echo "=== $(date -u +%FT%TZ) crank run"
  java -jar "$JAR" run
  local run=$? scripted=0
  if [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]]; then
    echo "=== $(date -u +%FT%TZ) crank scripted run"
    java -jar "$JAR" scripted run
    scripted=$?
  fi
  # Either failing fails the pass: an underfilled cohort or a missed scripted day is a failure too.
  (( run != 0 )) && return $run
  return $scripted
}

if [[ "${1:-}" == "--once" ]]; then
  pass
  exit $?
fi

while true; do
  pass || echo "=== pass ended with a failure; the next pass retries (exit $?)"
  sleep "${CRANK_INTERVAL_SECONDS:-600}"
done
