# Holdout — spike S1 runbook (wallet signing on real hardware)

**Version:** 1.0 · **Date:** 2026-09-21 · **Status:** working runbook for T012a

Companion to `specs/001-holdout-hackathon/research.md` (S1, R3, R12, R13) and task
**T012a**. The research note says *what S1 must answer*; this file says *how to
answer it*, in the order that fails cheapest first.

**Written for:** a Solana Seeker as the only handset, `holdout.app` not yet
registered, and a Windows host with WSL2. Where that changes a step, the step
says so.

**S1 blocks T038, T040 and T061** on the app side, and it is the last gate before
`kotlin/app` work begins. It does **not** block the program: the contract half of
Phase 1 is already merged and tested on LiteSVM.

---

## 0. The seven checks, and where they stand

S1's pass bar is not one question but seven. Two are already answered.

| # | Check | Status |
|---|---|---|
| 1 | The fixed template builds; `:app:assembleDebug` yields a working APK | **Answered** 2026-09-18 (R3). Re-confirm once on your machine — §3 |
| 2 | A Seeker connects Seed Vault Wallet over MWA and lands a devnet transaction with `signAndSendTransactions` | **Open** — §5 |
| 3 | A sponsor-paid transaction built with `web3-solana` and sent with `rpc-core` lands | **Open** — §6 |
| 4 | A phone with no wallet returns `NoWalletFound` (ES-0) | **Answered** 2026-09-18 on AVD `holdout37` (R3) |
| 5 | `web3-solana` and `rpc-core` run in a **plain JVM** module, for the crank | **Open**, and cheapest — §2 |
| 6 | `scrcpy --record` captures the app; does the wallet's approval screen record or come out black (`FLAG_SECURE`)? | **Open** — §7. This is T012 |
| 7 | Which wallets sign devnet transactions | **Open** — §5, §10 |

T012a also names *"a plain Android phone does the same with its own MWA wallet"*.
With one handset that sub-check cannot be done as written; §10 gives the nearest
honest substitute and what to record instead.

---

## 1. Environment, and the one WSL2 trap

The Solana and Anchor toolchains live in WSL2 (R19, quickstart §1). The **Android
half does not have to.** Keep them apart:

| Job | Where | Why |
|---|---|---|
| `./gradlew` (Kotlin, APK) | Either. Android Studio on Windows is simplest | Needs the Android SDK and JDK 21, not the Solana CLI |
| `adb`, `scrcpy` | **Windows** | See below |
| `solana airdrop`, `solana-keygen` | WSL2, as already set up | Already working there |

**The trap:** `adb` inside WSL2 cannot see a USB device. WSL2 has no USB
passthrough by default — you would have to attach the device with `usbipd-win`
first. Do not spend the evening on that. Run `adb` and `scrcpy` from Windows
(PowerShell), which is also what `docs/holdout_demo_video.md` §4 already assumes
for filming.

Before anything else, confirm the Seeker is visible and that its developer options
are on (Settings → About → tap build number seven times → enable USB debugging):

```powershell
adb devices          # the Seeker must appear as "device", not "unauthorized"
```

An `unauthorized` line means the RSA prompt on the phone has not been accepted.

Java must be 21 for the Gradle daemon, with bytecode at 17 (R3):

```bash
java -version        # 21.x
```

---

## 2. Check 5 first — do the libraries run without Android?

Do this before you touch the phone. It needs no device, it is the risk R12 rests
on (the crank is plain Kotlin/JVM and shares `kotlin/core` with the app), and it
is the check whose failure changes the most.

`kotlin/core/build.gradle.kts` already declares both libraries:

```kotlin
dependencies {
    api(libs.web3.solana)        // 0.3.1
    api(libs.rpc.core)           // 0.2.8
    implementation(libs.rpc.solana)
    implementation(libs.rpc.ktordriver)
    implementation(libs.multimult)
    testImplementation(kotlin("test"))
}
```

So the question is only whether they **resolve and run** off-Android:

```bash
cd kotlin
./gradlew :core:dependencies --configuration testRuntimeClasspath
./gradlew :core:test
```

`:core` has no tests yet, so a green `:core:test` proves resolution and a clean
JVM compile, not behaviour. To prove they actually *run*, add one throwaway test —
delete it afterwards, or keep it as the seed of T033:

```kotlin
// kotlin/core/src/test/kotlin/holdout/core/SpikeS1LibrariesTest.kt
package holdout.core

import com.solana.publickey.SolanaPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SpikeS1LibrariesTest {
    @Test
    fun `web3-solana decodes a base58 key on a plain JVM`() {
        val id = "Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4"
        assertEquals(id, SolanaPublicKey.from(id).base58())
    }
}
```

**Pass:** it compiles and passes with no Android SDK on the classpath.
**Fail:** if either library drags in an Android dependency or will not link on the
JVM, that is S1's fallback trigger — use **sol4k** for transaction building in
`:core` and `:crank` (research S1, "If it fails"). Record which library failed and
how; that decision changes T033 onward.

---

## 3. Check 1 — build and install on the Seeker

```bash
cd kotlin
./gradlew :app:assembleDebug
```

R3 records this working on 2026-09-18 with every version in
`gradle/libs.versions.toml` resolving as pinned. You are re-confirming, not
discovering. If Gradle cannot reach `dl.google.com` you will get an AGP/AndroidX
resolution failure — that is the same block that stops this work being done in the
cloud container, and it needs a network that allows Google's Maven host.

Then install and launch:

```powershell
adb install -r kotlin\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n holdout.app/holdout.app.MainActivity
```

You should see the scaffold screen: **"Kotlin + Mobile Wallet Adapter"** and a
*Connect wallet* button.

**Pass:** the APK installs and the screen renders on the Seeker.

---

## 4. Devnet SOL before you sign anything

Checks 2 and 3 both need funded keys, and neither can come from the in-app faucet
yet — `claim_faucet` exists in the program but is not deployed (T093), and the
faucet vault is unfunded (T010).

From WSL2:

```bash
# The wallet you will connect from the Seeker: read its address off the app
# screen after the first connect, then fund it.
solana airdrop 1 <seeker-wallet-pubkey> --url devnet

# A throwaway sponsor for check 3. Never commit this file.
solana-keygen new --no-bip39-passphrase -o ~/.config/solana/spike-sponsor.json
solana airdrop 1 --keypair ~/.config/solana/spike-sponsor.json --url devnet
solana balance --keypair ~/.config/solana/spike-sponsor.json --url devnet
```

The public devnet faucet rate-limits per IP and per address; if `airdrop` refuses,
wait or use `https://faucet.solana.com`. This throwaway sponsor is **not** the
bundled sponsor of R13 — it holds a full SOL, not the 0.05 float, and exists only
to prove the code path.

> **Never commit a keypair.** `.gitignore` already excludes keys; keep this one
> in `~/.config/solana/` as above.

---

## 5. Checks 2 and 7 — Seed Vault signs a devnet transaction

### 5.1 What has to be added

`MainViewModel.kt` today has `connect`, `signMessage` (via `signMessagesDetached`)
and `disconnect`. **Nothing calls `signAndSendTransactions`**, which is precisely
what S1 requires. Two edits:

**a. Give `:app` a path to `rpc-core`.** `kotlin/app/build.gradle.kts` declares
`mobile-wallet-adapter-clientlib-ktx`, `multimult` and `web3-solana`, but not the
RPC libraries and not `:core`. Prefer the module, since that is where this code
belongs permanently (R12 — one client, shared):

```kotlin
// kotlin/app/build.gradle.kts, in dependencies { }
implementation(project(":core"))
```

`:core` exposes both libraries with `api(...)`, so the app gets them transitively
and nothing is declared twice. If you would rather keep the spike out of `:core`,
add `implementation(libs.rpc.core)`, `implementation(libs.rpc.solana)` and
`implementation(libs.rpc.ktordriver)` to the app instead — but then remember to
remove them when T033 lands.

**b. Add a spike path that sends a transaction.** The smallest honest test is a
tiny SOL transfer from the connected wallet to itself: it is a real devnet
transaction, it needs the wallet's signature, and it costs 5,000 lamports.

### 5.2 Code sketch

> **Compile-check this against the library, do not paste and trust it.** It is
> written to match the shape already working in `MainViewModel.kt`
> (`walletAdapter.transact(sender) { authResult -> … }`) but the exact
> `signAndSendTransactions` signature and the `web3-solana` 0.3.1 message builder
> were **not** compiled while writing this — Gradle cannot resolve AGP in the
> container this was drafted in. Check the
> [clientlib-ktx javadoc](https://javadoc.io/doc/com.solanamobile/mobile-wallet-adapter-clientlib-ktx/latest/index.html)
> and Solana Mobile's
> [Using Mobile Wallet Adapter](https://docs.solanamobile.com/android-native/using_mobile_wallet_adapter)
> guide, both already in research.md's source list. Expect to adjust names.

```kotlin
// Add to MainViewModel.kt

/**
 * Spike S1, check 2: land a real devnet transaction signed by the connected
 * wallet. A self-transfer of 1 lamport — smallest thing that is still a
 * transaction the cluster must accept.
 */
fun sendSpikeTransaction(sender: ActivityResultSender) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, message = null) }

        val result = walletAdapter.transact(sender) { authResult ->
            val wallet = SolanaPublicKey(authResult.accounts.first().publicKey)

            // A recent blockhash is required. Fetch it over rpc-core (see §6 for
            // the client), or use the wallet's own submission path if the
            // clientlib offers one.
            val blockhash = fetchLatestBlockhash()

            val transaction = buildSelfTransfer(
                payer = wallet,
                lamports = 1L,
                recentBlockhash = blockhash,
            )

            signAndSendTransactions(arrayOf(transaction.serialize()))
        }

        when (result) {
            is TransactionResult.Success -> _uiState.update {
                it.copy(
                    isLoading = false,
                    message = "Signature: " +
                        Base58.encodeToString(result.payload.signatures.first()),
                )
            }
            is TransactionResult.NoWalletFound -> _uiState.update {
                it.copy(isLoading = false, message = "No wallet: ${result.message}")
            }
            is TransactionResult.Failure -> _uiState.update {
                it.copy(isLoading = false, message = "Failed: ${result.message}")
            }
        }
    }
}
```

Wire a button to it in `MainActivity.kt`, beside *Sign message*:

```kotlin
Button(onClick = onSendTransaction, enabled = !state.isLoading) {
    Text("Send devnet transaction")
}
```

### 5.3 Running it

1. Install the Seed Vault Wallet on the Seeker and **set it to devnet.** If it
   offers no devnet switch, that alone is an S1 finding — record it and go to §10.
2. Launch the app, tap **Connect wallet**, approve in the wallet.
3. Note the address shown, fund it (§4), then tap **Send devnet transaction**.
4. Approve in the wallet. Copy the signature from the screen.
5. Confirm it landed, from WSL2:

```bash
solana confirm -v <signature> --url devnet
```

**Pass:** `solana confirm` reports the transaction finalized, and the wallet was
Seed Vault on the Seeker.

**Record for check 7:** which wallet, which version, whether it had to be switched
to devnet manually, and whether the unverified identity (§8) produced a warning.

---

## 6. Check 3 — the sponsor-paid transaction

This one has **no MWA in it at all**, which is the point. R13's fee bootstrap has
the sponsor pay the fee for `claim_faucet`, and that instruction needs no member
signature — so the app must be able to build a transaction with `web3-solana`,
sign it locally with the bundled sponsor key, and send it with `rpc-core`. S1
proves those two libraries can do that before any of it is written for real.

Until the program is deployed (T093), stand in for `claim_faucet` with the same
shape: **the sponsor pays, and the recipient never signs.** A 1-lamport transfer
from the spike sponsor to the connected wallet does exactly that.

```kotlin
/**
 * Spike S1, check 3: sponsor-paid, recipient never signs. Built with
 * web3-solana, signed locally by the sponsor, sent with rpc-core — the R13
 * fee-bootstrap path, before claim_faucet is deployed.
 */
suspend fun sendSponsorPaid(recipient: SolanaPublicKey): String {
    val rpc = /* rpc-core client over the ktor driver, devnet endpoint */
    val blockhash = /* rpc.getLatestBlockhash() */
    val sponsor = /* load the spike sponsor keypair — see the note below */

    val transaction = buildTransfer(
        payer = sponsor.publicKey,
        from = sponsor.publicKey,
        to = recipient,
        lamports = 1L,
        recentBlockhash = blockhash,
    )
    transaction.sign(sponsor)
    return rpc.sendTransaction(transaction)   // returns the signature
}
```

**Do not bundle the spike sponsor key in the APK.** For the spike, push it onto
the device as a file and read it, or paste the base58 secret into a debug field —
and delete it afterwards. The real bundled sponsor of R13 is a deliberate design
decision with a 0.05 SOL float and a permissionless `top_up_sponsor` behind it;
a throwaway key holding a whole airdropped SOL is not the same thing and must not
be shipped.

**Pass:** `solana confirm -v <signature> --url devnet` shows it finalized, the fee
was paid by the sponsor, and the recipient's balance rose by 1 lamport without the
recipient signing.

**Fail:** if `rpc-core` cannot send, or `web3-solana` cannot build the message,
that is the sol4k fallback — the same one §2 may already have triggered.

---

## 7. Check 6 — scrcpy, and whether the approval screen records

This is **T012**, and its answer decides how the demo video is shot
(`docs/holdout_demo_video.md` §4 already reserves the decision). Many wallets set
`FLAG_SECURE` on approval screens, which makes screen capture record black.

From Windows, with the Seeker attached:

```powershell
scrcpy --record=s1-approval-test.mp4 --no-audio
```

With it recording, run §5 again end to end: connect, tap send, and **approve in
the wallet**. Stop scrcpy, then watch the file.

Record in `docs/holdout_demo_video.md` §4, replacing the "Spike S1 checks this"
sentence, which of these you saw:

- **The approval screen records normally** → the video can be shot entirely with
  scrcpy, and the second camera is only for the body shots.
- **The approval screen is black, the app is fine** → those seconds need the
  second phone on the tripod (R18). Everything else still comes from scrcpy.
- **scrcpy drops or refuses to mirror at all** → note the error; the whole filming
  method is affected, not just the approval beats.

---

## 8. Identity, and the Digital Asset Links file (T009)

`MainViewModel.kt` already uses the production identity and says why:

```kotlin
identityUri = Uri.parse("https://holdout.app"),
iconUri = Uri.parse("favicon.ico"),
identityName = "Holdout",
```

R3 requires that URI to be a domain you control, serving a Digital Asset Links
file, so wallets can verify the app. **`holdout.app` is not registered**, so for
S1 the identity is unverified. That is workable — run S1 anyway and **record
exactly what the wallet does**: connects silently, connects with an "unverified"
warning, or refuses. Whichever it is, it is an S1 finding, and a warning that
appears on camera matters for the walkthrough.

When you do register the domain, serve this at
`https://holdout.app/.well-known/assetlinks.json` — HTTPS, `Content-Type:
application/json`, and **no redirect** (verification follows none):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "holdout.app",
      "sha256_cert_fingerprints": [
        "AA:BB:CC:…  the SHA-256 of your signing certificate"
      ]
    }
  }
]
```

Get the fingerprint for whichever certificate signs the build you are testing —
debug and release differ, and both can be listed:

```bash
cd kotlin && ./gradlew signingReport      # prints SHA-256 per variant
```

Or directly from the debug keystore (password `android`):

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android
```

List the **release** fingerprint too before the APK ships, or the shipped build
will not verify.

---

## 9. Recording the result — S1 is not done until this is written down

The spike's value is the decision it fixes, so the evidence goes in the repo:

1. **`specs/001-holdout-hackathon/research.md`** — under the S1 row or as a dated
   correction to R3, in the style R3 already uses ("Corrected 2026-09-18, after
   actually generating it"): which wallet signed, on which device, the two
   signatures, whether the libraries ran on the plain JVM, and any fallback taken.
2. **`docs/holdout_demo_video.md` §4** — the `FLAG_SECURE` answer (§7).
3. **`specs/001-holdout-hackathon/tasks.md`** — tick **T012a**, and **T012** if
   §7 is answered. If a sub-check was not done (the second-handset one), say so
   beside the tick rather than ticking silently.
4. Keep the two signatures in the commit message. They are checkable by anyone
   with a devnet RPC, which is what makes this evidence and not recollection.

A commit message in this repo's style would name what was confirmed, what was
substituted, and what remains open.

---

## 10. Fallbacks, and the one check a single handset cannot make

Research S1's documented fallbacks, in the order you would reach for them:

| If | Then |
|---|---|
| Seed Vault will not sign devnet, or refuses the unverified identity | Install another MWA wallet on the Seeker — Phantom or Solflare, set to devnet — and run §5 against it. Record both outcomes; "which wallets sign devnet transactions" is literally S1's question |
| `web3-solana` or `rpc-core` will not build or send | **sol4k** for transaction building in that module (research S1). Note it against R3, because it changes T033 onward |
| Either library drags Android onto the JVM classpath | Same sol4k fallback, and `:core`'s dependency block changes — this is the R12 risk, so flag it prominently |
| The approval screen is `FLAG_SECURE` | Second camera for those beats (R18), recorded in the demo-video doc |

**The sub-check you cannot make.** T012a asks that *"a plain Android phone does
the same with its own MWA wallet"* — a second handset, without Seed Vault
hardware, proving the app is not Seeker-only. With one device, the nearest honest
substitute is a second MWA wallet **on the Seeker** (row 1 above): it proves the
MWA path is wallet-agnostic, but not that it works on non-Seeker hardware. Record
it as substituted, not passed.

Worth knowing now rather than in October: **`docs/holdout_demo_video.md` §4 plans
the shoot around two devices** — the finisher on the Seeker and the forfeiter on a
second phone, filmed in the same cohort. A second Android phone is therefore
needed for filming regardless of S1, and it closes this sub-check for free. Any
Android 8.0+ (minSdk 26) phone with a devnet-capable wallet will do; it is also
what the beta testers of T011 will be using.
