# Quickstart — validating 001-holdout-hackathon

**Phase 1 output of `/speckit-plan`.** How to build, run and prove the feature works end to end. It references contracts and the data model rather than repeating them; implementation lives in `tasks.md` and the code.

---

## 1. Prerequisites

| Tool | Version | Where |
|---|---|---|
| WSL2 + Ubuntu | 24.04 (GLIBC ≥ 2.39) | Windows host |
| Rust | ≥ 1.89 | WSL2 |
| Solana CLI (Agave) | 3.1.10 | WSL2 |
| Anchor CLI | 1.1.2 (pinned in `Anchor.toml`, same as `anchor-lang`/`anchor-spl`) | WSL2 |
| Surfpool | current (`curl -sL https://run.surfpool.run/ \| bash`; never `cargo install surfpool`) | WSL2 |
| Node.js | 22 LTS (≥ 20.18) | WSL2 |
| JDK | 21 (daemon toolchain); app bytecode targets Java 17 | Windows (Android Studio) |
| Android Studio / SDK | Current stable; compile/target SDK 37, min SDK 26 | Windows |
| Devices | A Solana Seeker with Seed Vault Wallet; one ordinary Android phone with an MWA wallet | — |
| Domain | One domain or subdomain for the MWA identity URI, with a Digital Asset Links file | — |
| Devnet SOL | Program deploy rent (measured at the rehearsal deploy); ~1.12 SOL for the 56 scripted wallets by 22 Sep (R12); 25 SOL in the faucet vault by 8 Oct (R13) | — |
| GitHub | A repository judges can clone, pushed from the first commit (FR-066) | — |

Commands in §3, §5 and §6 run inside WSL2. When an agent runs Anchor or Surfpool commands, prefix them with `NO_DNA=1`.

### One-time project setup

```bash
npx solana-mobile@latest create
```

Choose the **Kotlin Compose Minimal** template and generate it into `kotlin/`. Then remove the stray `dependencies { }` and `android { }` blocks from `kotlin/build.gradle.kts`, rename the `com.fitbet`-style placeholder namespace and the `https://yourdapp.com` identity, and add `rpc-core` to `gradle/libs.versions.toml` (research R3).

```bash
npx skills add solana-foundation/solana-dev-skill
```

```bash
claude mcp add --transport http solana-mcp-server https://mcp.solana.com/mcp
```

### Behind a restricted network

The installers above reach hosts a locked-down CI runner or sandbox may not
allow. The contract side needs **only** the Solana CLI, Anchor and Node, and all
three can be had from GitHub release assets and a git clone instead — which is
what a sandbox that permits `github.com` but not `release.anza.xyz` leaves you:

```bash
# Solana CLI 3.1.10, which carries cargo-build-sbf; it fetches platform-tools
# from GitHub on first use.
curl -sSL -o solana.tar.bz2 \
  https://github.com/anza-xyz/agave/releases/download/v3.1.10/solana-release-x86_64-unknown-linux-gnu.tar.bz2
tar -xjf solana.tar.bz2 && export PATH="$PWD/solana-release/bin:$PATH"

# Anchor CLI 1.1.2 from source — the same tag avm would build.
cargo install --git https://github.com/solana-foundation/anchor --tag v1.1.2 anchor-cli --locked
```

With those two on `PATH`, `NO_DNA=1 anchor build`, `npm --prefix tests run
build:test-leagues` and the whole LiteSVM suite run unchanged. **Surfpool is not
available this way**, so §6's smoke suite needs the ordinary installer and a
network that allows it; nothing else does.

The Android side has no such route: Gradle resolves AGP and AndroidX from
`dl.google.com`, so a sandbox without it cannot build `kotlin/` at all — not the
app and not the JVM-only modules, because the root build applies the Android
plugins. Maven Central and `services.gradle.org` are not enough on their own.

## 2. Repository layout

```text
programs/holdout/      Rust program (Anchor 1.1.2)
idl/                   Anchor IDL from the build
tests/                 TypeScript (Kit + LiteSVM) integration tests, Codama client, Surfpool smoke tests, fixture generation
fixtures/              Golden JSON shared by Rust, TypeScript and Kotlin
kotlin/core/           Kotlin/JVM: codecs, PDAs, instruction builders, Derive.kt
kotlin/repcounter/     Kotlin/JVM: pre-flight, start position and rep rules
kotlin/app/            Android app (Jetpack Compose)
kotlin/crank/          Kotlin/JVM CLI (automation, scripted members, chips)
```

## 3. Contract: tests first

```bash
NO_DNA=1 anchor build
```

```bash
cargo test -p holdout --lib math
```

Runs the pure settlement math against `fixtures/settlement-vectors.json` (PRD §19 V1–V22). **Expected:** all pass. On a fresh checkout before the math exists: all fail (constitution VII).

```bash
npm --prefix tests run codegen
```

Regenerates the Codama Kit client in `tests/clients/holdout/` from `idl/holdout.json`.

```bash
npm --prefix tests test
```

Runs Kit + LiteSVM integration tests with clock control and regenerates `fixtures/`. **Expected:** scenario tests for acceptance criteria 1–9, 11, 13, 15 (contract side), 16 and 18 (contract side) pass; the randomised invariant suite (INV-1…INV-8, 1,000-member cohort, pages out of order and repeated) passes.

```bash
git diff --exit-code fixtures/
```

**Expected:** no diff — fixtures are committed and match the program.

## 4. Kotlin modules

```bash
cd kotlin && ./gradlew :core:test :repcounter:test
```

**Expected:** `core` replays every fixture (accounts, PDAs, instructions, derived figures) with no mismatch; `repcounter` passes the recorded landmark traces — 10 honest push-up videos counted within 1 rep of 25, 3 cheat videos rejected, and the start position opening the gate only once it is held.

```bash
cd kotlin && ./gradlew :app:lint :app:checkNoAudioPermission
```

**Expected:** lint passes; the manifest check confirms `RECORD_AUDIO` is absent (FR-022a).

```bash
python scripts/design-book.py check
```

**Expected:** `design-book check: 0 drift` — every token in `docs/design/tokens.json` has its twin in `ui/theme/*.kt` with the same value, except the tokens the script lists as having no twin by design (`NO_TWIN_OK`, skipped) or as added when a screen first needs one (`ADD_WHEN_NEEDED`, reported as notes). Notes, such as those and the fonts not bundled yet (T035), do not fail it. Dynamic colour is off by construction: `Theme.kt` has one dark `ColorScheme` and no `dynamicColor` parameter. The lobby card's "at stake" with the joined count, never the word "prize" before a Holdout starts (FR-053, spec §1a), has no unit test; it is checked on the screen (holdout-ui skill, `references/verify.md`).

## 5. Full cycle on a compressed clock (spike S3 / rehearsal)

LiteSVM with a test build (`test-leagues` feature) and a test league with `day_length = 60`, the clock advanced by the test:

```bash
npm --prefix tests test -- --grep "full cycle"
```

**Expected:** open → join (with a lobby place) → lock → daily submits → forfeits → count → finalize (with remainder) → claim completes in minutes, and every INV holds at each step.

### Beta test on the rehearsal deployment (24–26 Sep)

The same compressed idea, but on devnet with real testers and real phones: a test league of **three 10-minute days at 5 reps a day**, a new cohort every 30 minutes (FR-044a, H36) — so each tester, or a single judge, runs faucet → join → sittings → a deliberate forfeit → settlement → claim in under an hour, and the lobby keeps opening cohorts through the live period.

```bash
java -jar kotlin/crank/build/libs/crank.jar init --test-league --first-start <iso>
```

Three 10-minute days at 5 reps, back to back every 30 minutes, joinable right up to each start (join lock 0, so one test cohort is always open), is the default; `--day-seconds`, `--days`, `--reps`, `--cadence` and `--join-lock` override it. If a test league already exists at id 100 with another shape (an earlier seven-day league, or the 5-minute join lock), the command refuses it; pass `--league-id 101`, or the next free id, to create the lobby. The newest test league is the lobby: the crank opens no more cohorts in the older one, which winds down, and opens the new lobby's first cohort only once every cohort of the older one has settled (FR-044a).

**Expected:** 5–7 testers each complete a full cycle on the short test cohort (FR-044a); bugs are fixed the same day. This run is the one the demo video is recorded from, labelled on screen as a short test cohort (constitution v1.4.0; no in-app walkthrough since 2026-10-09).

## 5a. The whole build on a local validator

Everything this build does, with free SOL and a real program binary: a local validator in WSL2, the crank on Windows, the app on a phone over USB. No devnet, no time travel — the short test league's compressed day is what makes a cycle take minutes.

```bash
# WSL2: build with test-leagues, start a fresh validator with the program loaded
# (upgrade authority = the admin key, so init passes the devnet check), fund keys.
NO_DNA=1 anchor build -- --features test-leagues
bash scripts/localnet.sh start && bash scripts/localnet.sh fund
```

```bash
# Git Bash on Windows: set up the program, then the test league (three 10-minute days, 5 reps) starting in ~20 minutes.
cd kotlin && ./gradlew :crank:jar && cd ..
bash scripts/localnet-crank.sh init --operator <operator> --sponsor <sponsor> --treasury <treasury> --rate 0.0213 --fund-faucet 20
bash scripts/localnet-crank.sh init --test-league --first-start <iso, ~20 min ahead>
# or instead: a newer test league with a cohort every 10 minutes (local only); it becomes the lobby and league 100 winds down
bash scripts/localnet-crank.sh init --test-league --league-id 102 --cadence 600 --join-lock 60 --first-start <iso>
bash scripts/localnet-crank.sh scripted fund
bash scripts/localnet-crank.sh --loop      # leave running: opens, fills, forfeits, settles
```

```bash
# The app on a phone over USB, reading and sending to the local validator.
adb reverse tcp:8899 tcp:8899
cd kotlin && ./gradlew :app:installMemberDebug -Pholdout.rpcUrl=http://127.0.0.1:8899 -Pholdout.sponsorKeypair=//wsl.localhost/Ubuntu/home/<you>/.config/solana/holdout/sponsor.json
```

A member debug build (`memberDebug`) pointed away from devnet asks the wallet to **sign only** and sends the transaction itself — the wallet would otherwise send it to devnet. Cleartext HTTP is allowed to `127.0.0.1`, `localhost` and `10.0.2.2`, in debug builds only.

**Expected:** connect → faucet → Home (no ladder screen since 2026-10-09) → the short test cohort, labelled → stake → lobby; from the start, 5 push-ups at 45° in each 10-minute day, submitted; scripted members forfeit on their seeded days; after the seventh day (70 minutes in) the cohort settles and the claim pays stake plus share. `bash scripts/localnet-crank.sh status` shows it all.

## 6. Devnet deployment

Smoke-test first against an embedded Surfpool network with time travel:

```bash
NO_DNA=1 anchor test
```

**Expected:** the smoke suite in `tests/smoke/` passes (one happy path per instruction plus a time-travelled settlement).

The program keypair is not in the repository. Put it where Anchor expects it first,
otherwise `anchor deploy` generates a fresh key and deploys to an address that no
longer matches `declare_id!`:

```bash
mkdir -p target/deploy && cp ~/.config/solana/holdout-program-keypair.json target/deploy/holdout-keypair.json && anchor keys list
```

**Expected:** `holdout: Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`, matching `Anchor.toml` and `declare_id!`. Back that file up — losing it means a new address and a redeploy, and once judged cohorts are running that would strand them.

```bash
NO_DNA=1 anchor deploy --provider.cluster devnet
```

Then, once:

```bash
java -jar kotlin/crank/build/libs/crank.jar init --admin <keypair> --operator <pubkey> --sponsor <pubkey> --treasury <pubkey>
```

```bash
java -jar kotlin/crank/build/libs/crank.jar chips publish --rate <usd-per-skr>
```

The set carries a deadline: the program refuses it from chain time + 10 minutes (`ChipSetExpired`, 6014), so a signed set left pending never lands much later than you meant. Give another with `--valid-until 2026-10-12T05:00:00Z`.

**The weekly chip check** (FR-056, T100) is the Operator's own scheduled command — never part of `crank run`, so the Operator key is loaded only while it runs. It reads the SKR rate from the Jupiter Price API and re-prices all four chips from it every week, publishes at most once per **publish window** — Monday 01:00 to 05:00 UTC, the 4 hours before the week's cohorts open, so they lock a rate at most 4 hours old — and exits `0` without publishing when Jupiter has no answer, saying why (FR-057). Try it first without sending:

```bash
java -jar kotlin/crank/build/libs/crank.jar chips publish --auto --dry-run
```

Then schedule it to run **every hour**, with `HOLDOUT_RPC_URL`, `HOLDOUT_PROGRAM_ID` and `HOLDOUT_OPERATOR_KEYPAIR` set (and `HOLDOUT_JUPITER_API_KEY` if you have one). The machine's time zone doesn't matter: the command itself knows the window by chain time, so the first run inside it publishes and every other run that week does nothing, without fetching a rate. Linux or WSL2 cron:

```cron
17 * * * *  cd /path/to/holdout && java -jar kotlin/crank/build/libs/crank.jar chips publish --auto >> crank-chips.log 2>&1
```

Windows Task Scheduler:

```powershell
schtasks /Create /TN "Holdout weekly chip check" /SC HOURLY /ST 00:17 /TR "cmd /c cd /d C:\path\to\holdout && java -jar kotlin\crank\build\libs\crank.jar chips publish --auto >> crank-chips.log 2>&1"
```

A run that fails or finds no rate is simply tried again the next hour inside the window — four tries at `:17`; if none succeeds, the cohorts opening at 05:00 keep the previous set (FR-057). Once a set has been published in the window, running it again does nothing. A once-a-Monday schedule is not used: cron ignores `CRON_TZ` on Debian, and a local-time run can miss the window or move with daylight saving. No send starts in the window's last 90 seconds (from 04:58:30 UTC), so a publish never lands after the cohorts open; a `:17` schedule never meets it. `--dry-run` works any day and reports `wouldPublishNow` and `sendCutoff`.

**Expected:** `Config`, the 7-day and 21-day `LeagueConfig`s (first start Monday 5 October 05:00 UTC by default — moved from 28 September on 26 September — so cohort index 0 is the first judged cohort and its lobby can open immediately), the mock SKR mint and a funded faucet vault exist; `crank status` lists the leagues with no cohorts yet.

```bash
java -jar kotlin/crank/build/libs/crank.jar run
```

**Expected:** cohort 0 of each league opens; running it again changes nothing.

## 7. App on a device

```bash
cd kotlin && ./gradlew :app:installMemberDebug
```

**Expected:** the app opens on the connect-wallet screen, with the test-network notice.

## 8. Acceptance scenarios

Run on devnet with real cohorts unless marked *local* (compressed clock, section 5). **[Phase 2]** scenarios are validated once their Phase 2 item is built.

| # | Scenario | How to run | Pass when |
|---|---|---|---|
| 1 | 7-day cycle; both leagues open | *local*: join 7-day, 7 submits, settle, claim; join a 21-day cohort with a fresh wallet that has no 7-day history (`tests/scenarios/open-leagues.spec.ts`) | Claim lands; the fresh wallet's 21-day join succeeds |
| 2 | Forfeit fee | *local*: join, skip a day, `crank run` | Pool rises by 70 % of the stake; developer destination +10 %; operations (`fee_destination`) +10 %; the other 10 % stays in the vault as `boost_due` and `comeback_due` until finalize (FR-027) |
| 3 | Equal stakes, equal payouts; remainder | *local*: V17 shape | Byte-identical payouts; remainder at fee destination; Σ claims = Σ stakes + pool − remainder |
| 4 | Claim arrives | Devnet cohort #3 finisher | SKR balance increases by payout |
| 5 | Crank killed a day; stranger cranks | *local*: stop crank, advance 1 day, run `crank run` from a different payer | Same forfeits and payouts as uninterrupted run; breached member's submit failed meanwhile |
| 6 | Vectors | Section 3 | All V1–V22 pass |
| 7 | Invariants at 1,000 members | Section 3 | Randomised suite green |
| 8 | Fresh install; counters at the deadline | Devnet: install, then connect; stop crank before a boundary | Wallet asked first, with nothing but help getting a wallet available before it; the first-run tour appears once the wallet connects, skips cleanly and re-opens from About (FR-067); cohort populated to at least its floor, scripted members labelled; one second after the deadline the counter already excludes non-submitters |
| 9 | No price read | Code search + test | No instruction reads a price; only `crank chips publish` uses a rate |
| 10 | No frame leaves device | Proxy the phone, run a sitting | Only RPC traffic observed |
| 11 | No finishers | *local*: all forfeit | The whole pool goes out: half (floored) to the developer destination, the rest to operations; no other cohort changes, whether or not the league has one open (FR-037) |
| 12 **[Phase 2]** | Weekly re-pricing | *local*: `crank chips publish --auto` outside the window, then twice inside Monday 01:00–05:00 UTC, then open the 05:00 cohort | Outside the window and the second run inside it do nothing; the first run inside publishes and the 05:00 cohort holds that set; a run after 04:58:30 sends nothing; old cohorts keep old chips |
| 13 | Lobby place, lock, overlap | *local* | Continuous 7-day → 7-day; cutoff ±1 s behaviour; 21-day overlap refused |
| 14 **[Phase 2]** | Leaderboard ranking | Devnet or *local* with seeded profiles | Ranking and "not ranked yet" as specified; no amounts; scripted labels |
| 15 | Banked reps across sittings | Device: 10 + 10 + 5; stop at 17; kill app | Day lands only after rep 25; 10 kept at 17; blocks survive kill; late sitting allowed with warning |
| 16 | Faucet from zero, and after a drained sponsor | Device with a brand-new wallet; then empty the sponsor's float with the bundled devnet key and try a second brand-new wallet before and after one `crank run` | One tap funds a full 7-day cycle; second tap the same day refused with the time it returns. With the float empty the claim fails as ES-0b and blames nobody; after the crank's `top_up_sponsor` the next brand-new wallet funds normally, and `crank status` shows the day's spend against `daily_lamport_cap` (AC-16, FR-042b) |
| 17 | Start position | Device: with every gating pill green (five pills since 2026-10-10, Knees visible among them; 002 FR-024a), hold the top of a push-up; then drop out of it before the countdown ends | Counting starts on the hold alone, with no tap and a spoken countdown; dropping out restarts the hold; banked reps unchanged |
| 18 | Offline completion | Device in airplane mode | Warning shown; reconnect before deadline + tap submit lands the day (automatic **[Phase 2]**); after deadline, breach |
| 19 | Submission complete | By 12 Oct: install the final member APK on a Seeker and a plain phone; build from a clean clone on a machine that has never built the project, following only the README; review the demo video and deck (R18) | Both installs run; clean clone builds the app and the program; video ≈ 3 min shows join, sitting, forfeit, settlement and claim from a cohort of the short test lobby, labelled on screen as one (AC-19, FR-044); scripted members and the test network disclosed in app, README, video and deck; submitted before 13 Oct 01:59 CEST |
