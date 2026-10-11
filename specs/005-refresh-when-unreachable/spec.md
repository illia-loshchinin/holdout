# Feature Specification: Check the Chain Again

**Feature Branch**: `005-refresh-when-unreachable`

**Created**: 2026-10-11

**Status**: In review (implemented; the phone checks T013 and T015 are open)

**Input**: User description: "Refresh while the chain can't be reached. Product owner's request (2026-10-11): when a member's app cannot reach the chain (the app's own "the chain can't be reached" state — the last chain read failed), they must be able to refresh the screen they are on to see whether the network is available again. Context: during a local test the Today/Home screen showed "Done — waiting for network" (spec 001 FR-024b, ES-3b) after `adb reverse` to the local validator dropped; the app re-polls on its own schedule and the member had nothing to tap to re-check. Scope to decide: pull-to-refresh on Home/Today, cohort detail and forfeit screens, and/or a "Check again" action on the "waiting for network"/"can't be reached" states. The refresh does an immediate chain read; when the chain answers and a completed day is waiting, the existing "Done — tap to submit" appears; it never submits without the member's tap in Phase 1. Member copy follows the design book's copy rules: never "You're offline"; name the network, as the existing "Devnet can't be reached" wording does. Extends spec 001 (FR-024b, ES-3b, AC-18); base branch 001-holdout-hackathon."

## Context

The app reads the chain on its own schedule while it is in front: its snapshot every 30 seconds, chain time every 10 minutes (or sooner while it has none), and at once when the phone reports a network coming back (001 research R8, T098). When a read fails, the app is in its *"the chain can't be reached"* state (001 FR-024b): the home shows *"Done — waiting for network"* for a finished day, or *"Devnet can't be reached right now…"* before a sitting, or *"Devnet unreachable — the Daily 25 still works"* with no Holdout shown.

The phone does not always report the chain coming back. In the owner's local test the phone's own network never dropped: the link to the validator did, then returned, and nothing told the app. The member waited up to 30 seconds with nothing to tap. This feature gives the member a way to ask now.

It changes no rule of spec 001. A check is the same read the app already makes on its schedule, made at the member's request; what the app does when the chain answers — offering *"Done — tap to submit"*, or the wallet opening by itself under FR-024b's Phase 2 — is unchanged. It adds to 001 FR-024b and ES-3b; AC-18 is unchanged.

## Clarifications

### Session 2026-10-11

- Q: Where can a member make the app check the chain again? → A: Both: pull-to-refresh on the home (every state), a Holdout's details and the forfeit screen, and a visible **Check again** button on every "can't be reached" line.
- Q: Does the pull work all the time, or only while the chain can't be reached? → A: Only while the chain can't be reached. While it answers, the pull does nothing and the 30-second schedule keeps the screens fresh.
- Q: What does the screen say when a check ends and Devnet still can't be reached? → A: *"Devnet still can't be reached."*, until the chain answers; the Check again button stays.
- Q: Do a Holdout's details and the forfeit screen say the chain can't be reached? → A: Yes: while it can't, they show the same one line, *"Devnet can't be reached right now."*, with Check again, as well as the pull.
- Q: When a member's check finds Devnet back and a finished day is waiting, does the wallet open by itself or does "Done — tap to submit" wait for a tap? → A: **The wallet opens by itself**, as 001 FR-024b's automatic send (Phase 2, built 2026-10-05) does whenever the chain answers again; after a decline, *"Done — tap to submit"*. A check adds no send of its own and changes none. *(After `/speckit-analyze`: the earlier wording expected the tap first, which the built app does not do. The same pass also fixed FR-007's source, SC-003's method and the unknown-chain-time edge case.)*

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Check again from the waiting day (Priority: P1)

A member finished today's reps while Devnet could not be reached. The home reads *"Done — waiting for network"*. They think the connection is back and want to know now, not in up to 30 seconds. They ask the app to check, see that it is checking, and either the day moves on to being sent (the chain answered) or the screen says Devnet still can't be reached.

**Why this priority**: it is the moment the owner hit: a finished day with a deadline running, and nothing to tap.

**Independent Test**: on a phone with a finished day waiting and the chain unreachable, restore the chain without touching the phone's network, then ask the app to check; the waiting line gives way within one read.

**Acceptance Scenarios**:

1. **Given** the home reads *"Done — waiting for network"* and the chain answers again, **When** the member checks again, **Then** the app reads the chain at once and the waiting line gives way to what FR-024b does once the chain answers: the wallet's approval opens by itself, and after a decline *"Done — tap to submit"*. Nothing is submitted without the member's approval in the wallet.
2. **Given** the home reads *"Done — waiting for network"* and the chain still cannot be reached, **When** the member checks again, **Then** the screen shows that it checked and still names the network as unreachable (*"Devnet still can't be reached."*), and the day stays waiting on the phone.
3. **Given** a check is already under way, **When** the member asks again, **Then** no second read starts and the screen still shows the one under way.

---

### User Story 2 - Check again from any screen that shows the chain (Priority: P2)

Devnet can't be reached and the member is on a screen built from chain data — the home with or without a running Holdout, a Holdout's details, the forfeit screen. The screen says so in one line with **Check again**, and they can also pull the screen down to check.

**Why this priority**: the same need outside the waiting day: standings, the prize pool and the forfeit's next Holdout come from the chain, and while it can't be reached the member sees the last answer and wants to know when it is back.

**Independent Test**: with the chain unreachable, on each listed screen, see the line, then pull down and tap Check again; each reads the chain at once and the screen redraws from the answer. With the chain answering, a pull does nothing.

**Acceptance Scenarios**:

1. **Given** the home of a running Holdout while Devnet can't be reached, **When** the member pulls down or taps Check again, **Then** the app reads the chain snapshot, chain time and that Holdout's roster at once, and the counter and pool redraw from the answer.
2. **Given** the home with no Holdout reading *"Devnet unreachable — the Daily 25 still works"*, **When** the member checks again and the chain answers, **Then** the Holdouts open to join and running now appear.
3. **Given** a Holdout's details or the forfeit screen while Devnet can't be reached, **Then** it shows *"Devnet can't be reached right now."* with Check again; **When** the member checks and the chain answers, **Then** the line goes and the screen redraws.
4. **Given** any of these screens while the chain answers, **When** the member pulls down, **Then** nothing happens: no indicator and no read.

---

### Edge Cases

- **The phone's network is down, not only the chain.** The check fails as any read does; the screen says Devnet still can't be reached. It never says "You're offline": the app knows only that the chain did not answer.
- **The chain's time is still unknown** (a start with no network and no saved reading). A check, offered while the snapshot read fails, asks for chain time too; until it answers, the screens say what they say today with no time known. If the snapshot answers but chain time is still unknown, no check is offered: the app's own retry asks again, at once when the network returns.
- **A check answers after the member has left the screen or switched wallet.** Its answer is used only as the app's scheduled reads are: never for another wallet, and never over a newer answer.
- **A check races the scheduled read.** Both may run; the newer answer wins, as today.
- **An action is under way** (the wallet is open for a join, a submit or a claim). The check reads only; it does not cancel or repeat the action.
- **The deadline passes while the day waits.** A check that finds the chain answering past that day's deadline leads to *"Done — too late to send"* wherever 001 FR-024b shows it; the check never offers a submit FR-024b would not.
- **The chain answers again by itself** (the 30-second read, or the network's return) while the member is on the screen: the line, the button and the pull go away, with no check needed.
- **Repeated pulls.** The member can check as often as they like; a pull while a check is under way joins it rather than starting another.
- **The Daily 25** needs no connection; its screens gain no check.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** While the app is in its *"the chain can't be reached"* state, a member can ask it to read the chain now by pulling down: the home in every state that shows chain data (no Holdout, a running Holdout, the waiting day), a Holdout's details, and the forfeit screen. While the chain answers, the pull does nothing — no indicator, no read — and the app's own schedule keeps these screens fresh.
- **FR-002** In the same state, the place that says so carries a visible **Check again** button, so the check does not depend on knowing the gesture: the home's *"Done — waiting for network"*, its *"Devnet can't be reached right now…"* notice, and *"Devnet unreachable — the Daily 25 still works"*. A Holdout's details and the forfeit screen, which say nothing about it today, show one line, *"Devnet can't be reached right now."*, with the same button. The line, the button and the pull go once the chain answers, by a check or by itself.
- **FR-003** A check reads what the app's own schedule reads for that screen — the chain snapshot and chain time, and the roster of the Holdout shown — at once, and applies the answer exactly as a scheduled read's (newest answer wins, never another wallet's). It changes no schedule and no rule of 001 FR-024b.
- **FR-004** A check sends nothing of its own. If it finds the chain answering and a completed day is waiting, the app does what 001 FR-024b does whenever the chain answers again: the wallet's approval opens by itself (the Phase 2 send, built), and the day is submitted only once the member approves there; after a decline the home shows *"Done — tap to submit"*. This feature changes neither.
- **FR-005** While a check is under way the screen shows that it is checking, and a second request joins it rather than starting another read.
- **FR-006** When a check ends and the chain still cannot be reached, the screen says so in one line naming the network — *"Devnet still can't be reached."* — and the member can check again. The line clears once the chain answers.
- **FR-007** Member copy follows the design book's voice and 001 FR-024b's wording rule: it names the network, never says "You're offline", "no internet" or "connection error", and never blames the member.
- **FR-008** The check runs only while the app is in front; nothing is read in the background, as today.

### Key Entities

- **Check**: a member's request to read the chain now. It is under way or not; when it ends, the chain either answered or did not. It is not kept on the phone.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** With a finished day waiting and the chain back, a member sees the day move past *"Done — waiting for network"* within 5 seconds of asking, instead of up to 30.
- **SC-002** While Devnet can't be reached, every screen listed in FR-001 responds to a pull or a Check again tap with a visible checking state within half a second; while it answers, a pull shows nothing.
- **SC-003** No check ever submits a day of its own: the check's code path calls no submit (code review), and in the quickstart walk a check that finds the chain back with a day waiting opens only the wallet approval FR-024b's automatic send opens, once.
- **SC-004** No member-facing string this feature adds contains "offline", "internet" or "error".

## Assumptions

- The refresh covers the chain only. Seeker ID names, the leaderboards and the faucet keep their own reads; the leaderboards already redraw on open.
- *"Devnet"* is the network's name from the deployment, as in today's copy; a local or another cluster's build shows its own name.
- Pull-to-refresh is the platform's standard gesture and indicator, styled with the design book's tokens.
- The owner's local `adb reverse` case is the main test: the phone's network stays up while the chain drops and returns.
