---

description: "Task list for 003 Seeker ID names"
---

# Tasks: Seeker ID Names

**Input**: Design documents from `specs/003-seeker-id-names/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/seeker-names.md](contracts/seeker-names.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution VII (tests precede logic) and plan research R10 call for them. Each test task comes before the code it covers and must fail first.

**Organization**: Grouped by user story (spec: US1 and US2 are P1, US3 is P2) so each can be built and checked on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: The user story the task belongs to (US1, US2, US3)
- Paths are from the repository root. Gradle commands run from `kotlin/`

---

## Phase 1: Setup

**Purpose**: The one build setting the feature needs

- [ ] T001 Add `buildConfigField("String", "NAMES_RPC_URL", …)` beside `RPC_URL` in `kotlin/app/build.gradle.kts`. It reads the Gradle property `holdout.namesRpcUrl` and defaults to `https://api.mainnet-beta.solana.com` (research R5, contracts §1), and the code comment says it is used only for read-only Seeker ID lookups.

---

## Phase 2: Foundational (blocking all three stories)

**Purpose**: The lookup, the cache and the shared identity line. No screen changes until this phase is done.

### Tests first (must fail before T005–T010)

- [ ] T002 [P] Write `kotlin/core/src/test/kotlin/holdout/core/SeekerNamesTest.kt` (JUnit 5), covering:
  - **Constants:** derive from first principles and compare with the research R1 table. The origin is `3mX9b4AZaQehNoQGfckVcmgmA6bkBoFcbLj9RMmMyNcU` (PDA `[sha256("ALT Name Service"+"ANS"), 0³², 0³²]` under `ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK`). The `.skr` parent is `F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF` and the `.skr` TLD house is `4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA` (PDA `["tld_house", ".skr"]` under `TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S`).
  - **Request:** `ownerSearchParams(wallet)` equals the contracts §1.1 `params` exactly.
  - **Reverse record:** `reverseRecordAddress(nameAccount)` = PDA `[sha256("ALT Name Service" + base58(nameAccount)), tldHouse(.skr), 32 zero bytes]`.
  - **Validation:** `nameFromReverseRecord(data)` takes the text after byte 200, up to the first zero byte. It returns null unless the text is "valid UTF-8", "1–63 characters", has "no character in Unicode categories Cc or Cf" (test a U+202E bidirectional override and a U+200B zero-width space), and does not already end in `.skr`.
  - **Choice:** `choose(listOf("zed","Alice","bob"))` = `"Alice.skr"`, by code-point order of the lower-cased text (R3); an empty list gives null.
  - **Truncation:** `fit(name, budget)` returns a name within budget unchanged. Otherwise it keeps the first `budget − 5` characters of the label, then `…`, then `.skr`, for budgets 20 and 28 (R9).
- [ ] T003 [P] Write `kotlin/core/src/test/kotlin/holdout/core/SeekerNameReaderTest.kt` with a scripted `FakeHttp : HttpNetworkDriver`, as `RpcTest.kt` does. Cases:
  - `nameOf(wallet)` sends `getProgramAccounts` with the contracts §1.1 body, then one `getMultipleAccounts` of the reverse-record addresses.
  - It returns `Lookup.Found("alice.skr")`, `Lookup.None` for `result: []`, and `Lookup.Unknown(rateLimited = true)` on HTTP 429.
  - It returns `Lookup.Unknown(rateLimited = false)` on a JSON-RPC error, an `IOException` or a 10 s timeout.
  - The class has no public member other than `nameOf` (reflection, as `StreakStoreTest` checks its API).
- [ ] T004 [P] Write `kotlin/app/src/test/kotlin/holdout/app/names/LookupQueueTest.kt` (JUnit 4, as the other app tests are), driving a pure `LookupQueue` with an explicit clock in milliseconds (data-model §4):
  - "at most one owner search per 1,200 ms";
  - priority 0 (connected) before 1 (on screen) before 2 (rest), and a wallet keeps its highest priority;
  - a wallet already pending is not queued twice;
  - after a rate-limited or timed-out result, no search for 15 s and the wallet goes back to the queue;
  - a wallet that has failed twice in a session is not queued again in that session.

### Implementation

- [ ] T005 [P] Implement `kotlin/core/src/main/kotlin/holdout/core/SeekerNames.kt` until T002 passes:
  - the constants `ANS_PROGRAM`, `TLD_HOUSE_PROGRAM`, `SKR_PARENT` and `SKR_TLD_HOUSE`;
  - `ownerSearchParams`, `reverseRecordAddress`, `nameFromReverseRecord`, `choose` and `fit` (contracts §3.1);
  - SHA-256 via `java.security.MessageDigest`, and PDAs via `ProgramDerivedAddress.find`, as `Pdas.kt` does.

  Its KDoc names the source (`@onsol/tldparser` 1.2.1) and the 2026-10-09 mainnet spike (research R1, R2).
- [ ] T006 Implement `kotlin/core/src/main/kotlin/holdout/core/SeekerNameReader.kt` until T003 passes (depends on T005):
  - a read-only client over its own `Rpc20Driver(endpoint, KtorNetworkDriver())`;
  - one public `suspend fun nameOf(wallet): Lookup`;
  - reverse reads batched "up to 100" per `getMultipleAccounts`;
  - a 10 s timeout.

  Its KDoc states that it can neither sign nor send, and why it is not an `Rpc` (research R5). Leave `Rpc.kt` unchanged.
- [ ] T007 Add kept name entries to the `Store` interface in `kotlin/app/src/main/kotlin/holdout/app/storage/Store.kt`, with both implementations (the MMKV one in the same file, and `InMemoryStore` for tests):
  - **Accessors:** `seekerName(wallet): KeptName?` and `setSeekerName(wallet, KeptName)`, where `KeptName(name: String?, checkedAtMillis: Long)` is stored under the key `seekerName:<wallet base58>` (data-model §2).
  - **Bound:** "at most 5,000 entries". The entry with the oldest `checkedAtMillis` is removed first.
  - **Index:** `allSeekerNames(): Map<String, String>` returns only the entries that have a name.
- [ ] T008 Implement `kotlin/app/src/main/kotlin/holdout/app/names/LookupQueue.kt` (pure, no Android or coroutine types) until T004 passes.
- [ ] T009 Implement `kotlin/app/src/main/kotlin/holdout/app/names/SeekerNameRepository.kt` (contracts §3.3; depends on T006–T008):
  - **`names: StateFlow<Map<String, String>>`** is seeded from `Store.allSeekerNames()` at construction.
  - **`want(wallets, priority)`** queues only wallets with no entry, or whose entry is older than "24 hours".
  - **`connected(wallet)`** always queues at priority 0.
  - **Worker:** one coroutine on `Dispatchers.IO` pulls from `LookupQueue` and calls `SeekerNameReader.nameOf`.
  - **Writes:** a `KeptName` is written only for `Found` or `None`, never for `Unknown` (FR-006), and the flow is then updated.
  - **Clock:** wall-clock time from an injectable `() -> Long`.

  Also add `kotlin/app/src/test/kotlin/holdout/app/names/SeekerNameRepositoryTest.kt`, using a fake reader and `InMemoryStore` with `runBlocking`. It checks:
  - a fresh entry is not looked up again;
  - a 25-hour-old entry is;
  - `connected` re-checks a fresh entry;
  - `Unknown` leaves the store untouched and the address shown.
- [ ] T010 Add `Format.identity(wallet, name: String?, budget: Int): String` and `@Composable MemberIdentity(wallet: String, isYou: Boolean, budget: Int, color: Color, modifier: Modifier)` to `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt` (contracts §3.4, research R8, data-model §3):
  - **Text:** the name through `SeekerNames.fit`, or `Format.short(wallet)` (unchanged) when there is none. Append ` · you` when `isYou`.
  - **Style:** `AmountStyle` in the caller's colour.
  - **Layout:** reserve the width of the longer form, and crossfade 200 ms when a name arrives.
  - **Accessibility:** the semantics `contentDescription` is the shown text.
  - **Previews:** one `@Preview` with a name and one with an address.
  - **Design rules:** follow `.claude/skills/holdout-ui` (theme tokens only; no literal colours, `.sp` or spacing `.dp`).

**Checkpoint**: `./gradlew :core:test :app:testDebugUnitTest` passes; no screen is changed yet.

---

## Phase 3: User Story 1 — See yourself by your Seeker ID (P1) 🎯 MVP

**Goal**: The connected wallet is shown by its Seeker ID, or its shortened address, wherever the app names it (FR-001).

**Independent Test**: Quickstart §4 rows 1–3: connect a wallet with a Seeker ID, then reopen offline, then connect one without.

- [ ] T011 [US1] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, wire up the repository:
  - create one `SeekerNameRepository` for the app's lifetime, alongside `ChainRepository`, with `SeekerNameReader(BuildConfig.NAMES_RPC_URL)` and the app's `Store`;
  - call `connected(wallet)` whenever a wallet connects or the saved session resumes.
- [ ] T012 [US1] Replace `Text("Disconnect ${Format.short(wallet)}", …)` at `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt:750` with the word "Disconnect" followed by `MemberIdentity(wallet, isYou = false, budget = 28, color = HoldoutColors.TextTertiary)`. The tap target, its size and its colour stay as they are.

**Checkpoint**: US1 works on a device (quickstart §4 rows 1–3); rosters and leaderboards still show addresses.

---

## Phase 4: User Story 2 — See the crowd by name (P1)

**Goal**: Rosters and leaderboards show each member's Seeker ID or shortened address. Order and the "you" and scripted markers don't change (FR-002, FR-008, FR-011).

**Independent Test**: Quickstart §4 rows 4–6, and the ordering test in T013.

- [ ] T013 [P] [US2] Add a test to `kotlin/app/src/test/kotlin/holdout/app/LeaderboardTextTest.kt`: given rows whose wallets do and don't have kept names, the row order, the ranks, the scripted label and the "you" marker are identical to the result with no names at all (FR-008, SC-003). Names are attached only after ordering.
- [ ] T014 [US2] In `kotlin/app/src/main/kotlin/holdout/app/roster/RosterScreen.kt`:
  - replace `Format.short(address) + if (address == me) " · you" else ""` (line 67) with `MemberIdentity(address, isYou = address == me, budget = 20, …)`, keeping the current colour for standing and forfeited rows;
  - from the `LazyColumn` state, call `want(visible wallets, priority 1)` as the visible items change, and `want(all roster wallets, priority 2)` once per roster load;
  - leave the scripted label and the ordering exactly as they are.
- [ ] T015 [US2] In `kotlin/app/src/main/kotlin/holdout/app/leaderboard/LeaderboardScreen.kt`, do the same for the row text at line 88:
  - keep `"$place "` in front;
  - show `MemberIdentity(address, isYou = r.wallet == me, budget = 20, …)`;
  - request names the same way as T014;
  - keep no amounts on the board (constitution XIII).

**Checkpoint**: US1 and US2 both work; a crowd with no Seeker IDs looks exactly as before.

---

## Phase 5: User Story 3 — Judges are told where names come from (P2)

**Goal**: One known-weaknesses entry, shown in About and in the README (FR-010).

**Independent Test**: Quickstart §4 row 7; the README row matches the JSON word for word.

- [ ] T016 [US3] Add the contracts §4 entry, verbatim (`what`, `why`, `fix`), to the `weaknesses` array in `kotlin/app/src/main/assets/weaknesses.json`, after the "Devnet with a mock SKR mint" entry.
- [ ] T017 [US3] Run `node scripts/render-weaknesses.mjs` from the repository root and commit the regenerated table in `README.md`. Check that About · for judges shows the entry (`kotlin/app/src/main/kotlin/holdout/app/about/AboutScreen.kt` reads the same JSON, so no code change is expected).

---

## Phase 6: Polish and cross-cutting

- [ ] T018 Run the quickstart §2–§3 checks:
  - `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugKotlin :app:ktlintCheck`;
  - `python scripts/design-book.py check --report-only` (no new drift or raw colours);
  - `bash scripts/check-wording.sh`.

  Fix what they report.
- [ ] T019 Change the design system artifact's Roster row wording from "by shortened address only" to "by Seeker ID, or shortened address when there is none" (the artifact at `docs/design/SOURCE.md`'s link). Then sync `docs/design/` with `.claude/skills/holdout-ui/references/sync.md`. **Product owner approval needed:** it edits the shared design system.
- [ ] T020 Amend 001 FR-063 through `/speckit-clarify` on `specs/001-holdout-hackathon`: leaderboards identify members by Seeker ID or shortened address, with no amounts (spec 003 FR-002).
- [ ] T021 Amend `specs/001-holdout-hackathon/contracts/android-app.md` §2 (network egress) through `/speckit-plan` on 001: add the names endpoint row from 003 contracts §2.
- [ ] T022 Run quickstart §4 on a Seeker with a Seeker ID connected to the devnet build, rows 1–7, and record the result in the PR. **Needs the product owner's device.**

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (T001)**: none.
- **Foundational (T002–T010)**: T001 for T009's endpoint. The tests T002–T004 come before their implementations: T002→T005, T003→T006, T004→T008. T006 needs T005. T009 needs T006, T007 and T008. T010 needs T005 (for `fit`) and T009 (for the flow).
- **US1 (T011–T012)**: all of Phase 2.
- **US2 (T013–T015)**: all of Phase 2 and T011 (the repository instance); independent of T012.
- **US3 (T016–T017)**: none of the code. It can start any time.
- **Polish**: T018 after T012–T017. T019–T021 are documentation through their own processes and can run any time. T022 comes last.

### Within each story

The tests are written first and fail, then the implementation makes them pass. Screen tasks touch one file each.

## Parallel Opportunities

- **Tests:** T002, T003 and T004 are three new test files with no shared code.
- **Implementation:** T005 (core) and T007 (Store) touch different modules.
- **Screens:** T013 in parallel with T014/T015, as a test file against screen files. T014 and T015 are different files, so both can run once T011 is done.
- **Text and docs:** T016–T017 and T019–T021 at any time, beside the code.

```text
# Phase 2, three people:
A: T002 → T005 → T006
B: T004 → T008
C: T007
then together: T009 → T010
# After T011:
A: T014    B: T015    C: T013, T016–T017
```

## Implementation Strategy

### MVP: User Story 1

Phases 1–3 (T001–T012): the connected member sees their own Seeker ID. This proves the mainnet read, pacing and cache on a real Seeker with one row, and it can ship alone. Rosters keep addresses until US2 lands.

### Incremental delivery

1. Phases 1–2: the lookup and the cache are tested on the JVM, and nothing visible changes.
2. US1: own identity, checked on the device (quickstart §4 rows 1–3).
3. US2: rosters and leaderboards (rows 4–6), where judges see the crowd.
4. US3: the disclosure (row 7). It is small, but constitution XI makes it required before submission.
5. Polish: checks, the design book and the 001 amendments.

### Fit with the 13 Oct submission

All 22 tasks are client-only and touch no program or crank. T001–T018 fit in about half a day. T019–T021 are documentation through their own skills. T022 needs the Seeker. The work runs beside the devnet deploy (Road to devnet, Track A) and doesn't block it.
