# Holdout Constitution

Scope: the CLOCK IN hackathon submission, devnet only. Every later phase — `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze` — is evaluated against these principles. A plan that violates one is wrong even when it works.

## Core Principles

### I. The Contract Is the Only Authority on Funds
No off-chain service may move, hold, or decide the fate of a stake. Principal stays segregated from the prize pool at every instant; only forfeited stakes ever enter a pool — a cohort's own, or a capped share of its league's earlier forfeits held in that league's program-owned reserve — so a shortfall is impossible by construction rather than by arithmetic care. Every split of a forfeited stake (prize pool, league reserves, developer, operations) is a fixed program rule; no key chooses where it goes.

### II. Forfeiture Is Derived, Never Declared
A member is in breach if and only if `now > deadline_of(days_completed)`, computable from on-chain state alone. Automation *materialises* breach into a transfer; it never creates it. A missed automated run may delay money and displayed counters; it may never change who won or lost.

### III. No Oracle in Contract Logic
No instruction reads a price, and no fiat figure is an input to any on-chain branch, amount, or validation. The contract validates SKR amounts only.

A rate MAY be used in exactly one place: off-chain Operator tooling that prices the stake chips in SKR before a cohort is created. Once written to a cohort, those SKR amounts are ordinary cohort parameters (Principle V) and nothing on-chain knows where they came from. The rate source and the pricing step are trusted off-chain inputs and MUST be named as such (Principle XI).

Fiat figures shown to members are display metadata only, always marked approximate.

### IV. SKR Only
Stakes, pool, payouts and fees are all SKR. No second asset, no swap, no conversion path anywhere in the system.

### V. Parameters Live on the Cohort
`reps_per_day`, `duration_days`, `day_length`, the exercise, the form (from the toes, or knees allowed), the boundary instant, the join cutoff and the stake chip amounts are fixed at cohort creation and read from the account, never from constants. A running cohort's rules can never move under its members. The list is not a closed set: anything that decides how a cohort behaves belongs on the cohort, not in a constant.

### VI. One Clock
Day boundaries are a single instant shared by every member of a cohort — 05:00 UTC — derived from chain time. No timezone, offset or locale exists anywhere in the system, and the streak day resets on that same instant. Time is presented to members as a countdown, never as a calendar date.

### VII. Tests Precede Logic (NON-NEGOTIABLE)
Settlement vectors and state invariants exist as failing tests before the instruction they describe is written.

### VIII. Idempotent, Chunkable Automation
Every automation-callable instruction is safe to call twice and safe to call with a page range. Two schedulers running at once must be harmless.

### IX. Every State Has a Screen
Every state in the specification is reachable in the UI or provably unreachable in the contract. A state a member can enter and not see is a bug report waiting to be filed.

### X. Camera Frames Never Leave the Device
No upload, no server-side inference, no video persistence, no human review. This is not a performance choice; it is the product's promise.

**One narrow exception, for rep recognition only.** So counting can be tested against what really happened and a knee classifier can be trained, **Holdout Lab** — a separate tester app, installed only on the team's and consenting testers' phones and never published or distributed to members — MAY save the camera stream of a sitting as a video file, on these terms and no others:

- **Holdout Lab only.** The member app MUST NOT contain the recorder in any build, developer or release, and a build check MUST fail any member-app build that does. The member app never persists a frame.
- **Video only, no audio** — the microphone rule of FR-022a is untouched.
- **On that device only**, in the app's private storage. The app never uploads, shares or sends a recording anywhere.
- **Pulled by the team over USB** (`adb`), labelled beside the landmark trace of the same sitting, and **deleted from the phone** once pulled.
- **Only people who agreed in writing to be filmed** — the team and consenting beta testers. A recording of anyone else is deleted unused.

Members of the public sharing their own recordings as training data is a separate, later decision and is **not** permitted by this exception.

### XI. Weaknesses Documented, Not Hidden
Anything faked, stubbed, flagged off or left trusting is named in the README with its real fix beside it. No submission material — demo video or pitch deck — hides or contradicts what the README names. A judge who discovers a shortcut reads deception; a judge who is told reads competence. Under the hackathon rules, misleading submission materials also disqualify the entry.

### XII. The Build Demonstrates Itself
Judges may install the submitted build on any day of judging, 13 October – 9 November 2026, and the build stays live with no manual intervention until winners are announced on 11 November 2026. Anyone installing it in that period must find a populated, live, visibly thinning Holdout and a free Daily 25 they can do immediately — never an empty state, never "come back Monday".

Those cohorts MAY be populated by scripted members, and only on these terms: the scripting is disclosed in the app, the README, the demo video and the pitch deck, and every scripted member is labelled wherever it appears — rosters and leaderboards included. An unlabelled scripted member is deception, not demonstration.

### XIII. Honest Language
"Prize", never "winnings", "returns", "yield" or "earnings"; never "bet", "wager", "gamble", "odds" or "luck". No fiat figure presented as a guarantee of what a member will receive. No leaderboard shows or ranks any stake, prize or payout amount — members are ranked on discipline, never on money. The app states plainly that it runs on a test network with a test token.

## Hackathon Constraints

- **Devnet only**, with a mock SKR mint. Stated in the README and in the app.
- **Delivered as a signed APK** with the submission. Publishing on the Solana dApp Store and any mainnet deployment are post-hackathon work; a winning app must be published on the dApp Store within 30 calendar days of the winners' announcement.
- **Two staked leagues** — the **Easy** 7-day and the **Hard** 21-day **Holdout**, 25 reps a day — over **the Daily 25**, free and open to everyone, which builds a cosmetic streak that unlocks nothing, appears on no leaderboard and is never paid against, on any network. A forfeit does not break a streak; the streak may be kept through a missed day by a freeze earned from consecutive active days, which never covers a Holdout day. How reps are judged (from the toes, or knees allowed) is the league's **form**, copied to each cohort (Principle V). (Members read "the Daily 25" and "a Holdout"; the contract stores a cohort in a league — spec §1a.)
- **Leaderboards are derived only from on-chain progress** — no server and no device-reported figure ever feeds a ranking.
- **Submission due 13 October 2026, 01:59 CEST** (12 October, 23:59 UTC; moved from 9 October, 08:59 CEST) — no late entries. It contains a functional APK that runs on a real device, a GitHub repository someone else can clone and run, a demo video of about three minutes and a pitch deck. Judges score completion from the demo video and technical depth from the commit history, so the full cycle is shown from the real app on devnet — never a mock-up. Because a judged 7-day Holdout cannot finish before the deadline, the video MAY show the cycle on a cohort of **the short test lobby** below, and says so on screen.
- **A short test lobby, devnet only, kept running, labelled, open to anyone.** So the whole cycle — joining, daily sittings, a forfeit, settlement and the claim — can be tested by **a single person alone** and shown in the submission video, the devnet program MAY be built with the `test-leagues` feature and the admin MAY create a test league (id 100 and up) with a compressed day. Its cohorts keep opening alongside the real 7-day and 21-day lobbies through the whole live period, so the devnet build always offers a test cohort to join. The league is created by the admin alone; every test cohort is labelled as a short test cohort everywhere it appears in the app, rosters and leaderboards included; and it is disclosed in the README, the demo video and the pitch deck (Principle XI). The judged 7-day and 21-day leagues keep real 24-hour days and never share a cohort with it. **A mainnet build never carries the `test-leagues` feature.**
- **Contract upgrade authority is retained** through the live period, for defect fixes only. An upgrade never changes the rules, parameters or stored layout of an existing cohort, membership or progress record (Principle V) and never moves funds outside the rule-driven instructions (Principle I). The authority is named in the README with its real fix — revoking it once the contract is final (Principle XI).
- **Deliberate flags, all named in the README:** `ladder_gate_enabled=false` (both leagues open), `genesis_gate_enabled=false` (testers have no Seeker), non-default `day_seconds` for compressed test cohorts. Flags disable checks; they never delete the logic behind them.
- **Out of scope by decision, not by omission:** geo-blocking, terms of service, third-party audit, free cohorts, calibration, Swelfie, share cards, badges, daily-streak or server-fed leaderboards, conditional pre-joining (a lobby place that depends on clearing the current cohort — the unconditional lobby place is in scope), cohort cancellation, demotion, any payment for the Daily 25, a dApp Store listing, mainnet deployment.

## Development Workflow

- Order is `/speckit-constitution` → `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`. Stack, accounts, PDAs and instruction signatures belong to `/speckit-plan`, never to the spec.
- Risk spikes precede implementation: wallet signing on real hardware, rep counting against real video including deliberate cheating, and a full cohort cycle on a manipulated clock.
- Published settlement vectors and every invariant listed in spec §8.4 are the first test suite, written before business logic. **Spec §8.4 is the authoritative list** — INV-1…INV-9 at this version — and an invariant added there is a new failing test, never an optional extra.

## Governance

This constitution supersedes other practice in this repository. Anything not required by a scenario in `specs/001-holdout-hackathon/spec.md` is out of scope; additions are made by amending the spec, not by writing code. Amendments are recorded here with a version bump and a line in `docs/holdout_decisions.md`. The deadline is what makes scope discipline load-bearing rather than decorative.

**Version**: 2.1.0 | **Ratified**: 2026-09-15 | **Last Amended**: 2026-10-09
