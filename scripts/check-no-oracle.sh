#!/usr/bin/env bash
# No price, no oracle, no fiat figure anywhere in the program (constitution III).
# Task T017a. Runs in CI and in `npm test`'s pre-step; exits 1 on a hit.
#
# The contract validates SKR amounts only. A rate is used in exactly one place —
# the Operator's off-chain `crank chips publish` — and by the time those amounts
# reach a cohort they are ordinary parameters that nothing on-chain can trace
# back to a price.
#
# Comments are stripped before the search: the program's own prose has to be able
# to explain *why* there is no oracle, and say words like "re-price", without the
# check firing on its own documentation.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/programs/holdout/src"
FORBIDDEN='price|oracle|pyth|switchboard|usd|fiat|dollar'

if [[ ! -d "$SRC" ]]; then
  echo "check-no-oracle: no program source at $SRC" >&2
  exit 1
fi

hits=0
while IFS= read -r -d '' file; do
  # Strip line comments (/// doc comments, //! module docs, // ordinary) and
  # anything inside a string literal, then look for the words as identifiers.
  stripped="$(sed -e 's://.*$::' -e 's:"[^"]*"::g' "$file")"
  if matches="$(printf '%s\n' "$stripped" | grep -nEi "[a-z_]*($FORBIDDEN)[a-z_]*" || true)"; [[ -n "$matches" ]]; then
    echo "check-no-oracle: forbidden identifier in ${file#"$ROOT/"}:" >&2
    printf '%s\n' "$matches" >&2
    hits=$((hits + 1))
  fi
done < <(find "$SRC" -name '*.rs' -print0)

if [[ $hits -gt 0 ]]; then
  echo >&2
  echo "The contract must never read a price or hold a fiat figure (constitution III)." >&2
  echo "A rate belongs in \`crank chips publish\` only; cohorts hold SKR amounts." >&2
  exit 1
fi

echo "check-no-oracle: clean — no price, oracle or fiat identifier in the program"
