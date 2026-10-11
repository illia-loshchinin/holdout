package holdout.app

import holdout.app.sitting.BankingController
import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.ToesMeter
import holdout.repcounter.RepStance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T051 — banking in blocks of ten (FR-024, FR-023, AC-15). */
class BankingControllerTest {

    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val day = 21_000L

    private fun controller(store: Store = InMemoryStore(), target: Int = 25) = BankingController(store, wallet, Store.CHALLENGE, day, target)

    private fun BankingController.doReps(n: Int) = (1..n).map { onVerifiedRep() }

    @Test
    fun `a block banks on its tenth rep, not before`() {
        val banking = controller()
        val outcomes = banking.doReps(10)
        assertTrue(outcomes.take(9).all { it is BankingController.Outcome.Counted })
        val banked = assertIs<BankingController.Outcome.Banked>(outcomes[9])
        assertEquals(10, banked.banked)
        assertEquals(15, banked.remaining)
    }

    @Test
    fun `the PRD's own example - 17 then 10 then 5`() {
        val store = InMemoryStore()

        // Morning: 17 reps. Ten bank, seven are lost when the sitting ends.
        val morning = controller(store)
        morning.doReps(17)
        assertEquals(7, morning.endSitting())
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, day))

        // Lunch: 10 more.
        val lunch = controller(store)
        lunch.doReps(10)
        assertEquals(20, store.bankedReps(wallet, Store.CHALLENGE, day))
        assertEquals(0, lunch.endSitting())

        // Evening: the last five finish the day.
        val evening = controller(store)
        val outcomes = evening.doReps(5)
        assertIs<BankingController.Outcome.DayComplete>(outcomes.last())
        assertTrue(store.isDayComplete(wallet, Store.CHALLENGE, day))
    }

    @Test
    fun `the last block may be smaller than ten - nobody does 30 to finish 25`() {
        val store = InMemoryStore()
        controller(store).doReps(20)

        val last = controller(store)
        val outcomes = last.doReps(5)
        val done = assertIs<BankingController.Outcome.DayComplete>(outcomes.last())
        assertEquals(25, done.total)
    }

    @Test
    fun `banked blocks survive a sitting that ends any way at all`() {
        val store = InMemoryStore()
        val crashed = controller(store)
        crashed.doReps(13)
        // No endSitting() at all: the process was killed. The block was already
        // durable when it formed, which is the whole point.
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, day))

        val resumed = controller(store)
        assertEquals(10, resumed.banked)
        assertEquals(15, resumed.remaining)
    }

    @Test
    fun `reps short of the next block are discarded, and never counted twice`() {
        val store = InMemoryStore()
        val first = controller(store)
        first.doReps(19)
        assertEquals(9, first.endSitting())
        assertEquals(0, first.endSitting(), "ending twice must not discard anything again")

        val second = controller(store)
        assertEquals(10, second.banked)
        assertEquals(10, second.total)
    }

    @Test
    fun `a completed day cannot be completed again`() {
        val store = InMemoryStore()
        controller(store).doReps(25)

        val extra = controller(store)
        assertIs<BankingController.Outcome.DayComplete>(extra.onVerifiedRep())
        assertEquals(25, store.bankedReps(wallet, Store.CHALLENGE, day))
    }

    @Test
    fun `yesterday's bank is not today's`() {
        val store = InMemoryStore()
        BankingController(store, wallet, Store.CHALLENGE, day - 1, 25).doReps(20)
        assertEquals(0, controller(store).banked, "banked reps expire at the deadline (FR-016)")
    }

    @Test
    fun `two wallets on one phone do not see each other`() {
        val store = InMemoryStore()
        BankingController(store, "walletA", Store.CHALLENGE, day, 25).doReps(20)
        assertEquals(0, BankingController(store, "walletB", Store.CHALLENGE, day, 25).banked)
    }

    @Test
    fun `a cohort day and the Daily 25 bank separately`() {
        val store = InMemoryStore()
        BankingController(store, wallet, "cohort7", day, 25).doReps(10)
        assertEquals(0, controller(store).banked)
    }

    @Test
    fun `a cohort's own target is used, not 25`() {
        val store = InMemoryStore()
        // A test league runs 5 reps a day (quickstart §5).
        val banking = BankingController(store, wallet, "cohort-test", day, target = 5)
        val outcomes = banking.doReps(5)
        assertIs<BankingController.Outcome.DayComplete>(outcomes.last())
    }

    @Test
    fun `at 0500 UTC a running sitting rolls over - blocks stay on yesterday, the rest counts for today`() {
        val store = InMemoryStore()
        val banking = controller(store)
        banking.doReps(14) // one block banked yesterday, four loose

        banking.rollOverTo(day + 1)
        assertEquals(4, banking.loose, "reps done across the boundary are not lost")
        assertEquals(0, banking.banked, "today starts from what today has")
        banking.doReps(6)

        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, day))
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, day + 1))
        assertFalse(store.isDayComplete(wallet, Store.CHALLENGE, day))
    }

    @Test
    fun `rolling over to the same or an earlier day changes nothing`() {
        val store = InMemoryStore()
        val banking = controller(store)
        banking.doReps(10)
        assertFalse(banking.rollOverTo(day))
        assertFalse(banking.rollOverTo(day - 1))
        assertEquals(10, banking.banked)
    }

    private fun BankingController.doReps(n: Int, stance: RepStance) = (1..n).map { onVerifiedRep(stance) }

    @Test
    fun `an abandoned sitting's loose reps never reach the toes meter - a later all-toes 25 is a Hard day`() {
        val store = InMemoryStore()
        val toes = ToesMeter(store, target = 25)

        // Nine from the knees, then the member gave up: none of them banked.
        val abandoned = BankingController(store, wallet, Store.CHALLENGE, day, 25, toes)
        abandoned.doReps(9, RepStance.KNEES)
        abandoned.endSitting()
        assertEquals(0, toes.read(wallet, day).measured, "a discarded rep is not part of the day")

        BankingController(store, wallet, Store.CHALLENGE, day, 25, toes).doReps(25, RepStance.TOES)
        val meter = toes.read(wallet, day)
        assertEquals(25, meter.toes)
        assertEquals(25, meter.measured)
        assertTrue(meter.hardDay(25), "not 16 of 25")
    }

    @Test
    fun `the toes meter counts the reps a block banked, not the loose ones after it`() {
        val store = InMemoryStore()
        val toes = ToesMeter(store, target = 25)
        val banking = BankingController(store, wallet, Store.CHALLENGE, day, 25, toes)

        banking.doReps(9, RepStance.TOES)
        assertEquals(0, toes.read(wallet, day).measured, "nothing is durable before the tenth rep")
        banking.doReps(1, RepStance.TOES)
        banking.doReps(7, RepStance.KNEES)
        banking.endSitting()

        assertEquals(10, toes.read(wallet, day).toes)
        assertEquals(10, toes.read(wallet, day).measured)
    }

    @Test
    fun `a rolled-over sitting's loose reps carry their stances into the new day`() {
        val store = InMemoryStore()
        val toes = ToesMeter(store, target = 25)
        val banking = BankingController(store, wallet, Store.CHALLENGE, day, 25, toes)
        banking.doReps(10, RepStance.TOES)
        banking.doReps(4, RepStance.KNEES)

        banking.rollOverTo(day + 1)
        banking.doReps(6, RepStance.TOES)

        assertEquals(10 to 10, store.toesDay(wallet, day))
        assertEquals(6 to 10, store.toesDay(wallet, day + 1), "four from the knees and six from the toes, banked today")
    }

    @Test
    fun `a day killed between banking 25 and marking it complete is recovered as complete`() {
        val store = InMemoryStore()
        val streaks = holdout.app.streak.StreakStore(store)
        store.setBankedReps(wallet, Store.CHALLENGE, day, 25) // the process died before markDayComplete

        assertTrue(controller(store).isDayComplete, "25 banked is a complete day")
        assertTrue(BankingController.recover(store, streaks, wallet, Store.CHALLENGE, day, target = 25))
        assertTrue(store.isDayComplete(wallet, Store.CHALLENGE, day))
        assertEquals(day, store.lastStreakDay(wallet), "and the streak it earned")
        assertFalse(BankingController.recover(store, streaks, wallet, Store.CHALLENGE, day, target = 25), "once")
    }

    @Test
    fun `a completed day whose streak write was lost gets its streak back, and never a later day walked back`() {
        val store = InMemoryStore()
        val streaks = holdout.app.streak.StreakStore(store)
        store.setBankedReps(wallet, Store.CHALLENGE, day, 25)
        store.markDayComplete(wallet, Store.CHALLENGE, day)
        BankingController.recover(store, streaks, wallet, Store.CHALLENGE, day, target = 25)
        assertEquals(day, store.lastStreakDay(wallet))

        streaks.recordCompletedDay(wallet, day + 1)
        BankingController.recover(store, streaks, wallet, Store.CHALLENGE, day, target = 25)
        assertEquals(day + 1, store.lastStreakDay(wallet))
    }
}
