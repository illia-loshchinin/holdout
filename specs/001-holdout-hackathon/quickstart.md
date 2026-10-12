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
| Devnet SOL | Program deploy rent (computed from the built `holdout.so` with `solana rent <bytes>` before the deploy; the program-data account is sized at about twice the binary and the deploy buffer is held temporarily); ~1.12 SOL for the 56 scripted wallets by 22 Sep (R12); 25 SOL in the faucet vault by 8 Oct (R13) | — |
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

## 5. Full cycle on a compressed clock (spike S3)

LiteSVM with a test build (`test-leagues` feature) and a test league with `day_length = 60`, the clock advanced by the test:

```bash
npm --prefix tests test -- --grep "full cycle"
```

**Expected:** open → join (with a lobby place) → lock → daily submits → forfeits → count → finalize (with remainder) → claim completes in minutes, and every INV holds at each step.

### Beta test on the devnet program

The same compressed idea, but on devnet with real testers and real phones: a test league of **three 10-minute days at 5 reps a day**, a new cohort every 30 minutes (FR-044a, H36) — so each tester, or a single judge, with a wallet holding a Seeker Genesis Token (FR-003b), runs faucet → join → sittings → a deliberate forfeit → settlement → claim in under an hour, and the lobby keeps opening cohorts through the live period.

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

Staking needs a wallet holding a Seeker Genesis Token on mainnet (FR-003b). On the emulator or a phone without one, build the member app with the debug-only bypass, `./gradlew :app:installMemberDebug -Pholdout.seekerGate=off` (research R17); a release build always checks.

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

The devnet program must carry the `test-leagues` feature: `programs/holdout/Cargo.toml` has `default = []`, so a plain `anchor build` omits it, and `crank init --test-league` is then refused with 6010. Build it with the feature, and check the deploy rent from the binary before funding it:

```bash
NO_DNA=1 anchor build -- --features test-leagues
solana rent "$(stat -c %s target/deploy/holdout.so)"
```

Then deploy that binary, without building again in between. The `admin` wallet pays and becomes the upgrade authority, which `initialize` requires (R14); `Anchor.toml`'s default wallet, `~/.config/solana/id.json`, holds no SOL:

```bash
NO_DNA=1 anchor deploy --provider.cluster devnet --provider.wallet ~/.config/solana/holdout/admin.json
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

### The signed release (the APK that is submitted)

With the release keystore outside the clone and its four Gradle properties (`holdout.keystore`, `holdout.keystorePassword`, `holdout.keyAlias`, `holdout.keyPassword`) set in `~/.gradle/gradle.properties` (`docs/holdout_setup_runbook.md` §4):

```bash
cd kotlin && ./gradlew :app:assembleMemberRelease
"$SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/member/release/app-member-release.apk
```

`SDK` is `sdk.dir` from `kotlin/local.properties`; the runbook has the PowerShell form.

**Expected:** `app-member-release.apk` exists and `apksigner` exits 0 (`--verbose`: `Verifies`, v2 scheme), printing the signer's certificate SHA-256, which is the fingerprint in `assetlinks.json`. A build with no keystore writes `app-member-release-unsigned.apk` and a `WARNING: assembleMemberRelease is UNSIGNED` line; that file is not submittable (FR-066, AC-19).

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
| 8 | Fresh install; counters at the deadline | Devnet: install, then connect; stop crank before a boundary | Wallet asked first, with nothing but help getting a wallet available before it; the first-run tour appears once the wallet connects; Skip on card 1–3 lands on card 4, the scripted-member disclosure; and it re-opens from About (FR-067); cohort populated to at least its floor, scripted members labelled; one second after the deadline the counter already excludes non-submitters |
| 9 | No price read | Code search + test | No instruction reads a price; only `crank chips publish` uses a rate |
| 10 | No frame leaves device | Proxy the phone, run a sitting | Only RPC traffic observed |
| 11 | No finishers | *local*: all forfeit | The whole pool goes out: half (floored) to the developer destination, the rest to operations; no other cohort changes, whether or not the league has one open (FR-037) |
| 12 **[Phase 2]** | Weekly re-pricing | *local*: `crank chips publish --auto` outside the window, then twice inside Monday 01:00–05:00 UTC, then open the 05:00 cohort | Outside the window and the second run inside it do nothing; the first run inside publishes and the 05:00 cohort holds that set; a run after 04:58:30 sends nothing; old cohorts keep old chips |
| 13 | Lobby place, lock, overlap | *local* | Continuous 7-day → 7-day; cutoff ±1 s behaviour; 21-day overlap refused |
| 14 **[Phase 2]** | Leaderboard ranking | Devnet or *local* with seeded profiles | Ranking and "not ranked yet" as specified; no amounts; scripted labels |
| 15 | Banked reps across sittings | Device: 10 + 10 + 5; stop at 17; kill app | Day lands only after rep 25; 10 kept at 17; blocks survive kill; late sitting allowed with warning |
| 16 | Faucet from zero, and after a drained sponsor | Device with a brand-new wallet; then empty the sponsor's float with the bundled devnet key and try a second brand-new wallet before and after one `crank run` | One tap funds a full 7-day cycle; second tap the same day refused with the time it returns. With the float empty the claim fails as ES-0b and blames nobody; after the crank's `top_up_sponsor` the next brand-new wallet funds normally, and `crank status` shows the day's spend against `daily_lamport_cap` (AC-16, FR-042b) |
| 17 | Start position | Device: with every gating pill green (five pills since 2026-10-10, Knees visible among them; 002 FR-024a), hold the top of a push-up; then drop out of it before the countdown ends | Counting starts on the hold alone, with no tap and a spoken countdown; dropping out restarts the hold; banked reps unchanged. Then send the app to the background during the countdown and again mid-set: each ends that sitting back at the checks, loose reps gone and banked blocks kept; on Hard, a countdown that ends with too little of the plank read goes back to the checks instead of counting (AC-17, §8.3) |
| 18 | Offline completion | Device in airplane mode | Warning shown; the finished screen reads *"Done — not sent yet"* with the reps saved on the phone and the time left to that day's own deadline (past it, *"Done — too late to send"*, whether the phone is still offline or already back online, and never a *Submit day N* button), and *Back to today* sends nothing; the home reads *"Done — waiting for network"*; reconnect with the app open before the deadline + tap submit lands the day (automatic **[Phase 2]**, the wallet's approval opening by itself); with the app closed nothing is sent; reconnect after the deadline: the finished screen reads *"Done — too late to send"* with no submit offered, and the member is in breach (AC-18, FR-024b) |
| 19 | Submission complete | By 12 Oct: install the final member APK (the signed `memberRelease` built and checked with `apksigner` as in section 7) on a Seeker and a plain phone; build from a clean clone on a machine that has never built the project, following only the README; review the demo video and deck (R18) | Both installs run; clean clone builds the app and the program; video ≈ 3 min shows join, sitting, forfeit, settlement and claim from a cohort of the short test lobby, labelled on screen as one (AC-19, FR-044); scripted members and the test network disclosed in app, README, video and deck; submitted before 13 Oct 01:59 CEST |
| 20 | Reminders | Device with notifications allowed, on a Holdout of 24-hour days and on the short test lobby: join; leave one day undone; finish another with the chain unreachable, then reach it and submit | A reminder arrives when the Holdout starts. On a 24-hour day the halfway reminder arrives with sound (17:00–18:00 UTC) and the one before the deadline arrives silent; a shorter day gets one sounding reminder before its deadline. A day already on chain gets none. A day done on the phone and not on chain keeps a silent, ongoing *"Day N is done, but not on chain yet"* notice in the shade, re-posted silently every hour until the submit lands or the deadline passes (on a day of 12 hours or more it replaces that day's halfway and deadline reminders; a shorter day keeps its sounding deadline reminder beside it). After leaving, none arrives; a member who declined notifications joins, sits and submits as before (AC-20, FR-068, FR-068a, R21) |
| 21 | Seeker-exclusive staking | *local*: `cargo test` (the genesis helper) and `open-leagues.spec.ts` with `genesis_gate_enabled` on. Device: join from the Seeker's wallet; then from a wallet with no Seeker Genesis Token; then, with the names endpoint unreachable, from a wallet never confirmed on the phone and from the Seeker's | The helper passes the real group and refuses a fake group, no extension, amount 0, another owner and SPL-Token; with the flag on a join with the token succeeds and one without fails with `GenesisGateClosed`. On the device the Seeker joins; the other wallet does the Daily 25 and sees the Holdouts, but every join shows the Seeker-exclusive sheet with the store link and nothing is signed; unreachable mainnet refuses the unconfirmed wallet with the try-again message and lets the confirmed Seeker join (FR-003b–FR-003d, R17) |
| 22 | Settling countdown and finished notice | Device with notifications allowed, on the short test lobby: finish every day, close the app before the end; a second run opening the app after the cohort settled and before end + 15 min; a third with notifications declined | Before settlement the cohort detail reads *"You finished · settling"* with *"usually within 15 minutes of the end"* and the time since the end, and the Home card *"You finished · settling, usually within Nm"*; about 15 minutes after the end one notice, with sound, says the claim is probably ready (with the comeback line when this finish earns a comeback refund), no amount; opening the app shows the claim once settled. In the second run no notice comes; in the third none, and the same screens. Then the Android paths no unit test reaches: with the app on screen at end + 15 min, no notice comes; with the notice in the shade, opening the app after the cohort settled takes it away; with the notice in the shade, disconnecting the wallet takes it away (AC-22, FR-035a, FR-068b) |
| 23 | Comeback offer right after a forfeit | *local* validator (section 5a) with a test league: join, miss day 1, do **not** run the crank; open the app; then join from the offer; run the crank through settlement of both cohorts and claim. `:core:test` (`ComebackTest`, `DailyHoldoutLineTest`) | Unit tests green, including the window's boundary seconds. On the device: the forfeit screen shows "up to" 20 % of the lost stake, the next round's start and time left to join, and the round after it; Today shows the nudge; Join opens the stake sheet on the lost chip with the per-chip amount; the join lands in one transaction with the forfeit record (ES-17); Today then says to finish it; the claim shows the Comeback row with the amount FR-037e computes, and the cohort detail keeps the breakdown after the claim. While the next round is not opened yet the offer stays with no Join and says when it opens ("shortly" if automation is late). *(Amended 2026-10-12.)* The forfeit screen reads top to bottom: the headline and a cause sentence that fits the day — "You didn't finish day 2 before its deadline. Your run has ended." for a day never finished, "It didn't reach the chain in time. Your run has ended." for a day finished on the phone and not sent (finish a day offline past its deadline to see the second) —, the **Stake lost** row with the fee sentence (a short test Holdout reads "…10 % to short test Holdout boosts and comebacks…", as the stake sheet does), the comeback block with its one primary button, one small quiet line "Your N-day streak is still alive." (or "Your streak is untouched.") with no card, and last a secondary button *Do today's Daily 25* — present only while today's Daily 25 is not done; once it is done the button is gone, and with no offer the block reads "The next Habit · 21-day starts a new run." alone (nothing when no next Holdout is open), while with it not done the block reads "Two ways back" and "Today's Daily 25 keeps your streak going. The next Habit · 21-day starts a new run." In the offer's timing line "Starts in 9m 24s · joining closes in 8m 24s" only the two countdowns are in the mono face, and under a minute a countdown reads "58s", never "0m 58s". A forfeit whose rounds have all closed shows no offer, and after a second forfeit the first forfeit's screen shows no offer (ES-19). *local* LiteSVM: `npx vitest run scenarios/comeback-join.spec.ts` from `tests/` — the record-and-join transaction (657 bytes, limit 1232) succeeds, frees the running slot without writing the old end back, and a bare join fails with `ObligationsOverlap` (ES-17) (AC-23, FR-037g) |
| 24a | Four tabs and their roots *(AC-24)* | JVM: the navigation reducer's transition tests and the Robolectric shell tests (`:app:test*UnitTest`, run by `scripts/ci-local.sh`). Device: open the app on a wallet with no Holdout, then in a lobby, then running one, then after a forfeit; open it from a reminder; leave a lobby place while its lobby is on screen; open the faucet sheet at once on a cold start, and open the stake sheet on a non-default chip with too little SOL, take the faucet, return | The bar shows Today · Standing · Holdouts · Me on each tab's root and on no sitting, connect, tour or signing screen. Today is, when a Holdout runs, the day (ring, reps, day button, deadline countdown, streak chip in the header) and one crowd strip "4 of 5 still standing · your share 85.04 SKR ›" that opens Standing — no big counter, prize-pool card or boost line, no Daily 25 card, no offers — and the Daily 25 with the offers otherwise. Standing is the running Holdout's detail, with the "your day" strip on top while the member stands in it and the day is open ("DAY 2 · 5 push-ups · 6m 24s left" and *Start* / *Keep going* / *Submit day 2*, the same button and rules as Today; *Start* opens the sitting; once the day is on chain the quiet line "Day 2 is done and on chain." with no button; no strip on a Holdout only watched or after the last day), else the last one's, else the empty state (Running now, then "Join one to have a crowd" → Holdouts; with only a lobby place "Yours starts in …" between, only its countdown in the mono face). Holdouts lists *Yours*, *Finished*, the offers and, under them, *Running now* (also for a member who is in a Holdout; a row opens the detail and then the roster); Me shows address, SKR, SOL, streak, *Get test tokens* (a sheet that returns to Me). The faucet sheet reads "Reading the chain…" in place of the balances, with *Get them* disabled, until the snapshot and chain time are read, and the stake sheet comes back from the faucet on the chip that was picked. A lobby left while on screen stays and reads "You left. All X is back in your wallet." with *Back to Holdouts*; the button and Back both land on My Holdouts. After the day is on chain Today shows no red countdown and says "Day 3 opens in …" (no extra "Day 2 is on chain." line); after the last day it says "That was the last day. It settles soon after the end, then your claim is ready.", never "Next day opens in …", with no settling countdown under it — at the end the app moves to Standing, where the settling card is (FR-069a, FR-035a). *Start* on Standing's day strip opens the sitting, and its end (the finish button, the Stopped page, Back) lands on Today's root, as for a sitting started from Today (FR-069b). A countdown under a minute reads "58s", "0s" once past and "1m 00s" at 60 seconds (FR-046). A message such as "Day 3 is on chain." does not appear on the claim screen. Back pops the tab, goes to Today from another root, leaves the app from Today. Each tab's dot (never a count, its meaning in the accessible name): Today while today's reps are not done; Standing after someone drops out, clearing once the detail is looked at; Holdouts while a claim is waiting; Me when SKR or SOL is too low to stake or submit (FR-069…FR-069d, FR-062a) |
| 24b | Signing flow results *(AC-24)* | JVM: the wallet-outcome → result mapping tests. Device or *local* validator (section 5a): join, submit and claim each with the wallet approving, declining, and the network not confirming; a join after the cutoff; a submit after the deadline; a submit with no SOL; a wallet answering "not submitted" (it signed but could not send), a session that drops after the wallet opened, then *Try again* on a *Declined* and on a *Failed* | Each opens *Waiting for your wallet…* with no Cancel, then the checklist, then: success (join → *You're in*, which offers *Do today's Daily 25* above *See the lobby* only when today's Daily 25 is not done and the Holdout starts on a later Daily 25 day, and otherwise *See the lobby* as its one primary button (FR-070b); submit → Today with the day on chain, also when signed from Standing's day strip; claim → amount, *Done*, and after a Spark — a short test Spark included — *Make it a Habit · 21-day*, or *Join the next Habit · 21-day* after a test Holdout with the toes form); *Declined* — "Nothing was sent."; *Failed* — "The network didn't confirm it.", with "Check again in a minute" and no *Try again* when it may still land (the session that dropped after the wallet opened), and with *Try again* when nothing reached the network (the wallet's "not submitted" or any other error it returned); *Try again* is on *Declined* as well and always asks the wallet for a new signature; *Joining closed*; *Deadline passed* → the forfeit screen; *Not enough SOL* → the faucet sheet and back to the flow's opener. The stake sheet and the faucet are bottom sheets that return to their opener (FR-070…FR-070c, ES-20, ES-22) |
| 24c | Four-card tour, level names and money copy *(AC-24)* | JVM: string tests over the member-facing copy (no "Easy", "Hard", "league", "cohort", "earn", "challenge"). Device: a fresh install, connect, Skip on card 1; open a stake sheet for each level; open a forfeit | Skip on card 1, 2 or 3 lands on card 4 (the scripted-member disclosure, no judges, no Holdout Lab); the pager reads "how it works · N of 4"; About re-opens the four cards. Members read **Spark · 7-day** and **Habit · 21-day** with their taglines, each stake sheet shows its own rule sentence word for word and never the other's (002 FR-014), the Daily 25 shows "counted as the Spark". The stake sheet's fee sentence is the 70 / 10 / 20 one with a no-break space before each %, the forfeit screen's sits under **Stake lost**, offers say "Prize boost: up to N SKR", and "≈ $" appears only beside a stake chip with "Dollar values are approximate." near it (FR-041, FR-053, FR-059, FR-067) |
| 24d | Sitting wiring, reminder and restore *(AC-25)* | JVM: `SittingOffersTest`, `SittingWiringTest`, `InterruptedSittingTest`, `AppOpenTest`, `NavSavedTest`, `ConnectScreenTest`. Device: finish a Daily 25 with no Holdout (then with one running, then on a wallet without a Seeker); finish a Holdout day after its deadline; kill the app during a sitting after banking reps, then reopen; tap a reminder with the app open and then kill the process from the recents with a lobby open; kill the process with a lobby open and tap a reminder (the app is made anew), then do it again returning from the launcher; press the phone's Back in a sitting, with the set-up guide open (ticking "Don't show this again") and on a finished day; connect a wallet set to another network | The Daily 25's page offers "Stake on yourself next · Spark · 7-day · joining closes in …" (or "Get your stake back") only with no Holdout running or in a lobby and never to a wallet without a Seeker; a day finished too late reads "Done — too late to send" and *Back to today* opens the forfeit screen; the next Today shows the crash variant of the Stopped page once with the blocks kept; a reminder's tap lands on Today without ending a sitting, also when it makes the app anew; after the process dies the app, returned to from the launcher or Recents, comes back on the same tab and pushed screens, with no sheet or signing flow; the phone's Back in a sitting ends it as the ✕ does (the Stopped page when anything was banked or discarded, no confirmation), closes the set-up guide first and keeps its tick, and is the finish button on a finished day; the wrong-network sentence reads "Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again…" (FR-053a, FR-024b, FR-069, FR-069e, FR-001b, ES-1) |
| 24e | Pre-flight tip sheet *(002 FR-020a, US-3)* | JVM: the trigger counter's tests (30 seconds in total short of green, the 30 s ignoring time under the set-up guide while a flip during it still counts; the third green-to-red turn; whichever is first; reset by a new sitting) and the Robolectric tests of the sheet (rows in the pills' order, red rows marked, the three exits, Back closing the sheet before the sitting). Device: start a Daily 25, then a Spark, with the phone leaning wrong so a check stays red | Before 30 seconds of the checks in view (time under the set-up guide does not count) and before a third turn there is no button; after, **"Still not green? Tips"** shows under the fix sentence and never opens by itself; it opens "Getting the checks green" with every pill's label and its fix sentence, the red ones marked; "Open the set-up guide" reopens the guide; "Back to the checks", Back, a tap outside and a drag each close it with the checks still running and no countdown started; a new sitting starts without the button |
| 24f | Streak milestone day *(AC-26; FR-049a)* | JVM only for the days: `MilestoneTest` (the list, the copy for every milestone including 200 and 365, frozen days not counted, "reached today" needs today's day complete) and the Robolectric tests of the day-complete page and Today. No debug build sets a streak on a device, so a device walk of a real 7-day streak is done only when a tester reaches one; otherwise the milestone state is checked on the design canvas and in those tests | Day 7 shows the milestone flame, "7-day streak" and its line once on the day-complete page, and the same chip with the line on Today for the rest of the day, the plain chip the next; days 8 and 13 show none; a Holdout running shows none on Today; with reduced motion the flame does not pulse |
| 24g | New-phone banner *(AC-27; FR-069f, ES-3a)* | JVM: the signal's tests (first seen today, a Holdout started before today, nothing banked; the upgrade case; the next day) and `HomeScreenTest`. Device: a fresh install, connect a wallet that has a Holdout membership started on another phone; then update an installed build whose wallet has seen the tour | Today shows "No banked reps on this device today. 25 to go." (or the Holdout's daily target) until a block is banked, and not the next day; the updated install never shows it |
