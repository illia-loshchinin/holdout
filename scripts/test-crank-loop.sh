#!/usr/bin/env bash
# Pins crank-loop.sh's poll deadline (PR #65 review): with a stub `java` that
# takes 2 s per call, `--once` with a 9 s poll window must end by the deadline,
# not one poll past it, and must have polled at least once.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin" "$WORK/keys"
cat > "$WORK/bin/java" <<'EOF'
#!/usr/bin/env bash
sleep 2
if [[ "$*" == *"scripted run"* ]]; then echo '{"steps":[{"step":"join"}]}'; else echo '{}'; fi
EOF
chmod +x "$WORK/bin/java"

start=$(date +%s)
until=$(( start + 9 ))
out="$(PATH="$WORK/bin:$PATH" HOLDOUT_SCRIPTED_KEYS_DIR="$WORK/keys" CRANK_JAR=stub.jar \
  CRANK_ONCE_POLL_UNTIL="$until" CRANK_SCRIPTED_POLL_SECONDS=1 CRANK_POLL_MIN_SECONDS=1 \
  bash "$ROOT/scripts/crank-loop.sh" --once 2>&1)"
status=$?
end=$(date +%s)

fail() { echo "test-crank-loop: $*" >&2; echo "$out" >&2; exit 1; }
(( status == 0 )) || fail "exit $status"
(( end <= until + 1 )) || fail "ended at +$(( end - start )) s, past the +9 s deadline"
grep -q "(poll)" <<< "$out" || fail "never polled"
echo "test-crank-loop: ok (ended at +$(( end - start )) s, deadline +9 s)"
