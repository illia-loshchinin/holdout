# Feature Specification: Seeker ID Names

**Feature Branch**: `003-seeker-id-names`

**Created**: 2026-10-09

**Status**: Draft

**Input**: User description: "Seeker ID names instead of raw wallet addresses. Holdout runs on Solana Mobile's Seeker, and every Seeker owner has a Seeker ID: a .skr name (e.g. alice.skr) that maps to their wallet. Members should see people by their Seeker ID rather than a shortened base58 address. (1) Your own identity … falling back to the shortened address. (2) Other members: rosters, leaderboards and any other list or line that identifies a member … (3) Names are display only … (4) … a read-only mainnet read, with no server … disclosed in the README weaknesses table and the About screen. (5) A wallet may hold several .skr names; the app shows one, chosen by a fixed rule … (6) Lookups are cached on the device … never blocks a screen and never shows an error … (7) Show the full name with its .skr suffix … (8) … the fallback is the common case and must look intentional. This amends spec 001 FR-063 … and the design book's RosterRow … Client only: no contract change, no crank change. Deadline: must fit the 13 Oct 01:59 CEST submission alongside the devnet deploy, so keep it small."

## Context

Holdout is built for Solana Mobile's Seeker. Every Seeker owner receives a **Seeker ID**: a name ending in `.skr` (for example `alice.skr`) that is registered once per device and points at a wallet the owner controls. Today the app identifies every member, the connected member included, by a shortened wallet address such as `7xKX…9fQ2`. A crowd of addresses is hard to read and says nothing about who is in it; "the crowd is the product" (design book, principle 1) reads better as a crowd of names.

This feature amends:

- `specs/001-holdout-hackathon` **FR-063**: leaderboards identify members by shortened wallet address **only**. After this feature they identify a member by Seeker ID when one exists, and by shortened address otherwise. The rest of FR-063 (no stake, prize or payout amount on any leaderboard) is unchanged.
- The design book's **Roster row** component ("one member of a cohort, by shortened address only"). The design book is a mirror, so the change is made in the design system artifact and then synced.

Seeker ID names are recorded on Solana **mainnet**. Holdout runs on **devnet**. A wallet's address is the same on both networks, so a member's devnet wallet can be looked up against the mainnet names without the member doing anything. That lookup is a read: it moves no funds, signs nothing and touches no Holdout account.

## Clarifications

### Session 2026-10-09

- Q: Where is the Seeker ID note shown, now that the app has no About section for judges (spec 004, constitution v2.1.0)? → A: In the README's known-weaknesses table only; the app shows no weaknesses screen.
- Q: How fast must names appear in a long list the first time it is opened, given that the public mainnet node allows about 10 owner searches per 10 seconds (plan research R4) and an earlier answer of "10 seconds on screen, 2 minutes for the whole list" leaves no room under that limit? → A: Names are minor, so they load lazily: only the connected wallet and the rows on screen are looked up, rows on screen are named within 15 seconds the first time, rows never scrolled into view are not looked up, and every kept name shows immediately on later visits.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See yourself by your Seeker ID (Priority: P1)

A member connects the wallet on their Seeker. The app shows them who they are connected as by their Seeker ID, `alice.skr`, instead of a string of letters and digits. A member whose wallet has no Seeker ID sees their shortened address, as today, and nothing suggests anything is missing.

**Why this priority**: It is the moment that makes the app feel native to the Seeker, and the smallest slice that proves the lookup works on a real device.

**Independent Test**: Connect a wallet that holds a `.skr` name and one that does not. The first is shown by its name wherever the app names the connected wallet; the second by its shortened address. Both work with the phone offline after the first connection.

**Acceptance Scenarios**:

1. **Given** a member connects a wallet that holds the Seeker ID `alice.skr`, **When** the app names the connected wallet (the place they disconnect from, and Me once it exists), **Then** it shows `alice.skr`.
2. **Given** a member connects a wallet that holds no Seeker ID, **When** the app names the connected wallet, **Then** it shows the shortened address in the same place and style, with no message about a missing name.
3. **Given** the name lookup has not answered yet, **When** the screen opens, **Then** the shortened address shows at once and is replaced by the name when the lookup answers, without the layout jumping.
4. **Given** the member connected before and the phone is now offline, **When** they open the app, **Then** the name found last time is shown.

---

### User Story 2 - See the crowd by name (Priority: P1)

On a Holdout's roster and on the league leaderboards, each member is shown by their Seeker ID when they have one and by their shortened address otherwise. The connected member is still marked as "you". Scripted members keep their scripted label and mark.

**Why this priority**: The roster is where "still standing" becomes people. It is the screen judges look at to see the crowd thin.

**Independent Test**: Open a roster and a leaderboard that mix members with and without Seeker IDs, including scripted members. Each row shows the right identity, scripted members stay labelled, and the order is exactly what it was before this feature.

**Acceptance Scenarios**:

1. **Given** a roster whose members include `alice.skr`, a member with no Seeker ID and a scripted member, **When** the roster opens, **Then** Alice's row shows `alice.skr`, the second row shows a shortened address, and the scripted row keeps its scripted label and mark.
2. **Given** the same members, **When** the roster or leaderboard is sorted, **Then** the order is identical to the order without names: names never affect ranking or ordering.
3. **Given** the connected member appears in a roster or on a leaderboard, **When** their row is shown, **Then** it shows their identity followed by "you".
4. **Given** a roster of 1,000 members, **When** it opens, **Then** rows appear as fast as they do today, names fill in for the rows on screen as they are found, never holding a row back, and rows the member never scrolls to are not looked up.

---

### User Story 3 - The README says where names come from (Priority: P2)

Anyone reading the README, a judge included, learns that Seeker ID names are read from Solana mainnet while everything else runs on devnet, that names are display only, and what happens when a member has none.

**Why this priority**: Constitution XI: anything that reaches outside the stated devnet setup is named, with its limits, before a judge finds it.

**Independent Test**: The README's known-weaknesses table contains one entry for Seeker ID names, rendered from the same source as every other entry.

**Acceptance Scenarios**:

1. **Given** a reader opens the README's known weaknesses, **When** they read the table, **Then** one entry says that Seeker ID names are read from mainnet, are for display only, and fall back to the shortened address.
2. **Given** the weaknesses source and the README table, **When** the table is rendered again, **Then** the entry is unchanged: the README is never edited by hand.

---

### Edge Cases

- **A wallet holds several Seeker IDs.** One is shown, chosen by a fixed rule (FR-005), so the same wallet shows the same name on every screen and in every session.
- **A Seeker ID moves to another wallet** (an owner can move it between their own wallets). The old wallet may keep showing the name until its cached entry is refreshed (FR-007); the name never decides anything, so nothing else is affected.
- **A very long name.** It is shortened in the middle so the end, `.skr`, always shows (FR-004).
- **A name made of unusual characters** (for example letters from another script, or characters that look like others). It is shown as written; it is never used to identify anyone for money or ranking (FR-008).
- **The mainnet lookup fails, is slow or is refused.** The shortened address shows; no error, spinner or retry button appears (FR-006).
- **Two different wallets would show the same visible text** (for example after truncation). Rows stay distinct because ordering and "you" come from the wallet, not the name.
- **Scripted members.** Their wallets have no Seeker ID, so they show shortened addresses with their scripted label, exactly as today.
- **The member scrolls fast through a long list.** Rows that leave the screen before their lookup starts are dropped from the queue and show their shortened address; they are looked up if they come back into view (FR-012).
- **The connected member disconnects and connects another wallet.** The new wallet's own name, or its shortened address, is shown; nothing carries over from the previous wallet.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** Wherever the app names the **connected wallet**, it shows that wallet's Seeker ID when the wallet holds one, and its shortened address otherwise.
- **FR-002** Wherever the app identifies **another member** (a Holdout's roster and the league leaderboards, and any later list or line that names a member), it shows that member's Seeker ID when the wallet holds one, and its shortened address otherwise. This amends 001 FR-063, whose rule that no leaderboard shows or ranks any stake, prize or payout amount is unchanged.
- **FR-003** A Seeker ID is shown in full with its `.skr` ending, in the same typeface and colour the shortened address uses in that place, so a row with a name and a row without one look like the same kind of row. No icon, badge or colour marks a name as verified or an address as missing.
- **FR-004** A Seeker ID too long for its space is shortened in the middle of the part before `.skr`, keeping its start and the whole `.skr` ending visible.
- **FR-005** When a wallet holds more than one Seeker ID, the app shows the one that comes first in plain alphabetical order, so the choice is the same on every screen, in every session and on every phone.
- **FR-006** Looking a name up never delays, blocks or fails a screen. The shortened address shows at once; the name replaces it when found, in the same space. A failed, slow or refused lookup shows nothing but the shortened address: no error, spinner or retry control.
- **FR-007** Names found are kept on the phone and shown at once, offline included, the next time the same wallet appears. A kept entry, including the finding that a wallet has no Seeker ID, is checked again at most once a day, and the connected wallet's own entry is checked again every time it connects.
- **FR-008** Names are for display only. No ranking, ordering, eligibility, payout, stake, streak, label or any other decision uses a name. Everything that identified a member before this feature still identifies them by wallet, the "you" marker and the scripted label included.
- **FR-009** The lookup only reads public name records. It signs nothing, sends no transaction, needs no permission from the member, and sends nothing but the wallet addresses being looked up. It uses no server of Holdout's own: like every other read in the app, it goes straight from the phone to the network (001 constitution: no server).
- **FR-010** *(amended 2026-10-09, spec 004)* The README's known-weaknesses table (rendered from its single source) gains one entry saying that Seeker ID names are read from Solana mainnet while the app runs on devnet, that they are for display only, and that members without one are shown by shortened address.
- **FR-011** Scripted members are labelled exactly as before (001 FR-064), whatever identity their row shows.
- **FR-012** Names are looked up lazily. Only the connected wallet and the rows currently on screen are looked up, the connected wallet first. A row that leaves the screen before its lookup starts is not looked up, and nothing is fetched ahead for rows the member has not scrolled to.

### Key Entities

- **Seeker ID**: a `.skr` name registered on Solana mainnet and pointing at one wallet. Read by the app, never written.
- **Shown identity**: what the app displays for a wallet: its chosen Seeker ID (FR-005) or its shortened address. It belongs to the wallet, not to a Holdout or a league.
- **Kept name entry**: the phone's record of the last lookup for a wallet: the name found, or "none", and when it was checked.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** On a Seeker whose wallet holds a Seeker ID, the connected member sees their own name within 3 seconds of connecting on a normal connection, and immediately on every later open, offline included.
- **SC-002** A roster or leaderboard of 100 members shows every row as quickly as it does without this feature. The first time it is opened, the names of the rows on screen appear within 15 seconds on a normal connection, and no lookup is made for a row that has not been on screen; on later visits every kept name shows immediately.
- **SC-003** In 100% of rosters and leaderboards, ordering and the "you" and scripted markers are identical with and without names.
- **SC-004** With the network cut, or with name lookups made to fail, no screen shows an error, spinner or empty space where an identity belongs: 0 occurrences across Today, roster, leaderboard and the connected-wallet line.
- **SC-005** A reader of the README can tell, from one entry, where names come from and that they are display only.

## Assumptions

- Seeker IDs can be read from the public mainnet name records without an account, key or fee. If the public network refuses or limits the reads, members are shown by shortened address (FR-006) and the feature still meets FR-001–FR-011.
- Testers and judges without a Seeker, and every scripted member, have no Seeker ID, so the shortened address is the most common identity during the hackathon. That is why FR-003 makes the two look alike.
- A name is chosen once, when the Seeker is set up, and cannot be changed, but it can move between its owner's wallets. A day-old kept entry (FR-007) is fresh enough for display.
- A member's Seeker ID is public on chain, and so is their Holdout activity. Showing the two together reveals nothing a public block explorer does not. An opt-out is not offered in this build.
- Out of scope: other name systems (`.sol` and other endings), looking a member up by name, showing a name in transaction screens or wallet approvals (those belong to the member's wallet app), and any contract or scheduler change.
- The bottom bar and the Me tab come from separate screen work. Where Me does not exist yet, FR-001 applies to the place the app names the connected wallet today.
