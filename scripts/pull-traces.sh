#!/usr/bin/env bash
# Copies Holdout Lab's set traces off the phone, over USB, into phone_traces/
# (git-ignored). Traces hold landmarks and the counter's decisions only -
# never a picture (kotlin/repcounter Trace.kt).
#
# Lab sets come from holdout.app.lab, the labDebug build (spec 004): `run-as`
# needs a debuggable app. Traces and videos left in the member app by debug
# builds made before the split are drained once, before that phone gets the
# new member build:
#
#   scripts/pull-traces.sh --package holdout.app
#
# Set videos (Holdout Lab with "record video" on; constitution X v2.1.0)
# come too - `<trace>.mp4` beside its trace, with a `.video.json` saying when
# recording started. Every file, trace or video, is DELETED from the phone
# once its copy here is complete and the sizes match: a set lives on the
# phone only until it is pulled. Pull with no set running in Lab. Videos
# are of people who agreed in writing to be filmed; anyone else's is deleted
# unused. phone_traces/ is git-ignored - never commit a video.
#
# A useful trace becomes a test: write the true count into its header
# (expectedReps=N), and move it to fixtures/rep-traces/.
# Label its reps from the video first: node scripts/label-reps.mjs.
#
# With more than one device attached, set ANDROID_SERIAL first.
set -euo pipefail

package="holdout.app.lab"
dest="phone_traces"
while [ $# -gt 0 ]; do
  case "$1" in
    --package) package="$2"; shift 2 ;;
    *) dest="$1"; shift ;;
  esac
done
mkdir -p "$dest"

# Only real file names: when the folder does not exist yet, `ls` prints its
# complaint on the same stream. One per line (-1): over some adb transports
# `ls` prints columns, and a trace sharing a line with a video was once taken
# for a video — copied, then deleted from the phone.
listing=$(adb exec-out run-as "$package" sh -c 'ls -1 files/traces 2>/dev/null' | tr -d '\r' || true)
traces=$(grep '\.trace\.csv$' <<<"$listing" || true)
videos=$(grep -E '\.mp4$|\.video\.json$' <<<"$listing" | grep -v '\.recording\.mp4$' || true)

if [ -z "$traces" ] && [ -z "$videos" ]; then
  echo "No traces in $package yet (Holdout Lab records one per set)."
  exit 0
fi

# Every set is copied, checked against the phone's size, and only then
# deleted from the phone (spec 004 data-model: pulled sets leave the phone).
for f in $traces $videos; do
  adb exec-out run-as "$package" cat "files/traces/$f" > "$dest/$f"
  phone=$(adb exec-out run-as "$package" sh -c "wc -c < 'files/traces/$f'" | tr -d '\r ')
  here=$(wc -c < "$dest/$f" | tr -d ' ')
  if [ -n "$phone" ] && [ "$phone" = "$here" ]; then
    adb exec-out run-as "$package" rm "files/traces/$f"
    echo "pulled and deleted from the phone: $f"
  else
    echo "NOT deleted - copy incomplete ($here of $phone bytes): $f" >&2
  fi
done
ls -la "$dest"
