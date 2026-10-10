#!/usr/bin/env bash
# The CI workflow (.github/workflows/ci.yml), run on this machine instead of
# GitHub's runners — the same four jobs, the same commands, the same pass/fail.
#
#   program   rustfmt, the no-oracle check, clippy (with and without
#             test-leagues), the Rust unit suites
#   anchor    `anchor build` + the test-leagues binary; the committed IDL must
#             match the build
#   contract  npm ci (when the lockfile changed), Codama codegen (the committed
#             client must not move), tsc, the vitest gate, fixtures unchanged
#   kotlin    ktlint, the JVM suites, both debug APKs, the weaknesses table,
#             the app's wording
#
# Usage:
#   bash scripts/ci-local.sh                      every job
#   bash scripts/ci-local.sh --only kotlin        one job, or a comma list
#
# Where it runs: program, anchor and contract need the Solana/Anchor toolchain,
# so from Git Bash on Windows they are handed to WSL (in place, on the same
# checkout); kotlin needs the JDK and the Android SDK and runs on Windows. Run
# inside WSL, kotlin is skipped unless an Android SDK is configured there.
#
# A job stops at its first failing step, as on GitHub; the other jobs still
# run, except contract, which needs anchor's binary. Exit status is 0 only when
# every job that ran passed. The drift checks compare the files before and after
# regenerating them, ignoring CR/LF, so they also hold on a checkout with
# uncommitted work: a check fails only if regenerating changes something.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ALL_JOBS="program,anchor,contract,kotlin"
ONLY="$ALL_JOBS"
while [ $# -gt 0 ]; do
  case "$1" in
    --only) ONLY="${2:?--only needs a job list}"; shift 2 ;;
    -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
    *) echo "ci-local: unknown argument $1" >&2; exit 2 ;;
  esac
done
wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

declare -A RESULT
SUMMARY_ORDER=()
record() { RESULT[$1]="$2"; SUMMARY_ORDER+=("$1"); }
say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# One step of a job: prints it, runs it, and returns its status.
step() {
  local name="$1"; shift
  printf '\n-- %s\n' "$name"
  "$@"
}

# "<sha1 of the content with CR stripped> <path>" for every file under the
# given paths — the before/after picture the drift checks compare.
snapshot() {
  find "$@" -type f -not -path '*/node_modules/*' 2>/dev/null | LC_ALL=C sort |
    while IFS= read -r f; do
      printf '%s %s\n' "$(tr -d '\r' < "$f" | sha1sum | cut -c1-40)" "$f"
    done
}

# Fails, naming the files, when the snapshot taken in $1 no longer matches.
unchanged_since() {
  local before="$1"; shift
  local after
  after="$(snapshot "$@")"
  if [ "$before" != "$after" ]; then
    echo "changed by regenerating them — commit the regenerated files:"
    diff <(printf '%s\n' "$before") <(printf '%s\n' "$after") | grep '^[<>]' | awk '{print "   " $3}' | sort -u
    return 1
  fi
}

same_ignoring_cr() { cmp -s <(tr -d '\r' < "$1") <(tr -d '\r' < "$2"); }

# Regenerating rewrites files with this platform's line endings, which Git on a
# Windows checkout then lists as modified. Keep the originals, and put them back
# byte for byte when the check passes; a failing check leaves the regenerated
# files in place to inspect and commit.
BACKUP="$(mktemp -d)"
trap 'rm -rf "$BACKUP"' EXIT
keep() { mkdir -p "$BACKUP/$1" && cp -a "$1/." "$BACKUP/$1/"; }
put_back() { rm -rf "$1" && mkdir -p "$1" && cp -a "$BACKUP/$1/." "$1/"; }

on_windows() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) return 0 ;; *) return 1 ;; esac; }

# ------------------------------------------------------------------ jobs

job_program() {
  step "rustfmt" cargo fmt --all -- --check &&
  step "No price, no oracle, no fiat figure" bash scripts/check-no-oracle.sh &&
  step "clippy" cargo clippy --workspace --all-targets -- -D warnings &&
  step "clippy (test-leagues)" cargo clippy --workspace --all-targets --features test-leagues -- -D warnings &&
  step "cargo test" cargo test -p holdout --lib --features test-leagues
}

job_anchor() {
  step "anchor build" anchor build &&
  step "the test-leagues binary" npm --prefix tests run build:test-leagues &&
  step "The committed IDL matches the build" idl_matches
}

idl_matches() {
  same_ignoring_cr target/idl/holdout.json idl/holdout.json && return 0
  echo "idl/holdout.json differs from target/idl/holdout.json — copy the build's IDL and regenerate the client"
  return 1
}

job_contract() {
  if [ ! -f target/deploy/holdout.so ]; then
    echo "no target/deploy/holdout.so — run the anchor job first"
    return 1
  fi
  step "Install dependencies (when the lockfile changed)" npm_ci_if_needed &&
  local clients fixtures
  clients="$(snapshot tests/clients)" && keep tests/clients &&
  step "Regenerate the Codama client" npm --prefix tests run codegen &&
  step "The committed client matches the IDL" unchanged_since "$clients" tests/clients &&
  put_back tests/clients &&
  step "Type-check" bash -c 'cd tests && npx tsc --noEmit' &&
  fixtures="$(snapshot fixtures)" && keep fixtures &&
  step "vitest — the gate" npm --prefix tests run ci:gate &&
  step "Fixtures unchanged" unchanged_since "$fixtures" fixtures &&
  put_back fixtures
}

# `npm ci` wipes node_modules, so only when the lockfile moved since the last
# install, or the installed native modules are not this platform's.
npm_ci_if_needed() {
  local stamp=tests/node_modules/.ci-local-lock want
  want="$(tr -d '\r' < tests/package-lock.json | sha1sum | cut -c1-40)-$(uname -s)"
  if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$want" ]; then
    echo "node_modules is current"
    return 0
  fi
  (cd tests && npm ci) && echo "$want" > "$stamp"
}

job_kotlin() {
  step "ktlint" bash -c 'cd kotlin && ./gradlew ktlintCheck' &&
  step "JVM unit tests" bash -c 'cd kotlin && ./gradlew :core:test :repcounter:test :crank:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest' &&
  step "Debug APKs, the member app and Holdout Lab" bash -c 'cd kotlin && ./gradlew :app:assembleMemberDebug :app:assembleLabDebug' &&
  step "Weaknesses table matches its source" node scripts/render-weaknesses.mjs --check &&
  step "The app's wording" bash scripts/check-wording.sh
}

has_android_sdk() {
  [ -n "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && return 0
  local dir
  dir="$(sed -n 's/^sdk\.dir=//p' kotlin/local.properties 2>/dev/null | tr -d '\r')"
  [ -n "$dir" ] && [ -d "$dir" ]
}

run_job() {
  local name="$1"
  say "$name"
  if "job_$name"; then record "$name" pass; else record "$name" FAIL; fi
}

# ------------------------------------------------------------------ main

LINUX_JOBS=""
for j in program anchor contract; do wants "$j" && LINUX_JOBS="${LINUX_JOBS:+$LINUX_JOBS,}$j"; done

if on_windows && [ -n "$LINUX_JOBS" ]; then
  # Hand the Solana side to WSL, on this same checkout.
  say "program / anchor / contract → WSL"
  WSL_ROOT="$(wsl.exe -e wslpath -a "$(cygpath -m "$ROOT")" | tr -d '\r')"
  if wsl.exe -e bash -lc "cd '$WSL_ROOT' && bash scripts/ci-local.sh --only $LINUX_JOBS"; then
    record "wsl ($LINUX_JOBS)" pass
  else
    record "wsl ($LINUX_JOBS)" FAIL
  fi
elif [ -n "$LINUX_JOBS" ]; then
  export NO_DNA=1
  solana_bin="$HOME/.local/share/solana/install/active_release/bin"
  if ! command -v solana >/dev/null 2>&1 && [ -d "$solana_bin" ]; then export PATH="$solana_bin:$PATH"; fi
  wants program && run_job program
  if wants anchor; then run_job anchor; fi
  if wants contract; then
    if wants anchor && [ "${RESULT[anchor]}" != pass ]; then
      record contract "skipped (anchor failed)"
    else
      run_job contract
    fi
  fi
fi

if wants kotlin; then
  if on_windows || has_android_sdk; then
    run_job kotlin
  else
    record kotlin "skipped (no Android SDK here — run from Git Bash on Windows)"
  fi
fi

say "summary"
status=0
for name in "${SUMMARY_ORDER[@]}"; do
  printf '  %-26s %s\n' "$name" "${RESULT[$name]}"
  [ "${RESULT[$name]}" = FAIL ] && status=1
done
exit "$status"
