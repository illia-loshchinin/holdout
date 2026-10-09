# Quickstart: Seeker ID Names

**Feature**: [spec.md](spec.md) · **Contracts**: [contracts/seeker-names.md](contracts/seeker-names.md)

How to prove the feature works. The build itself is 001's (`specs/001-holdout-hackathon/quickstart.md`); only the steps that differ are here. Commands run from `kotlin/` unless noted.

## 1. Prerequisites

- 001's Android toolchain (JDK 21, Android SDK), and a devnet `member` build of the app that runs. The branch includes spec 004 (flavours, `docs/weaknesses.json`).
- For §4, a **Solana Seeker whose Seed Vault wallet holds a Seeker ID**, and a second wallet without one.
- Optional: a mainnet RPC endpoint of your own, passed as `-Pholdout.namesRpcUrl=<url>`. Without it the public node is used (research R4).

## 2. Unit tests (no network)

```bash
./gradlew :core:test --tests "holdout.core.SeekerNamesTest"
./gradlew :app:testMemberDebugUnitTest :app:testLabDebugUnitTest --tests "holdout.app.names.*"
```

**Expect**: all pass. `SeekerNamesTest` covers:
- the research R1 constants derived from first principles;
- the exact owner-search JSON (contracts §1.1);
- parsing and validation of synthetic reverse records (a bidirectional override and a zero-width character are refused);
- the R3 choice among several names;
- the R9 truncation, which keeps `.skr`.

The queue and repository tests cover:
- one owner search per 1.2 s;
- priority 0 (connected) → 1 (on screen), and nothing else;
- a row that leaves the screen before its search starts is never searched (FR-012);
- the connected wallet and 10 on-screen rows all started by 12 s (SC-002);
- a 15 s pause after a 429;
- no entry written on failure;
- the 24-hour freshness, and the re-check on connect.

## 3. Checks shared with 001

```bash
./gradlew :app:assembleMemberDebug :app:assembleLabDebug ktlintCheck
```

From the repository root:

```bash
bash scripts/check-wording.sh
```

```bash
node scripts/render-weaknesses.mjs --check
```

**Expect**: both flavours build and ktlint passes, the wording check passes, and the README's weaknesses table has the contracts §4 row, matching `docs/weaknesses.json` word for word (SC-005).

## 4. On a Seeker (manual, about 10 minutes)

| # | Do | Expect | Covers |
|---|---|---|---|
| 1 | Install the devnet `member` build, connect the wallet **with** a Seeker ID | Within about 3 s the connected-wallet line changes from the shortened address to `name.skr`, in the same place and style, without a jump | US1-1, US1-3, SC-001 |
| 2 | Turn on airplane mode, force-stop and reopen the app | The name shows at once | US1-4, FR-007 |
| 3 | Disconnect, connect the wallet **without** a Seeker ID | The shortened address shows; no message, spinner or error | US1-2, FR-006 |
| 4 | Open a running Holdout's roster with scripted members and at least one member with a Seeker ID | That member shows `name.skr`; scripted rows show addresses with their scripted label; order is as before; your row ends with "· you" | US2-1, US2-3, FR-011 |
| 5 | Open the league leaderboard | Same identities; the ranking is unchanged and shows no amounts | US2-2, FR-008, SC-003 |
| 6 | Install a build whose names endpoint refuses every lookup (`cd kotlin && ./gradlew :app:installMemberDebug -Pholdout.namesRpcUrl=http://127.0.0.1:9`: nothing listens there). Clear storage (Settings → Apps → Holdout → Clear storage), reconnect online and open the roster | Every row, yours included, shows its shortened address; no error, spinner or retry anywhere. Lookups fail rather than racing a network switch, so the result is the same every run | SC-004, FR-006 |
| 7 | Reinstall the normal build (`./gradlew :app:installMemberDebug`), clear storage, reconnect, open a roster of 30 or more and scroll straight to the bottom | The rows on screen are named within 15 s; the rows you passed stay as addresses until you scroll back to them | FR-012, SC-002 |
| 8 | Open the README on GitHub | The known-weaknesses table has the Seeker ID entry | US3, SC-005 |

## 5. Optional: watch the requests

With a proxy or `adb logcat` on the debug build's network log, sitting on the roster:
- only `getProgramAccounts` and `getMultipleAccounts` go to the names endpoint;
- the owner searches are at least 1.2 s apart (contracts §1, §2);
- nothing goes anywhere else beyond 001's egress (001 T102).
