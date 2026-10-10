#!/usr/bin/env bash
# Reads changed paths, one per line, on stdin and prints "true" when any of them
# is something a CI job reads, "false" when the change is documentation alone.
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
# A file a job starts reading moves into that list; ci.yml's `changes` job is
# the only caller.

code=false
while IFS= read -r f; do
  case "$f" in
    '') ;;
    README.md | docs/weaknesses.json | specs/001-holdout-hackathon/contracts/program-instructions.md) code=true ;;
    *.md | docs/* | specs/* | .claude/* | .specify/*) ;;
    *) code=true ;;
  esac
done
echo "$code"
