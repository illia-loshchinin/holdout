# Research: Seeker ID Names

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-10-09

Each decision below resolves a question the plan's Technical Context raised. Program IDs, offsets and the hash prefix were read from the published `@onsol/tldparser` 1.2.1 package source (`dist/cjs/svm`), the client the Solana Mobile docs point to. They were then confirmed by a spike against Solana mainnet on 2026-10-09 (R2, R4).

---

## R1 · Where Seeker ID names live and how they are found

**Decision**: Read `.skr` names straight from the AllDomains name program on Solana **mainnet**, with two RPC calls per wallet.

1. **Owner search**: `getProgramAccounts` on the ANS program, filtered on owner = the wallet (offset 40) and parent = the `.skr` TLD's name account (offset 8), with a zero-length `dataSlice`. It returns the wallet's `.skr` name accounts.
2. **Reverse records**: one `getMultipleAccounts` for the reverse-lookup record of each name account found. The name's text sits after the 200-byte header.

| Constant | Value | How it is known |
|---|---|---|
| ANS program | `ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK` | tldparser `constants.js` |
| TLD house program | `TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S` | tldparser `constants.js` |
| Hash prefix | `ALT Name Service` | `NameRecordHeader.HASH_PREFIX` |
| Origin name account | `3mX9b4AZaQehNoQGfckVcmgmA6bkBoFcbLj9RMmMyNcU` | PDA `[sha256(prefix+"ANS"), 0³², 0³²]` under ANS. Derived in the spike, equal to tldparser's `ROOT_ANS_PUBLIC_KEY` |
| `.skr` parent name account | `F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF` | PDA `[sha256(prefix+".skr"), 0³², origin]` under ANS (spike) |
| `.skr` TLD house | `4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA` | PDA `["tld_house", ".skr"]` under the TLD house program (spike) |
| Reverse record of a name account `N` | PDA `[sha256(prefix + base58(N)), tldHouse, 0³²]` under ANS | `performReverseLookupBatched` |
| Name record header | 200 bytes: discriminator 8 · parent 32 · owner 32 · class 32 · expiresAt u64 · createdAt u64 · nonTransferable u8 · padding 79 | `NameRecordHeader.schema` |

**Rationale**: It is the same lookup the official client does (`getParsedAllUserDomainsFromTld`), reduced to the two calls a `.skr` name needs. The spike resolved `alice.skr` to its owner and back from the owner to `alice.skr` with exactly these two calls.

**Alternatives considered**:
- **The main-domain record** (PDA `["main_domain", wallet]`, batched 100 wallets per call): far cheaper, but it exists only when the owner has chosen a primary name. The `alice.skr` owner has none, so it cannot tell "no Seeker ID" from "no primary name set". It is kept out of this build.
- **The JavaScript client** in a WebView or a JS engine: a second runtime and a large dependency for two RPC calls.
- **A backend proxy** (Solana Mobile's recommendation, for keys and shared caching): ruled out. The app has no server (001 plan constraints) and the spec forbids one (FR-009).
- **Listing every `.skr` name once** (parent filter only): hundreds of thousands of accounts. Too large for a phone and refused by public nodes.

## R2 · Are `.skr` names wrapped as NFTs or transferable?

**Decision**: Treat a `.skr` name's owner field as the holder. The NFT-wrapped path (owner = an NFT record, holder = the mint's owner) is not followed.

**Rationale**: The spike read `alice.skr`'s header: `nonTransferable = 1`, `expiresAt = 0` (never expires), and an owner that is a plain wallet. Solana Mobile describes a Seeker ID as minted once per device and movable only between the owner's own Seed Vault accounts, which matches a non-transferable record whose owner field is updated in place.

**Alternatives considered**: tldparser's NFT path. It needs the wallet's token accounts and metadata (several more calls per wallet) for a case `.skr` does not use. If a wrapped `.skr` ever appears, it simply shows as "no Seeker ID" (FR-006).

## R3 · Which name when a wallet holds several (FR-005)

**Decision**: Keep the names whose text is valid (R6), compare them by their text lower-cased (Unicode code-point order), and show the first.

**Rationale**: It is deterministic on every phone and every session, which FR-005 asks for. tldparser sorts with `localeCompare`, which depends on the locale. Every Seeker gets one name, so several names on one wallet is rare (an owner with two Seekers who moved both names to one wallet).

## R4 · How fast the public mainnet RPC answers, and what that means for SC-002

**Decision**: All lookups go through **one paced queue**: at most one owner search every 1.2 seconds (8 per 10 seconds). The queue serves the connected wallet first, then rows on screen, then the rest of the open list. On HTTP 429 it waits 15 seconds before the next search. Reverse records are batched up to 100 per call outside that pace. The mainnet endpoint is a build setting defaulting to `https://api.mainnet-beta.solana.com`, so a keyed endpoint can be used without code changes (R5).

**Measured on 2026-10-09** from this machine against `api.mainnet-beta.solana.com`:

| Test | Result |
|---|---|
| Owner search, single call | 0.08–0.67 s (median ≈ 0.17 s) |
| 30 owner searches, 4 at a time | the first 10 answered, the next 20 refused with HTTP 429 |
| 16 owner searches, one every 0.5 s | 10 answered, then 429 |
| 16 owner searches, one every 1 s | all 16 answered |
| 12 × `getMultipleAccounts` of 100 addresses, back to back | all answered, 1.3 s in total |

**Consequence for the spec**: The public node allows about **10 owner searches per 10 seconds per device**. From a cold cache:

| Who | Time to a name |
|---|---|
| The connected wallet | about 1 s |
| The 8–10 rows on screen | about 10 s |
| A 30-member Holdout (the scripted floor is 20 per 7-day Holdout) | about 40 s |
| A **100-member** list | about **2 minutes** |

After that, names come from the phone's cache at once (FR-007).

SC-002 asks for every name in a 100-member list within 10 seconds. That **cannot be met on the public node from a cold cache**. It can be met only with a keyed RPC provider, whose key would ship in the APK (a disclosed trust gap, like the faucet sponsor key in 001 R13). **Recommended spec amendment** (through `/speckit-clarify`, before `/speckit-tasks`): SC-002 should say "rows on screen within 10 seconds; a whole 100-member list within 2 minutes the first time, and at once afterwards". This plan is built to that wording. Nothing else in the spec depends on the endpoint.

**Alternatives considered**:
- **Unpaced parallel searches**: they trip the 429 limit within a second. The node then refuses everything, including the connected wallet's own search.
- **A keyed provider by default**: its key is extractable from the APK, and its free tier is shared by every install. It stays available as a build setting, not the default.

## R5 · Keeping "devnet only" true

**Decision**: The name read gets its **own read-only client**, `SeekerNameReader`, built on the same `rpc-core` driver as `Rpc` but exposing only `getProgramAccounts` and `getMultipleAccounts` against the names endpoint. `Rpc` (devnet, the only class that can send a transaction) is unchanged, so its rule "no code path here could touch mainnet" stays true. The names endpoint is a separate build field, `NAMES_RPC_URL`, from the Gradle property `holdout.namesRpcUrl`, defaulting to the public mainnet node.

**Rationale**: The constitution's "Devnet only, with a mock SKR mint" is about where the program runs and where value moves. A read of public name records moves nothing and signs nothing (FR-009). Giving it a type that cannot send makes that checkable in code review, not just in prose. Constitution XI still applies, so the read is named in the weaknesses list (FR-010).

**Alternatives considered**: a second `Rpc(endpoint = MAINNET)` instance. It reuses more code, but it would put `send` and `simulate` within reach of a mainnet endpoint, and the class's own comment says that never happens.

## R6 · What counts as a valid name to show

**Decision**: A reverse record's text is shown only if all of these hold:
- the bytes after the header are valid UTF-8, up to the first zero byte;
- it is 1–63 characters long;
- it contains no control characters (Unicode `Cc`) and no format characters (`Cf`, which include the bidirectional overrides and zero-width characters);
- it doesn't already end in `.skr`, which the app appends.

Otherwise the wallet counts as having no Seeker ID (FR-006).

**Rationale**: A bidirectional override can make a name render as different text, for example `alice.skr` drawn from other bytes. Refusing control and format characters closes that, while keeping the spec's edge case: letters from any script are shown as written. Names never identify anyone for ranking or money (FR-008), so the remaining look-alike risk is cosmetic.

## R7 · Where kept names live (FR-007)

**Decision**: In MMKV, the store the app already uses (`storage/Store.kt`). There is one entry per wallet under `seekerName:<base58>`, holding `name|none` and `checkedAtMillis`.
- **Freshness**: an entry older than 24 hours is re-checked when the wallet next appears. The connected wallet is re-checked on every connect.
- **Size**: the cache holds at most 5,000 entries; when full, the oldest check goes first.
- **Clock**: freshness uses the device's wall clock. Unlike every deadline in 001, it decides nothing, so it doesn't need chain time (001 R7).

**Rationale**: No new storage dependency. 5,000 entries cover every member a judge could meet in the live period at under 0.5 MB.

## R8 · How screens show an identity without jumping (FR-003, FR-006)

**Decision**: One shared composable, `MemberIdentity(wallet, …)`, renders `Format.identity(wallet, name)`. It reserves the width of the longer of the two forms (a truncated name fits within it, R9), uses `AmountStyle` (JetBrains Mono, the address style today) in the same colour, and crossfades in 200 ms when the name arrives. Screens call it wherever they call `Format.short` today:
- `RosterScreen.kt:67`;
- `LeaderboardScreen.kt:88`;
- the connected-wallet line at `HoldoutApp.kt:750`.

**Rationale**: It is one place to keep FR-003's "looks like the same kind of row" true, and the design book's 200 ms standard motion.

## R9 · Truncating a long name (FR-004)

**Decision**: Fit the name to a character budget given by the caller: 20 characters in a roster or leaderboard row, 28 on the connected-wallet line. If the label part doesn't fit, keep its first `budget − 5` characters, then `…`, then `.skr`. `Format.short` stays as it is for the fallback.

**Rationale**: Character budgets keep the logic pure and unit-testable in `core`. The mono face makes characters a good proxy for width.

## R10 · Testing without depending on mainnet

**Decision**: Three layers, none of which needs a live mainnet node to pass:
- **`core` unit tests** (JUnit 5):
  - the derived constants equal the R1 table;
  - the owner-search request JSON is exact;
  - reverse-record parsing works on synthetic bytes;
  - R3 ordering, R6 validation and R9 truncation.
- **`app` JVM tests**: the paced queue and cache with a fake reader:
  - pace and priority order, and the 429 back-off;
  - the 24-hour re-check, and every connect re-checking the connected wallet;
  - a failure leaves the address.
- **One manual device check** (quickstart): it uses a Seeker with a Seeker ID connected to the devnet build.

The spike script stays out of the repository. Its numbers are recorded in R4.
