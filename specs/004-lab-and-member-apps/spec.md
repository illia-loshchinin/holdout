# Feature Specification: Holdout Lab and the Member App

**Feature Branch**: `004-lab-and-member-apps`

**Created**: 2026-10-09

**Status**: Draft

**Input**: User description: "Split Holdout into two apps from one codebase: Holdout Lab, a tester app for recording and labelling push-up sets to train and fix rep counting, and Holdout, the member app that is deployed and submitted. … No wallet and no chain … Holdout (member app): everything a member uses, and nothing else … It also loses every judge-only part, because the submission must be the app's final state and the demo video is sent as a link … It keeps … the short test lobby (a labelled 30-minute Holdout anyone can join, no longer described as for judges). Known weaknesses are documented in the README only. Amends constitution X's recording exception (debug builds → the Lab app only), 001 FR-001a/FR-044/FR-066/AC-19 (the in-app walkthrough), 001 FR-044a wording, 002 FR-023/FR-025/FR-026 (recording and labelling in debug builds → in Lab), and 003 FR-010 (README only). Must fit the 13 Oct 01:59 CEST submission."

## Context

Today there is one app. The tools the team uses to improve rep counting live inside it behind developer builds and test switches on the member's own screens:
- a landmark trace for every sitting;
- an optional video;
- a form for labelling what was really done;
- a clean-screen switch and a counting-mode switch;
- a reset;
- the counter's readouts.

The app also carries parts made only for judges: an *About · for judges* screen, an in-app walkthrough video, and *For judges* links reachable before a wallet is connected.

The product owner has decided two things:

1. **Counting work moves to its own app, Holdout Lab.** It's for the team and consenting testers only. It records and labels sets with no wallet and no chain, and counts them exactly as the member app does, so every counting fix is made once and reaches members.
2. **The member app holds only what members use.** The submission must be the app's final state, and the demo video goes to judges as a link. So nothing in the member app is for judges only. The disclosures every member needs stay, and so does the short test lobby, as an ordinary labelled offer.

This feature amends:

| Where | Today | After this feature |
|---|---|---|
| Constitution X (recording exception) | A **debug build** may record video | **Holdout Lab** may record video; the member app never can, in any build |
| 001 FR-001a | The walkthrough is the one thing usable before a wallet | Nothing is usable before a wallet but the connect screen and the no-wallet help |
| 001 FR-044, FR-066, AC-19 | A recorded walkthrough inside the app | The full cycle is shown in the demo video, sent as a link with the submission |
| 001 FR-044a | A short test lobby "kept running for judges" | A short test lobby kept running, labelled, open to anyone |
| 002 FR-023, FR-025, FR-026 | Traces, labelling and video in debug builds | In Holdout Lab |
| 003 FR-010 | The Seeker ID note shown in About and the README | In the README only |

The constitution changes through `/speckit-constitution`; 001, 002 and 003 change through `/speckit-clarify` on each.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A tester records and labels a set in Holdout Lab (Priority: P1)

A tester opens Holdout Lab, an app of its own beside Holdout on their phone. They choose how the set is counted — the Daily 25, an Easy Holdout or a Hard Holdout — and follow the same set-up guide a member sees. They do their push-ups while the counter's readouts show what it is reading. When they stop, they say what they really did. The team later pulls the set off the phone with the same tools as today and uses it to find and fix counting mistakes.

**Why this priority**: Fixing counting is the reason for the split, and the tools must keep working through the change.

**Independent Test**: Install Holdout Lab alone on a phone with no wallet app. Record one set in each counting mode, label each, pull them to a computer, and open them in the labelling page.

**Acceptance Scenarios**:

1. **Given** a phone with Holdout Lab installed and no wallet app, **When** the tester opens it, **Then** they reach the counting-mode choice without being asked to connect anything.
2. **Given** the tester picks Easy Holdout, **When** they set up and do a set, **Then** it is placed, checked and counted exactly as an Easy Holdout sitting in the member app, and the counter's readouts are on screen.
3. **Given** a set ends by Stop or by reaching the target, **When** it ends, **Then** the tester is asked what they really did, as today, and their answer is saved with the set.
4. **Given** the tester turned video recording on, after the on-screen reminder that only people who agreed in writing may be filmed, **When** they record a set, **Then** a video without sound is saved beside that set's trace on the phone.
5. **Given** sets recorded in Holdout Lab, **When** the team runs the pull and labelling tools, **Then** the sets arrive and open as sets recorded before this feature did.

---

### User Story 2 - A member's app never records them (Priority: P1)

A member installs Holdout. Nothing in it can save their camera, keep a trace of their body, ask them to label reps, or show tester switches, in any build the team makes of it. The promise "camera frames never leave the device" becomes simpler: the member app never keeps a frame at all.

**Why this priority**: It is the product's privacy promise (constitution X), and the submitted APK is the member app.

**Independent Test**: Build the member app in both its developer and release forms. Inspect both for the recorder, the labelling form and the tester switches, and do sittings on a phone: nothing is saved, and no tester control appears.

**Acceptance Scenarios**:

1. **Given** any build of the member app, **When** it is inspected, **Then** it contains no recorder, no trace or video saving, no labelling form, no tester switch and no counter readout.
2. **Given** a member finishes or stops a sitting, **When** the sitting ends, **Then** no labelling question appears and nothing about the sitting is saved beyond the banked reps the member app already keeps.
3. **Given** a build of the member app that accidentally includes any tester feature, **When** it is built, **Then** the build fails.

---

### User Story 3 - The member app is the product, with nothing for judges only (Priority: P1)

Anyone who installs Holdout, a judge included, gets the app a member gets:
- the connect screen;
- the tour, with the scripted-member disclosure;
- Today, Holdouts and the short test lobby, labelled as a short test Holdout.

There is no screen, link or video meant only for judges. The full cycle is shown in the demo video, which is sent as a link with the submission.

**Why this priority**: The submission is judged as the app's final state.

**Independent Test**: Walk every screen of a release build, before and after connecting a wallet. No screen, link or text is addressed to judges, and every required disclosure is present.

**Acceptance Scenarios**:

1. **Given** the connect screen, **When** no wallet is connected, **Then** the only actions are connecting a wallet and, when the phone has no wallet app, help getting one; there is no video and no link for judges.
2. **Given** a connected member, **When** they browse every screen, **Then** none is titled or labelled for judges, and no in-app video player exists.
3. **Given** a member on first run, **When** the tour runs, **Then** it still says that some members are scripted and that the app runs on a test network with a test token.
4. **Given** the short test lobby has a Holdout open, **When** a member sees it, **Then** it is offered like any other Holdout, labelled as a short test Holdout with its day length, and described without any mention of judges.

---

### User Story 4 - A counting fix reaches both apps at once (Priority: P2)

The team finds a counting mistake in a Lab set and fixes it. The next build of each app counts the same way, so a member is held to exactly the rule the team tested.

**Why this priority**: It is what makes Lab useful rather than a fork that drifts.

**Independent Test**: Replay every committed labelled set through both apps' counting and compare the results.

**Acceptance Scenarios**:

1. **Given** the committed labelled sets, **When** each is counted as the Lab app counts it and as the member app counts it, **Then** the counts and refusals are identical for every set.
2. **Given** a change to the counting rules, **When** both apps are built from the same revision, **Then** both carry the change; neither can be built with a different version of the rules.

---

### Edge Cases

- **Traces still inside the member app on testers' phones** from before the split. They are pulled once with today's tools before the member app is updated; after the update the member app never writes another.
- **Both apps installed on one phone.** They share no data, settings or counts. Lab sets never appear in the member app's streak or banked reps.
- **Lab with video on, for a person who has not agreed to be filmed.** Lab cannot know. The switch carries the consent reminder, starts off on every new install, and the team deletes such a recording unused (constitution X).
- **The phone runs out of space during a Lab set.** The set is still counted on screen; Lab says the trace or video could not be saved.
- **Holdout Lab reaching members.** It is installed directly by the team on testers' phones and is never published to a store or linked from the member app, the README's install steps or the submission.
- **A Hard Holdout set in Lab.** It is counted with the Hard checks, but nothing is staked and no day is submitted: Lab never connects to a wallet or a network.

## Requirements *(mandatory)*

### Functional Requirements

**Holdout Lab**

- **FR-001** Holdout Lab is a separate app that installs beside Holdout on the same phone, with its own name ("Holdout Lab") and an icon a person can tell apart from Holdout's at a glance.
- **FR-002** Holdout Lab needs no wallet and no network. It never connects to a wallet, never reads or writes any chain, and works fully offline.
- **FR-003** Holdout Lab opens straight to choosing a counting mode — the Daily 25, an Easy Holdout or a Hard Holdout — then the same set-up guide, placement checks and counting a member gets in that mode.
- **FR-004** Every Lab set saves a trace — the body landmarks and the counter's decisions for each moment, never a picture — in the format and place the team's pull and labelling tools read today (002 FR-023).
- **FR-005** Holdout Lab has a video switch, off by default and remembered on the phone, whose label says that only people who agreed in writing to be filmed may be recorded. With it on, each set also saves a video without sound, on the phone only (constitution X, 002 FR-026).
- **FR-006** When a Lab set ends, by Stop or by reaching the target, the tester is asked what they really did, exactly as 002 FR-025 describes today, and the answer is saved with the set.
- **FR-007** Holdout Lab keeps today's tester controls:
  - the counter's readouts during a set;
  - a clean-screen mode with no overlay, for training recordings;
  - starting a fresh set at any time.
- **FR-008** The team's tools for pulling and labelling sets work on Lab sets without any change to the sets themselves; only where the tools look for them changes.
- **FR-009** Holdout Lab is installed directly by the team, only on phones of the team and testers who agreed in writing. It is never published, never linked from the member app or its README, and never part of the submission.

**Holdout, the member app**

- **FR-010** No build of the member app, developer or release, contains a recorder, trace saving, video saving, the labelling form, a tester switch or the counter's readouts. A build that contains any of them fails (this replaces constitution X's "a build check MUST fail the release build").
- **FR-011** Both apps count with the same rules from the same source: placement, the set-up checks, rep counting and refusals. A change to the rules reaches both apps in their next build, and neither can be built with a different version of them.
- **FR-012** The member app has no screen, link, text or video meant only for judges:
  - no *About · for judges*;
  - no walkthrough player or bundled video;
  - no *For judges* link.

  Before a wallet is connected, the only things reachable are the connect screen and, when the phone has no wallet app, help getting one (amends 001 FR-001a).
- **FR-013** The member app keeps every disclosure a member needs:
  - *"Test network, test token."*;
  - the first-run note that some members are scripted;
  - the SCRIPTED label on every scripted member;
  - the TEST COHORT label, with its day length, on every short test Holdout.
- **FR-014** The member app keeps the short test lobby: short test Holdouts open continuously, offered beside the 7-day and 21-day Holdouts to anyone, labelled as short test Holdouts and described without reference to judges (amends 001 FR-044a's wording only; its rules are unchanged).
- **FR-015** Known weaknesses are documented in the README only. The member app has no weaknesses screen (amends 003 FR-010; constitution XI requires the README only).
- **FR-016** Nothing a member uses changes beyond FR-010 and FR-012: every other screen, rule and transaction stays as it is.

### Key Entities

- **Lab set**: one recorded set in Holdout Lab — its counting mode, its trace, optionally its video, and the tester's label. Lives on the tester's phone until pulled.
- **Counting rules**: the single shared definition of placement, checks and rep counting that both apps use.
- **Member app build**: any build of Holdout, developer or release. Never contains a Lab feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** A tester goes from opening Holdout Lab to counting a set in under 60 seconds, with no wallet app on the phone.
- **SC-002** Inspection of the member app's developer and release builds finds 0 Lab features, and a deliberately added one fails the build.
- **SC-003** Every labelled set the team has committed counts identically under both apps' counting: 100% the same counts and refusals.
- **SC-004** A walk through every screen of the member app's release build, before and after connecting a wallet, finds 0 screens, links or texts addressed to judges, and every disclosure in FR-013 present.
- **SC-005** With both apps on one phone, 10 Lab sets change nothing in the member app's banked reps, streak or settings.
- **SC-006** A set recorded in Holdout Lab is pulled and opened in the labelling page with the same steps and in the same time as a set recorded before the split.

## Assumptions

- **Same codebase.** Both apps are built from one repository and one revision. How (build variants or separate modules) is decided in `/speckit-plan`.
- **Distribution.** Testers get Holdout Lab by direct install from the team. Testers already in the beta have agreed in writing to be filmed (the existing consent).
- **Same counting.** Counting in Lab for a staked mode uses the same checks as a staked sitting, but nothing is staked; "the counting-mode switch" of today becomes Lab's mode choice.
- **The demo video** is hosted elsewhere and linked in the submission form. Neither app plays or links it.
- **Names in the app.** The short test lobby keeps its existing name and labels toward members; only text that addresses judges goes.
- **Out of scope:** new recording features, uploading sets from Lab, any change to rep-counting rules, the contract or the crank, and the member app's navigation redesign (a later feature).
- **The deadline.** The member app built from this feature is the one submitted by 13 October 2026, 01:59 CEST.
