# Privacy

[← README](../README.md) · [Rep counting](rep-counting.md)

Holdout watches you do push-ups. Here is exactly what it does with that, and how the build makes
sure of it.

## What never leaves the phone

| | |
|---|---|
| **Video** | Never stored, never uploaded. Each camera frame goes to the on-device pose model and is dropped right after. |
| **Sound** | Never recorded. The app has **no microphone permission**, and the build fails if one ever appears. |
| **Body data** | The 33 landmarks per frame live in memory during the sitting and are thrown away. No landmark or rep trace is sent anywhere. |
| **Banked reps** | Kept in the app's private storage until the day is sent. Cloud backup and device transfer are switched off. |

## What goes on chain

One transaction per completed day, signed by your own wallet:

```
submit_day(day_index, proof_hash)
```

- `day_index`: which day of the Holdout.
- `proof_hash`: a SHA-256 of `holdout-day-v1 | cohort | day | reps | model name`. It is emitted in
  an event and not stored.

No image, no landmark and no trace is part of it. The Daily 25 never touches the chain at all.

## Network traffic

| To | Why |
|---|---|
| Solana **devnet** RPC | Read Holdouts, send your transactions |
| Solana **mainnet** RPC, read-only | Show members' public Seeker ID names (`.skr`), and check that your wallet holds a Seeker Genesis Token before a join. Nothing is ever written to mainnet. |
| Your wallet app, through Mobile Wallet Adapter | Signing. The app never sees your keys. |

There is no Holdout server, no analytics SDK and no ad SDK.

## Permissions

The member app asks for exactly four
([`AndroidManifest.xml`](../kotlin/app/src/main/AndroidManifest.xml)):

| Permission | Used for |
|---|---|
| `CAMERA` | Counting reps |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Solana RPC |
| `POST_NOTIFICATIONS` | On-device reminders |

## How the build enforces it

These are Gradle tasks in [`kotlin/app/build.gradle.kts`](../kotlin/app/build.gradle.kts). CI runs
them on every build:

- **`checkNoAudioPermission`** fails the build if `RECORD_AUDIO` reaches the merged manifest.
- **`checkMemberHasNoLabCode`** fails the member build if any recording code from the tester app
  is in it.
- Backup is off: `allowBackup=false`, plus data-extraction rules that block cloud backup and
  device transfer.

## The one exception: Holdout Lab

The thresholds were set from real recordings. Those come from a **separate tester app, Holdout
Lab** (`holdout.app.lab`), used only by people who agreed to be filmed:

- it can save sitting video (no audio) and landmark traces to its own private storage;
- it has **no network access**: its manifest removes `INTERNET`;
- files leave it only by USB (`adb`), and they are deleted afterwards;
- it is never distributed, and none of its recording code is in the member app (see above).

The rules for it are in constitution principle X and in
[`specs/004-lab-and-member-apps`](../specs/004-lab-and-member-apps/spec.md).

## What this costs

There is no microphone and no video, so nothing tells a live person from a video played to the
lens. That is a known weakness, listed with its fix in the README.
