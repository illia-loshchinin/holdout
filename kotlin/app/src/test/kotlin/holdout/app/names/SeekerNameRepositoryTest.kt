package holdout.app.names

import holdout.app.storage.InMemoryStore
import holdout.app.storage.KeptName
import holdout.app.storage.Store
import holdout.core.Lookup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kept Seeker IDs and when they are asked for again (spec 003 FR-006,
 * FR-007, FR-012), with a fake reader and no pacing: the queue's own rules
 * are `LookupQueueTest`'s.
 */
class SeekerNameRepositoryTest {

    private val nowMillis = 1_000_000_000_000L
    private val hour = 60 * 60 * 1_000L
    private val store = InMemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @AfterTest
    fun stop() = scope.cancel()

    private fun repository(isScripted: (String) -> Boolean = { false }, answer: suspend (String) -> Lookup) = SeekerNameRepository(
        nameOf = { wallet ->
            asked += wallet
            answer(wallet)
        },
        store = store,
        now = { nowMillis },
        queue = LookupQueue(paceMillis = 0, backOffMillis = 0),
        scope = scope,
        isScripted = isScripted,
    )

    private suspend fun eventually(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(10)
        }
        assertTrue(condition(), "timed out waiting")
    }

    @Test
    fun `kept names are there at once, before any lookup`() {
        store.setSeekerName("w", KeptName("alice.skr", nowMillis - hour))
        store.setSeekerName("none", KeptName(null, nowMillis - hour))
        val names = repository { Lookup.None }.names.value
        assertEquals(mapOf("w" to "alice.skr"), names)
    }

    @Test
    fun `a wallet checked within the day is not looked up again`() = runBlocking {
        store.setSeekerName("w", KeptName("alice.skr", nowMillis - 23 * hour))
        repository { Lookup.Found("other.skr") }.showing(listOf("w"))
        delay(200)
        assertEquals(emptyList(), asked.toList())
    }

    @Test
    fun `a wallet checked more than a day ago is looked up, and the answer kept`() = runBlocking {
        store.setSeekerName("w", KeptName("alice.skr", nowMillis - 25 * hour))
        val repo = repository { Lookup.Found("bob.skr") }
        repo.showing(listOf("w"))
        eventually { repo.names.value["w"] == "bob.skr" }
        assertEquals(listOf("w"), asked.toList())
        assertEquals(KeptName("bob.skr", nowMillis), store.seekerName("w"))
    }

    @Test
    fun `the connected wallet is checked again however fresh`() = runBlocking {
        store.setSeekerName("me", KeptName("alice.skr", nowMillis - hour))
        val repo = repository { Lookup.Found("alice.skr") }
        repo.connected("me")
        eventually { asked.isNotEmpty() }
        assertEquals(listOf("me"), asked.toList())
    }

    @Test
    fun `finding no Seeker ID is kept too, and takes a name away`() = runBlocking {
        store.setSeekerName("w", KeptName("moved.skr", nowMillis - 25 * hour))
        val repo = repository { Lookup.None }
        repo.showing(listOf("w"))
        eventually { store.seekerName("w")?.name == null }
        assertEquals(KeptName(null, nowMillis), store.seekerName("w"))
        assertNull(repo.names.value["w"])
    }

    @Test
    fun `a lookup with no answer changes nothing kept, and the address stays`() = runBlocking {
        val old = KeptName("alice.skr", nowMillis - 25 * hour)
        store.setSeekerName("w", old)
        val repo = repository { Lookup.Unknown(rateLimited = true) }
        repo.showing(listOf("w", "new"))
        eventually { asked.size >= 2 }
        delay(100)
        assertEquals(old, store.seekerName("w"))
        assertNull(store.seekerName("new"))
        assertEquals(mapOf("w" to "alice.skr"), repo.names.value)
    }

    @Test
    fun `a row that leaves the screen before its turn is never looked up`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = repository { wallet ->
            if (wallet == "me") {
                started.complete(Unit)
                release.await()
            }
            Lookup.None
        }
        repo.connected("me")
        started.await()
        repo.showing(listOf("a"))
        repo.showing(emptyList())
        release.complete(Unit)
        eventually { store.seekerName("me") != null }
        delay(200)
        assertEquals(listOf("me"), asked.toList())
    }

    @Test
    fun `a scripted wallet is never looked up, on screen or connected`() = runBlocking {
        val repo = repository(isScripted = { it == "bot" }) { Lookup.Found("$it.skr") }
        repo.showing(listOf("bot", "a"))
        repo.connected("bot")
        eventually { store.seekerName("a") != null }
        delay(200)
        assertEquals(listOf("a"), asked.toList())
        assertNull(store.seekerName("bot"))
        assertEquals(mapOf("a" to "a.skr"), repo.names.value)
    }

    @Test
    fun `the phone keeps at most 5000 entries, dropping the oldest check`() {
        val small = mapOf("a" to KeptName("a.skr", 3), "b" to KeptName(null, 1), "c" to KeptName("c.skr", 2))
        assertEquals("b", KeptName.toDrop(small, "d", max = 3))
        assertNull(KeptName.toDrop(small, "a", max = 3))
        assertNull(KeptName.toDrop(small, "d", max = 4))
        repeat(Store.SEEKER_NAMES_MAX + 3) { store.setSeekerName("w$it", KeptName(null, it.toLong())) }
        assertNull(store.seekerName("w0"))
        assertEquals(KeptName(null, 3), store.seekerName("w3"))
    }

    @Test
    fun `a kept entry survives being written and read back as text`() {
        listOf(KeptName("alice.skr", 42), KeptName(null, 7), KeptName("a|b.skr", 9)).forEach {
            assertEquals(it, KeptName.decode(it.encode()))
        }
        assertNull(KeptName.decode("garbage"))
    }
}
