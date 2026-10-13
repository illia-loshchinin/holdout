# How Holdout works

[← README](../README.md) · [Economics](economics.md) · [Rep counting](rep-counting.md) · [Contract](contract.md)

## Three ways to take part

| | The Daily 25 | Spark · 7-day | Habit · 21-day |
|---|---|---|---|
| Tagline | *All at once, or 10 at a time* | *Light it up · knees allowed* | *Make it stick · toes only* |
| Cost | Free | A stake in SKR | A stake in SKR |
| Who | Anyone with a Solana wallet | Wallets holding a Seeker Genesis Token | Wallets holding a Seeker Genesis Token |
| Reps | 25 a day | 25 a day for 7 days | 25 a day for 21 days |
| Knee push-ups | Count | Count (full push-ups encouraged) | Don't count |
| What you get | A streak | Stake back + a share of the pool | Stake back + a share of the pool |
| On chain? | No, it lives on the phone | Yes | Yes |

The two Holdouts are separate levels for different people, not rungs of a ladder. Neither unlocks
the other. A finished Spark offers *Make it a Habit · 21-day*, and that's all.

**Rule sentences, exactly as the app shows them:**

- Spark: *"At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count."*
- Habit: *"At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes —
  push-ups from the knees don't count."*

### The Daily 25

Free, every day, for everyone. Do all 25 at once, or at your own pace: 10 in the morning, 10 at
lunch, 5 in the evening. Every 10 are banked for good. Close the app or stop mid-set and banked reps
stay. Each day done grows your streak. A lost Holdout never breaks it.

The Daily 25 pays nothing and unlocks nothing. It is the habit, and the honest way to see how far
you are from a Habit: the done screen shows how many of the day's reps came from the toes.

### A Holdout

A Holdout is a group of members who start on the same Monday and finish together. Everyone stakes
SKR. Miss a day and your stake is lost: 70 % to the prize pool, 10 % set aside for your comeback,
20 % to the project. Finish, and you take your stake back plus a share of what the others lost.
If nobody drops out, you get exactly your stake back. The full money rules are in
[Economics](economics.md).

## The calendar

| | |
|---|---|
| A new Spark and a new Habit start | Every Monday, 05:00 UTC |
| A Holdout day | 24 hours, 05:00 UTC to 05:00 UTC, judged by chain time |
| Joining and leaving close | 1 hour before the start |
| Leaving before that | Full refund, nothing taken |
| Daily target | 25 reps, banked in blocks of 10, any number of sittings |
| After the last day | The crank settles, then each finisher claims |

A day done on the phone counts once it reaches the chain before that day's deadline. The phone
keeps a finished day that is waiting for a network and sends it as soon as it can.

## Hackathon · Spark and Hackathon · Habit

Nobody can wait 7 days to see a payout, so devnet also runs **Hackathon** rounds. A Hackathon
round is a real Holdout under the same contract and the same rules, with shorter days. Two are
open at the same time, one per form: **Hackathon · Spark** (knees allowed) and **Hackathon ·
Habit** (toes only). Each round:

- three 10-minute days, 5 reps a day: about 30 minutes in all;
- a new round every 30 minutes, joinable right up to its start;
- labelled **DEVNET COHORT · 10-MINUTE DAYS** wherever it appears, with one combined *Hackathon*
  leaderboard;
- topped up to 10 members by scripted members in its last 3 minutes, once someone real has joined
  it (see below); a round nobody has joined stays empty.

A Hackathon round lets one person run join → sittings → miss or finish → settlement → claim
alone in a quarter of an hour. The demo video is recorded from one. Real Spark and Habit Holdouts always
use 24-hour days. Short days exist only in a program built with the `test-leagues` feature, and a
mainnet build never carries it.

## Scripted members (disclosed)

A Holdout must never look empty to someone who installs the app during judging. Real members come
first. **Scripted members** top a Holdout up to a minimum and then stop joining. On devnet they
play the Hackathon rounds only: each is topped up to 10 members in its last 3 minutes, once someone
real has joined it. Spark and Habit get none.

- They are ordinary wallets run by automation, under exactly the same contract rules. Their
  stakes are real test SKR, their forfeits fund real prizes, and a scripted finisher takes its
  share like anyone else.
- Their days are **not** camera-checked.
- Every one is labelled **SCRIPTED** in the app. Their addresses ship in the build
  ([`scripted-wallets.json`](../kotlin/core/src/main/resources/scripted-wallets.json)), so anyone
  can check them.
- The tour's last card discloses them. It can't be skipped on first run.

## Seeker-exclusive staking

Joining a Holdout needs a wallet that holds a **Seeker Genesis Token**. The Daily 25 is open to
everyone.

- **On mainnet** the contract checks the token on every join. The check is written and tested
  (`programs/holdout/src/genesis.rs`) and is switched on by the `genesis_gate_enabled` flag.
- **On devnet** Seeker Genesis Tokens don't exist, so the flag is off. The app instead reads the
  connected wallet's token from Solana mainnet (read-only) before it offers a join. This is listed
  as a known weakness in the README.

## The app

Four tabs:

| Tab | What it is for |
|---|---|
| **Today** | The one thing to do today: the Daily 25, or your Holdout day, and what is left |
| **Standing** | Your Holdout's crowd: who is still standing, how the group thinned, the roster, and the leaderboards |
| **Holdouts** | Yours: open to join, running, finished, and claims waiting |
| **Me** | Wallet, balances, the faucet, your streak, About |

A five-card tour runs after the first wallet connection: the Daily 25 · a Holdout · where a lost
stake goes · how reps are counted · scripted members. About re-opens it.

**Leaderboards** rank members by discipline: the share of their Holdouts they finished, after 3
played. Money never ranks anyone. There is one board for Spark, one for Habit and one combined
board for the Hackathon rounds, all read live from on-chain progress.

**Reminders** are scheduled on the phone from chain time, because no server knows your deadlines.
You get one when a Holdout starts and one before each day's deadline while that day isn't done. A
24-hour day also gets one halfway through, with sound, and then the one before the deadline is
silent. A day done on the phone but not yet on chain stays in the notification shade until it is
sent.

## Getting started with no SOL

A wallet needs SOL to pay fees, and a brand-new wallet has none. The app has a built-in
**faucet**. One tap gives enough test SKR for the top chip of any open Holdout, plus enough devnet
SOL for a full 21-day cycle. A fee sponsor key inside the app pays for that first transaction.

There is no per-wallet limit. The faucet is bounded by one on-chain daily budget covering every
lamport it can spend, and anyone can refill the sponsor from the faucet's own funds. Test tokens
have no value. There will be no faucet on mainnet.

## What you need

- **For Holdouts:** a Solana Seeker (Seed Vault Wallet) holding a Seeker Genesis Token.
- **For the Daily 25:** any Android 8.0+ phone with a front camera and a
  [Mobile Wallet Adapter](https://docs.solanamobile.com/) wallet on devnet.
- A floor, the phone propped at 45° ahead of your shoulder, and fitted clothes so the camera can
  see your knees.
