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
- **Run `bash scripts/ci-local.sh` and get it green before a pull request is marked ready for
  review, and again before it is merged** — on the branch as it will land, after merging in the
  latest base. It runs the same four jobs as GitHub Actions (the Solana side in WSL, Kotlin on
  Windows), so it is the gate whether or not the Actions run starts; GitHub runs no CI for a
  change that touches only documentation. Put the run's summary in the PR. A docs-only change
  needs no run.
