#!/usr/bin/env bash
# The crank's schedule (T094, research R12): one automation pass, then scripted
# members, every CRANK_INTERVAL_SECONDS (default 600) — or sooner, when `crank
# run` reports a cohort open falling due before then (`nextOpenAt`, chain time):
# the loop wakes a few seconds after it, so the next test cohort is joinable
# moments after the one before starts, not up to an interval later (FR-044a).
# Between passes `crank scripted run` runs alone every CRANK_SCRIPTED_POLL_SECONDS
# (default 60, 0 to switch off), so a Hackathon round tops up soon after someone
# real joins it (FR-043). Runs in the Fly machine, and in the GitHub Actions
# fallback with --once (then CRANK_ONCE_POLL_SECONDS of scripted polls).
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

SUMMARY="$KEYS/last-run.json"

pass() {
  echo "=== $(date -u +%FT%TZ) crank run"
  java -jar "$JAR" run | tee "$SUMMARY"
  local run=${PIPESTATUS[0]} scripted=0
  if [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]]; then
    echo "=== $(date -u +%FT%TZ) crank scripted run"
    java -jar "$JAR" scripted run
    scripted=$?
  fi
  # Either failing fails the pass: an underfilled cohort or a missed scripted day is a failure too.
  (( run != 0 )) && return $run
  return $scripted
}

# Between full passes, `scripted run` alone every CRANK_SCRIPTED_POLL_SECONDS
# (default 60): a Hackathon round gets scripted members only once someone real
# joins it (FR-043), and its lobby lasts 30 minutes, so a judge who joins finds
# it topped up within a minute rather than up to a pass later. A poll that
# does nothing prints nothing.
poll_scripted() {
  local out
  out="$(java -jar "$JAR" scripted run)"
  local status=$?
  if (( status != 0 )) || grep -q '"step"' <<< "$out"; then
    echo "=== $(date -u +%FT%TZ) crank scripted run (poll)"
    echo "$out"
    (( status != 0 )) && echo "=== poll ended with a failure; the next one retries (exit $status)"
  fi
  return 0
}

# One pass, then `scripted run` alone every CRANK_SCRIPTED_POLL_SECONDS for
# CRANK_ONCE_POLL_SECONDS more (default 0): the GitHub Actions fallback starts
# a pass every 10 minutes and sets this so it polls too (crank.yml).
if [[ "${1:-}" == "--once" ]]; then
  pass
  status=$?
  left="${CRANK_ONCE_POLL_SECONDS:-0}"
  poll="${CRANK_SCRIPTED_POLL_SECONDS:-60}"
  while [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]] && (( poll > 0 && left >= poll )); do
    sleep "$poll"
    left=$(( left - poll ))
    poll_scripted
  done
  exit $status
fi

# Seconds to sleep: the interval, or until 5 s after the next open falls due
# (a margin for the chain clock), never less than 5 s.
next_sleep() {
  local interval="${CRANK_INTERVAL_SECONDS:-600}" next wait
  next="$(grep -o '"nextOpenAt":[0-9]*' "$SUMMARY" 2>/dev/null | head -n 1 | cut -d: -f2)"
  [[ -z "$next" ]] && { echo "$interval"; return; }
  wait=$(( next + 5 - $(date +%s) ))
  (( wait < 5 )) && wait=5
  (( wait < interval )) && echo "$wait" || echo "$interval"
}

while true; do
  : > "$SUMMARY"
  pass || echo "=== pass ended with a failure; the next pass retries (exit $?)"
  wait="$(next_sleep)"
  poll="${CRANK_SCRIPTED_POLL_SECONDS:-60}"
  while [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]] && (( poll > 0 && wait > poll )); do
    sleep "$poll"
    wait=$(( wait - poll ))
    poll_scripted
  done
  sleep "$wait"
done
