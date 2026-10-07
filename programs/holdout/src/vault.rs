//! SKR leaving a program-owned token account, signed by the PDA that owns it.
//!
//! Every movement of money in the program is one of these: a cohort's vault
//! paying a destination, a league's reserve feeding a cohort, or a cohort
//! crediting its league's reserve (constitution I). A zero amount is skipped,
//! so callers need not guard every part of a split.

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Transfer};

pub fn transfer_signed<'info>(
    token_program: &AccountInfo<'info>,
    from: AccountInfo<'info>,
    to: AccountInfo<'info>,
    authority: AccountInfo<'info>,
    seeds: &[&[u8]],
    amount: u64,
) -> Result<()> {
    if amount == 0 {
        return Ok(());
    }
    token::transfer(
        CpiContext::new_with_signer(
            token_program.key(),
            Transfer {
                from,
                to,
                authority,
            },
            &[seeds],
        ),
        amount,
    )
}
