# Implementation Plan: Seeker ID Names

**Branch**: `003-seeker-id-names` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-seeker-id-names/spec.md`

## Summary

Show members by their Seeker ID (`alice.skr`) instead of a shortened address. That covers the connected wallet, the roster and the leaderboards, with the shortened address as the common, identical-looking fallback.

The app reads the names itself from the AllDomains name program on **Solana mainnet**, with two RPC calls per wallet:
- an owner search;
- a batched reverse-record read.

There is no server and no new dependency. The work is client only: a pure lookup module in `kotlin/core` and a paced, cached repository in `kotlin/app`. One shared `MemberIdentity` composable replaces the three `Format.short` call sites. Program and crank are untouched.

The public mainnet node allows about 10 owner searches per 10 seconds (research R4), so lookups are paced and prioritised:
1. the connected wallet;
2. rows on screen;
3. the rest of the list.

Results are kept on the phone for 24 hours.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (JDK 21 daemon, Java 17 bytecode), as in 001.

**Primary Dependencies**: All already in the build, nothing new:
- `rpc-core` (`Rpc20Driver`, `KtorNetworkDriver`) for the two RPC calls;
- `web3-solana` (`ProgramDerivedAddress`, `SolanaPublicKey`) for the addresses;
- `java.security.MessageDigest` for SHA-256;
- `kotlinx-serialization-json` for the request bodies;
- MMKV for the cache;
- Jetpack Compose for the identity line.

**Storage**: Device only, in MMKV: one kept name entry per wallet (data-model §2). Nothing is written to any chain.

**Testing**:
- **`core`** (JUnit 5): constants, request JSON, record parsing, choice, validation and truncation, against synthetic bytes.
- **`app`** (JVM unit tests with `kotlinx-coroutines-test`): pacing, priority, back-off, freshness and fallback, with a fake reader.
- **On a device**: one manual check (quickstart).

**Target Platform**: The Android app from 001 (minSdk 26, Seeker first). The names are read from Solana **mainnet**, and everything else stays on devnet.

**Project Type**: The existing mobile app plus its shared Kotlin core. No new module.

**Performance Goals** (research R4, on the public node, cold cache):
- the connected wallet's name in about 1 s;
- rows on screen in about 10 s;
- a 30-member list in about 40 s;
- a 100-member list in about 2 min.

From the cache, every name shows at once. No screen waits on a lookup.

**Constraints**:
- **Read-only:** the lookup signs and sends nothing (FR-009).
- **Pacing:** at most 8 owner searches per 10 s (one every 1.2 s), and a 15 s back-off after HTTP 429.
- **Endpoints:** the names endpoint is separate from the devnet endpoint (R5).
- **Display only:** names never reach ordering, ranking, labels or money (FR-008).

**Scale/Scope**:
- **Code:** 3 call sites, 1 new composable, 1 core file and 1 app repository file, plus tests, 1 weaknesses entry and 1 design-book wording change.
- **Data:** lists up to 1,000 members (001 FR-011a); the cache holds up to 5,000 entries.

No `NEEDS CLARIFICATION` items remain; all are resolved in [research.md](research.md). There is **one recommended spec amendment**: SC-002's 10-second figure cannot be met from a cold cache on the public node (research R4).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Pre-research | Post-design evidence |
|---|---|---|---|
| **I. Contract is the only authority on funds** | Nothing off-chain moves funds | PASS | The feature moves no funds and calls no Holdout instruction |
| **II. Forfeiture is derived** | Breach comes from chain state only | PASS | Untouched. Names never feed any state |
| **III. No oracle in contract logic** | No price or outside input in the contract | PASS | Contract untouched. Names are read by the app only, for display |
| **IV. SKR only** | Value is SKR | PASS | No value involved |
| **V. Parameters live on the cohort** | Cohort parameters fixed at creation | PASS | Untouched |
| **VI. One clock** | Deadlines by chain time, shown as countdowns | PASS | The 24-hour cache freshness uses the device clock and decides nothing (R7). No date is shown |
| **VII. Tests precede logic** | Tests written first | PASS | Tasks order the `core` tests (constants, parsing, choice, validation, truncation) and the queue tests before the code they cover |
| **VIII. Idempotent automation** | Automation safe twice and paged | PASS | No automation. The lookup is a read and safe to repeat |
| **IX. Every state has a screen** | Every state visible | PASS | One visible form per state: a name, or the shortened address. Not looked up, failed and none all show the address (FR-006); found shows the name |
| **X. Camera frames never leave the device** | No camera data off the device | PASS | Only wallet addresses already public on chain are sent, to the names endpoint (contracts §2) |
| **XI. Weaknesses documented** | Anything outside the stated setup is named | PASS, **action required** | One weaknesses entry for the mainnet read (FR-010, contracts §4), rendered to About and the README. A keyed endpoint, if configured, is named the same way (R4) |
| **XII. The build demonstrates itself** | Live, populated, labelled crowd | PASS | Scripted members keep their label (FR-011). The fallback looks deliberate (FR-003), so a crowd of addresses still reads as a crowd |
| **XIII. Honest language** | No money on boards; honest wording | PASS | No amounts added. Rows show only an identity |
| **Hackathon constraints: devnet only** | The program and all value on devnet | PASS, **justified** | The program, every transaction and every token stay on devnet. The one mainnet contact is a read of public name records through a read-only client that cannot send (R5). See Complexity Tracking |
| **Hackathon constraints: leaderboards on chain only** | No server or device figure feeds a ranking | PASS | Rankings are unchanged and computed before names are attached (FR-008) |
| **Development workflow** | Stack in the plan; spec amended through the skills | PASS, **follow-up** | Amendments go through skills: 001 FR-063 via `/speckit-clarify` on 001; 001 `contracts/android-app.md` §2 egress via `/speckit-plan` on 001; SC-002 wording via `/speckit-clarify` on 003; the design book's Roster row via the artifact, then a sync |

**Gate result: PASS.** The only item outside the plain reading of a constraint is the read-only mainnet contact, justified below and disclosed (XI).

## Project Structure

### Documentation (this feature)

```text
specs/003-seeker-id-names/
├── spec.md
├── plan.md               # This file
├── research.md           # R1–R10: name program, spike timings, pacing, validation
├── data-model.md         # Seeker ID, kept name entry, shown identity, lookup queue
├── quickstart.md         # Build, test and the on-device check
├── contracts/
│   └── seeker-names.md   # The two RPC requests, the core API, egress and the weaknesses entry
├── checklists/
│   └── requirements.md
└── tasks.md              # Created by /speckit-tasks
```

### Source Code (repository root)

```text
kotlin/core/src/main/kotlin/holdout/core/
├── SeekerNames.kt            # NEW — constants, address derivation, owner-search query,
│                             #       reverse-record parsing, choice (R3), validation (R6),
│                             #       truncation (R9)
└── SeekerNameReader.kt       # NEW — read-only mainnet client: owner search + batched reads (R5)

kotlin/core/src/test/kotlin/holdout/core/
└── SeekerNamesTest.kt        # NEW — R1 constants, request JSON, parsing, R3/R6/R9

kotlin/app/src/main/kotlin/holdout/app/
├── names/
│   └── SeekerNameRepository.kt   # NEW — paced priority queue, MMKV cache, StateFlow of names
├── storage/Store.kt              # + kept name entries (data-model §2)
├── ui/Components.kt              # + Format.identity(...) and MemberIdentity composable (R8)
├── roster/RosterScreen.kt        # Format.short → MemberIdentity
├── leaderboard/LeaderboardScreen.kt  # Format.short → MemberIdentity
└── HoldoutApp.kt                 # the connected-wallet line → MemberIdentity; repository wiring

kotlin/app/src/test/kotlin/holdout/app/names/
└── SeekerNameRepositoryTest.kt   # NEW — pace, priority, back-off, freshness, fallback

kotlin/app/build.gradle.kts           # + NAMES_RPC_URL from holdout.namesRpcUrl
kotlin/app/src/main/assets/weaknesses.json   # + one entry; README re-rendered
```

**Structure Decision**: The pure, network-free logic goes in `kotlin/core`, next to `Pdas.kt` and `Rpc.kt`, where every rule in this codebase is unit-tested on the JVM. The read-only network client also goes there, beside `Rpc`, so the two drivers sit side by side and a review sees that only one of them can send. The cache, pacing and Compose pieces go in `kotlin/app`, beside the screens that use them. Program, crank and tests under `tests/` are untouched.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A read from **Solana mainnet** in a "devnet only" build | Seeker IDs exist only on mainnet; there is no devnet copy to read | Not showing names defeats the feature. A server that mirrors names breaks "no server" (FR-009, 001 constraints). The read is public, signs nothing, and goes through a client that cannot send (R5). It is disclosed under XI |
| A **second RPC client** (`SeekerNameReader`) beside `Rpc` | Keeps `Rpc`'s guarantee that no path can reach mainnet with a send | A second `Rpc` instance on mainnet would expose `send`/`simulate` to a mainnet endpoint |
