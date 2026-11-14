package dev.dache.core.dashboard

import dev.dache.core.account.AccountRepository
import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ProviderStatus
import dev.dache.core.model.StatusLevel
import dev.dache.core.provider.mock.MockFixtures
import dev.dache.core.provider.mock.MockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotStoreTest {
    private val now = Instant.parse("2026-11-14T09:00:00Z")
    private val fileSystem = FakeFileSystem()
    private val dir = "/data/snapshots".toPath()

    // File work runs on the test scheduler, not Dispatchers.IO: on real threads the repository's keepOnly could
    // land after a later save, or not have finished when an assertion read the directory.
    private val testScope = TestScope()
    private val io = UnconfinedTestDispatcher(testScope.testScheduler)
    private val store = FileSnapshotStore(fileSystem, dir, io)

    @Test
    fun roundTripsEveryProvidersSnapshotAndTheStatuses() =
        testScope.runTest {
            val status =
                ProviderStatus(ProviderId.NETLIFY, StatusLevel.DEGRADED, "Slow", null, emptyList(), emptyList(), now)
            ProviderId.entries.forEachIndexed { i, id ->
                store.save(CachedSnapshot("acc-$i", now, MockFixtures.snapshot(id, "acc-$i", now)))
            }
            store.saveStatuses(listOf(status))

            val cached = FileSnapshotStore(fileSystem, dir, io).load()

            assertEquals(ProviderId.entries.size, cached.snapshots.size)
            ProviderId.entries.forEachIndexed { i, id ->
                assertEquals(
                    MockFixtures.snapshot(id, "acc-$i", now),
                    cached.snapshots
                        .single {
                            it.accountId ==
                                "acc-$i"
                        }.snapshot,
                )
            }
            assertEquals(listOf(status), cached.statuses)
            assertEquals(now, cached.lastUpdated())
        }

    @Test
    fun keepOnlyDeletesTheOtherAccountsFiles() =
        testScope.runTest {
            store.save(CachedSnapshot("keep", now, ProviderSnapshot()))
            store.save(CachedSnapshot("gone", now, ProviderSnapshot()))
            store.saveStatuses(emptyList())

            store.keepOnly(listOf("keep"))

            assertEquals(listOf("keep"), store.load().snapshots.map { it.accountId })
            assertTrue(fileSystem.exists(dir / "statuses.json"))
        }

    @Test
    fun anUnreadableFileIsSkippedNotFatal() =
        testScope.runTest {
            store.save(CachedSnapshot("good", now, ProviderSnapshot()))
            fileSystem.write(dir / "bad.json") { writeUtf8("{ not json") }
            fileSystem.write(dir / "statuses.json") { writeUtf8("[1, 2") }

            val cached = store.load()

            assertEquals(listOf("good"), cached.snapshots.map { it.accountId })
            assertTrue(cached.statuses.isEmpty())
        }

    @Test
    fun anEmptyDirectoryIsAColdStart() {
        assertNull(store.load().lastUpdated())
        assertTrue(store.load().snapshots.isEmpty())
    }

    @Test
    fun theCacheNeverHoldsTheToken() =
        testScope.runTest {
            val token = "secret-token-7f3a9c"
            val vault = InMemoryTokenVault()
            val clock =
                object : Clock {
                    override fun now() = this@SnapshotStoreTest.now
                }
            val providers = ProviderId.entries.associateWith { MockProvider(it, vault, clock, Duration.ZERO) }
            val accounts = AccountRepository(InMemoryAccountStore(), vault, providers)
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
            val dashboard = DashboardRepository(accounts, scope, clock, store)
            ProviderId.entries.forEach { accounts.connect(it, token).getOrThrow() }

            dashboard.refresh()

            val files = fileSystem.list(dir)
            assertEquals(ProviderId.entries.size + 1, files.size)
            files.forEach { file ->
                val text = fileSystem.read(file) { readUtf8() }
                assertFalse(token in text, "${file.name} holds the token")
                assertFalse("secret" in text.lowercase(), "${file.name} mentions a secret")
            }
        }

    @Test
    fun aColdStartShowsTheCacheAndAFailedRefreshKeepsIt() =
        testScope.runTest {
            val clock =
                object : Clock {
                    override fun now() = this@SnapshotStoreTest.now
                }
            val vault = InMemoryTokenVault()
            val providers = ProviderId.entries.associateWith { MockProvider(it, vault, clock, Duration.ZERO) }
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
            val accountStore = InMemoryAccountStore()
            val accounts = AccountRepository(accountStore, vault, providers)
            val first = DashboardRepository(accounts, scope, clock, store)
            val account = accounts.connect(ProviderId.NETLIFY, "fake-token").getOrThrow()
            first.refresh()
            val fetched =
                first.state.value.results
                    .single()
            assertEquals(now, fetched.fetchedAt)

            // A new process: the cache is all it has, dated by the last refresh, and the provider resolves.
            val later =
                object : Clock {
                    override fun now() = this@SnapshotStoreTest.now + 3.hours
                }
            val second = DashboardRepository(accounts, scope, later, FileSnapshotStore(fileSystem, dir, io))
            val restored = second.state.value
            assertEquals(now, restored.lastUpdated)
            assertEquals(fetched.snapshot, restored.results.single().snapshot)
            assertEquals(
                "Netlify",
                restored.results
                    .single()
                    .account.providerId.displayName,
            )
            assertEquals(
                2,
                restored.snapshots
                    .single()
                    .sites.size,
            )
            assertEquals(
                "Netlify",
                restored.snapshots
                    .single()
                    .sites
                    .first()
                    .providerId.displayName,
            )
            assertTrue(restored.isStale(later.now(), 1.hours))

            // The token is now wrong: the refresh fails but the old snapshot stays underneath the error.
            vault.tokens.keys.forEach { vault.tokens[it] = "${MockProvider.FAIL_PREFIX}token" }
            second.refresh()
            val failed =
                second.state.value.results
                    .single()
            assertNotNull(failed.error)
            assertEquals(fetched.snapshot, failed.snapshot)
            assertEquals(now, failed.fetchedAt)
            assertEquals(later.now(), second.state.value.lastUpdated)

            // Removing the account removes its file.
            accounts.remove(account)
            assertTrue(FileSnapshotStore(fileSystem, dir, io).load().snapshots.isEmpty())
        }
}
