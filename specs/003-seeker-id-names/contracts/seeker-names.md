# Contract: Seeker ID name lookup

**Feature**: [../spec.md](../spec.md) · **Data model**: [../data-model.md](../data-model.md)

What the app sends, what it accepts back, and the in-code interface the screens use. It is the source of the names endpoint row in `specs/001-holdout-hackathon/contracts/android-app.md` §2 (network egress), added through `/speckit-plan` on 001 (`ec25e91`).

## 1. Requests to the names endpoint

**Endpoint**: `BuildConfig.NAMES_RPC_URL`, from the Gradle property `holdout.namesRpcUrl`. The default is `https://api.mainnet-beta.solana.com`. JSON-RPC 2.0 over HTTPS. **Only the two methods below are ever sent**, by `SeekerNameReader`, which has no other method (research R5).

### 1.1 Owner search: one per wallet, paced (data-model §4)

```json
{"jsonrpc":"2.0","id":1,"method":"getProgramAccounts","params":[
  "ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK",
  {"encoding":"base64","commitment":"confirmed",
   "dataSlice":{"offset":0,"length":0},
   "filters":[
     {"memcmp":{"offset":40,"bytes":"<wallet base58>"}},
     {"memcmp":{"offset":8,"bytes":"F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF"}}]}]}
```

| Answer | Meaning |
|---|---|
| `result: []` | The wallet has no `.skr` name; write a kept entry with no name |
| `result: [{pubkey}, …]` | Name accounts to reverse-read (§1.2) |
| JSON-RPC `error`, HTTP 429, other HTTP error, timeout (10 s) | **Unknown**: write nothing, back off (data-model §4), show the address |

### 1.2 Reverse records: batched, up to 100 per call

```json
{"jsonrpc":"2.0","id":1,"method":"getMultipleAccounts","params":[
  ["<reverse record address>", "…"],
  {"encoding":"base64","commitment":"confirmed"}]}
```

Each `value` is null (no record: not a candidate) or an account whose data after byte 200 is the name's text (data-model §1.3).

## 2. Network egress after this feature

| Destination | Purpose | Sends |
|---|---|---|
| The configured **devnet** RPC endpoint | Unchanged from 001 | Unchanged |
| The configured **names** endpoint (mainnet) | Seeker ID lookups, §1.1 and §1.2 only from `SeekerNameReader`. It is one of the app's **two** mainnet reads: the other, the Seeker Genesis Token check before a join, is 001's (FR-003b, R17; `SeekerGenesisReader`, its own read-only client on the same endpoint) | Wallet addresses and derived account addresses, all already public on chain. No signature, no transaction, no device data |
| Local MWA wallet app | Unchanged from 001 | Unchanged |

## 3. In-code interface

### 3.1 `holdout.core.SeekerNames` (pure, no I/O)

| Member | Contract |
|---|---|
| `ANS_PROGRAM`, `TLD_HOUSE_PROGRAM`, `SKR_PARENT`, `SKR_TLD_HOUSE` | The research R1 constants; a test derives each from first principles and compares |
| `ownerSearchParams(wallet): JsonArray` | Exactly the `params` of §1.1 |
| `reverseRecordAddress(nameAccount): SolanaPublicKey` | data-model §1.2 |
| `nameFromReverseRecord(data: ByteArray): String?` | data-model §1.3 validation; null when invalid |
| `choose(candidates: List<String>): String?` | The first in R3 order with `.skr` appended, or null |
| `fit(name: String, budget: Int): String` | R9; a name within budget is returned unchanged |

### 3.2 `holdout.core.SeekerNameReader` (read-only network)

| Member | Contract |
|---|---|
| `suspend fun nameOf(wallet): Lookup` | §1.1 then §1.2 → `Lookup.Found(name)`, `Lookup.None` or `Lookup.Unknown(rateLimited: Boolean)` |
| (no other public member) | It cannot sign, send or simulate |

### 3.3 `holdout.app.names.SeekerNameRepository`

| Member | Contract |
|---|---|
| `val names: StateFlow<Map<String, String>>` | wallet base58 → chosen name, for every kept entry that has one. Starts from the cache at once |
| `fun showing(wallets: Collection<String>)` | Replace the on-screen set: queue those not fresh in the cache (data-model §2, §4), and drop pending ones no longer on screen (FR-012). Scripted wallets are skipped. Returns at once. Screens pass only their visible rows, and an empty list when they leave |
| `fun connected(wallet: String?)` | Re-check the connected wallet now at priority 0 (FR-007). Null on disconnect: the wallet that was connected is dropped unsearched unless it is a row on screen (FR-012). A scripted wallet is never queued (FR-012) |

### 3.4 Compose

| Member | Contract |
|---|---|
| `Format.identity(wallet, name: String?, budget: Int): String` | data-model §3 table, without the "you" marker |
| `val LocalSeekerNames: CompositionLocal<Map<String, String>>` | Every kept name, provided once by `HoldoutApp` from the repository's `names`; empty in previews and tests |
| `@Composable MemberIdentity(wallet, isYou, budget, color, modifier, style = AmountStyle)` | Reads `LocalSeekerNames`; the caller's style and colour (FR-003); width fixed from `budget` or the space given, a wider name cut with "…" (R8, FR-004 known limit); 200 ms crossfade; `contentDescription` is the shown identity |
| `@Composable ReportShownWallets(list, onShowing, walletOf)` | Reports a `LazyColumn`'s visible wallets to `showing` as they change, and an empty list when the app stops or the screen leaves (FR-012) |

## 4. Weaknesses entry (FR-010)

Added to `docs/weaknesses.json` (its location since spec 004) and rendered into the README by `node scripts/render-weaknesses.mjs`. The app shows no weaknesses screen:

```json
{
  "what": "Seeker ID names (and, since 001 R17, the wallet's Seeker Genesis Token) are read from Solana mainnet",
  "why": "Seeker IDs (.skr names) exist only on mainnet; the app reads them, for display only, from the public mainnet RPC, while everything else runs on devnet. Members without one, scripted members included, are shown by shortened address",
  "fix": "Nothing to fix for display: on mainnet the same lookup reads the same network. A dedicated RPC provider would make first-time lookups of large lists faster"
}
```
