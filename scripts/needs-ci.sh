#!/usr/bin/env bash
# Reads changed paths, one per line, on stdin and says which CI jobs they reach.
#
#   bash scripts/needs-ci.sh           "true" when any path is something a job
#                                      reads, "false" for documentation alone
#   bash scripts/needs-ci.sh --sides   two lines, "solana=true|false" and
#                                      "kotlin=true|false": which side's jobs
#                                      the change reaches (ci.yml's `changes`)
#
# Documentation: Markdown anywhere, docs/, specs/, .claude/ and .specify/ —
# except the few such files a job does read, which count as code:
#
#   README.md, docs/weaknesses.json      the weaknesses table check and the
#                                        app's wording (kotlin job)
#   specs/001-holdout-hackathon/contracts/program-instructions.md
#                                        tests/scenarios/no-oracle.spec.ts
#                                        (the vitest gate, contract-tests job)
#
# The Solana side is the program, anchor and contract-tests jobs; the Kotlin
# side is the kotlin job. A path neither list claims — fixtures/ (golden JSON
# both sides read), .github/, any other script, anything new — reaches both,
# so a file a job starts reading is never skipped by mistake: move it into a
# side's list only once you know it is read there alone.

solana=false
kotlin=false
while IFS= read -r f; do
  case "$f" in
    '') ;;
    specs/001-holdout-hackathon/contracts/program-instructions.md) solana=true ;;
    README.md | docs/weaknesses.json) kotlin=true ;;
    *.md | docs/* | specs/* | .claude/* | .specify/*) ;;
    programs/* | tests/* | idl/* | Anchor.toml | Cargo.toml | Cargo.lock | rust-toolchain* | scripts/check-no-oracle.sh) solana=true ;;
    kotlin/* | scripts/check-wording.sh | scripts/render-weaknesses.mjs | scripts/crank-loop.sh | scripts/test-crank-loop.sh) kotlin=true ;;
    *) solana=true kotlin=true ;;
  esac
done

if [[ "${1:-}" == "--sides" ]]; then
  echo "solana=$solana"
  echo "kotlin=$kotlin"
elif [[ $solana == true || $kotlin == true ]]; then
  echo true
else
  echo false
fi
