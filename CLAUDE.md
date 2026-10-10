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
- **Run `bash scripts/ci-local.sh --report` and get it green before a pull request is marked
  ready for review, and again before it is merged** — on the branch as it will land: the latest
  base merged in, committed and pushed. It runs the same four jobs as GitHub Actions (the Solana
  side in WSL, Kotlin on Windows) and replaces the Actions check while Actions is off: it sets the
  commit status **CI (local)** on the PR's head commit and keeps one comment on the PR with the
  per-job results. A green CI (local) on the head commit is the merge gate. A run that started
  with uncommitted changes, or whose HEAD is not the pushed PR head or does not contain the base's
  tip, sets no status; one that skipped a job sets no green one. A change needs no run only when
  `git diff --name-only origin/<base>...HEAD | bash scripts/needs-ci.sh` prints `false` —
  documentation alone, where the few docs files a job reads (the README's weaknesses table and
  its source, the program-instructions contract) count as code.
- **No Codex review means a stand-in review, before merge.** When `chatgpt-codex-connector[bot]`
  answers a pull request with "You have reached your Codex usage limits for code reviews" — or no
  Codex review arrives — run a strong independent reviewer as a separate agent instead: not the
  session that wrote the change, with no context from it, at the highest effort available
  (`/code-review xhigh <PR number> --comment`, which posts its findings on the PR). Fix or answer
  every finding on the PR, and say in the PR that this review replaced Codex's. The PR is not
  ready for review, and is not merged, until that review is done and answered.
- Do not use `archive` folder for development or implementation. The data source inside the archive folder must be treated as deprecated.
