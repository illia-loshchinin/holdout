# Build and install the Android app

[← README](../README.md) · [Development](development.md)

You don't need any keys, secrets or a local blockchain to build the app. The default build talks
to Solana devnet.

## 1. Requirements

| | Version |
|---|---|
| JDK | 21 (Gradle picks it through the toolchain resolver if it is missing) |
| Android SDK | Platform 37 and build-tools. [Android Studio](https://developer.android.com/studio) (current stable) installs both. |
| Device | Android 8.0+ (API 26) with a front camera. Holdouts need a Solana Seeker. |
| Network | The first build downloads the MediaPipe pose model (~9 MB, SHA-256 pinned) |

Tell Gradle where the SDK is. Android Studio writes `kotlin/local.properties` for you. Otherwise,
create it yourself (it is git-ignored):

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

`ANDROID_HOME` also works instead.

## 2. Build a debug APK

```bash
git clone https://github.com/illia-loshchinin/holdout.git
cd holdout/kotlin
./gradlew :app:assembleMemberDebug
```

On Windows use `gradlew.bat` in `cmd` or PowerShell. The APK lands in:

```
kotlin/app/build/outputs/apk/member/debug/app-member-debug.apk
```

## 3. Install it

Enable **Developer options → USB debugging** on the phone, connect it, then either:

```bash
./gradlew :app:installMemberDebug
```

or install the file directly:

```bash
adb install -r app/build/outputs/apk/member/debug/app-member-debug.apk
```

You can also copy the APK to the phone and open it there, after allowing installs from that
source.

The app opens on the connect-wallet screen. Connect a Mobile Wallet Adapter wallet **set to
devnet**, then use the faucet on **Me** for test SKR and fee SOL. A build without
`holdout.sponsorKeypair` has no fee sponsor, so the wallet needs a little devnet SOL first
(`solana airdrop 1 <address> --url devnet` or [faucet.solana.com](https://faucet.solana.com)).

**No Seeker?** A release build always checks the Seeker Genesis Token before a join. A debug build
can skip that check, so you can try a Holdout on any phone or the emulator:

```bash
./gradlew :app:installMemberDebug -Pholdout.seekerGate=off
```

## 4. Build a signed release APK

The release build is minified. Without signing properties it still builds, but it writes
`app-member-release-unsigned.apk` and prints a warning.

1. Create a keystore **outside the clone**:

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore ~/holdout-release.jks -alias holdout \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Holdout, O=Holdout, C=DE"
   ```

   A PKCS12 keystore has one password, so the key password is the same value.

2. Put the four properties in your **user** Gradle file, `~/.gradle/gradle.properties`
   (`%USERPROFILE%\.gradle\gradle.properties` on Windows). The build refuses them in the
   repository's `kotlin/gradle.properties`.

   ```properties
   holdout.keystore=C:/Users/<you>/holdout-release.jks
   holdout.keystorePassword=<password>
   holdout.keyAlias=holdout
   holdout.keyPassword=<same password>
   ```

   Use an absolute path with forward slashes. A leading `~` is not expanded.

3. Build and verify:

   ```bash
   ./gradlew :app:assembleMemberRelease
   "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
     app/build/outputs/apk/member/release/app-member-release.apk
   ```

   On Windows the tool is `apksigner.bat`. If only some of the four properties are set, the release
   tasks fail on purpose (`checkReleaseSigning`).

The details, including backing up the keystore, are in
[`docs/holdout_setup_runbook.md`](holdout_setup_runbook.md) §4.

## 5. Build options

Pass these with `-P` or set them in `~/.gradle/gradle.properties`:

| Property | Default | Use |
|---|---|---|
| `holdout.rpcUrl` | `https://api.devnet.solana.com` | Your own devnet RPC, or `http://127.0.0.1:8899` for a local validator (debug only; run `adb reverse tcp:8899 tcp:8899`). A hosted devnet node needs `devnet` in its host name (e.g. `devnet.helius-rpc.com`); any other host is taken for a local validator. Its key is built into the APK, so use a free, devnet-only key, and keep it in `~/.gradle/gradle.properties`, never in the repo |
| `holdout.programId` | `Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4` | A program you deployed yourself |
| `holdout.namesRpcUrl` | Solana mainnet-beta | The read-only RPC for Seeker ID names and the Seeker Genesis Token check |
| `holdout.sponsorKeypair` | none | Path to a Solana CLI keypair JSON bundled as the faucet's fee sponsor. Without it the app builds and runs, but a wallet with zero SOL can't use the faucet. |
| `holdout.seekerGate` | `on` | `off` skips the app's Seeker check. **Debug builds only.** |

## 6. Two apps, one codebase

`:app` has two product flavors:

| Flavor | Package | What it is |
|---|---|---|
| `member` | `holdout.app` | The app members install. Build this one. |
| `lab` | `holdout.app.lab` | Holdout Lab, the internal tester app that records sittings to tune the counter. No wallet, no network. Never distributed. |

Both share the same counting code, so a counting fix reaches members unchanged. The member build
fails if any Lab code ends up in it (`checkMemberHasNoLabCode`).

## 7. Run the tests

```bash
./gradlew ktlintCheck :core:test :repcounter:test :crank:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest
```

Or run the whole CI on your machine with `bash scripts/ci-local.sh`. See
[Development](development.md).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Create `kotlin/local.properties` with `sdk.dir=…`, or set `ANDROID_HOME` |
| First build fails downloading `pose_landmarker_full.task` | It needs internet once. The file is cached in `kotlin/app/src/main/assets` afterwards. |
| The wallet says it is on another network | Switch the wallet to devnet, then disconnect and connect again from **Me** |
| *Join* is missing on a phone that isn't a Seeker | Expected in a release build. Use a debug build with `-Pholdout.seekerGate=off`. |
| The faucet says it can't pay today | The faucet's shared daily budget is spent. It works again the next day. |
