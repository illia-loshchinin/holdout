# Data Model: Seeker ID Names

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)

Nothing here is written to any chain. §1 is read from mainnet; §2 and §3 live on the phone; §4 is in memory only.

## 1. Seeker ID (read from mainnet, never written)

The AllDomains name program's records, as far as this feature reads them (research R1). All addresses are under the ANS program `ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK`.

### 1.1 Name record header (200 bytes, every ANS account)

| Offset | Size | Field | Used for |
|---|---|---|---|
| 0 | 8 | discriminator | not read |
| 8 | 32 | `parentName` | owner-search filter: must equal the `.skr` parent `F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF` |
| 40 | 32 | `owner` | owner-search filter: must equal the wallet |
| 72 | 32 | `class` | not read |
| 104 | 8 | `expiresAt` (u64 LE, seconds) | not read; `.skr` names have 0 (never expire, research R2) |
| 112 | 8 | `createdAt` | not read |
| 120 | 1 | `nonTransferable` | not read; 1 on `.skr` (R2) |
| 121 | 79 | padding | — |
| 200 | … | data | in a **reverse record**: the name's text, UTF-8, up to the first zero byte |

### 1.2 Accounts touched

| Account | Address | Read with |
|---|---|---|
| A wallet's `.skr` **name accounts** | found, not derived: owner search (contracts §1.1) | `getProgramAccounts`, `dataSlice` length 0 |
| The **reverse record** of name account `N` | PDA `[sha256("ALT Name Service" + base58(N)), tldHouse(.skr), 32 zero bytes]` | `getMultipleAccounts`, up to 100 per call (contracts §1.2) |

`tldHouse(.skr)` = `4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA`, the PDA `["tld_house", ".skr"]` under `TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S`.

### 1.3 Validation (research R6)

A reverse record's text becomes a **candidate name** only if all of these hold:
- it is valid UTF-8 up to the first zero byte;
- it is 1–63 characters long;
- it has no character in Unicode categories `Cc` or `Cf`;
- it doesn't already end in `.skr`.

A wallet's **chosen name** is the first candidate in code-point order of the lower-cased text, followed by `.skr` (R3). No candidate means the wallet has no Seeker ID.

## 2. Kept name entry (device, MMKV)

One per wallet, key `seekerName:<wallet base58>`.

| Field | Type | Rule |
|---|---|---|
| `name` | string or absent | The chosen name (`alice.skr`), or absent for "checked, has none" |
| `checkedAtMillis` | long | Device wall clock at the last **answered** lookup. A failed lookup writes nothing (FR-006) |

**Freshness (FR-007)**:
- an entry younger than 24 hours is used without asking again;
- an older one is still shown and is re-checked when the wallet next appears;
- the connected wallet is re-checked on every connect, whatever its age.

**Bound**: at most 5,000 entries. When a new entry would exceed it, the entry with the oldest `checkedAtMillis` is removed (R7).

**Never stored**: anything about a lookup that failed, the name accounts' addresses, or any other wallet data.

## 3. Shown identity (derived, never stored)

`identity(wallet, keptEntry?, budget)`:

| Kept entry | Shown | Example |
|---|---|---|
| has `name` | the name, truncated to `budget` (R9) | `alice.skr`, `averyveryverylo….skr` |
| absent, or `name` absent | `Format.short(wallet)` (unchanged) | `7xKX…9fQ2` |

Followed by ` · you` when `wallet` is the connected wallet. That marker is driven by the wallet and never by the name (FR-008).

| Place | Budget (characters) |
|---|---|
| Roster row, leaderboard row | 20 |
| Connected-wallet line | 28 |

## 4. Lookup queue (memory only)

One per app process, owned by `SeekerNameRepository`.

| Field | Rule |
|---|---|
| Pending wallets | A set, so a wallet is never searched twice at once |
| Priority | 0 = the connected wallet; 1 = rows currently on screen; 2 = the rest of the open list. A wallet keeps its highest priority |
| Pace | At most one owner search per 1,200 ms |
| Back-off | After HTTP 429 or a timeout, no owner search for 15 s; the wallet goes back to the queue |
| Reverse reads | Batched up to 100 name accounts and sent as soon as owner searches find any; not paced (R4) |

**States of a wallet's lookup**: not kept → queued → searching → answered (entry written: name or none) | failed (nothing written, re-queued at most twice per session, then left as the address until the next session).
