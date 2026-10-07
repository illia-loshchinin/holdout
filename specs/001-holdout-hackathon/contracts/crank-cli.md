# Contract — `crank` command-line tool

**Phase 1 output of `/speckit-plan`.** A Kotlin/JVM tool (`kotlin/crank`) built on `kotlin/core`. It is the usual caller of the permissionless instructions, the driver of scripted members, and the Operator's chip-set tool. Nothing it does is privileged except `chips publish`.

Packaging: `kotlin/crank/build/libs/crank.jar` (`java -jar crank.jar <command>`), or `./gradlew :crank:run --args="<command>"` in development.

## Configuration

| Variable | Required | Meaning |
|---|---|---|
| `HOLDOUT_RPC_URL` | yes | Devnet RPC endpoint supporting `getProgramAccounts` filters |
| `HOLDOUT_PROGRAM_ID` | yes | Deployed `holdout` program |
| `HOLDOUT_PAYER_KEYPAIR` | yes | Fee payer for automation (any funded devnet wallet) |
| `HOLDOUT_OPERATOR_KEYPAIR` | `chips publish` only | Must equal `Config.operator` |
| `HOLDOUT_JUPITER_API_KEY` | no | `chips publish --auto` only: a free Jupiter key sent as `x-api-key`; keyless works for a weekly call |
| `HOLDOUT_JUPITER_PRICE_URL` | no | `chips publish --auto` only: overrides `https://api.jup.ag/price/v3` |
| `HOLDOUT_ADMIN_KEYPAIR` | `init` only (or `--admin`) | Becomes `Config.admin` |
| `HOLDOUT_SCRIPTED_KEYS_DIR` | `scripted` only | Secret keypairs for the wallets listed in `scripted-wallets.json` |
| `HOLDOUT_PAGE_SIZE` | no (default 10) | `(Membership, Profile)` pairs per paged transaction |

All commands print a JSON summary to stdout and exit `0` on success, `1` on configuration errors, `2` if any transaction failed after retries. Every command accepts `--dry-run` (build and simulate, send nothing).

## Commands

| Command | Does | Safe to repeat? |
|---|---|---|
| `crank init --admin <keypair> --operator <pubkey> --sponsor <pubkey> --treasury <pubkey> --developer <pubkey> --rate <usd-per-skr> [--first-start <iso>]` | *(H35: `initialize` creates the treasury's account — the operations destination — and the developer's — the developer destination; `create_league` makes league 0 **Easy** (form 1) and league 1 **Hard** (form 0), each with its reserve vault `["reserve", id]`; a test league takes `--form easy|hard`, default easy.)* Admin helper: sends `initialize` — **`--admin` must be the keypair holding the program's upgrade authority, which the program verifies (6003)** — then `publish_chip_set` with `--rate` through `HOLDOUT_OPERATOR_KEYPAIR`, then `create_league` for the 7-day (id 0) and 21-day (id 1) leagues with devnet timing, then funds the faucet vault. Publishing before any league exists means no cohort can be opened in a window where the chip set is still zero (6012, FR-055b) | Fails harmlessly if already initialised |
| `crank run` | For each league, **in this order**: `open_cohort` if due — for test leagues, only in the newest (highest id), an older one winding down, and the newest only once every older test cohort has settled (FR-044a); `top_up_sponsor` if the sponsor holds less than half its float (FR-042b); for each running cohort: page `record_forfeits` over members currently in breach; for each settling cohort, **oldest index first**, and across leagues in the order the cohorts ended, a stuck cohort holding later ones that share its progress slot: page `settle_count` until all counted, then `settle_finalize`, which with no finishers sends the whole pool to the fee destination (FR-037). Counting a cohort before the league's previous one has settled is refused on-chain, so the order is not a preference (FR-061a). A refill the program refuses — float still above half, or the day's lamport budget spent — is reported in the JSON summary and does **not** set exit `2`: it is an expected outcome, retried on the next pass | Yes — every step is idempotent on-chain *(H35: a forfeit pays its developer and operations parts at once; `settle_finalize` draws the boost and the comeback funding from the league's reserve vault and credits the cohort's reserve dues to it; with no finishers a third of the pool goes to the developer, the rest to operations.)* |
| `crank scripted plan --cohort <address>` | Prints the league's floor, the cohort's current real and scripted member counts, the resulting deficit, and each scripted wallet's tier and scheduled forfeit day (seeded from the cohort address, R12) | Yes |
| `crank scripted run` | For every open cohort: fills it **only up to its league's floor** — `deficit = floor − member_count`, counting real members — with wallets that claim the faucet (SKR only) and join; half the floor when the lobby opens, the full floor 24 h before the join cutoff. For every running cohort: scripted wallets not scheduled to forfeit today submit the current day | Yes — recomputes the deficit from chain state, so it never over-fills, and skips wallets already joined or already submitted |
| `crank scripted fund --lamports <n>` | Operator tops up scripted wallets' SOL once (56 wallets × 0.02 SOL, R12) | Yes |

Floors are crank configuration — `--floor-7d` (default 20) and `--floor-21d` (default 12) — and never reach the chain: no account records which wallets are scripted, and no cohort behaves differently because of them (FR-043a, constitution I).
| `crank chips publish --rate <usd-per-skr>` | Converts $10/$25/$50/$100 at the given rate, rounds each down to the nearest 10 whole SKR (min 10), sends `publish_chip_set` | Publishes a new version each time — run deliberately |
| `crank chips publish --auto` **[Phase 2]** | The Operator's weekly re-pricing, run **every hour** by the Operator's OS scheduler in any time zone — never by `crank run`; the boundary guard makes all but one run a week do nothing. Fetches the SKR rate from the Jupiter Price API (by mint) and publishes all four tiers re-priced at it, every week, even if unchanged; exits `0` without publishing if no rate is available, saying why; `--dry-run` reports the rate and the set it would publish | Yes — at most once per weekly boundary: nothing is fetched or sent when `Config.chip_published_at` is at or after the latest Monday 05:00 UTC |
| `crank status` | Prints every cohort's status, member count, recorded forfeits, counted and settled flags; the faucet vault's balance, the sponsor's balance against `sponsor_float`, and the day's faucet spend as `lamports_today / daily_lamport_cap` — the figures the live period is monitored on (T110, FR-042b) | Yes *(H35: also both fee destinations, and per league its form, both reserves and the reserve vault's balance.)* |

## Schedule

A Fly.io scheduled machine runs `crank run && crank scripted run` **every 10 minutes**, and sooner when a cohort open falls due first: `crank run`'s summary carries `nextOpenAt` (chain time, absent when nothing falls due later), and `scripts/crank-loop.sh` wakes 5 s after it, so the test lobby's next cohort opens moments after the one before starts (FR-044a). The Phase 2 chip check runs hourly on the Operator's machine and publishes once a week, on its first run after Monday 05:00 UTC. The GitHub Actions workflow `.github/workflows/crank.yml` is a disabled-by-default fallback with the same commands.

## Guarantees

- The crank never holds authority over stakes; losing it delays transfers and leaderboards, never outcomes (ES-8).
- Anyone can run `crank run` with their own payer and get the same on-chain result (FR-030a, acceptance criterion 5) — including the sponsor refill, which needs no key of ours.
