package holdout.app

import holdout.app.sitting.BankingController
import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T051 — banking in blocks of ten (FR-024, FR-023, PRD §8.6). */
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
        // The rehearsal test league runs 5 reps a day (quickstart §5).
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
}
