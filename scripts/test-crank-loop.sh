#!/usr/bin/env bash
# Pins crank-loop.sh's wake deadline (PR #65 review; wakes instead of polls,
# 2026-10-13): with a stub `java` that takes 2 s per call and whose `scripted
# run` asks to run again 1 s later — on a chain clock 30 s behind this one, as
# its `chainTime` and `localTime` say — `--once` with a 12 s window must end by
# the deadline, not one wake past it, must have woken at least once, must never
# run `scripted run` when it did not ask to, and must not retry a configuration
# error (exit 1) every 30 s; the loop refuses to start without HOLDOUT_RPC_URL.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin" "$WORK/keys"
cat > "$WORK/bin/java" <<'EOF'
#!/usr/bin/env bash
sleep 2
if [[ "$*" == *"scripted run"* ]]; then
  [[ -n "${STUB_CONFIG:-}" ]] && exit 1
  chain=$(( $(date +%s) - 30 ))
  if [[ -n "${STUB_QUIET:-}" ]]; then echo '{"steps":[]}'; else echo "{\"chainTime\":$chain,\"localTime\":$(date +%s),\"nextScriptedAt\":$(( chain + 1 )),\"steps\":[{\"step\":\"join\"}]}"; fi
else
  echo '{}'
fi
EOF
chmod +x "$WORK/bin/java"

run_once() {
  PATH="$WORK/bin:$PATH" HOLDOUT_SCRIPTED_KEYS_DIR="$WORK/keys" CRANK_JAR=stub.jar \
    HOLDOUT_RPC_URL="${STUB_RPC_URL-http://rpc.test}" HOLDOUT_PROGRAM_ID=stub \
    CRANK_ONCE_POLL_UNTIL="$1" CRANK_WAKE_MARGIN_SECONDS=0 CRANK_WAKE_GAP_SECONDS=1 CRANK_POLL_MIN_SECONDS=1 \
    bash "$ROOT/scripts/crank-loop.sh" --once 2>&1
}

fail() { echo "test-crank-loop: $*" >&2; echo "$out" >&2; exit 1; }

start=$(date +%s)
until=$(( start + 12 ))
out="$(run_once "$until")"
status=$?
end=$(date +%s)
(( status == 0 )) || fail "exit $status"
(( end <= until + 1 )) || fail "ended at +$(( end - start )) s, past the +12 s deadline"
grep -q "(wake)" <<< "$out" || fail "never woke"

# A `scripted run` that asks for nothing is not run again: no timer polls the RPC.
quiet_start=$(date +%s)
out="$(STUB_QUIET=1 run_once $(( quiet_start + 12 )))"
quiet_end=$(date +%s)
grep -q "(wake)" <<< "$out" && fail "woke though scripted run asked for nothing"
(( quiet_end - quiet_start <= 6 )) || fail "waited $(( quiet_end - quiet_start )) s for nothing"
# A configuration error is not retried every 30 s: nothing heals it before the next pass.
config_start=$(date +%s)
out="$(STUB_CONFIG=1 CRANK_RETRY_SECONDS=1 run_once $(( config_start + 12 )))"
grep -q "(wake)" <<< "$out" && fail "retried a configuration error"

# No RPC URL: the loop says so and stops, instead of failing every pass for ever.
out="$(STUB_RPC_URL= run_once $(( $(date +%s) + 12 )))"
status=$?
(( status == 2 )) && grep -q "HOLDOUT_RPC_URL is not set" <<< "$out" || fail "started without HOLDOUT_RPC_URL (exit $status)"

echo "test-crank-loop: ok (ended at +$(( end - start )) s, deadline +12 s; nothing asked, nothing run)"
