#!/usr/bin/env bash
# The app's own wording, checked (T103a, FR-039, FR-040, FR-042, constitution XIII):
#   1. no gambling or investment words in any user-facing string,
#   2. every fiat figure carries "≈",
#   3. the test-network notice is present.
# Reads string literals only — in the member app's Kotlin and string resources
# (src/main, src/member; spec 004), and the README's weaknesses source
# (docs/weaknesses.json) — so Kotlin's `return` keyword is not a finding.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/kotlin/app/src/main"
MEMBER="$ROOT/kotlin/app/src/member"
WEAK="$ROOT/docs/weaknesses.json"
fail=0

words='\b(winnings|returns?|yield|earnings|bet|wager|gambl[a-z]*|odds|luck[a-z]*)\b'
if hits="$(grep -rnoiIE "\"[^\"]*${words}[^\"]*\"" "$SRC/kotlin" "$SRC/res/values" "$MEMBER" "$WEAK" 2>/dev/null)"; then
  echo "check-wording: forbidden wording in user-facing strings:"
  echo "$hits"
  fail=1
fi

if hits="$(grep -rnoIE '"[^"]*\$[0-9][^"]*"' "$SRC/kotlin" "$SRC/res/values" "$MEMBER" "$WEAK" 2>/dev/null | grep -v '≈')"; then
  echo "check-wording: a fiat figure without \"≈\":"
  echo "$hits"
  fail=1
fi

if ! grep -rq "TEST NETWORK · TEST TOKEN" "$SRC/kotlin"; then
  echo "check-wording: the test-network notice is missing"
  fail=1
fi

[[ $fail -eq 0 ]] && echo "check-wording: clean"
exit $fail
