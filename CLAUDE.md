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
  ready for review, and again before it is merged** — on the branch as it will land, after merging
  in the latest base, committed and pushed. It runs the same four jobs as GitHub Actions (the
  Solana side in WSL, Kotlin on Windows) and replaces the Actions check while Actions is off: it
  sets the commit status **CI (local)** on the PR's head commit and keeps one comment on the PR
  with the per-job results. A green CI (local) on the head commit is the merge gate; a run with
  uncommitted changes or an unpushed HEAD sets no status. A docs-only change needs no run.
