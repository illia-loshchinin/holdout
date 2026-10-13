#!/usr/bin/env bash
# The crank's schedule (T094, research R12): one automation pass, then scripted
# members, every CRANK_INTERVAL_SECONDS (default 600) — or sooner, when `crank
# run` reports a cohort open falling due before then (`nextOpenAt`, chain time):
# the loop wakes a few seconds after it, so the next test cohort is joinable
# moments after the one before starts, not up to an interval later (FR-044a).
# Between passes `crank scripted run` runs alone only when it asked to
# (`nextScriptedAt`, chain time, moved onto this machine's clock by its
# `localTime`): every minute while a Hackathon lobby has room, so it is topped
# up soon after someone real joins it, and each day of a round a scripted member
# still plays (FR-043). An empty lobby's check reads only the cohort list, never
# a roster, and a wake reads a running cohort's roster only when a day has begun
# since the last clean `scripted run` (`--since`; owner, 13 October; stand-in
# review of #70). Runs in the Fly machine, and
# in the GitHub Actions fallback with --once (then scripted wakes until
# CRANK_ONCE_POLL_UNTIL).
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

# Without these every pass fails as a configuration error, which no retry heals:
# stop here, loudly, rather than log a failure every interval for ever.
for name in HOLDOUT_RPC_URL HOLDOUT_PROGRAM_ID; do
  if [[ -z "${!name:-}" ]]; then
    echo "crank-loop: $name is not set; set it (a Fly secret or the workflow's env) and start again" >&2
    exit 2
  fi
done

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
SCRIPTED="$KEYS/last-scripted.json"
# When `scripted run` next wants to run, on this machine's clock (epoch seconds),
# one per line: from its last summary, plus any retry.
WAKES="$KEYS/wakes"
# The chain time of the last `scripted run` that ended clean (--since).
SINCE=""

# The time [$1] asked for in the summary [$2], on this machine's clock: a chain
# time moved by how far the chain clock stood from this one when the run read it
# (`localTime` - `chainTime`), so a chain clock running behind never wakes the
# loop early, again and again until it catches up (stand-in review of #70). A
# summary without `localTime` is taken as it is.
local_time() {
  local at chain here
  at="$(grep -o "\"$1\":[0-9]*" "$2" 2>/dev/null | head -n 1 | cut -d: -f2)"
  [[ -n "$at" ]] || return 0
  chain="$(grep -o '"chainTime":[0-9]*' "$2" | head -n 1 | cut -d: -f2)"
  here="$(grep -o '"localTime":[0-9]*' "$2" | head -n 1 | cut -d: -f2)"
  if [[ -n "$chain" && -n "$here" ]]; then echo $(( at - chain + here )); else echo "$at"; fi
}

# After a `scripted run` that exited [$1]: its wake, its chain time as the next
# --since when it ended clean, and a retry within CRANK_RETRY_SECONDS (default
# 30) when it failed in a way the next try can heal (Codex review of #70). A
# configuration error (exit 1) is left to the next pass, not retried every 30 s.
after_scripted() {
  local status=$1 chain
  : > "$WAKES"
  local_time nextScriptedAt "$SCRIPTED" >> "$WAKES"
  if (( status == 0 )); then
    chain="$(grep -o '"chainTime":[0-9]*' "$SCRIPTED" | head -n 1 | cut -d: -f2)"
    [[ -n "$chain" ]] && SINCE=$chain
  elif (( status != 1 )); then
    echo $(( $(date +%s) + ${CRANK_RETRY_SECONDS:-30} )) >> "$WAKES"
  fi
}

pass() {
  echo "=== $(date -u +%FT%TZ) crank run"
  java -jar "$JAR" run | tee "$SUMMARY"
  local run=${PIPESTATUS[0]} scripted=0
  if [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]]; then
    # The full pass reads every running roster (no --since).
    echo "=== $(date -u +%FT%TZ) crank scripted run"
    java -jar "$JAR" scripted run | tee "$SCRIPTED"
    scripted=${PIPESTATUS[0]}
    after_scripted "$scripted"
  fi
  # Either failing fails the pass: an underfilled cohort or a missed scripted day is a failure too.
  (( run != 0 )) && return $run
  return $scripted
}

# The earliest time `scripted run` asked to run again, on this machine's clock, or nothing.
next_scripted() {
  sort -n "$WAKES" 2>/dev/null | head -n 1
}

# `scripted run` alone, since the last clean one, killed at the deadline it was
# given ($1 seconds): every step is idempotent, and the next pass re-reads the
# chain. One that does nothing prints nothing.
wake_scripted() {
  local out status
  out="$(timeout "$1" java -jar "$JAR" scripted run ${SINCE:+--since "$SINCE"})"
  status=$?
  printf '%s\n' "$out" > "$SCRIPTED"
  if (( status != 0 )) || grep -q '"step"' <<< "$out"; then
    echo "=== $(date -u +%FT%TZ) crank scripted run (wake)"
    echo "$out"
    (( status != 0 )) && echo "=== wake ended with a failure (exit $status)"
  fi
  after_scripted "$status"
  return 0
}

# Until the absolute time [$1], and never past it: sleeps to each time `scripted
# run` asked for (plus CRANK_WAKE_MARGIN_SECONDS, default 2; at least CRANK_WAKE_GAP_SECONDS, default 5, after the last one) and
# runs it there. A wake starts only while CRANK_POLL_MIN_SECONDS (default 20)
# of run time still fit, and is stopped at the deadline, so the next full pass
# is never pushed back; a wake due later is left to that pass.
scripted_until() {
  local deadline=$1 least="${CRANK_POLL_MIN_SECONDS:-20}" margin="${CRANK_WAKE_MARGIN_SECONDS:-2}" gap="${CRANK_WAKE_GAP_SECONDS:-5}"
  local next at left
  [[ -n "${HOLDOUT_SCRIPTED_KEYS_DIR:-}" ]] || return 0
  while true; do
    next="$(next_scripted)"
    [[ -n "$next" ]] || return 0
    at=$(( next + margin ))
    (( at < $(date +%s) + gap )) && at=$(( $(date +%s) + gap ))
    (( deadline - at > least )) || return 0
    sleep $(( at - $(date +%s) ))
    left=$(( deadline - $(date +%s) ))
    (( left > 0 )) || return 0
    wake_scripted "$left"
  done
}

# One pass, then `scripted run` alone whenever it asks, until
# CRANK_ONCE_POLL_UNTIL (epoch seconds; the GitHub Actions fallback sets it from
# its job's start, crank.yml), or for CRANK_ONCE_POLL_SECONDS (default 0).
if [[ "${1:-}" == "--once" ]]; then
  : > "$SCRIPTED"
  : > "$WAKES"
  pass
  status=$?
  scripted_until "${CRANK_ONCE_POLL_UNTIL:-$(( $(date +%s) + ${CRANK_ONCE_POLL_SECONDS:-0} ))}"
  exit $status
fi

# Seconds to sleep: the interval, or until 5 s after the next open falls due
# (on this machine's clock), never less than 5 s.
next_sleep() {
  local interval="${CRANK_INTERVAL_SECONDS:-600}" next wait
  next="$(local_time nextOpenAt "$SUMMARY")"
  [[ -z "$next" ]] && { echo "$interval"; return; }
  wait=$(( next + 5 - $(date +%s) ))
  (( wait < 5 )) && wait=5
  (( wait < interval )) && echo "$wait" || echo "$interval"
}

while true; do
  : > "$SUMMARY"
  : > "$SCRIPTED"
  : > "$WAKES"
  pass || echo "=== pass ended with a failure; the next pass retries (exit $?)"
  deadline=$(( $(date +%s) + $(next_sleep) ))
  scripted_until "$deadline"
  rest=$(( deadline - $(date +%s) ))
  (( rest > 0 )) && sleep "$rest"
done
