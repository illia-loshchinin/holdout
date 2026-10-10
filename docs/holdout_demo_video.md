# Holdout — demo video, recording workflow and beta test

**Version:** 1.0 · **Date:** 2026-09-17 · **Status:** production companion to the spec

Companion to `specs/001-holdout-hackathon/spec.md` (FR-044, FR-066), research R18 and the plan's delivery sequence. The spec says *what* the walkthrough is; this file says *what is in it, how it is filmed, and who is in the cohort while it is filmed*.

**Everything here is filmed on cohort #3** — the first judged 7-day cohort, 28 September to 5 October 2026, on the judged devnet program. No rehearsal footage, no shortened days, no mock-ups (H14).

---

## 1. What is being produced

| Output | Format | Where it goes |
|---|---|---|
| **Demo video** | 16:9, 1080p, about 3:00 | The submission, due 9 October 08:59 CEST. Judges score completion from it (FR-066) |
| **In-app walkthrough — for judges** | 9:16, 720×1280, ~1.5 Mbps, ≈ 34 MB | Bundled in the final APK, played with Media3 ExoPlayer. Lives in About plus a quiet connect-screen link, and plays **without a wallet** (FR-001a, H29) |
| **Short explainer** | 9:16, ≤ 45 s, cut from the same footage | Card 2 of the first-run tour (FR-067). Ships in the same 7 October video drop; until then the tour is cards only |

Both are cut from the same footage. Until they exist, the app says "Walkthrough recording in progress".

## 2. Storyboard — 3:00

Screen numbers are the hackathon PRD's screen inventory (§10.1 of `docs/archive/holdout_v1_prd_hackathon-build_2026-09-18.md`, archived); the same screens as built are the cards in `docs/holdout_screen_map.md`.

| Time | Beat | On screen | Filmed |
|---|---|---|---|
| 0:00–0:08 | **Cold open** | Split screen: real push-ups beside the counting screen (8), a block banking | A sitting, 30 Sep – 3 Oct |
| 0:08–0:20 | **The number** | Home (4): "14 / 20 still standing", prize rising | Home screen, day 4 |
| 0:20–0:32 | **Open it cold** | App icon tapped → Welcome (1) → Connect wallet (2), with a glimpse of the no-wallet variant → Disclosure (3) | Fresh install, any day |
| 0:32–0:44 | **Funding from zero** | Get test tokens (3a): one tap, test SKR and fee SOL arrive | A brand-new wallet |
| 0:44–0:56 | **The free tier** | Ladder (5): Daily 25 as the primary action, streak, two staked leagues | 27 Sep |
| 0:56–1:12 | **Stake** | Stake confirm (6): chips $10–$100 with their SKR amounts, "if nobody drops out you get exactly your stake back", wallet approval | 27 Sep |
| 1:12–1:22 | **Lobby and lock** | Lobby (7) counting down → locked variant, roster final | 27 Sep, then 28 Sep 04:00–05:00 UTC |
| 1:22–1:52 | **A day's reps** | Sitting (8) end to end: home → four pre-flight checks → the start position held → countdown → counting, with one rep rejected → "20 banked" → day complete → submitted | The best sitting, 28 Sep – 4 Oct |
| 1:52–2:06 | **The crowd thins** | Day-by-day time-lapse of Home (4); Roster (12) with scripted members labelled; Cohort detail (11) | Daily captures at about 05:05 UTC |
| 2:06–2:16 | **A forfeit** | Forfeit screen (9) on the second phone: stake gone, streak alive, two ways back | 30 Sep, after the day-2 deadline |
| 2:16–2:36 | **Settle and claim** | Settlement at 05:00 UTC → Claim (10) → wallet approval → SKR balance rises → the transaction in a block explorer | 5 Oct |
| 2:36–2:48 | **Why it holds** | Diagram card: the program decides, deadlines from chain time, anyone can run settlement, pose detection on the phone | Card |
| 2:48–3:00 | **Honest close** | End card: devnet and test SKR, scripted members labelled, weaknesses in the README, the real tester count and survival rate, next cohort Monday | Card |

## 3. Screen coverage

Every Phase 1 screen appears at least once. Tick each one before the edit is locked.

| Screen (PRD §10.1) | Where |
|---|---|
| 1 Welcome | 0:20 |
| 2 Connect wallet, incl. no-wallet variant | 0:20 |
| 3 Disclosure of scripted members | 0:28 |
| 3a Get test tokens | 0:32 |
| 4 Home — challenge variant | 0:44 |
| 4 Home — cohort variant | 0:08, 1:52 |
| 4 Home — "Done — tap to submit" | 1:48, a short cut |
| 5 League ladder | 0:44 |
| 6 Stake confirm | 0:56 |
| 7 Lobby, and the locked variant | 1:12 |
| 8 Sitting — all sub-states | 1:22–1:52 |
| 9 Forfeit | 2:06 |
| 10 Claim, and claimed | 2:16–2:36 |
| 11 Cohort detail | 2:00 |
| 12 Roster with labels | 1:56 |
| Daily 25 and streak | 0:44 |

Screen 13 is the walkthrough player itself and is not filmed.

## 4. Filming method

- **Phone screen:** `scrcpy --record=<file>.mp4 --no-audio` over USB from Windows. No time limit, nothing drawn over the app.
- **Body:** a second phone on a tripod, side-on at chest height, filming the person and the phone together. One clap at the start of each take syncs the two files.
- **Wallet approval screens:** **answered by spike S1, 22 Sep — Seed Vault's approval screen records.** It is not `FLAG_SECURE`, so no moment in the film needs the second camera and every take can be a single `scrcpy` capture. (Another wallet on the second phone may still block; check it on the first tester install.)
- **Two wallets, two devices:** the **finisher** on the Seeker (joins, completes all 7 days, claims) and the **forfeiter** on the second phone (joins, then misses day 2).
- **Daily capture:** at about 05:05 UTC each day, right after the boundary, record 10 seconds of Home and Roster. These become the time-lapse.
- **Light and framing:** film the sittings in the same place each day so the time-lapse and the cut-aways match.

## 5. Shot log

Every clip gets a row. The transaction signature is what lets anyone check the shot was real.

| Shot | Date, chain time (UTC) | Screens | File | Transaction signature |
|---|---|---|---|---|
| S-01 | 22.09.2026 18:49 | Connect, signing message, send devnet transaction, disconnect wallet | scrcpy/entry_wallet_sign_send_transaction_disconnect.mp4 | [3PN4n...JwzzL](https://explorer.solana.com/tx/3PN4nN1Lp2Yb5DijZVvepefn5QmYe2tPUyAjXrETCDkLMtD7qPUzJY8ypzNZfZsSCpRdHx8TkAk7YVdUKkfJwzzL?cluster=devnet) |

Keep it in this file or beside the footage; the README's "how to verify" section links the signatures that appear on screen.

## 6. Filming calendar

| Date | What |
|---|---|
| By 21 Sep | Testers recruited, kit ready, `scrcpy` checked |
| 24–26 Sep | Rehearsal beta (§9). Practice takes only — never used |
| 27 Sep | Onboarding shots with a fresh wallet; both filming wallets and the testers join cohort #3 |
| 28 Sep 04:00–05:00 | Locked lobby shots |
| 28 Sep – 4 Oct | A sitting filmed daily, best one kept; daily 05:05 captures; forfeit screen on 30 Sep |
| 5 Oct | Settlement and the claim, with the explorer |
| 5–6 Oct | Rough cut, voiceover, captions |
| 7 Oct | Both exports locked; the 9:16 file goes into the final APK |
| 8 Oct | Submit |

## 7. Edit and export

- **Cut first, voiceover last**, recorded against the locked picture.
- **Captions burned in** — judges often watch muted.
- **Submission export:** 16:9, 1080p, H.264, about 3:00.
- **In-app export:** 9:16, 720×1280, H.264 at about 1.5 Mbps → roughly 34 MB, inside the 40 MB budget. Check the file size before it goes into the APK.
- **Music:** only tracks that are licensed for this use, or none.
- **Cards and captions** use the app's own tokens (`docs/design/tokens.json`): black canvas, white text, `#80ECFF` for emphasis, `#14F195` for the claim moment, and the Solana gradient only on the end card. Captions in Roboto, amounts in JetBrains Mono, so the video and the app look like one thing.

## 8. Rules

1. **Nothing shortened.** No rehearsal-deployment or compressed-day footage, in either export (FR-044).
2. **Nothing re-enacted as if real.** Every on-chain moment on screen is a real transaction, and its signature is in the shot log.
3. **Disclosure in the video itself:** devnet and test SKR, and that judging-period cohorts include labelled scripted members (constitution XI, XII; FR-066).
4. **Wording** (constitution XIII, H28): "prize", never "winnings", "returns", "yield" or "earnings"; never "bet", "wager", "odds". The app's word for taking the payout is **claim** — captions say claim, not withdrawal, because "withdrawn" means leaving a lobby before it starts. Captions use the members' names too: **the Daily 25** and **a Holdout**, and a lobby's money is **at stake**, never a prize.
5. **Fiat is approximate.** Chips shown as "≈ $25".
6. **Faces:** nobody appears without written consent (§9).

## 9. Beta testers

Five to seven real people, recruited by 21 September. They are the point: real members make the crowd real, and the rubric's stickiness quarter is about real engagement. Scripted wallets only top a cohort up to its floor — 20 members in a 7-day cohort (FR-043).

### What they need

- An Android phone, and a wallet that signs devnet transactions over Mobile Wallet Adapter. Seed Vault on a Seeker passed spike S1 (research, "S1 · passed 22 September 2026"); another phone's own wallet is checked at its first install.
- The APK, from the GitHub release.
- About five minutes a day, plus 25 push-ups.

### Onboarding note — one page, sent with the APK

1. Install the APK and the wallet.
2. Open Holdout, connect the wallet, tap **Get test tokens**.
3. Do today's Daily 25 to see how counting feels, with the phone propped at about 45° to one side of you; the camera check says when it is right.
4. For a whole cycle in under an hour, join the test lobby: three 10-minute days at 5 reps, a new Holdout every 30 minutes. The 7-day and 21-day Holdouts start every Monday at 05:00 UTC, and joining closes an hour before.
5. In a 7-day or 21-day Holdout, 25 push-ups a day before 05:00 UTC. Miss one and the stake is gone. Reps bank in blocks of 10, so several sittings a day are fine.
6. Everything is a test network and a test token: nothing has value.
7. The camera never leaves the phone — no video or landmark is uploaded.
8. Report anything odd in the group chat, with the time it happened.

### The rehearsal run, 24–26 September

On the rehearsal deployment, with a test league of **seven one-hour days at 5 reps a day** — a 7-day Holdout in 7 hours (H32) — each tester runs faucet → join → sittings → a deliberate forfeit → settlement → claim in an afternoon. This is how bugs are found before the freeze; the footage is never used.

### Consent

Anyone who may appear on camera confirms in writing — a message in the group chat is enough — that their footage and wallet address may appear in the submission. Keep the messages.

### Feedback

- **After day 1:** what was confusing? Did the counter miss or add reps?
- **After the claim:** would you join another cohort? What would make you keep going?

### Numbers for the deck

Read from the chain, never from a spreadsheet: members joined, survivors each day, survival rate, claims. `crank status` prints them, and a block explorer confirms them.

## 10. Before submitting

- [ ] Both exports render, and the in-app file is under 40 MB.
- [ ] Every screen in §3 appears.
- [ ] The video states devnet, test token and scripted members.
- [ ] Every on-chain moment has a signature in the shot log.
- [ ] Captions match the app's wording (§8.4).
- [ ] The walkthrough plays on a fresh install **with no wallet connected**.
- [ ] Consent recorded for everyone on camera.
