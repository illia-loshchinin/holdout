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

    // ------------------------------------------- a real-shaped Seeker Genesis Token

    /// The extensions a Seeker Genesis Token's mint carries, in the order they
    /// sit on mainnet (see [`MAINNET_MINT`]).
    const SEEKER_MINT_EXTENSIONS: [ExtensionType; 5] = [
        ExtensionType::MetadataPointer,
        ExtensionType::PermanentDelegate,
        ExtensionType::MintCloseAuthority,
        ExtensionType::GroupMemberPointer,
        ExtensionType::TokenGroupMember,
    ];

    /// A token account shaped as a real Seeker Genesis Token's: an associated
    /// token account, so it carries `ImmutableOwner` (170 bytes), and frozen,
    /// as Solana Mobile mints it.
    fn frozen_ata_bytes(owner: &Pubkey, mint: &Pubkey, amount: u64) -> Vec<u8> {
        use anchor_spl::token_2022::spl_token_2022::extension::immutable_owner::ImmutableOwner;
        let len = ExtensionType::try_calculate_account_len::<TokenAccount2022>(&[
            ExtensionType::ImmutableOwner,
        ])
        .unwrap();
        let mut data = vec![0u8; len];
        let mut state =
            StateWithExtensionsMut::<TokenAccount2022>::unpack_uninitialized(&mut data).unwrap();
        state.base = TokenAccount2022 {
            mint: *mint,
            owner: *owner,
            amount,
            state: AccountState::Frozen,
            ..Default::default()
        };
        state.pack_base();
        state.init_account_type().unwrap();
        state.init_extension::<ImmutableOwner>(true).unwrap();
        data
    }

    /// A mint shaped as a real Seeker Genesis Token's: metadata pointer,
    /// permanent delegate, mint close authority, group member pointer and the
    /// group member itself.
    fn seeker_mint_bytes(mint_key: &Pubkey, group: &Pubkey) -> Vec<u8> {
        use anchor_spl::token_2022::spl_token_2022::extension::{
            group_member_pointer::GroupMemberPointer, metadata_pointer::MetadataPointer,
            mint_close_authority::MintCloseAuthority, permanent_delegate::PermanentDelegate,
        };
        let authority = Pubkey::new_unique();
        let len =
            ExtensionType::try_calculate_account_len::<Mint2022>(&SEEKER_MINT_EXTENSIONS).unwrap();
        let mut data = vec![0u8; len];
        let mut state =
            StateWithExtensionsMut::<Mint2022>::unpack_uninitialized(&mut data).unwrap();
        state.base = Mint2022 {
            mint_authority: Some(authority).into(),
            supply: 1,
            decimals: 0,
            is_initialized: true,
            freeze_authority: Some(authority).into(),
        };
        state.pack_base();
        state.init_account_type().unwrap();
        let pointer = state.init_extension::<MetadataPointer>(true).unwrap();
        pointer.authority = Some(authority).try_into().unwrap();
        pointer.metadata_address = Some(*group).try_into().unwrap();
        let delegate = state.init_extension::<PermanentDelegate>(true).unwrap();
        delegate.delegate = Some(authority).try_into().unwrap();
        let close = state.init_extension::<MintCloseAuthority>(true).unwrap();
        close.close_authority = Some(authority).try_into().unwrap();
        let member_pointer = state.init_extension::<GroupMemberPointer>(true).unwrap();
        member_pointer.authority = Some(authority).try_into().unwrap();
        member_pointer.member_address = Some(*mint_key).try_into().unwrap();
        let member = state.init_extension::<TokenGroupMember>(true).unwrap();
        *member = TokenGroupMember::new(mint_key, group, 121_184);
        data
    }

    #[test]
    fn a_frozen_ata_of_a_multi_extension_seeker_mint_passes() {
        let member = Pubkey::new_unique();
        let mint_key = Pubkey::new_unique();
        let case = Case {
            member,
            token_program: token_2022(),
            token: frozen_ata_bytes(&member, &mint_key, 1),
            mint_key,
            mint_program: token_2022(),
            mint: seeker_mint_bytes(&mint_key, &SEEKER_GENESIS_GROUP),
        };
        // The same shape as the mainnet token: 170 and 450 bytes, frozen.
        assert_eq!(case.token.len(), 170);
        assert_eq!(case.token[108], AccountState::Frozen as u8);
        assert_eq!(case.mint.len(), 450);
        assert!(case.holds(), "frozen does not mean not held");

        let mut fake = case;
        fake.mint = seeker_mint_bytes(&fake.mint_key, &Pubkey::new_unique());
        assert!(
            !fake.holds(),
            "the extra extensions do not let another group in"
        );
    }

    // ------------------------------------------------ the real token, from mainnet

    /// A Seeker Genesis Token's token account on mainnet,
    /// `DfmpRDbPJDH2y8KPrKFgGi9FG7guXF8NdkeDt7pvtHnQ` (`getAccountInfo`,
    /// base64, slot 455 330 599, 2026-10-10). Owner [`MAINNET_OWNER`].
    const MAINNET_TOKEN: &str = concat!(
        "7OW3BPMfpWtFB961qP/kCUBqoHZRq5SWZgG5dVYFSO7fTfWndFuE+5dUjbdrqqIeF8/LP1CrRtPK",
        "LcT88FZZBwEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAAA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgcAAAA=",
    );

    /// Its mint, `GwkNucuURygbHb61o8c5nbfwcSZL9Y3EB39D8MSs2siu`, fetched the
    /// same way (slot 455 330 605).
    const MAINNET_MINT: &str = concat!(
        "AQAAAOWKqhdAvydh2LouHq0lKwjraNJka0hmiNGggbQhTCm9AQAAAAAAAAAAAQEAAADliqoXQL8n",
        "Ydi6Lh6tJSsI62jSZGtIZojRoIG0IUwpvQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAARIAQADl",
        "iqoXQL8nYdi6Lh6tJSsI62jSZGtIZojRoIG0IUwpveWJkm0XmBofiHcH5IkGhI6YGUP+gVGTmnUr",
        "qnPiEJ7pDAAgAOWKqhdAvydh2LouHq0lKwjraNJka0hmiNGggbQhTCm9AwAgAOWKqhdAvydh2Lou",
        "Hq0lKwjraNJka0hmiNGggbQhTCm9FgBAAOWKqhdAvydh2LouHq0lKwjraNJka0hmiNGggbQhTCm9",
        "7OW3BPMfpWtFB961qP/kCUBqoHZRq5SWZgG5dVYFSO4XAEgA7OW3BPMfpWtFB961qP/kCUBqoHZR",
        "q5SWZgG5dVYFSO7liZJtF5gaH4h3B+SJBoSOmBlD/oFRk5p1K6pz4hCe6WDZAQAAAAAA",
    );

    const MAINNET_OWNER: Pubkey = pubkey!("G2gs92PGkUgxxuqmNHeuDh6u7huzxovBB5pdUHaU3epE");
    const MAINNET_MINT_KEY: Pubkey = pubkey!("GwkNucuURygbHb61o8c5nbfwcSZL9Y3EB39D8MSs2siu");

    /// Standard base64, enough for the fixtures above.
    fn base64(text: &str) -> Vec<u8> {
        const ALPHABET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        let mut out = Vec::new();
        let (mut acc, mut bits) = (0u32, 0u32);
        for c in text.bytes().filter(|&c| c != b'=') {
            let value = ALPHABET.iter().position(|&a| a == c).expect("base64") as u32;
            acc = ((acc << 6) | value) & 0xffff;
            bits += 6;
            if bits >= 8 {
                bits -= 8;
                out.push((acc >> bits) as u8);
            }
        }
        out
    }

    fn mainnet(member: Pubkey) -> Case {
        Case {
            member,
            token_program: token_2022(),
            token: base64(MAINNET_TOKEN),
            mint_key: MAINNET_MINT_KEY,
            mint_program: token_2022(),
            mint: base64(MAINNET_MINT),
        }
    }

    #[test]
    fn the_real_mainnet_seeker_genesis_token_passes_for_its_owner_only() {
        let case = mainnet(MAINNET_OWNER);
        // What the crafted cases above copy: a frozen 170-byte ATA, and a mint
        // with these five extensions in this order.
        assert_eq!(case.token.len(), 170);
        assert_eq!(case.token[108], AccountState::Frozen as u8);
        let mint = StateWithExtensions::<Mint2022>::unpack(&case.mint).unwrap();
        assert_eq!(
            mint.get_extension_types().unwrap(),
            SEEKER_MINT_EXTENSIONS.to_vec()
        );
        assert!(case.holds());

        assert!(!mainnet(Pubkey::new_unique()).holds(), "another wallet");
        let mut other_mint = mainnet(MAINNET_OWNER);
        other_mint.mint_key = Pubkey::new_unique();
        assert!(!other_mint.holds(), "the token account names its own mint");
        let mut swapped = mainnet(MAINNET_OWNER);
        std::mem::swap(&mut swapped.token, &mut swapped.mint);
        assert!(!swapped.holds(), "the two accounts in the wrong order");
    }
}
