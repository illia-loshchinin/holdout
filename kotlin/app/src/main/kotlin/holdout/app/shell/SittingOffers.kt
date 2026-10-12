package holdout.app.shell

import holdout.app.chain.ChainView
import holdout.app.home.OfferCardState
import holdout.app.home.TodayModel
import holdout.app.nav.NavEvent
import holdout.app.sitting.DayCompleteOffer
import holdout.core.Derive
import holdout.core.Stage

/** What a finished Daily 25 offers, and where a tap on it goes. */
internal data class SittingOffer(val offer: DayCompleteOffer, val go: NavEvent)

/**
 * The offer under a finished Daily 25 (board 19; rulings C-D8, G12): the next
 * Holdout to join, or a comeback in its place. Only while the member has no
 * Holdout running or in a lobby, and never for a wallet known not to hold a
 * Seeker — it could not make the stake. Pure, so a JVM test pins every case.
 */
internal object SittingOffers {
    const val STAKE_TITLE = "Stake on yourself next"
    const val COMEBACK_TITLE = "Get your stake back"

    fun of(view: ChainView?, now: Long?, seekerKnown: Boolean, knownNotSeeker: Boolean): SittingOffer? {
        if (view == null || now == null || view.config == null || knownNotSeeker) return null
        val busy = view.mine(now).any { h -> h.stage(now).let { it is Stage.Active || it == Stage.Lobby || it == Stage.LobbyLocked } }
        if (busy) return null
        TodayModel.comeback(view, now, seekerKnown)?.let { return SittingOffer(DayCompleteOffer(COMEBACK_TITLE, it.text), it.go) }
        val next = view.joinable(now).minByOrNull { Derive.timeUntilJoiningCloses(it.cohort, now) } ?: return null
        val card = OfferCardState.of(next, now)
        return SittingOffer(
            DayCompleteOffer(STAKE_TITLE, "${card.title} · ${card.timing.replaceFirstChar { it.lowercase() }}"),
            NavEvent.OpenStake(card.cohort, seekerKnown = seekerKnown),
        )
    }
}
