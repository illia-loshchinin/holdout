#!/usr/bin/env bash
# The app's own wording, checked (T103a, FR-039, FR-040, FR-042, constitution XIII):
#   1. no gambling or investment words in any user-facing string,
#   2. every fiat figure carries "≈",
#   3. the test-network notice is present,
#   4. nothing the v2 tab screens retired is back, and the member-facing words the copy rules
#      ban (cohort but for DEVNET COHORT, league, challenge, earn, survivors, Easy, Hard) stay
#      out of every sentence (T390, FR-067, FR-069; the same patterns as CopyRulesTest).
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

# Member-facing sentences: the string literals of the member app's Kotlin that hold a space, one per
# line as "file:line:literal", with comment lines skipped and `$name` / `${...}` taken out (a literal
# that is only a key or a format has no space left to be a sentence).
sentences="$(find "$SRC/kotlin" "$MEMBER" -name '*.kt' -print0 | xargs -0 awk '
  /^[ \t]*(\*|\/\/|\/\*)/ { next }
  {
    line = $0
    gsub(/\$\{[^}]*\}/, "", line)
    gsub(/\$[A-Za-z_][A-Za-z_0-9]*/, "", line)
    while (match(line, /"[^"]*"/)) {
      lit = substr(line, RSTART, RLENGTH)
      if (lit ~ / /) print FILENAME ":" FNR ":" lit
      line = substr(line, RSTART + RLENGTH)
    }
  }')"

# Prints the sentences (lines of "file:line:literal") whose literal part matches the ERE $1 - the file
# name is not searched ("cohort/" is a package). A second argument "ci" ignores case.
sentences_matching() {
  awk -v re="$1" -v ci="${2:-}" '{
    lit = $0
    sub(/^[^:]*:[0-9]+:/, "", lit)
    if (ci == "ci") lit = tolower(lit)
    if (lit ~ re) print
  }' <<<"$sentences"
}

if hits="$(sentences_matching "The roster|Start today's|5 cards|Check again|(^|[^a-z])Standing|[+] ?[0-9][0-9,.]* ?(SKR )?boost" | grep -vE 'still Standing|STILL STANDING')"; then
  echo "check-wording: copy the v2 tab screens retired:"
  echo "$hits"
  fail=1
fi

# DEVNET COHORT is the one place the word may stay (it names the network, not a group of members).
if hits="$({ sentences_matching "(^|[^a-z])(cohorts?|leagues?|challenges?|earn|earns|earned|earning|earnings|survivors?)([^a-z]|$)" ci | grep -viE 'DEVNET COHORT|earn a streak freeze'; sentences_matching "(^|[^A-Za-z])(Easy|Hard)([^A-Za-z]|$)"; } || true)" && [[ -n "$hits" ]]; then
  echo "check-wording: a banned member word (cohort, league, challenge, earn, survivors, Easy, Hard):"
  echo "$hits"
  fail=1
fi

[[ $fail -eq 0 ]] && echo "check-wording: clean"
exit $fail
