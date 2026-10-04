#!/usr/bin/env bash
# Copies the debug build's sitting traces off the phone, over USB, into
# phone_traces/ (git-ignored). Traces hold landmarks and the counter's
# decisions only - never a picture (kotlin/repcounter Trace.kt).
#
# Sitting videos (debug builds with "record video" on; constitution X, v1.5.0)
# come too - `<trace>.mp4` beside its trace, with a `.video.json` saying when
# recording started - and are then DELETED from the phone once the copy here
# is complete: a recording lives on the phone only until it is pulled. Videos
# are of people who agreed in writing to be filmed; anyone else's is deleted
# unused. phone_traces/ is git-ignored - never commit a video.
#
# A useful trace becomes a test: write the true count into its header
# (expectedReps=N), and move it to fixtures/rep-traces/.
# Label its reps from the video first: node scripts/label-reps.mjs.
#
# With more than one device attached, set ANDROID_SERIAL first.
set -euo pipefail

dest="${1:-phone_traces}"
mkdir -p "$dest"

# Only real file names: when the folder does not exist yet, `ls` prints its
# complaint on the same stream. One per line (-1): over some adb transports
# `ls` prints columns, and a trace sharing a line with a video was once taken
# for a video — copied, then deleted from the phone.
listing=$(adb exec-out run-as holdout.app sh -c 'ls -1 files/traces 2>/dev/null' | tr -d '\r' || true)
traces=$(grep '\.trace\.csv$' <<<"$listing" || true)
videos=$(grep -E '\.mp4$|\.video\.json$' <<<"$listing" | grep -v '\.recording\.mp4$' || true)

if [ -z "$traces" ] && [ -z "$videos" ]; then
  echo "No traces on the phone yet (a debug build records one per sitting)."
  exit 0
fi

for f in $traces; do
  adb exec-out run-as holdout.app cat "files/traces/$f" > "$dest/$f"
done

for f in $videos; do
  adb exec-out run-as holdout.app cat "files/traces/$f" > "$dest/$f"
  phone=$(adb exec-out run-as holdout.app sh -c "wc -c < 'files/traces/$f'" | tr -d '\r ')
  here=$(wc -c < "$dest/$f" | tr -d ' ')
  if [ -n "$phone" ] && [ "$phone" = "$here" ]; then
    adb exec-out run-as holdout.app rm "files/traces/$f"
    echo "pulled and deleted from the phone: $f"
  else
    echo "NOT deleted - copy incomplete ($here of $phone bytes): $f" >&2
  fi
done
ls -la "$dest"
