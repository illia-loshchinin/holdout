# Holdout — constitution (hackathon build)

**Date:** 2026-09-15 · **Scope:** the CLOCK IN submission only, devnet only

> **Superseded — historical source draft.** The ratified constitution is `.specify/memory/constitution.md` (v1.2.0), Mapping: P1 + P5 → I, P2 → II, P3 → III, P4 → IV, P6 → V, **VI One Clock is new**, P8 → VII, P7 → VIII, P9–P13 → IX–XIII, P14 folded into XIII, P15 into Governance. It also differs in substance: III permits off-chain chip pricing only; V adds the boundary instant, join cutoff and chip amounts; VI (One Clock) is new; XII requires scripted members to be disclosed and labelled; XIII forbids money on leaderboards. Do not paste this file into `/speckit-constitution` again.

> SpecKit path: `.specify/memory/constitution.md` — paste this into `/speckit.constitution`.
> Every later phase (`/speckit.specify`, `.plan`, `.tasks`, `.analyze`) is evaluated against these principles. A plan that violates one is wrong even if it works.

---

## I. Money principles

**P1. The contract is the only authority on funds.** No off-chain service may move, hold, or decide the fate of a stake.

**P2. Forfeiture is derived, never declared.** A member is in breach if and only if `now > deadline_of(days_completed)` — computable from on-chain state alone. The keeper *materialises* breach into a transfer; it never *creates* breach. A missed keeper run may delay money and displayed counters; it may never change who won or lost.

**P3. No oracle in contract logic.** No instruction reads a price. Fiat figures exist only as display metadata supplied by the client and are never an input to any branch, amount, or validation.

**P4. SKR is the only asset.** Stakes, pool, payouts and fees are all SKR. No second token, no swap, no conversion path anywhere in the system.

**P5. Principal is segregated from the prize pool at every instant.** Only forfeited stakes ever enter the pool. A shortfall must be impossible by construction, not by arithmetic care.

---

## II. Correctness principles

**P6. Cohort parameters live on the cohort, never in constants.** `reps_per_day`, `duration_days`, `day_seconds` are fixed at creation and read from the account. A running cohort's rules can never move under its members.

**P7. Every keeper-callable instruction is idempotent and chunkable.** Calling twice is a no-op; calling with a page range is safe. Two keepers running simultaneously must be harmless.

**P8. Tests precede logic.** The settlement vectors and the state invariants exist as failing tests before the instruction they describe is written.

**P9. Every state in the specification has a screen, or is provably unreachable.** A state a user can enter and not see is a bug report waiting to be filed.

---

## III. Trust and privacy principles

**P10. Camera frames never leave the device.** No upload, no server-side inference, no video persistence, no human review. This is not a performance choice; it is the product's promise.

**P11. Known weaknesses are documented, not hidden.** Anything faked, stubbed, or left trusting for the hackathon is named in the README with the real fix beside it. A judge who discovers a shortcut reads deception; a judge who is told reads competence.

---

## IV. Presentation principles

**P12. The submitted APK must be self-demonstrating for the whole judging month.** Judging runs weeks after the last real cohort ends. Anyone installing the app on any day must see a populated, live, visibly thinning cohort — never an empty state, never a "come back Monday".

**P13. Honest language in the UI.** "Prize", never "winnings", "returns", "yield" or "earnings". No fiat figure presented as a guarantee of what a member will receive.

**P14. Devnet is stated, not implied.** The app and the README say plainly that this runs on devnet with a test token.

---

## V. Scope discipline

**P15. Anything not required by a scenario in `spec.md` is out of scope.** Additions are made by amending the spec, not by writing code. The deadline is the constraint that makes this principle load-bearing rather than decorative.
