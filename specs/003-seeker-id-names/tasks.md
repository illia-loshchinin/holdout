---

description: "Task list for 003 Seeker ID names"
---

# Tasks: Seeker ID Names

**Input**: Design documents from `specs/003-seeker-id-names/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/seeker-names.md](contracts/seeker-names.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution VII (tests precede logic) and plan research R10 call for them. Each test task comes before the code it covers and must fail first.

**Organization**: Grouped by user story (spec: US1 and US2 are P1, US3 is P2) so each can be built and checked on its own.

**Lazy lookups**: Only the connected wallet and the rows on screen are ever looked up (spec FR-012, research R11, data-model §4, contracts §3.3 `showing`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: The user story the task belongs to (US1, US2, US3)
- Paths are from the repository root, as they are once 004 is merged (T001). Gradle commands run from `kotlin/`

---

## Phase 1: Setup

**Purpose**: The code base this feature builds on, the 001 amendments that bring it into scope (constitution Governance), and the one build setting it needs

- [x] T001 Bring spec 004 into this branch: `origin/004-lab-and-member-apps` (`f10ba1e`) is merged into `003-seeker-id-names` as `0b55419`. These paths now exist: `docs/weaknesses.json`, the `member` and `lab` product flavours in `kotlin/app/build.gradle.kts`, and the slimmed `kotlin/app/src/main/kotlin/holdout/app/about/AboutScreen.kt`. Every later task assumes them. Once 004's pull request lands on `001-holdout-hackathon`, merge that base branch in again before opening 003's pull request.
- [x] T002 Amend 001 FR-063, and FR-062 for rosters, through `/speckit-clarify` on `specs/001-holdout-hackathon`: leaderboards and rosters identify members by Seeker ID or shortened address, for display only and with no amounts (spec 003 FR-002, FR-008). Done in `9973a19` (001 Clarifications, Session 2026-10-09). It comes before any code because the constitution's Governance puts out of scope anything 001's spec does not require.
- [x] T003 Amend `specs/001-holdout-hackathon/contracts/android-app.md` §2 (network egress) through `/speckit-plan` on 001: add the names endpoint row from 003 contracts §2. Done in `ec25e91`, before T004, so 001's egress contract names the mainnet read before any code makes it.
- [ ] T004 Add `buildConfigField("String", "NAMES_RPC_URL", …)` beside `RPC_URL` in `defaultConfig` of `kotlin/app/build.gradle.kts`, so both flavours get it. It reads the Gradle property `holdout.namesRpcUrl` and defaults to `https://api.mainnet-beta.solana.com` (research R5, contracts §1). Its code comment says it is used only for read-only Seeker ID lookups.

---

## Phase 2: Foundational (blocking all three stories)

**Purpose**: The lookup, the cache and the shared identity line. No screen changes until this phase is done.

### Tests first (must fail before T008–T013)

- [ ] T005 [P] Write `kotlin/core/src/test/kotlin/holdout/core/SeekerNamesTest.kt` (JUnit 5), covering:
  - **Constants:** derive them from first principles and compare with the research R1 table.
    - The origin is `3mX9b4AZaQehNoQGfckVcmgmA6bkBoFcbLj9RMmMyNcU`, the PDA `[sha256("ALT Name Service"+"ANS"), 0³², 0³²]` under `ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK`.
    - The `.skr` parent is `F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF`.
    - The `.skr` TLD house is `4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA`, the PDA `["tld_house", ".skr"]` under `TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S`.
  - **Request:** `ownerSearchParams(wallet)` equals the contracts §1.1 `params` exactly.
  - **Reverse record:** `reverseRecordAddress(nameAccount)` = PDA `[sha256("ALT Name Service" + base58(nameAccount)), tldHouse(.skr), 32 zero bytes]`.
  - **Validation:** `nameFromReverseRecord(data)` takes the text after byte 200, up to the first zero byte. It returns null unless the text:
    - is "valid UTF-8";
    - is "1–63 characters";
    - has "no character in Unicode categories Cc or Cf" (test a U+202E bidirectional override and a U+200B zero-width space);
    - does not already end in `.skr`.
  - **Choice:** `choose(listOf("zed","Alice","bob"))` = `"Alice.skr"`, by code-point order of the lower-cased text (R3); an empty list gives null.
  - **Truncation:** `fit(name, budget)` returns a name within budget unchanged. Otherwise it keeps the first `budget − 5` characters of the label, then `…`, then `.skr`, for budgets 20 and 28 (R9).
- [ ] T006 [P] Write `kotlin/core/src/test/kotlin/holdout/core/SeekerNameReaderTest.kt` with a scripted `FakeHttp : HttpNetworkDriver`, as `RpcTest.kt` does. Cases:
  - `nameOf(wallet)` sends `getProgramAccounts` with the contracts §1.1 body, then one `getMultipleAccounts` of the reverse-record addresses.
  - It returns `Lookup.Found("alice.skr")`, `Lookup.None` for `result: []`, and `Lookup.Unknown(rateLimited = true)` on HTTP 429.
  - It returns `Lookup.Unknown(rateLimited = false)` on a JSON-RPC error, an `IOException` or a 10 s timeout.
  - The class has no public member other than `nameOf` (checked by reflection, as `StreakStoreTest` checks its API).
- [ ] T007 [P] Write `kotlin/app/src/test/kotlin/holdout/app/names/LookupQueueTest.kt` (JUnit 4, as the other app tests are). It drives a pure `LookupQueue` with an explicit clock in milliseconds (data-model §4, research R11):
  - "at most one owner search per 1,200 ms";
  - the connected wallet is served before any on-screen wallet;
  - `showing(set)` replaces the on-screen set. A pending wallet that is no longer in the set, and is not the connected wallet, is dropped without being searched (FR-012). It is queued again if a later `showing` includes it;
  - a wallet that is neither connected nor in the on-screen set is never handed out;
  - a wallet already pending is not queued twice;
  - after a rate-limited or timed-out result, no search for 15 s, and the wallet goes back to the queue if it is still on screen;
  - a wallet that has failed twice in a session is not queued again in that session;
  - **SC-002 budget:** with the connected wallet plus 10 on-screen wallets queued at t = 0, and every search answering at once, all 11 have been handed out by t = 12,000 ms (inside the 15 s of SC-002).

### Implementation

- [ ] T008 [P] Implement `kotlin/core/src/main/kotlin/holdout/core/SeekerNames.kt` until T005 passes:
  - the constants `ANS_PROGRAM`, `TLD_HOUSE_PROGRAM`, `SKR_PARENT` and `SKR_TLD_HOUSE`;
  - `ownerSearchParams`, `reverseRecordAddress`, `nameFromReverseRecord`, `choose` and `fit` (contracts §3.1);
  - SHA-256 via `java.security.MessageDigest`, and PDAs via `ProgramDerivedAddress.find`, as `Pdas.kt` does.

  Its KDoc names the source (`@onsol/tldparser` 1.2.1) and the 2026-10-09 mainnet spike (research R1, R2).
- [ ] T009 Implement `kotlin/core/src/main/kotlin/holdout/core/SeekerNameReader.kt` until T006 passes (depends on T008):
  - a read-only client over its own `Rpc20Driver(endpoint, KtorNetworkDriver())`;
  - one public `suspend fun nameOf(wallet): Lookup`;
  - reverse reads batched "up to 100" per `getMultipleAccounts`;
  - a 10 s timeout.

  Its KDoc states that it can neither sign nor send, and why it is not an `Rpc` (research R5). Leave `Rpc.kt` unchanged.
- [ ] T010 Add kept name entries to the `Store` interface in `kotlin/app/src/main/kotlin/holdout/app/storage/Store.kt`, in both implementations: `MmkvStore` and `InMemoryStore` (for tests).
  - **Accessors:** `seekerName(wallet): KeptName?` and `setSeekerName(wallet, KeptName)`. `KeptName(name: String?, checkedAtMillis: Long)` is stored under the key `seekerName:<wallet base58>` (data-model §2).
  - **Bound:** "at most 5,000 entries". The entry with the oldest `checkedAtMillis` is removed first.
  - **Index:** `allSeekerNames(): Map<String, String>` returns only the entries that have a name.
- [ ] T011 Implement `kotlin/app/src/main/kotlin/holdout/app/names/LookupQueue.kt` until T007 passes. It is pure, with no Android or coroutine types. It has two priorities, connected and on screen, and nothing else (FR-012).
- [ ] T012 Implement `kotlin/app/src/main/kotlin/holdout/app/names/SeekerNameRepository.kt` (contracts §3.3; depends on T009–T011):
  - **`names: StateFlow<Map<String, String>>`** is seeded from `Store.allSeekerNames()` at construction.
  - **`showing(wallets: Collection<String>)`** sets the on-screen set and returns at once. It queues only wallets with no entry, or whose entry is older than "24 hours". Wallets that left the screen are dropped from the queue (FR-012).
  - **`connected(wallet)`** always queues at the connected priority.
  - **Worker:** one coroutine on `Dispatchers.IO` pulls from `LookupQueue` and calls `SeekerNameReader.nameOf`.
  - **Writes:** a `KeptName` is written only for `Found` or `None`, never for `Unknown` (FR-006). The flow is then updated.
  - **Clock:** wall-clock time from an injectable `() -> Long`.

  Also add `kotlin/app/src/test/kotlin/holdout/app/names/SeekerNameRepositoryTest.kt`, using a fake reader and `InMemoryStore` with `runBlocking`. It checks:
  - a fresh entry is not looked up again;
  - a 25-hour-old entry is;
  - `connected` re-checks a fresh entry;
  - `Unknown` leaves the store untouched and the address shown;
  - a wallet passed to `showing` and then removed by a second `showing`, before the worker reaches it, is never sent to the reader.
- [ ] T013 Add two members to `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt` (contracts §3.4, research R8, data-model §3): `Format.identity(wallet, name: String?, budget: Int): String` and `@Composable MemberIdentity(wallet: String, isYou: Boolean, budget: Int, color: Color, modifier: Modifier)`.
  - **Text:** the name through `SeekerNames.fit`, or `Format.short(wallet)` (unchanged) when there is none. Append ` · you` when `isYou`.
  - **Style:** `AmountStyle` in the caller's colour.
  - **Layout:** reserve the width of the longer form, and crossfade 200 ms when a name arrives.
  - **Accessibility:** the semantics `contentDescription` is the shown text.
  - **Previews:** one `@Preview` with a name and one with an address.
  - **Design rules:** theme tokens from `kotlin/app/src/main/kotlin/holdout/app/ui/theme/` only; no literal colours, `.sp` or spacing `.dp`.

**Checkpoint**: `./gradlew :core:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest` passes; no screen is changed yet.

---

## Phase 3: User Story 1 — See yourself by your Seeker ID (P1) 🎯 MVP

**Goal**: The connected wallet is shown by its Seeker ID, or its shortened address, wherever the app names it (FR-001).

**Independent Test**: Quickstart §4 rows 1–3: connect a wallet with a Seeker ID, then reopen offline, then connect one without.

- [ ] T014 [US1] Wire up the repository in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`:
  - in `HoldoutApp`, create one `SeekerNameRepository` with `remember`, beside `val store` (≈ line 114). Give it `SeekerNameReader(BuildConfig.NAMES_RPC_URL)` and that `store`;
  - call `connected(wallet)` whenever `address` becomes non-null: after `holdouts.wallet.connect(sender)` succeeds, and for `holdouts.wallet.restored` at start. Use a `LaunchedEffect(address)`.
- [ ] T015 [US1] Replace `Text("Disconnect ${Format.short(wallet)}", …)` in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` (≈ line 692) with the word "Disconnect" followed by `MemberIdentity(wallet, isYou = false, budget = 28, color = HoldoutColors.TextTertiary)`. The tap target, its size and its colour stay as they are.

**Checkpoint**: US1 works on a device (quickstart §4 rows 1–3); rosters and leaderboards still show addresses.

---

## Phase 4: User Story 2 — See the crowd by name (P1)

**Goal**: Rosters and leaderboards show each member's Seeker ID or shortened address, looked up only for rows on screen. Order and the "you" and scripted markers don't change (FR-002, FR-008, FR-011, FR-012).

**Independent Test**: Quickstart §4 rows 4–7, and the ordering test in T016.

- [ ] T016 [P] [US2] Add a test to `kotlin/app/src/test/kotlin/holdout/app/LeaderboardTextTest.kt`: given rows whose wallets do and don't have kept names, the row order, the ranks, the scripted label and the "you" marker are identical to the result with no names at all (FR-008, SC-003). Names are attached only after ordering.
- [ ] T017 [US2] In `kotlin/app/src/main/kotlin/holdout/app/roster/RosterScreen.kt`:
  - replace `Format.short(address) + if (address == me) " · you" else ""` (≈ line 67) with `MemberIdentity(address, isYou = address == me, budget = 20, …)`. Keep the current colour for standing and forfeited rows;
  - give the `LazyColumn` `state = rememberLazyListState()`, because it has none today. Read the visible wallets from that state's `layoutInfo.visibleItemsInfo` through `snapshotFlow`, keeping only keys that are a `String`. Member rows are already keyed by the wallet's base58 (`items(roster, key = { it.member.wallet.base58() })`), and the header items have no key of their own. Call `showing(visible wallets)` each time they change, and `showing(emptyList())` when the screen leaves composition. **Never pass the whole roster** (FR-012, research R11);
  - leave the scripted label and the ordering exactly as they are.
- [ ] T018 [US2] In `kotlin/app/src/main/kotlin/holdout/app/leaderboard/LeaderboardScreen.kt`, make the same change to the row text in `Line` (≈ line 88):
  - keep `"$place "` in front;
  - show `MemberIdentity(address, isYou = r.wallet == me, budget = 20, …)`;
  - report visible wallets the same way as T017, with its own `rememberLazyListState()` passed as the `LazyColumn`'s `state`. Its rows are keyed `"r" + wallet` (ranked, ≈ line 72) and `"n" + wallet` (not ranked, ≈ line 75), so drop each key's first character before passing it to `showing`;
  - keep no amounts on the board (constitution XIII).

**Checkpoint**: US1 and US2 both work; a crowd with no Seeker IDs looks exactly as before.

---

## Phase 5: User Story 3 — The README says where names come from (P2)

**Goal**: One known-weaknesses entry in the README, rendered from its single source (FR-010). No in-app screen.

**Independent Test**: Quickstart §4 row 8: the README's table has the entry, and `node scripts/render-weaknesses.mjs --check` passes.

- [ ] T019 [US3] Add the contracts §4 entry, verbatim (`what`, `why`, `fix`), to the `weaknesses` array in `docs/weaknesses.json`, after the "Devnet with a mock SKR mint" entry.
- [ ] T020 [US3] Run `node scripts/render-weaknesses.mjs` from the repository root and commit the regenerated table in `README.md`. Do not edit the README by hand. No app code changes: `AboutScreen.kt` shows no weaknesses since 004.

---

## Phase 6: Polish and cross-cutting

- [ ] T021 Run the quickstart §2–§3 checks for both flavours, and fix what they report:
  - `./gradlew :core:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest :app:assembleMemberDebug :app:assembleLabDebug ktlintCheck`;
  - `node scripts/render-weaknesses.mjs --check`;
  - `bash scripts/check-wording.sh`.
- [ ] T022 In the Holdout design system artifact (https://claude.ai/artifact/XF5A1KCgH7WY9651C5ZXhs), change the Roster row wording from "by shortened address only" to "by Seeker ID, or shortened address when there is none". **Product owner approval needed:** it edits the shared design system.
- [ ] T023 On a Seeker with a Seeker ID, connected to the devnet `member` build, run quickstart §4 rows 1–8 and record the results in the PR. Row 7 is the lazy-loading check (FR-012, SC-002), and row 8 is the README entry (US3). **Needs the product owner's device.**

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup**:
  - T001, T002 and T003 are done, so 001 covers the feature before any code (constitution Governance).
  - T004 comes next; it edits the flavoured `build.gradle.kts`.
- **Foundational (T005–T013)**: after T003.
  - The tests come before their implementations: T005→T008, T006→T009, T007→T011.
  - T009 needs T008. T012 needs T004 (the endpoint), T009, T010 and T011. T013 needs T008 (for `fit`) and T012 (for the flow).
- **US1 (T014–T015)**: all of Phase 2.
- **US2 (T016–T018)**: all of Phase 2 and T014 (the repository instance); independent of T015.
- **US3 (T019–T020)**: T003 only. It can start at any time after that.
- **Polish**:
  - T021 after T015–T020.
  - T022 is a design-system edit and can run at any time.
  - T023 comes last.

### Within each story

The tests are written first and fail, then the implementation makes them pass. Screen tasks touch one file each.

## Parallel Opportunities

- **Tests:** T005, T006 and T007 are three new test files with no shared code.
- **Implementation:** T008 (core) and T010 (Store) touch different modules.
- **Screens:** T016 can run beside T017/T018, since it is a test file and they are screen files. T017 and T018 are different files, so both can run once T014 is done.
- **Text and docs:** T019–T020 and T022 at any time after T003, beside the code.

```text
# After T003–T004, three people:
A: T005 → T008 → T009
B: T007 → T011
C: T010, T019 → T020
then together: T012 → T013
# After T014:
A: T017    B: T018    C: T016
```

## Implementation Strategy

### MVP: User Story 1

Phases 1–3 (T001–T015): the connected member sees their own Seeker ID. This proves the mainnet read, pacing and cache on a real Seeker with one row, and it can ship alone. Rosters keep addresses until US2 lands.

### Incremental delivery

1. Phase 1: 004 is merged in and 001 is amended (both done), then the endpoint setting is added.
2. Phase 2: the lookup and the cache are tested on the JVM. Nothing visible changes.
3. US1: own identity, checked on the device (quickstart §4 rows 1–3).
4. US2: rosters and leaderboards, looked up only for rows on screen (quickstart §4 rows 4–7).
5. US3: the README disclosure (row 8). It is small, but constitution XI makes it required before submission.
6. Polish: the checks, the design system wording and the device run.

### Fit with the 13 Oct submission

All 23 tasks are client-only and touch no program or crank; three are already done. Lazy loading keeps the queue small: two priorities and no background pass over the list. T003–T021 fit in about half a day. T022 is one wording change in the design system artifact. T023 needs the Seeker. The work runs beside the devnet deploy and doesn't block it.
