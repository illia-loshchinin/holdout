# Implementation Plan: Seeker ID Names

**Branch**: `003-seeker-id-names` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-seeker-id-names/spec.md`

## Summary

Show members by their Seeker ID (`alice.skr`) instead of a shortened address. That covers the connected wallet, the roster and the leaderboards, with the shortened address as the common, identical-looking fallback.

The app reads the names itself from the AllDomains name program on **Solana mainnet**, with two RPC calls per wallet:
- an owner search;
- a batched reverse-record read.

There is no server and no new dependency. The work is client only: a pure lookup module in `kotlin/core` and a paced, cached repository in `kotlin/app`. One shared `MemberIdentity` composable replaces the three `Format.short` call sites. Program and crank are untouched.

The public mainnet node allows about 10 owner searches per 10 seconds (research R4), so lookups are paced. They are also lazy (FR-012, research R11): only two kinds of wallet are ever looked up, in this order:
1. the connected wallet;
2. rows on screen.

A row that leaves the screen before its search starts is dropped, and nothing is fetched ahead for rows the member has not scrolled to. Results are kept on the phone for 24 hours.

The feature builds on spec 004's layout: the `member` and `lab` app flavours, which both carry it from `src/main`, and the weaknesses source in `docs/weaknesses.json`, shown in the README only.

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
- **`app`** (JVM unit tests, run for both flavours): pacing, priority, dropping rows that leave the screen, back-off, freshness and fallback, with a fake reader.
- **On a device**: one manual check (quickstart).

**Target Platform**: The Android app from 001 (minSdk 26, Seeker first). The names are read from Solana **mainnet**, and everything else stays on devnet.

**Project Type**: The existing mobile app, with its `member` and `lab` flavours since spec 004, plus its shared Kotlin core. No new module or flavour-specific code.

**Performance Goals** (research R4, on the public node, cold cache):
- the connected wallet's name in about 1 s (SC-001: within 3 s);
- a screenful of rows, about 10 plus the connected wallet, all started within 12 s at the pace (SC-002: named within 15 s);
- rows never on screen: no lookup at all (FR-012).

From the cache, every name shows at once. No screen waits on a lookup.

**Constraints**:
- **Read-only:** the lookup signs and sends nothing (FR-009).
- **Pacing:** at most 8 owner searches per 10 s (one every 1.2 s), and a 15 s back-off after HTTP 429.
- **Lazy:** only the connected wallet and the rows on screen are looked up (FR-012).
- **Endpoints:** the names endpoint is separate from the devnet endpoint (R5).
- **Display only:** names never reach ordering, ranking, labels or money (FR-008).

**Scale/Scope**:
- **Code:** 3 call sites, 1 new composable, 1 core file and 1 app repository file, plus tests, 1 weaknesses entry and 1 design-system wording change.
- **Data:** lists up to 1,000 members (001 FR-011a); the cache holds up to 5,000 entries.

No `NEEDS CLARIFICATION` items remain; all are resolved in [research.md](research.md). The SC-002 amendment research R4 called for was made through `/speckit-clarify` (spec Clarifications, Session 2026-10-09): names load lazily (FR-012), and rows on screen are named within 15 s.

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
| **XI. Weaknesses documented** | Anything outside the stated setup is named | PASS, **action required** | One weaknesses entry for the mainnet reads — the Seeker ID names (FR-010, contracts §4) and, since 001 R17, the Seeker Genesis Token check, which the README's row names beside them, in `docs/weaknesses.json` and rendered to the README. Since spec 004 the app shows no weaknesses screen. A keyed endpoint, if configured, is named the same way (R4) |
| **XII. The build demonstrates itself** | Live, populated, labelled crowd | PASS | Scripted members keep their label (FR-011). The fallback looks deliberate (FR-003), so a crowd of addresses still reads as a crowd |
| **XIII. Honest language** | No money on boards; honest wording | PASS | No amounts added. Rows show only an identity |
| **Hackathon constraints: devnet only** | The program and all value on devnet | PASS, **justified** | The program, every transaction and every token stay on devnet. The mainnet contact is two reads — public name records (this spec) and, since 2026-10-10, the wallet's Seeker Genesis Token before a join (001 FR-003b, R17) — each through a read-only client that cannot send (R5; `SeekerGenesisReader`). See Complexity Tracking |
| **Hackathon constraints: leaderboards on chain only** | No server or device figure feeds a ranking | PASS | Rankings are unchanged and computed before names are attached (FR-008) |
| **Development workflow** | Stack in the plan; spec amended through the skills | PASS, **follow-up** | Amendments went through the skills, all done 2026-10-09: 001 FR-063 and FR-062 via `/speckit-clarify` on 001 (`9973a19`); 001 `contracts/android-app.md` §2 egress via `/speckit-plan` on 001 (`ec25e91`); SC-002 wording via `/speckit-clarify` on 003. The one follow-up is the Roster row wording in the claude.ai design system artifact, which has no local mirror (tasks T022) |

**Gate result: PASS.** The only item outside the plain reading of a constraint is the read-only mainnet contact (the names, and the Genesis Token check 001 added), justified below and disclosed (XI).

## Project Structure

### Documentation (this feature)

```text
specs/003-seeker-id-names/
├── spec.md
├── plan.md               # This file
├── research.md           # R1–R11: name program, spike timings, pacing, validation, lazy lookups
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
├── SeekerNameReader.kt       # NEW — read-only mainnet client: owner search + batched reads (R5)
└── SeekerGenesisReader.kt    # added by 001 R17 — read-only mainnet client: Token-2022 accounts + mint reads

kotlin/core/src/test/kotlin/holdout/core/
├── SeekerNamesTest.kt        # NEW — R1 constants, request JSON, parsing, R3/R6/R9
├── SeekerNameReaderTest.kt   # NEW — the two requests and the Lookup results, with a fake driver
└── SeekerGenesisReaderTest.kt # added by 001 R17

kotlin/app/src/main/kotlin/holdout/app/
├── names/
│   ├── LookupQueue.kt            # NEW — pure two-priority paced queue; drops rows that leave the screen
│   └── SeekerNameRepository.kt   # NEW — worker over the queue, MMKV cache, StateFlow of names
├── storage/Store.kt              # + kept name entries (data-model §2)
├── ui/Components.kt              # + Format.identity(...), LocalSeekerNames and the MemberIdentity composable (R8)
├── ui/ShownWallets.kt            # NEW — ReportShownWallets: visible rows → showing(), empty when stopped or gone (R11)
├── roster/RosterScreen.kt        # Format.short → MemberIdentity; rosterRowWallet(key)
├── leaderboard/LeaderboardScreen.kt  # Format.short → MemberIdentity; leaderboardRowWallet(key) drops the r/n prefix
├── about/AboutScreen.kt          # the "test network" line names the mainnet reads (FR-009, FR-010; both since 001 R17)
├── MainViewModel.kt              # comment only: no value touches mainnet; the names read and the Seeker Genesis Token check do (SeekerNameReader, SeekerGenesisReader)
└── HoldoutApp.kt                 # the connected-wallet line → MemberIdentity; one repository per process, provided
                                  # through LocalSeekerNames; connected(address) on every change, null on disconnect

kotlin/app/src/debug/res/xml/network_security_config.xml  # cleartext on the base config, never per domain:
                                  # a <domain-config> makes Ktor CIO fail every HTTPS call in a debug build

kotlin/app/src/test/kotlin/holdout/app/names/
├── LookupQueueTest.kt            # NEW — pace, priority, dropping off-screen rows, disconnect, back-off, SC-002 budget
├── SeekerNameRepositoryTest.kt   # NEW — freshness, re-check on connect, fallback, no lookup off screen, scripted, eviction
├── MemberIdentityTextTest.kt     # NEW — Format.identity, and the debug network config's base-config rule
└── ShownRowWalletTest.kt         # NEW — row keys → wallets for the roster and the leaderboard

kotlin/app/build.gradle.kts           # + NAMES_RPC_URL from holdout.namesRpcUrl, in defaultConfig (both flavours)
docs/weaknesses.json                  # + one entry; README re-rendered (spec 004 moved it here)
README.md                             # the opening "devnet only" line names the mainnet reads (two since 001 R17)
```

**Structure Decision**: The pure, network-free logic goes in `kotlin/core`, next to `Pdas.kt` and `Rpc.kt`, where every rule in this codebase is unit-tested on the JVM. The read-only network clients also go there, beside `Rpc` — `SeekerNameReader` for the names and, since 001 R17, `SeekerGenesisReader` for the Seeker Genesis Token check — so the drivers sit side by side and a review sees that only `Rpc` can send. The cache, pacing and Compose pieces go in `kotlin/app`, beside the screens that use them. Program, crank and tests under `tests/` are untouched.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A read from **Solana mainnet** in a "devnet only" build | Seeker IDs exist only on mainnet; there is no devnet copy to read | Not showing names defeats the feature. A server that mirrors names breaks "no server" (FR-009, 001 constraints). The read is public, signs nothing, and goes through a client that cannot send (R5). It is disclosed under XI |
| A **second RPC client** (`SeekerNameReader`) beside `Rpc` | Keeps `Rpc`'s guarantee that no path can reach mainnet with a send | A second `Rpc` instance on mainnet would expose `send`/`simulate` to a mainnet endpoint |
| A **third RPC client** (`SeekerGenesisReader`, since 001 R17) beside `Rpc` and `SeekerNameReader` | The Seeker Genesis Token exists only on mainnet, so the member app checks it there before offering a join (001 FR-003b). It reads the wallet's Token-2022 accounts (`getTokenAccountsByOwner`) and their mints (`getMultipleAccounts`), sends only the wallet's already-public address, and like `SeekerNameReader` can neither sign nor send | Reusing `SeekerNameReader` mixes a name lookup with a gate on every join, and its answers (found, none, unknown) differ. Reusing `Rpc` would let a send reach mainnet. Disclosed in the same weaknesses row (XI) |
