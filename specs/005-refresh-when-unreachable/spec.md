# Feature Specification: Check the Chain Again

**Feature Branch**: `005-refresh-when-unreachable`

**Created**: 2026-10-11

**Status**: In review (implemented; the waiting-day walk, T015 and T016, is open)

**Input**: User description: "Refresh while the chain can't be reached. Product owner's request (2026-10-11): when a member's app cannot reach the chain (the app's own "the chain can't be reached" state — the last chain read failed), they must be able to refresh the screen they are on to see whether the network is available again. Context: during a local test the Today/Home screen showed "Done — waiting for network" (spec 001 FR-024b, ES-3b) after `adb reverse` to the local validator dropped; the app re-polls on its own schedule and the member had nothing to tap to re-check. Scope to decide: pull-to-refresh on Home/Today, cohort detail and forfeit screens, and/or a "Check again" action on the "waiting for network"/"can't be reached" states. The refresh does an immediate chain read; when the chain answers and a completed day is waiting, the existing "Done — tap to submit" appears; it never submits without the member's tap in Phase 1. Member copy follows the design book's copy rules: never "You're offline"; name the network, as the existing "Devnet can't be reached" wording does. Extends spec 001 (FR-024b, ES-3b, AC-18); base branch 001-holdout-hackathon."

## Context

The app reads the chain on its own schedule while it is in front: a 30-second pulse on the screens that show live chain data — every screen this feature covers is one — backing off to at most 5 minutes while reads fail (001 FR-071, since 2026-10-13), chain time every 10 minutes (or sooner while it has none), and at once when the phone reports a network coming back (001 research R8, T098). When reads fail — two snapshot reads in a row, or the first while there is no view yet, each call asked once more before a read gives up (001 FR-071, since 2026-10-13; before, one failed read) — the app is in its *"the chain can't be reached"* state (001 FR-024b): the home shows *"Done — waiting for network"* for a finished day, or *"Devnet can't be reached right now…"* before a sitting, or *"Devnet unreachable — the Daily 25 still works"* with no Holdout shown.

The phone does not always report the chain coming back. In the owner's local test the phone's own network never dropped: the link to the validator did, then returned, and nothing told the app. The member waited up to 30 seconds with nothing to tap. This feature gives the member a way to ask now.

It changes no rule of spec 001. A check is the same read the app already makes on its schedule, made at the member's request; what the app does when the chain answers — offering *"Done — tap to submit"*, or the wallet opening by itself under FR-024b's Phase 2 — is unchanged. It adds to 001 FR-024b and ES-3b; AC-18 is unchanged.

## Clarifications

### Session 2026-10-11

- Q: Where can a member make the app check the chain again? → A: Both: pull-to-refresh on the home (every state), a Holdout's details and the forfeit screen, and a visible **Check again** button on every "can't be reached" line.
- Q: Does the pull work all the time, or only while the chain can't be reached? → A: Only while the chain can't be reached. While it answers, the pull does nothing and the 30-second schedule keeps the screens fresh. *(The schedule is the 30-second pulse of 001 FR-071 since 2026-10-13.)*
- Q: What does the screen say when a check ends and Devnet still can't be reached? → A: *"Devnet still can't be reached."*, until the chain answers; the Check again button stays.
- Q: Do a Holdout's details and the forfeit screen say the chain can't be reached? → A: Yes: while it can't, they show the same one line, *"Devnet can't be reached right now."*, with Check again, as well as the pull.
- Q: When a member's check finds Devnet back and a finished day is waiting, does the wallet open by itself or does "Done — tap to submit" wait for a tap? → A: **The wallet opens by itself**, as 001 FR-024b's automatic send (Phase 2, built 2026-10-05) does whenever the chain answers again; after a decline, *"Done — tap to submit"*. A check adds no send of its own and changes none. *(After `/speckit-analyze`: the earlier wording expected the tap first, which the built app does not do. The same pass also fixed FR-007's source, SC-003's method and the unknown-chain-time edge case.)*
- Q: Does the home say Devnet can't be reached when it shows an earlier read and none of its three existing lines applies (an earlier read with no Holdout running, or a running Holdout whose day is done)? → A: **Yes**, as User Story 2 already says. The phone walk found it silent there, with only the pull, which nobody would find. It shows *"Devnet can't be reached right now."* with Check again, the same line as details and forfeit. Where that line is used, a failed check changes the line itself to *"Devnet still can't be reached."*, so there is one line and nothing moves. *(Consistency fix from the phone walk; no new decision.)*

### Session 2026-10-12

- Q: Does a check start its own read of the roster when one of that Holdout's roster is already out, and how often is a roster read otherwise? → A: **It takes the read already out.** A check reads the roster of the Holdout shown at once unless a read of that roster is already out, whose answer then serves the check: one roster read per Holdout at a time, given up after 60 seconds. The running Holdout's roster is read by one app-wide loop every 30 seconds, a finished one every 10 minutes. Decided by the product owner's delegate on 12 October from the PR #62 review. *(Superseded the same evening, as built in `b2f62ea` and recorded 2026-10-13: a check's roster read starts its own — see Session 2026-10-13.)*
- Q: On which tab screens do the unreachable line and Check again show, now that the app has four tabs? → A: **Today (the home), the Holdouts tab's root, a Holdout's details (also as Standing's root) and the forfeit screen; Standing's empty state has none.** Recorded as built: the Holdouts root carries the line and the button, with no pull. Decided by the product owner's delegate on 12 October from the PR #62 review.

### Session 2026-10-13

- Q: When is the app in its "the chain can't be reached" state, now that one dropped call showed the line? (001 FR-071) → A: **Once two snapshot reads in a row have failed**, each call asked once more after about 1.5 seconds; until then the last view stays and no line, button or pull shows. With no view yet (the first read, another wallet), the first failed read is enough. Decided by the product owner on 13 October (PR #71).
- Q: Does a check join a snapshot read already out, and does the 30-second pass join a check's? (FR-003, FR-005, 001 FR-071) → A: **A check reads at once and joins nothing; the 30-second pass joins whatever read is out, a check's included**, waiting at most 60 seconds on it. A read started earlier, maybe while the chain was down, cannot answer a check, which promises a fresh read. Recorded from speckit-analyze A2 of PR #71.
- Q: Does a check start its own read of the roster when one is already out? (FR-003) → A: **Yes — the check's roster read is forced.** A read already out may be hanging, and the check promises a fresh roster; the newer answer wins either way. Built on 12 October in the evening (`b2f62ea`), superseding that day's first answer; recorded from speckit-analyze A3 of PR #71.
- Q: How long does the chain take to come back by itself now that the app reads by change, and what does a check read? (FR-003, SC-001, 001 FR-071) → A: **Up to 5 minutes, which is what Check again is for.** The app's schedule is a 30-second pulse that runs only on screens showing live chain data — all of this feature's — and, while reads fail, waits 60, 120, 240 seconds and at most 5 minutes; a check that answers starts that wait over. A check's snapshot read is a fresh one: it also reads the wallet's memberships and its own settled cohorts. A roster is read when its cohort's counters move, on a 30-second age only while its Holdout runs, otherwise at the latest every 10 minutes, lobbies included. Decided by the product owner on 13 October (001 FR-071, PR #72).

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

1. **Given** the home of a running Holdout while Devnet can't be reached, **When** the member pulls down or taps Check again, **Then** the app reads the chain snapshot, chain time and that Holdout's roster at once, each read its own (FR-003), and the counter and pool redraw from the answer.
2. **Given** the home with no Holdout reading *"Devnet unreachable — the Daily 25 still works"*, **When** the member checks again and the chain answers, **Then** the Holdouts open to join and running now appear.
3. **Given** a Holdout's details or the forfeit screen while Devnet can't be reached, **Then** it shows *"Devnet can't be reached right now."* with Check again; **When** the member checks and the chain answers, **Then** the line goes and the screen redraws.
4. **Given** any of these screens while the chain answers, **When** the member pulls down, **Then** nothing happens: no indicator and no read.

---

### Edge Cases

- **The phone's network is down, not only the chain.** The check fails as any read does; the screen says Devnet still can't be reached. It never says "You're offline": the app knows only that the chain did not answer.
- **The chain's time is still unknown** (a start with no network and no saved reading). A check, offered while the snapshot read fails, asks for chain time too; until it answers, the screens say what they say today with no time known. If the snapshot answers but chain time is still unknown, no check is offered: the app's own retry asks again, at once when the network returns.
- **A check answers after the member has left the screen or switched wallet.** Its answer is used only as the app's scheduled reads are: never for another wallet, and never over a newer answer.
- **A check races the scheduled read.** A check started while the scheduled read is out runs alongside it, and the newer answer wins; a scheduled pass that comes while the check's read is out joins it rather than starting another (001 FR-071).
- **An action is under way** (the wallet is open for a join, a submit or a claim). The check reads only; it does not cancel or repeat the action.
- **The deadline passes while the day waits.** A check that finds the chain answering past that day's deadline leads to *"Done — too late to send"* wherever 001 FR-024b shows it; the check never offers a submit FR-024b would not.
- **The chain answers again by itself** (the app's pulse — every 30 seconds, backing off to at most 5 minutes while reads fail — or the network's return) while the member is on the screen: the line, the button and the pull go away, with no check needed.
- **Repeated pulls.** The member can check as often as they like; a pull while a check is under way joins it rather than starting another.
- **The Daily 25** needs no connection; its screens gain no check.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** While the app is in its *"the chain can't be reached"* state, a member can ask it to read the chain now by pulling down: Today (the home) in every state that shows chain data (no Holdout, a running Holdout, the waiting day), a Holdout's details (also as the Standing tab's root), and the forfeit screen; the Holdouts tab's root carries the line and the button of FR-002 but no pull, and Standing's empty state, with no chain figure of its own to refresh, carries none of them. While the chain answers, the pull does nothing — no indicator, no read — and the app's own schedule keeps these screens fresh.
- **FR-002** In the same state, the place that says so carries a visible **Check again** button, so the check does not depend on knowing the gesture: the home's *"Done — waiting for network"*, its *"Devnet can't be reached right now…"* notice, and *"Devnet unreachable — the Daily 25 still works"*. Every other screen in FR-001 that would otherwise say nothing about it shows one line, *"Devnet can't be reached right now."*, with the same button. That covers a Holdout's details, the forfeit screen, the Holdouts tab's root, the home showing an earlier read with no Holdout running, and the home of a running Holdout whose day is done. On those screens a failed check rewrites that line as *"Devnet still can't be reached."* instead of adding a second line. The line, the button and the pull go once the chain answers, by a check or by itself.
- **FR-003** A check reads what the app's own schedule reads for that screen — the chain snapshot and chain time, and the roster of the Holdout shown — at once — its own snapshot read, never one already out, and a fresh one that also reads the wallet's memberships and its own settled cohorts (001 FR-071), and its own roster read even when one of that roster is already out, since that one may be hanging (other asks share one roster read per Holdout at a time, given up after 60 seconds); otherwise a roster is read when its cohort's counters move, on a 30-second age only while its Holdout runs, and at the latest every 10 minutes (001 FR-071). A check that answers starts the pulse's wait over. It applies the answer exactly as a scheduled read's (newest answer wins, never another wallet's). It changes no schedule and no rule of 001 FR-024b.
- **FR-004** A check sends nothing of its own. If it finds the chain answering and a completed day is waiting, the app does what 001 FR-024b does whenever the chain answers again: the wallet's approval opens by itself (the Phase 2 send, built), and the day is submitted only once the member approves there; after a decline the home shows *"Done — tap to submit"*. This feature changes neither.
- **FR-005** While a check is under way the screen shows that it is checking, and a second request joins it rather than starting another read.
- **FR-006** When a check ends and the chain still cannot be reached, the screen says so in one line naming the network — *"Devnet still can't be reached."* — and the member can check again. The line clears once the chain answers.
- **FR-007** Member copy follows the design book's voice and 001 FR-024b's wording rule: it names the network, never says "You're offline", "no internet" or "connection error", and never blames the member.
- **FR-008** The check runs only while the app is in front; nothing is read in the background, as today.

### Key Entities

- **Check**: a member's request to read the chain now. It is under way or not; when it ends, the chain either answered or did not. It is not kept on the phone.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** With a finished day waiting and the chain back, a member sees the day move past *"Done — waiting for network"* within 5 seconds of asking, instead of waiting for the app's pulse — up to 5 minutes while it backs off.
- **SC-002** While Devnet can't be reached, every screen listed in FR-001 responds to a pull or a Check again tap with a visible checking state within half a second; while it answers, a pull shows nothing.
- **SC-003** No check ever submits a day of its own: the check's code path calls no submit (code review), and in the quickstart walk a check that finds the chain back with a day waiting opens only the wallet approval FR-024b's automatic send opens, once.
- **SC-004** No member-facing string this feature adds contains "offline", "internet" or "error".

## Assumptions

- The day-complete screen, the lobby, the roster, the claim screen and the stake sheet carry no check. The day-complete screen already says the day is not sent yet, and *Back to today* lands on the home, which has one. The others are short visits from the home or details, which have one, and their own actions report a chain that does not answer.
- The refresh covers the chain only. Seeker ID names, the leaderboards and the faucet keep their own reads; the leaderboards already redraw on open.
- *"Devnet"* is the network's name from the deployment, as in today's copy; a local or another cluster's build shows its own name.
- Pull-to-refresh is the platform's standard gesture and indicator, styled with the design book's tokens.
- The owner's local `adb reverse` case is the main test: the phone's network stays up while the chain drops and returns.
