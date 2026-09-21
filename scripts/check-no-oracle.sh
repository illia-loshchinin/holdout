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

# `rate` needs its own pattern, and a case-sensitive one. Matched as a substring
# it fires on `crate`, `accurate`, `saturating`; matched case-insensitively it
# fires on all of those again through `Rate`. So it is matched as a *name
# component*: `rate`, `exchange_rate`, `rate_bps`, `ExchangeRate` — a word on its
# own in snake_case, or a capitalised hump in PascalCase. That is the shape a
# field or an argument carrying a conversion rate would have, and the shape
# `crate` never has.
RATE='(^|[^A-Za-z0-9])[Rr]ate([^A-Za-z0-9]|$)|[a-z0-9]Rate([^A-Za-z0-9]|$)'

if [[ ! -d "$SRC" ]]; then
  echo "check-no-oracle: no program source at $SRC" >&2
  exit 1
fi

hits=0
while IFS= read -r -d '' file; do
  # Strip line comments (/// doc comments, //! module docs, // ordinary) and
  # anything inside a string literal, then look for the words as identifiers.
  stripped="$(sed -e 's://.*$::' -e 's:"[^"]*"::g' "$file")"
  named="$(printf '%s\n' "$stripped" | grep -nEi "[a-z_]*($FORBIDDEN)[a-z_]*" || true)"
  rated="$(printf '%s\n' "$stripped" | grep -nE "$RATE" || true)"
  if matches="$(printf '%s\n%s' "$named" "$rated" | grep -v '^$' || true)"; [[ -n "$matches" ]]; then
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

echo "check-no-oracle: clean — no price, rate, oracle or fiat identifier in the program"
