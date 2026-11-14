package dev.dache.core.icons

import dev.dache.core.model.AccountInfo
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderIcon
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.ProviderCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderIconRepositoryTest {
    private val testScope = TestScope()
    private val io = UnconfinedTestDispatcher(testScope.testScheduler)
    private val fileSystem = FakeFileSystem()
    private val store = FileIconStore(fileSystem, "/data/icons".toPath(), io)

    private var now = Instant.parse("2026-11-15T09:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = this@ProviderIconRepositoryTest.now
        }

    private fun png(marker: Int) =
        ProviderIcon.of(
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), marker.toByte()),
        )!!

    /** A provider whose site is [site], or for a self-hosted one the account's `server` setting. */
    private class IconProvider(
        override val id: ProviderId,
        private val site: String?,
        var answer: () -> Result<ProviderIcon?>,
    ) : DevOpsProvider {
        val fetched = mutableListOf<Map<String, String>>()

        override val capabilities = setOf(Capability.SITES)

        override suspend fun validateCredentials(
            token: String,
            settings: Map<String, String>,
        ) = Result.success(AccountInfo("x"))

        override suspend fun fetchSnapshot(account: ConnectedAccount) = Result.success(ProviderSnapshot())

        override fun iconSite(settings: Map<String, String>) = site ?: settings["server"]?.let { "https://$it" }

        override suspend fun fetchIcon(settings: Map<String, String>): Result<ProviderIcon?> {
            fetched += settings
            return answer()
        }
    }

    private val cloud =
        IconProvider(ProviderId("cloud", "Cloud"), "https://cloud.example.com") { Result.success(png(1)) }
    private val selfHosted = IconProvider(ProviderId("selfhosted", "Self"), null) { Result.success(png(2)) }
    private val accounts = MutableStateFlow<List<ConnectedAccount>>(emptyList())
    private val catalog = ProviderCatalog(listOf(cloud, selfHosted).associateBy { it.id })

    private fun TestScope.repository() =
        ProviderIconRepository(
            catalog,
            accounts,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            store,
            clock,
        ).apply { start() }

    private fun account(
        id: String,
        provider: ProviderId,
        server: String? = null,
    ) = ConnectedAccount(id, provider, id, settings = server?.let { mapOf("server" to it) }.orEmpty())

    @Test
    fun fetchesEachProviderOnceAndEachSelfHostedServerPerAccount() =
        testScope.runTest {
            accounts.value =
                listOf(
                    account("a", cloud.id),
                    account("b", cloud.id),
                    account("c", selfHosted.id, "one.home.example"),
                    account("d", selfHosted.id, "two.home.example"),
                )

            val icons = repository().icons.value

            assertEquals(1, cloud.fetched.size)
            assertEquals(listOf("one.home.example", "two.home.example"), selfHosted.fetched.map { it["server"] })
            assertEquals(png(1), icons.of(cloud.id))
            assertEquals(png(1), icons.of(cloud.id, "a"))
            assertEquals(png(2), icons.of(selfHosted.id, "c"))
            assertNull(icons.of(selfHosted.id), "a self-hosted provider has no icon of its own")
        }

    @Test
    fun theCacheIsUsedUntilItIsDue() =
        testScope.runTest {
            repository()
            assertEquals(1, cloud.fetched.size)

            // A later launch within the month reads the cache and fetches nothing.
            assertEquals(png(1), repository().icons.value.of(cloud.id))
            assertEquals(1, cloud.fetched.size)

            now += ProviderIconRepository.refetchAfter
            repository()
            assertEquals(2, cloud.fetched.size)
        }

    @Test
    fun aSiteWithoutAnIconOrOfflineIsRetriedSooner() =
        testScope.runTest {
            cloud.answer = { Result.success(null) }
            repository()
            now += ProviderIconRepository.retryWhenMissing - 1.days
            repository()
            assertEquals(1, cloud.fetched.size)
            now += 1.days
            repository()
            assertEquals(2, cloud.fetched.size)

            cloud.answer = { Result.failure(Exception("offline")) }
            now += ProviderIconRepository.refetchAfter
            repository()
            now += ProviderIconRepository.retryAfterFailure
            cloud.answer = { Result.success(png(3)) }
            assertEquals(png(3), repository().icons.value.of(cloud.id))
        }

    @Test
    fun aFailedRefetchKeepsTheIconItHad() =
        testScope.runTest {
            repository()
            now += ProviderIconRepository.refetchAfter
            cloud.answer = { Result.failure(Exception("offline")) }

            assertEquals(png(1), repository().icons.value.of(cloud.id))
        }

    @Test
    fun aPluginIconWinsAndIsNeverFetched() =
        testScope.runTest {
            val shipped = png(9)
            val branded =
                IconProvider(ProviderId("branded", "Branded", icon = shipped), "https://branded.example.com") {
                    error("must not fetch")
                }
            catalog.replace(mapOf(branded.id to branded))

            assertEquals(shipped, repository().icons.value.of(branded.id))
            assertEquals(emptyList(), branded.fetched)
        }

    @Test
    fun removedPluginsAndAccountsLoseTheirCachedIcons() =
        testScope.runTest {
            accounts.value = listOf(account("c", selfHosted.id, "one.home.example"))
            repository()
            assertEquals(2, store.load().size)

            accounts.value = emptyList()
            catalog.replace(mapOf(selfHosted.id to selfHosted))

            assertEquals(emptyList(), store.load().map { it.site })
        }
}
