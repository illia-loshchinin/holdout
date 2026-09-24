#!/usr/bin/env bash
# Copies the debug build's sitting traces off the phone, over USB, into
# phone_traces/ (git-ignored). Traces hold landmarks and the counter's
# decisions only - never a picture (kotlin/repcounter Trace.kt).
#
# A useful trace becomes a test: write the true count into its header
# (expectedReps=N), and move it to fixtures/rep-traces/.
set -euo pipefail

dest="${1:-phone_traces}"
mkdir -p "$dest"

# Only real trace names: when the folder does not exist yet, `ls` prints its
# complaint on the same stream.
files=$(adb exec-out run-as holdout.app sh -c 'ls files/traces 2>/dev/null' | tr -d '\r' | grep '\.trace\.csv$' || true)
if [ -z "$files" ]; then
  echo "No traces on the phone yet (a debug build records one per sitting)."
  exit 0
fi

for f in $files; do
  adb exec-out run-as holdout.app cat "files/traces/$f" > "$dest/$f"
done
ls -la "$dest"
