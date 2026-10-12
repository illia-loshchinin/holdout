# Development

[← README](../README.md) · [Build the app](build-android.md) · [Contract](contract.md)

## Repository map

| Path | What it is |
|---|---|
| `programs/holdout/` | The Solana program (Rust, Anchor 1.1.2) |
| `idl/holdout.json` | The committed IDL. CI checks it matches the build. |
| `tests/` | Contract tests (TypeScript, `@solana/kit` + LiteSVM), the Codama client, and the Surfpool smoke suite |
| `fixtures/` | Golden vectors for settlement, accounts and instructions, plus recorded rep traces |
| `kotlin/app` | The Android app: Jetpack Compose, CameraX, MediaPipe, Mobile Wallet Adapter. Flavors `member` and `lab`. |
| `kotlin/core` | The shared Kotlin Solana client: codecs, PDAs, instruction builders, chain reads |
| `kotlin/repcounter` | The push-up rule, a plain JVM module |
| `kotlin/crank` | The operator CLI and the permissionless crank loop (also runs the scripted members) |
| `scripts/` | CI, guards (`check-no-oracle.sh`, `check-wording.sh`, `render-weaknesses.mjs`), crank and deploy helpers |
| `specs/` | Spec-kit features: spec, plan, tasks, data model, contracts, quickstart |
| `.specify/memory/constitution.md` | The principles every change is checked against |
| `docs/` | This wiki, the decision record, screen maps, the design book and the runbook |

## The Solana side

The Solana toolchain runs on Linux. On Windows, use WSL2 (Ubuntu 24.04).

| Tool | Version |
|---|---|
| Rust | stable (≥ 1.89) |
| Solana CLI (Agave) | 3.1.10 |
| Anchor | 1.1.2 |
| Node.js | 22 LTS |
| Surfpool | only for the smoke suite |

```bash
NO_DNA=1 anchor build                                  # program + target/idl/holdout.json
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test -p holdout --lib --features test-leagues    # Rust unit tests and golden vectors

npm --prefix tests ci
npm --prefix tests run codegen                         # regenerate the Codama client from the IDL
npm --prefix tests test                                # contract tests on LiteSVM
```

`anchor test` runs the Surfpool smoke suite.
[`specs/001-holdout-hackathon/quickstart.md`](../specs/001-holdout-hackathon/quickstart.md) covers
a local validator, a devnet deploy, the crank, and every acceptance scenario.

## The Kotlin side

See [Build the app](build-android.md). The JVM tests:

```bash
cd kotlin
./gradlew ktlintCheck :core:test :repcounter:test :crank:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest
```

## Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) defines four jobs:

| Job | Checks |
|---|---|
| `program` | rustfmt, the no-oracle guard, clippy (with and without `test-leagues`), Rust unit tests |
| `anchor` | Anchor build, the test-leagues build, committed IDL = built IDL |
| `contract-tests` | Codama client = generated client, TypeScript types, the LiteSVM suite and invariants, fixtures unchanged |
| `kotlin` | ktlint, every JVM test (including Robolectric and Roborazzi screen tests), both debug APKs, the README weaknesses table = `docs/weaknesses.json`, the wording guard |

They run on GitHub Actions for every push to `001-holdout-hackathon` and `main` and for every pull
request. The required check is `CI`. The same four jobs run locally too:

```bash
bash scripts/ci-local.sh            # all four jobs: the Solana side in WSL, Kotlin on Windows
bash scripts/ci-local.sh --only kotlin
bash scripts/ci-local.sh --report   # also sets the "CI (local)" commit status and a PR comment
```

`scripts/needs-ci.sh` decides whether a change is documentation alone. Then the jobs are skipped
and `CI` still reports. The README counts as code, because a job checks its weaknesses table.

## Guards worth knowing

| Guard | Fails when |
|---|---|
| `scripts/check-no-oracle.sh` | Price-oracle code appears in the program |
| `checkNoAudioPermission` (Gradle) | `RECORD_AUDIO` reaches the merged manifest |
| `checkMemberHasNoLabCode` (Gradle) | Holdout Lab recording code reaches the member app |
| `checkReleaseSigning` (Gradle) | Only some of the release signing properties are set |
| `scripts/render-weaknesses.mjs --check` | The README's Known weaknesses table differs from `docs/weaknesses.json` |
| `scripts/check-wording.sh` | Gambling words (*bet, wager, odds, winnings, yield…*) appear in disclosed copy, or a dollar figure lacks `≈` |
| `RepRatchetTest` | Any labelled rep is counted worse than the baseline |

## How changes are made

The product is specified with [Spec Kit](https://github.com/github/spec-kit):
`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` →
`/speckit-implement`, with `/speckit-analyze` before each pull request is marked ready.

When documents disagree: constitution → spec → decision record
([`docs/holdout_decisions.md`](holdout_decisions.md)). The screen maps, the design book and the
runbook describe, and decide nothing. The weaknesses table is edited in `docs/weaknesses.json`,
then rendered with `node scripts/render-weaknesses.mjs`.
