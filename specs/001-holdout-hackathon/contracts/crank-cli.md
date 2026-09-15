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
| `HOLDOUT_ADMIN_KEYPAIR` | `init` only (or `--admin`) | Becomes `Config.admin` |
| `HOLDOUT_SCRIPTED_KEYS_DIR` | `scripted` only | Secret keypairs for the wallets listed in `scripted-wallets.json` |
| `HOLDOUT_PAGE_SIZE` | no (default 10) | `(Membership, Profile)` pairs per paged transaction |

All commands print a JSON summary to stdout and exit `0` on success, `1` on configuration errors, `2` if any transaction failed after retries. Every command accepts `--dry-run` (build and simulate, send nothing).

## Commands

| Command | Does | Safe to repeat? |
|---|---|---|
| `crank init --admin <keypair> --operator <pubkey> --sponsor <pubkey> --treasury <pubkey> [--first-start <iso>]` | Admin helper: sends `initialize`, then `create_league` for the 7-day (id 0) and 21-day (id 1) leagues with devnet timing, then funds the faucet vault | Fails harmlessly if already initialised |
| `crank run` | For each league: `open_cohort` if due; for each running cohort: page `record_forfeits` over members currently in breach; for each settling cohort: page `settle_count` until all counted, then `settle_finalize` (passing the league's open cohort as carry-over target) | Yes — every step is idempotent on-chain |
| `crank scripted plan --cohort <address>` | Prints each scripted wallet's tier and scheduled forfeit day (seeded from the cohort address, R12) | Yes |
| `crank scripted run` | For every open cohort: scripted wallets due to join today claim the faucet (SKR only) and join; for every running cohort: scripted wallets not scheduled to forfeit today submit the current day | Yes — skips wallets already joined or already submitted |
| `crank scripted fund --lamports <n>` | Operator tops up scripted wallets' SOL once | Yes |
| `crank chips publish --rate <usd-per-skr>` | Converts $10/$25/$50/$100 at the given rate, rounds each down to the nearest 10 whole SKR (min 10), sends `publish_chip_set` | Publishes a new version each time — run deliberately |
| `crank chips publish --auto --if-drift 0.20` **[Phase 2]** | Fetches the SKR rate; publishes only if the current chip set is off its tiers by more than 20 %; exits `0` without publishing if no rate is available | Yes |
| `crank status` | Prints every cohort's status, member count, recorded forfeits, counted and settled flags, held carry-over | Yes |

## Schedule

A Fly.io scheduled machine runs `crank run && crank scripted run` **every 10 minutes**. The Phase 2 chip check runs Mondays after 05:00 UTC. The GitHub Actions workflow `.github/workflows/crank.yml` is a disabled-by-default fallback with the same commands.

## Guarantees

- The crank never holds authority over stakes; losing it delays transfers and leaderboards, never outcomes (ES-8).
- Anyone can run `crank run` with their own payer and get the same on-chain result (FR-030a, acceptance criterion 5).
