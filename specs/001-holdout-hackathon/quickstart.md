# Quickstart — validating 001-holdout-hackathon

**Phase 1 output of `/speckit-plan`.** How to build, run and prove the feature works end to end. It references contracts and the data model rather than repeating them; implementation lives in `tasks.md` and the code.

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| Rust + Solana CLI (Agave) | Versions required by Anchor 1.0.x |
| Anchor CLI | 1.0.x (pinned in `Anchor.toml`) |
| Node.js | 22 LTS (program integration tests, fixture generation) |
| JDK | 17 |
| Android SDK / Android Studio | Current stable; platform API 35, min API 29 |
| Devices | A Solana Seeker with Seed Vault Wallet; one ordinary Android phone with an MWA wallet |
| Devnet SOL | ~40 SOL for faucet pre-funding (R13) plus deploy costs, gathered in advance |

## 2. Repository layout

```text
programs/holdout/      Rust program (Anchor)
tests/                 TypeScript LiteSVM integration tests + fixture generation
fixtures/              Golden JSON shared by Rust, TypeScript and Kotlin
kotlin/core/           Kotlin/JVM: codecs, PDAs, instruction builders, Derive.kt
kotlin/repcounter/     Kotlin/JVM: pre-flight, palm and rep rules
kotlin/app/            Android app (Jetpack Compose)
kotlin/crank/          Kotlin/JVM CLI (automation, scripted members, chips)
```

## 3. Contract: tests first

```bash
anchor build
```

```bash
cargo test -p holdout --lib math
```

Runs the pure settlement math against `fixtures/settlement-vectors.json` (PRD §19 V1–V22). **Expected:** all pass. On a fresh checkout before the math exists: all fail (constitution VII).

```bash
npm --prefix tests test
```

Runs LiteSVM integration tests with clock control and regenerates `fixtures/`. **Expected:** scenario tests for acceptance criteria 1–9, 11, 13, 15 (contract side), 16 and 18 (contract side) pass; the randomised invariant suite (INV-1…INV-8, 1,000-member cohort, pages out of order and repeated) passes.

```bash
git diff --exit-code fixtures/
```

**Expected:** no diff — fixtures are committed and match the program.

## 4. Kotlin modules

```bash
cd kotlin && ./gradlew :core:test :repcounter:test
```

**Expected:** `core` replays every fixture (accounts, PDAs, instructions, derived figures) with no mismatch; `repcounter` passes the recorded landmark traces — 10 honest push-up videos counted within 1 rep of 25, 3 cheat videos rejected, palm timeout after 30 s.

```bash
cd kotlin && ./gradlew :app:lint :app:checkNoAudioPermission
```

**Expected:** lint passes; the manifest check confirms `RECORD_AUDIO` is absent (FR-022a).

## 5. Full cycle on a compressed clock (spike S3 / rehearsal)

LiteSVM with a test build (`test-leagues` feature) and a test league with `day_length = 60`, the clock advanced by the test:

```bash
npm --prefix tests test -- --grep "full cycle"
```

**Expected:** open → join (with a lobby place) → lock → daily submits → forfeits → count → finalize (with remainder) → claim completes in minutes, and every INV holds at each step.

## 6. Devnet deployment

```bash
anchor deploy --provider.cluster devnet
```

Then, once:

```bash
java -jar kotlin/crank/build/libs/crank.jar init --admin <keypair> --operator <pubkey> --sponsor <pubkey> --treasury <pubkey>
```

```bash
java -jar kotlin/crank/build/libs/crank.jar chips publish --rate <usd-per-skr>
```

**Expected:** `Config`, the 7-day and 21-day `LeagueConfig`s (first start Monday 28 September 05:00 UTC, so cohort index 0 is cohort #3 and its lobby can open immediately), the mock SKR mint and a funded faucet vault exist; `crank status` lists the leagues with no cohorts yet.

```bash
java -jar kotlin/crank/build/libs/crank.jar run
```

**Expected:** cohort 0 of each league opens; running it again changes nothing.

## 7. App on a device

```bash
cd kotlin && ./gradlew :app:installDevnetDebug
```

**Expected:** the app opens on the connect-wallet screen, with the test-network notice.

## 8. Acceptance scenarios

Run on devnet with real cohorts unless marked *local* (compressed clock, section 5). **[Phase 2]** scenarios are validated only if Phase 2 lands before the freeze.

| # | Scenario | How to run | Pass when |
|---|---|---|---|
| 1 | 7-day cycle, unlock, gate | *local*: join 7-day, 7 submits, settle, claim; flip `set_flags(ladder=true)`; try 21-day join with a fresh wallet | Claim lands; `Profile.qualified`; fresh wallet gets `LadderGateClosed` |
| 2 | Forfeit fee | *local*: join, skip a day, `crank run` | Pool rises by stake − 20 %; fee destination +20 % |
| 3 | Equal stakes, equal payouts; remainder | *local*: V17 shape | Byte-identical payouts; remainder at fee destination; Σ claims = Σ stakes + pool − remainder |
| 4 | Claim arrives | Devnet cohort #3 finisher | SKR balance increases by payout |
| 5 | Crank killed a day; stranger cranks | *local*: stop crank, advance 1 day, run `crank run` from a different payer | Same forfeits and payouts as uninterrupted run; breached member's submit failed meanwhile |
| 6 | Vectors | Section 3 | All V1–V22 pass |
| 7 | Invariants at 1,000 members | Section 3 | Randomised suite green |
| 8 | Fresh install; counters at the deadline | Devnet: install, connect; stop crank before a boundary | Wallet asked first; populated labelled cohort; one second after the deadline the counter already excludes non-submitters |
| 9 | No price read | Code search + test | No instruction reads a price; only `crank chips publish` uses a rate |
| 10 | No frame leaves device | Proxy the phone, run a sitting | Only RPC traffic observed |
| 11 | No finishers, carry-over | *local*: all forfeit; with and without an open next cohort | 50 % fee; carry-over in the open cohort, or held and recorded (delivery **[Phase 2]**) |
| 12 **[Phase 2]** | Weekly re-pricing | *local*: `crank chips publish --auto` with drift below and above 20 % | Publishes only above 20 %; old cohorts keep old chips |
| 13 | Lobby place, lock, overlap | *local* | Continuous 7-day → 7-day; cutoff ±1 s behaviour; 21-day overlap refused |
| 14 **[Phase 2]** | Leaderboard ranking | Devnet or *local* with seeded profiles | Ranking and "not ranked yet" as specified; no amounts; scripted labels |
| 15 | Banked reps across sittings | Device: 10 + 10 + 5; stop at 17; kill app | Day lands only after rep 25; 10 kept at 17; blocks survive kill; late sitting allowed with warning |
| 16 | Faucet from zero | Device with a brand-new wallet | One tap funds a full 7-day cycle; second tap same day refused with time |
| 17 | Palm timeout | Device: cover camera at palm step | Tip after 30 s, back to pre-flight; banked reps unchanged |
| 18 | Offline completion | Device in airplane mode | Warning shown; reconnect before deadline + tap submit lands the day (automatic **[Phase 2]**); after deadline, breach |
