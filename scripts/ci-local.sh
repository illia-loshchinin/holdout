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
#   bash scripts/ci-local.sh --report             every job, then report to the PR
#
# --report stands in for the GitHub Actions check: it sets the commit status
# "CI (local)" on the pull request's head commit and keeps one comment on the
# pull request up to date with the per-job results. The status is set only for
# a run of every job on a clean working tree whose HEAD is the PR's head commit,
# so a green "CI (local)" always means that exact commit passed. Otherwise the
# comment still goes up and says why no status was set. Needs `gh`.
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
REPORT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --only) ONLY="${2:?--only needs a job list}"; shift 2 ;;
    --report) REPORT=1; shift ;;
    -h|--help) sed -n '2,34p' "$0"; exit 0 ;;
    *) echo "ci-local: unknown argument $1" >&2; exit 2 ;;
  esac
done
wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

# A nested run (the WSL half of a run started on Windows) writes its results and
# logs where the outer run tells it to, and leaves the summary and the report to
# the outer run.
NESTED=0
if [ -n "${CI_LOCAL_RESULTS:-}" ]; then
  NESTED=1
else
  CI_LOCAL_RESULTS="$(mktemp)"
  CI_LOCAL_LOGS="$(mktemp -d)"
fi
RESULTS="$CI_LOCAL_RESULTS"
LOGS="$CI_LOCAL_LOGS"

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
record() { printf '%s|%s|%s\n' "$1" "$2" "${3:-}" >> "$RESULTS"; }
result_of() { awk -F'|' -v j="$1" '$1 == j { r = $2 } END { print r }' "$RESULTS"; }

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
cleanup() {
  rm -rf "$BACKUP"
  [ "$NESTED" = 1 ] || rm -rf "$RESULTS" "$LOGS"
}
trap cleanup EXIT
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

# Runs a job with its output both on screen and in its log, and records the
# result and how long it took.
run_job() {
  local name="$1" start=$SECONDS status
  say "$name"
  "job_$name" 2>&1 | tee "$LOGS/$name.log"
  status=${PIPESTATUS[0]}
  if [ "$status" = 0 ]; then record "$name" pass $((SECONDS - start)); else record "$name" FAIL $((SECONDS - start)); fi
}

# ------------------------------------------------------------------ run

LINUX_JOBS=""
for j in program anchor contract; do wants "$j" && LINUX_JOBS="${LINUX_JOBS:+$LINUX_JOBS,}$j"; done

if on_windows && [ -n "$LINUX_JOBS" ]; then
  # Hand the Solana side to WSL, on this same checkout, writing into this run's
  # results and logs.
  say "program / anchor / contract → WSL"
  to_wsl() { wsl.exe -e wslpath -a "$(cygpath -m "$1")" | tr -d '\r'; }
  if ! wsl.exe -e bash -lc "cd '$(to_wsl "$ROOT")' && CI_LOCAL_RESULTS='$(to_wsl "$RESULTS")' CI_LOCAL_LOGS='$(to_wsl "$LOGS")' bash scripts/ci-local.sh --only $LINUX_JOBS"; then
    # A job that failed has recorded itself; a WSL that never started has not.
    [ -s "$RESULTS" ] || record wsl FAIL
  fi
elif [ -n "$LINUX_JOBS" ]; then
  export NO_DNA=1
  solana_bin="$HOME/.local/share/solana/install/active_release/bin"
  if ! command -v solana >/dev/null 2>&1 && [ -d "$solana_bin" ]; then export PATH="$solana_bin:$PATH"; fi
  wants program && run_job program
  wants anchor && run_job anchor
  if wants contract; then
    if wants anchor && [ "$(result_of anchor)" != pass ]; then
      record contract "skipped (anchor failed)"
    else
      run_job contract
    fi
  fi
fi

if wants kotlin && [ "$NESTED" = 0 ]; then
  if on_windows || has_android_sdk; then
    run_job kotlin
  else
    record kotlin "skipped (no Android SDK here — run from Git Bash on Windows)"
  fi
fi

failed=0
grep -q '|FAIL|' "$RESULTS" && failed=1
[ "$NESTED" = 1 ] && exit "$failed"

say "summary"
while IFS='|' read -r name result secs; do
  printf '  %-10s %-60s %s\n' "$name" "$result" "${secs:+${secs}s}"
done < "$RESULTS"

# ------------------------------------------------------------------ report

gh_retry() { local i; for i in 1 2 3 4; do "$@" && return 0; sleep 3; done; return 1; }

report() {
  command -v gh >/dev/null 2>&1 || { echo "ci-local: --report needs the GitHub CLI (gh)"; return 1; }
  local repo pr head_pr head base dirty all reason state body marker id url desc
  marker='<!-- ci-local -->'
  repo="$(gh_retry gh repo view --json nameWithOwner -q .nameWithOwner)" || { echo "ci-local: cannot reach GitHub"; return 1; }
  read -r pr head_pr <<<"$(gh_retry gh pr view --json number,headRefOid -q '"\(.number) \(.headRefOid)"')"
  [ -n "${head_pr:-}" ] || { echo "ci-local: no open pull request for this branch"; return 1; }
  head="$(git rev-parse HEAD)"
  base="$(git rev-parse --short "origin/$(gh pr view "$pr" --json baseRefName -q .baseRefName 2>/dev/null || echo 001-holdout-hackathon)" 2>/dev/null || echo '?')"
  dirty="$(git status --porcelain --untracked-files=normal)"
  all=1; for j in program anchor contract kotlin; do [ -n "$(result_of "$j")" ] || all=0; done

  reason=""
  [ "$all" = 1 ] || reason="not every job ran (--only)"
  [ -z "$dirty" ] || reason="the working tree has uncommitted changes"
  [ "$head" = "$head_pr" ] || reason="HEAD ${head:0:7} is not the PR's head commit ${head_pr:0:7} — push first"
  state=success; [ "$failed" = 1 ] && state=failure

  body="$(mktemp)"
  {
    echo "$marker"
    if [ "$state" = success ]; then echo "### ✅ CI (local) passed"; else echo "### ❌ CI (local) failed"; fi
    echo
    echo "\`bash scripts/ci-local.sh\` on \`${head:0:7}\` (base \`$base\`), $(date -u '+%Y-%m-%d %H:%M UTC'), $(uname -s | cut -d_ -f1) + WSL."
    echo
    echo "| Job | Result | Time |"
    echo "|---|---|---|"
    while IFS='|' read -r name result secs; do
      echo "| $name | $result | ${secs:+${secs}s} |"
    done < "$RESULTS"
    if [ -n "$reason" ]; then
      echo
      echo "**No commit status set:** $reason. This result does not stand for the PR's head commit."
    fi
    while IFS='|' read -r name result _; do
      [ "$result" = FAIL ] || continue
      echo
      echo "<details><summary>$name — last 40 lines</summary>"
      echo
      echo '```'
      sed 's/\x1b\[[0-9;]*m//g' "$LOGS/$name.log" 2>/dev/null | tail -n 40
      echo '```'
      echo "</details>"
    done < "$RESULTS"
  } > "$body"

  # gh is a Windows program under Git Bash: hand it a Windows path to the body.
  local body_arg="$body"
  on_windows && body_arg="$(cygpath -m "$body")"

  # One comment per PR, edited on every run.
  id="$(gh_retry gh api "repos/$repo/issues/$pr/comments" --paginate -q ".[] | select(.body | contains(\"$marker\")) | .id" | tail -n 1)"
  if [ -n "$id" ]; then
    url="$(gh_retry gh api -X PATCH "repos/$repo/issues/comments/$id" -F "body=@$body_arg" -q .html_url)"
  else
    url="$(gh_retry gh api -X POST "repos/$repo/issues/$pr/comments" -F "body=@$body_arg" -q .html_url)"
  fi
  rm -f "$body"
  echo "ci-local: PR #$pr comment ${url:-not written}"

  if [ -z "$reason" ]; then
    desc="$(awk -F'|' '{ printf "%s%s %s", (NR>1?" · ":""), $1, ($2=="pass"?"✓":"✗") }' "$RESULTS")"
    gh_retry gh api -X POST "repos/$repo/statuses/$head" -f state="$state" -f context="CI (local)" \
      -f description="${desc:0:139}" ${url:+-f target_url="$url"} >/dev/null &&
      echo "ci-local: status \"CI (local)\" = $state on ${head:0:7}"
  else
    echo "ci-local: no commit status — $reason"
  fi
}

if [ "$REPORT" = 1 ]; then report || failed=1; fi
exit "$failed"
