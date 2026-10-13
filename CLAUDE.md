# Holdout — agent rules

- **Spec-kit artifacts change only through the `/speckit-*` skills, never by hand.** That covers
  `specs/*/spec.md`, `plan.md`, `tasks.md`, `data-model.md`, `research.md`, `contracts/`,
  `quickstart.md` and `.specify/memory/constitution.md`. Pick the skill that fits — a product
  decision goes through `/speckit-clarify`, a principle through `/speckit-constitution`, a
  consistency check through `/speckit-analyze` — and run its extension hooks as written.
- **Verify the spec-kit artifacts before a pull request is marked ready for review, and again
  before it is merged.** Commits land in between, so one check is not enough. Each time, run
  `/speckit-analyze` on the feature and check spec, plan, tasks, data model, contracts and
  quickstart against the code on the branch. Fix any drift through the skills above, then mark it
  ready or merge.
- **CI before ready, and again before merge — on the branch as it will land:** the latest base
  merged in, committed and pushed. **Before a pull request is marked ready for review**, run
  `bash scripts/ci-local.sh` and get it green: Actions does not run on a draft. **Before it is
  merged**, the GitHub Actions check `CI` must be green on the PR's head commit: marking the PR
  ready starts that run, and every push after it runs again. Actions also runs on pushes to
  `main`, not to `001-holdout-hackathon` (a PR's run already tests its merge). The repository is
  private again since 2026-10-13, so its minutes are counted: `scripts/needs-ci.sh --sides` runs
  only the side a change reaches — the Solana jobs (program, anchor, contract tests) or the Kotlin
  job — and neither for documentation alone, where the few docs files a job reads (the README's
  weaknesses table and its source, the program-instructions contract) count as code.
  `bash scripts/ci-local.sh` runs the same four jobs on this machine (the Solana side in WSL,
  Kotlin on Windows); with `--report` it also sets the commit status **CI (local)** and a PR
  comment, which is the merge gate only if Actions is unavailable.
- **Codex reviews only a pull request that is ready for review — so mark it ready to get one.**
  A draft gets no Codex review. Once the spec-kit check and `ci-local.sh` are green on the head
  commit (the two rules above), run `gh pr ready <PR number>` and comment `@codex review`, then
  wait for Codex with the PR left ready. Do not keep a PR in draft while waiting for a review, and
  do not start a stand-in review while Codex can still answer. Fix or answer every Codex finding on
  the PR before merge.
- **No Codex review means a stand-in review, before merge.** When `chatgpt-codex-connector[bot]`
  answers a pull request with "You have reached your Codex usage limits for code reviews" — or no
  Codex review arrives on a PR that is ready for review — run a strong independent reviewer as a
  separate agent instead: not the session that wrote the change, with no context from it, on
  Sonnet, at the highest effort available (`/code-review xhigh <PR number> --comment`, which posts
  its findings on the PR). Fix or answer every finding on the PR, and say in the PR that this
  review replaced Codex's. The PR is not merged until that review is done and answered.
- **Agents run on Sonnet, code reviews included.** Subagents, agent-team teammates and workflow
  agents all run on Sonnet; Opus only for genuinely complex program or test work, by explicit
  choice. Reviews and checks (`/code-review`, `/speckit-analyze`) run inside whichever agent invokes
  them, so start that agent on Sonnet: a subagent with `model: "sonnet"`, or a fresh session set to
  Sonnet. `.claude/settings.json` sets `CLAUDE_CODE_SUBAGENT_MODEL=sonnet`, but it is only the
  fallback when no model is given (an agent definition's `model:`, `inherit` and forks take their
  own), so always pass `model` explicitly.
- **For Kotlin code, use the `LSP` tool when it is available** (the `kotlin-lsp` plugin, installed
  per user, not by this repo) for outline, hover, go-to-definition and find-references before
  `Grep`/`Read` sweeps. The server needs a warm Gradle daemon to start inside the harness's 30 s
  limit; a `SessionStart` hook in `.claude/settings.json` starts warming it in the background, so
  a call in the first minute can still time out. If `LSP` times out during initialization, run
  `./gradlew :core:dependencies --configuration compileClasspath -q` from `kotlin/` (it returns
  once the daemon is warm) and retry.
- **Two sides, one checkout — pick the side by the tool, do not probe.** The repo is on `W:`; WSL
  (Ubuntu) sees the same files at `/mnt/w`. `cargo`, `anchor`, `solana` (and the devnet keypair,
  `~/.config/solana/id.json`) exist **only in WSL**; `java`/Gradle, `adb` and `gh` run on
  **Windows**. So: Rust, Anchor, the IDL, Codama/npm contract checks, and every deploy go through
  WSL; Kotlin, Gradle, `adb` and APKs go through Windows. Do not run `which`/`--version` to find
  out which side has a tool — this rule says.
- **Reach WSL with one form:** `wsl.exe -e bash -lc "cd '$(wsl.exe -e wslpath -a "$(cygpath -m "$PWD")" | tr -d '\r')' && <command>"`
  from Git Bash. Do not `cd` between the two sides inside one command, do not type `/mnt/w/...`
  paths by hand, and do not retry a command on the other side when it fails — read the error.
  For the CI jobs use `bash scripts/ci-local.sh --only <job>`; it already splits the sides.
- **Line endings are LF in the repo** (`.gitattributes`); a CR/LF-only diff is noise, not a change.
  Do not "fix" it by rewriting files.
- Do not use `archive` folder for development or implementation. The data source inside the archive folder must be treated as deprecated.
