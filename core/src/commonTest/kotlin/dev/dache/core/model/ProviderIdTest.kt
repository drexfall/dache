package dev.dache.core.model

import dev.dache.core.account.AccountRepository
import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.provider.ProviderCatalog
import dev.dache.core.provider.mock.MockProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderIdTest {
    @Test
    fun idsAreEqualByIdAlone() {
        assertEquals(ProviderId.NETLIFY, ProviderId("netlify"))
        assertEquals(ProviderId.NETLIFY.hashCode(), ProviderId("netlify").hashCode())
    }

    @Test
    fun storesTheIdAndReadsEnumNamesFromOlderBuilds() {
        val account = ConnectedAccount("a1", ProviderId.VERCEL, "me")
        val json = Json.encodeToString(ConnectedAccount.serializer(), account)

        assertEquals("""{"id":"a1","providerId":"vercel","displayName":"me"}""", json)
        val legacy = Json.decodeFromString(ConnectedAccount.serializer(), json.replace("\"vercel\"", "\"VERCEL\""))
        assertEquals(ProviderId.VERCEL, legacy.providerId)
    }

    @Test
    fun storedAccountsTakeTheInstalledProvidersNameAndLinks() =
        runTest {
            val store = InMemoryAccountStore()
            val vault = InMemoryTokenVault()
            val catalog = ProviderCatalog()
            val repository = AccountRepository(store, vault, catalog)
            store.update { listOf(ConnectedAccount("a1", ProviderId("netlify"), "me")) }

            assertNull(
                repository.accounts
                    .first()
                    .single()
                    .providerId.dashboardUrl,
            )
            catalog.replace(mapOf(ProviderId.NETLIFY to MockProvider(ProviderId.NETLIFY, vault)))
            val bound =
                repository.accounts
                    .first()
                    .single()
                    .providerId

            assertEquals("Netlify", bound.displayName)
            assertEquals(ProviderId.NETLIFY.dashboardUrl, bound.dashboardUrl)
        }
}
