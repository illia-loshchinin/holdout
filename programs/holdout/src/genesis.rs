//! The Seeker Genesis Token check behind `genesis_gate_enabled` (research R17,
//! spec FR-003b, constitution v2.3.0). Tasks T152, T153.
//!
//! Staked Holdouts are Seeker-exclusive. With the flag on, `join_cohort` reads
//! two remaining accounts — the member's Token-2022 token account and its mint
//! — and accepts the join only when [`holds_seeker_genesis`] says the member
//! holds a Seeker Genesis Token. The flag ships off on devnet, where these
//! tokens do not exist and the app keeps staking Seeker-exclusive instead; it is
//! turned on at mainnet launch.
//!
//! A Seeker Genesis Token is a Token-2022 NFT whose mint carries a
//! `TokenGroupMember` extension naming the Seeker Genesis group. A member can
//! only be added to a group with the group's update authority signing, so no
//! one can mint a token that claims the real group. An emptied account stays
//! behind after a transfer, so an amount of 0 never counts.

use anchor_lang::prelude::*;
use anchor_spl::token_2022::spl_token_2022::{
    extension::{BaseStateWithExtensions, StateWithExtensions},
    state::{Account as TokenAccount2022, Mint as Mint2022},
};
use spl_token_group_interface::state::TokenGroupMember;

/// The Seeker Genesis Token group, which is also its metadata address
/// (Solana Mobile docs, *Seeker Genesis Token*). A fact of the Seeker, not a
/// parameter, so it is a constant rather than a `Config` field.
pub const SEEKER_GENESIS_GROUP: Pubkey = pubkey!("GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te");

/// Whether `member` holds a Seeker Genesis Token, shown by `[token, mint]`.
pub fn holds_seeker_genesis(member: &Pubkey, accounts: &[AccountInfo]) -> bool {
    let [token, mint, ..] = accounts else {
        return false;
    };
    let (Ok(token_data), Ok(mint_data)) = (token.try_borrow_data(), mint.try_borrow_data()) else {
        return false;
    };
    holds_seeker_genesis_bytes(
        member,
        token.owner,
        &token_data,
        mint.key,
        mint.owner,
        &mint_data,
    )
}

/// The check over the two accounts' owning programs and bytes, kept free of
/// `AccountInfo` so it is unit-tested on crafted accounts:
///
/// - both accounts are owned by the Token-2022 program;
/// - the token account belongs to `member`, is of `mint_key`, and holds ≥ 1;
/// - the mint's `TokenGroupMember` extension is for this mint and names
///   [`SEEKER_GENESIS_GROUP`].
pub fn holds_seeker_genesis_bytes(
    member: &Pubkey,
    token_program: &Pubkey,
    token_data: &[u8],
    mint_key: &Pubkey,
    mint_program: &Pubkey,
    mint_data: &[u8],
) -> bool {
    let token_2022 = anchor_spl::token_2022::ID;
    if *token_program != token_2022 || *mint_program != token_2022 {
        return false;
    }
    let Ok(account) = StateWithExtensions::<TokenAccount2022>::unpack(token_data) else {
        return false;
    };
    if account.base.owner != *member || account.base.mint != *mint_key || account.base.amount == 0 {
        return false;
    }
    let Ok(mint) = StateWithExtensions::<Mint2022>::unpack(mint_data) else {
        return false;
    };
    let Ok(member_ext) = mint.get_extension::<TokenGroupMember>() else {
        return false;
    };
    member_ext.mint == *mint_key && member_ext.group == SEEKER_GENESIS_GROUP
}

#[cfg(test)]
mod tests {
    use super::*;
    use anchor_spl::token_2022::spl_token_2022::{
        extension::{BaseStateWithExtensionsMut, ExtensionType, StateWithExtensionsMut},
        state::AccountState,
    };
    use solana_program_pack::Pack;

    fn token_2022() -> Pubkey {
        anchor_spl::token_2022::ID
    }

    /// A Token-2022 mint carrying a `TokenGroupMember` extension, or none.
    fn mint_bytes(mint_key: &Pubkey, group: Option<&Pubkey>) -> Vec<u8> {
        let extensions: &[ExtensionType] = if group.is_some() {
            &[ExtensionType::TokenGroupMember]
        } else {
            &[]
        };
        let len = ExtensionType::try_calculate_account_len::<Mint2022>(extensions).unwrap();
        let mut data = vec![0u8; len];
        let mut state =
            StateWithExtensionsMut::<Mint2022>::unpack_uninitialized(&mut data).unwrap();
        state.base = Mint2022 {
            decimals: 0,
            supply: 1,
            is_initialized: true,
            ..Default::default()
        };
        state.pack_base();
        state.init_account_type().unwrap();
        if let Some(group) = group {
            let ext = state.init_extension::<TokenGroupMember>(true).unwrap();
            *ext = TokenGroupMember::new(mint_key, group, 7);
        }
        data
    }

    /// A plain Token-2022 token account.
    fn token_bytes(owner: &Pubkey, mint: &Pubkey, amount: u64) -> Vec<u8> {
        let mut data = vec![0u8; TokenAccount2022::LEN];
        TokenAccount2022 {
            mint: *mint,
            owner: *owner,
            amount,
            state: AccountState::Initialized,
            ..Default::default()
        }
        .pack_into_slice(&mut data);
        data
    }

    struct Case {
        member: Pubkey,
        token_program: Pubkey,
        token: Vec<u8>,
        mint_key: Pubkey,
        mint_program: Pubkey,
        mint: Vec<u8>,
    }

    impl Case {
        fn seeker() -> Self {
            let member = Pubkey::new_unique();
            let mint_key = Pubkey::new_unique();
            Case {
                member,
                token_program: token_2022(),
                token: token_bytes(&member, &mint_key, 1),
                mint_key,
                mint_program: token_2022(),
                mint: mint_bytes(&mint_key, Some(&SEEKER_GENESIS_GROUP)),
            }
        }

        fn holds(&self) -> bool {
            holds_seeker_genesis_bytes(
                &self.member,
                &self.token_program,
                &self.token,
                &self.mint_key,
                &self.mint_program,
                &self.mint,
            )
        }
    }

    #[test]
    fn a_member_of_the_seeker_genesis_group_passes() {
        assert!(Case::seeker().holds());
    }

    #[test]
    fn a_mint_of_another_group_fails() {
        let mut case = Case::seeker();
        case.mint = mint_bytes(&case.mint_key, Some(&Pubkey::new_unique()));
        assert!(!case.holds());
    }

    #[test]
    fn a_mint_with_no_group_member_extension_fails() {
        let mut case = Case::seeker();
        case.mint = mint_bytes(&case.mint_key, None);
        assert!(!case.holds());
    }

    #[test]
    fn an_emptied_account_fails() {
        let mut case = Case::seeker();
        case.token = token_bytes(&case.member, &case.mint_key, 0);
        assert!(!case.holds());
    }

    #[test]
    fn someone_elses_account_fails() {
        let mut case = Case::seeker();
        case.token = token_bytes(&Pubkey::new_unique(), &case.mint_key, 1);
        assert!(!case.holds());
    }

    #[test]
    fn an_account_of_another_mint_fails() {
        let mut case = Case::seeker();
        case.token = token_bytes(&case.member, &Pubkey::new_unique(), 1);
        assert!(!case.holds());
    }

    #[test]
    fn a_member_extension_for_another_mint_fails() {
        let mut case = Case::seeker();
        case.mint = mint_bytes(&Pubkey::new_unique(), Some(&SEEKER_GENESIS_GROUP));
        assert!(!case.holds());
    }

    #[test]
    fn accounts_not_owned_by_token_2022_fail() {
        let mut token_owned_by_spl = Case::seeker();
        token_owned_by_spl.token_program = anchor_spl::token::ID;
        assert!(!token_owned_by_spl.holds());

        let mut mint_owned_by_spl = Case::seeker();
        mint_owned_by_spl.mint_program = anchor_spl::token::ID;
        assert!(!mint_owned_by_spl.holds());
    }

    #[test]
    fn garbage_fails() {
        let mut case = Case::seeker();
        case.mint = vec![1, 2, 3];
        assert!(!case.holds());
        let mut case = Case::seeker();
        case.token = vec![];
        assert!(!case.holds());
    }

    #[test]
    fn missing_accounts_fail() {
        assert!(!holds_seeker_genesis(&Pubkey::new_unique(), &[]));
    }
}
