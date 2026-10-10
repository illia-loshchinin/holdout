#!/usr/bin/env bash
# The CI workflow (.github/workflows/ci.yml), run on this machine instead of
# GitHub's runners — the same four jobs, the same commands, the same pass/fail.
#
#   program   rustfmt, the no-oracle check, clippy (with and without
#             test-leagues), the Rust unit suites
#   anchor    `anchor build` + the test-leagues binary; the committed IDL must
#             match the build
#   contract  npm ci (when package.json or the lockfile changed), Codama
#             codegen (the committed client must not move), tsc, the vitest
#             gate, fixtures unchanged
#   kotlin    ktlint, the JVM suites, both debug APKs, the weaknesses table,
#             the app's wording
#
# Usage:
#   bash scripts/ci-local.sh                      every job
#   bash scripts/ci-local.sh --only kotlin        one job, or a comma list
#   bash scripts/ci-local.sh --report             every job, then report to the PR
#
# The job names are program, anchor, contract (or contract-tests, as ci.yml
# calls it) and kotlin; any other name is an error.
#
# --report stands in for the GitHub Actions check: it sets the commit status
# "CI (local)" on the pull request's head commit and keeps one comment of its
# own on the pull request up to date with the per-job results. A status is set
# only when the run started on a clean working tree, HEAD did not move during
# it, HEAD is the PR's head commit, and HEAD contains the tip of the PR's base —
# so the commit tested is the one GitHub would test as the merge. "success"
# also needs every job to have run and passed and the tree to be clean at the
# end, so a green "CI (local)" always means that exact commit passed everything;
# "failure" is set whenever a job failed. Otherwise the comment still goes up
# and says why no status was set. Needs `gh`.
#
# Where it runs: program, anchor and contract need the Solana/Anchor toolchain,
# so from Git Bash on Windows they are handed to WSL (in place, on the same
# checkout); kotlin needs the JDK and the Android SDK and runs on Windows. Run
# inside WSL, kotlin is skipped unless an Android SDK is configured there, and
# a run that skipped a job sets no "success".
#
# A job stops at its first failing step, as on GitHub; the other jobs still
# run, except contract, which needs anchor's binary. Exit status is 0 only when
# every job that ran passed; a WSL half that dies part-way fails the jobs it did
# not finish. The drift checks compare the files before and after regenerating
# them, ignoring CR/LF, so they also hold on a checkout with uncommitted work: a
# check fails only if regenerating changes something. A passing check puts back
# the files the regeneration touched; a failing or interrupted one leaves the
# regenerated files to inspect and keeps the originals, printing where.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ALL_JOBS="program,anchor,contract,kotlin"
ONLY="$ALL_JOBS"
REPORT=0
ARGS="$*"
while [ $# -gt 0 ]; do
  case "$1" in
    --only) ONLY="${2:?--only needs a job list}"; shift 2 ;;
    --report) REPORT=1; shift ;;
    -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) echo "ci-local: unknown argument $1" >&2; exit 2 ;;
  esac
done
ONLY="${ONLY//contract-tests/contract}"
for j in ${ONLY//,/ }; do
  case ",$ALL_JOBS," in
    *",$j,"*) ;;
    *) echo "ci-local: no job called $j — the jobs are $ALL_JOBS" >&2; exit 2 ;;
  esac
done
wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

# What the run starts on: the only state --report may vouch for.
START_HEAD="$(git rev-parse HEAD 2>/dev/null)"
START_DIRTY="$(git status --porcelain --untracked-files=normal 2>/dev/null)"

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
# A job ran when it passed or failed; "skipped (…)" or no result means it did not.
ran() { case "$(result_of "$1")" in pass|FAIL) return 0 ;; *) return 1 ;; esac; }

# One step of a job: prints it, runs it, and returns its status.
step() {
  local name="$1"; shift
  printf '\n-- %s\n' "$name"
  "$@"
}

# "<sha1 of the content with CR stripped> <path>" for every file under the
# given paths — the before/after picture the drift checks compare. The recorded
# sittings under fixtures/rep-traces (nearly all of the fixtures' size) are left
# out: nothing the suite generates is written there.
snapshot() {
  find "$@" -type f -not -path '*/node_modules/*' -not -path 'fixtures/rep-traces/*' 2>/dev/null | LC_ALL=C sort |
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
    diff <(printf '%s\n' "$before") <(printf '%s\n' "$after") | grep '^[<>]' | cut -d' ' -f3- | sed 's/^/   /' | sort -u
    return 1
  fi
}

same_ignoring_cr() { cmp -s <(tr -d '\r' < "$1") <(tr -d '\r' < "$2"); }

# Regenerating rewrites files with this platform's line endings, which Git on a
# Windows checkout then lists as modified. Keep a copy of the files a snapshot
# lists and, when the check passes, put back byte for byte only the ones the
# regeneration touched. A failing check leaves the regenerated files in place to
# inspect and commit; then, and on an interrupt, the copies are kept and their
# path printed instead of deleted. The jobs run in subshells, so what is still
# owed back is a file, not a variable.
BACKUP="$(mktemp -d)"
OWED="$BACKUP/.owed"
cleanup() {
  if [ -s "$OWED" ]; then
    echo "ci-local: the originals of $(tr '\n' ' ' < "$OWED")are kept in $BACKUP"
  else
    rm -rf "$BACKUP"
  fi
  [ "$NESTED" = 1 ] || rm -rf "$RESULTS" "$LOGS"
}
trap cleanup EXIT

# keep <dir> <snapshot>
keep() {
  local f
  while read -r _ f; do
    [ -n "$f" ] || continue
    mkdir -p "$BACKUP/$(dirname "$f")" && cp -p "$f" "$BACKUP/$f" || return 1
  done <<<"$2"
  echo "$1" >> "$OWED"
}

# put_back <dir> <snapshot before> <snapshot after>: copies back every file whose
# bytes changed (line endings included) or that went missing, and removes the
# ones the regeneration added.
put_back() {
  local f
  while read -r _ f; do
    [ -n "$f" ] || continue
    [ -f "$f" ] && cmp -s "$BACKUP/$f" "$f" && continue
    mkdir -p "$(dirname "$f")" && cp -p "$BACKUP/$f" "$f" || return 1
  done <<<"$2"
  LC_ALL=C comm -13 <(printf '%s\n' "$2" | cut -s -d' ' -f2- | LC_ALL=C sort) <(printf '%s\n' "$3" | cut -s -d' ' -f2- | LC_ALL=C sort) |
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      rm -f "$f" || exit 1
    done || return 1
  grep -vxF "$1" "$OWED" > "$OWED.new"; mv "$OWED.new" "$OWED"
}

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
  local clients fixtures
  if [ ! -f target/deploy/holdout.so ]; then
    echo "no target/deploy/holdout.so — run the anchor job first"
    return 1
  fi
  step "Install dependencies (when package.json or the lockfile changed)" npm_ci_if_needed &&
  clients="$(snapshot tests/clients)" && keep tests/clients "$clients" &&
  step "Regenerate the Codama client" npm --prefix tests run codegen &&
  step "The committed client matches the IDL" unchanged_since "$clients" tests/clients &&
  put_back tests/clients "$clients" "$(snapshot tests/clients)" &&
  step "Type-check" bash -c 'cd tests && npx tsc --noEmit' &&
  fixtures="$(snapshot fixtures)" && keep fixtures "$fixtures" &&
  step "vitest — the gate" npm --prefix tests run ci:gate &&
  step "Fixtures unchanged" unchanged_since "$fixtures" fixtures &&
  put_back fixtures "$fixtures" "$(snapshot fixtures)"
}

# `npm ci` wipes node_modules, so only when package.json or the lockfile moved
# since the last install, or the installed LiteSVM native module does not load
# here (Windows and WSL share the checkout's node_modules). GitHub always runs
# `npm ci`, which refuses a package.json the lockfile does not match; hashing
# both makes such an edit reach `npm ci` here too.
npm_ci_if_needed() {
  local stamp=tests/node_modules/.ci-local-lock want
  want="$(cat tests/package.json tests/package-lock.json | tr -d '\r' | sha1sum | cut -c1-40)"
  if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$want" ] &&
     (cd tests && node --input-type=module -e "await import('litesvm')") >/dev/null 2>&1; then
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

WHERE=""
if on_windows && [ -n "$LINUX_JOBS" ]; then
  # Hand the Solana side to WSL, on this same checkout, writing into this run's
  # results and logs.
  WHERE="$LINUX_JOBS in WSL"
  say "program / anchor / contract → WSL"
  to_wsl() { wsl.exe -e wslpath -a "$(cygpath -m "$1")" | tr -d '\r'; }
  if ! wsl.exe -e bash -lc "cd '$(to_wsl "$ROOT")' && CI_LOCAL_RESULTS='$(to_wsl "$RESULTS")' CI_LOCAL_LOGS='$(to_wsl "$LOGS")' bash scripts/ci-local.sh --only $LINUX_JOBS"; then
    # A job that failed has recorded itself. One the WSL half never reached, or
    # died in (WSL did not start, the VM went down mid-run), has not: record it
    # as failed — and the WSL half itself, if it failed with nothing to show.
    for j in ${LINUX_JOBS//,/ }; do [ -n "$(result_of "$j")" ] || record "$j" FAIL; done
    grep -q '|FAIL|' "$RESULTS" || record wsl FAIL
  fi
elif [ -n "$LINUX_JOBS" ]; then
  WHERE="$LINUX_JOBS on $(uname -s)"
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
    if on_windows; then WHERE="${WHERE:+$WHERE, }kotlin on Windows"; else WHERE="${WHERE:+$WHERE, }kotlin on $(uname -s)"; fi
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
  local repo me pr head_pr base_ref base_tip base_line head missing j state body marker id url desc i
  local block="" partial=""
  marker='<!-- ci-local -->'
  repo="$(gh_retry gh repo view --json nameWithOwner -q .nameWithOwner)" || { echo "ci-local: cannot reach GitHub"; return 1; }
  me="$(gh_retry gh api user -q .login)" || { echo "ci-local: cannot tell which GitHub user this is"; return 1; }
  read -r pr head_pr base_ref <<<"$(gh_retry gh pr view --json number,headRefOid,baseRefName -q '"\(.number) \(.headRefOid) \(.baseRefName)"')"
  [ -n "${head_pr:-}" ] || { echo "ci-local: no open pull request for this branch"; return 1; }
  head="$(git rev-parse HEAD)"

  # What stops any status: the commit tested is not, or may not be, the PR's
  # head commit as GitHub would merge it.
  [ -z "$START_DIRTY" ] || block="${block:+$block; }the run started with uncommitted changes"
  [ "$head" = "$START_HEAD" ] || block="${block:+$block; }HEAD moved during the run, from ${START_HEAD:0:7} to ${head:0:7}"
  [ "$head" = "$head_pr" ] || block="${block:+$block; }HEAD ${head:0:7} is not the PR's head commit ${head_pr:0:7} — push first"
  if gh_retry git fetch -q origin "$base_ref"; then
    base_tip="$(git rev-parse --short "origin/$base_ref")"
    if git merge-base --is-ancestor "origin/$base_ref" "$head"; then
      base_line="contains \`$base_ref\` at \`$base_tip\`"
    else
      base_line="does **not** contain the tip of \`$base_ref\` (\`$base_tip\`)"
      block="${block:+$block; }HEAD does not contain the tip of $base_ref ($base_tip) — merge the base first"
    fi
  else
    base_line="base \`$base_ref\` not checked"
    block="${block:+$block; }could not fetch $base_ref to check that HEAD contains it"
  fi

  # What stops a green status but not a red one.
  missing=""
  for j in program anchor contract kotlin; do ran "$j" || missing="$missing $j"; done
  [ -z "$missing" ] || partial="${partial:+$partial; }not every job ran (missing or skipped:$missing)"
  [ -z "$(git status --porcelain --untracked-files=normal)" ] || partial="${partial:+$partial; }the working tree has uncommitted changes at the end of the run"

  state=success; [ "$failed" = 1 ] && state=failure
  local reason="$block"
  [ "$state" = success ] && reason="${block:+$block${partial:+; }}$partial"

  body="$(mktemp)"
  {
    echo "$marker"
    if [ "$state" = success ]; then echo "### ✅ CI (local) passed"; else echo "### ❌ CI (local) failed"; fi
    echo
    echo "\`bash scripts/ci-local.sh${ARGS:+ $ARGS}\` on \`${head:0:7}\`, which $base_line; $(date -u '+%Y-%m-%d %H:%M UTC'). Ran ${WHERE:-nothing}."
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

  # One comment per PR, this user's, edited on every run. A POST is not retried
  # blindly: one that timed out may still have landed, so look for it first.
  find_comment() {
    gh api "repos/$repo/issues/$pr/comments" --paginate \
      -q ".[] | select(.user.login == \"$me\" and (.body | startswith(\"$marker\"))) | .id" | tail -n 1
  }
  id="$(gh_retry find_comment)"
  url=""
  for i in 1 2 3; do
    if [ -n "$id" ]; then
      url="$(gh_retry gh api -X PATCH "repos/$repo/issues/comments/$id" -F "body=@$body_arg" -q .html_url)"
      break
    fi
    url="$(gh api -X POST "repos/$repo/issues/$pr/comments" -F "body=@$body_arg" -q .html_url)" && break
    sleep 3
    id="$(gh_retry find_comment)"
  done
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
