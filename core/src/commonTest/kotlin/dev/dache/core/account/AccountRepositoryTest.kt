package dev.dache.core.account

import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.Profile
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.mock.MockProvider
import dev.dache.core.security.VaultUnavailableException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class AccountRepositoryTest {
    private val store = InMemoryAccountStore()
    private val vault = InMemoryTokenVault()
    private val repository =
        AccountRepository(
            store = store,
            vault = vault,
            providers = mapOf(ProviderId.NETLIFY to MockProvider(ProviderId.NETLIFY, vault, latency = Duration.ZERO)),
            newId = { "acc-1" },
        )

    @Test
    fun connectStoresTrimmedTokenInVaultAndAccountWithoutToken() =
        runTest {
            val account = repository.connect(ProviderId.NETLIFY, "  fake-token-123  ").getOrThrow()

            assertEquals("fake-token-123", vault.tokens[account.tokenKey()])
            assertEquals(listOf(account), store.accounts.first())
            assertEquals("netlify", account.tokenKey().pluginId)
        }

    @Test
    fun invalidTokenIsNotStored() =
        runTest {
            val result = repository.connect(ProviderId.NETLIFY, "short")

            assertIs<ProviderError.Unauthorized>(result.exceptionOrNull())
            assertTrue(vault.tokens.isEmpty())
            assertTrue(store.accounts.first().isEmpty())
        }

    @Test
    fun vaultFailureIsReturnedAndNoAccountIsAdded() =
        runTest {
            vault.unavailable = true

            val result = repository.connect(ProviderId.NETLIFY, "fake-token-123")

            assertIs<VaultUnavailableException>(result.exceptionOrNull())
            assertTrue(store.accounts.first().isEmpty())
        }

    @Test
    fun unsupportedProviderFails() =
        runTest {
            val result = repository.connect(ProviderId.GITHUB, "fake-token-123")

            assertIs<ProviderError.NotFound>(result.exceptionOrNull())
        }

    @Test
    fun removeDeletesTokenAndAccount() =
        runTest {
            val account = repository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()

            repository.remove(account)

            assertTrue(vault.tokens.isEmpty())
            assertTrue(store.accounts.first().isEmpty())
        }

    private var nextId = 0
    private val profileRepository =
        AccountRepository(
            store = store,
            vault = vault,
            providers = mapOf(ProviderId.NETLIFY to MockProvider(ProviderId.NETLIFY, vault, latency = Duration.ZERO)),
            newId = { "id-${nextId++}" },
        )

    @Test
    fun accountsStoredBeforeProfilesBelongToTheDefaultProfile() =
        runTest {
            val account = repository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()

            assertEquals(Profile.DEFAULT_ID, account.profileId)
            assertEquals(listOf(account), repository.activeAccounts.first())
        }

    @Test
    fun oneProfileHoldsSeveralTokensForTheSameProvider() =
        runTest {
            val first = profileRepository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            val second = profileRepository.connect(ProviderId.NETLIFY, "fake-token-456").getOrThrow()

            assertEquals(listOf(first, second), profileRepository.activeAccounts.first())
            assertEquals("fake-token-123", vault.tokens[first.tokenKey()])
            assertEquals("fake-token-456", vault.tokens[second.tokenKey()])
        }

    @Test
    fun newAccountsJoinTheActiveProfileAndOnlyItsAccountsAreActive() =
        runTest {
            val personal = profileRepository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            val work = profileRepository.createProfile("  Work  ")!!
            val workAccount = profileRepository.connect(ProviderId.NETLIFY, "fake-token-456").getOrThrow()

            assertEquals("Work", work.name)
            assertEquals(work.id, workAccount.profileId)
            assertEquals(listOf(workAccount), profileRepository.activeAccounts.first())
            assertEquals(2, profileRepository.accounts.first().size)

            profileRepository.switchProfile(Profile.DEFAULT_ID)

            assertEquals(listOf(personal), profileRepository.activeAccounts.first())
        }

    @Test
    fun cyclingWrapsAroundTheProfiles() =
        runTest {
            val work = profileRepository.createProfile("Work")!!
            val home = profileRepository.createProfile("Home")!!

            assertEquals(Profile.DEFAULT_ID, profileRepository.cycleProfile().id)
            assertEquals(work.id, profileRepository.cycleProfile().id)
            assertEquals(home.id, profileRepository.cycleProfile().id)
            assertEquals(
                home.id,
                profileRepository.profiles
                    .first()
                    .active.id,
            )
        }

    @Test
    fun cyclingASingleProfileStaysOnIt() =
        runTest {
            assertEquals(Profile.DEFAULT_ID, profileRepository.cycleProfile().id)
        }

    @Test
    fun blankProfileNamesAreRefused() =
        runTest {
            val work = profileRepository.createProfile("Work")!!

            assertEquals(null, profileRepository.createProfile("   "))
            profileRepository.renameProfile(work.id, " ")
            assertEquals(
                "Work",
                profileRepository.profiles
                    .first()
                    .active.name,
            )

            profileRepository.renameProfile(work.id, "Clients")
            assertEquals(
                "Clients",
                profileRepository.profiles
                    .first()
                    .active.name,
            )
        }

    @Test
    fun deletingAProfileRemovesItsAccountsAndTokens() =
        runTest {
            val personal = profileRepository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            val work = profileRepository.createProfile("Work")!!
            val workAccount = profileRepository.connect(ProviderId.NETLIFY, "fake-token-456").getOrThrow()

            assertTrue(profileRepository.deleteProfile(work.id))

            assertEquals(listOf(personal), store.accounts.first())
            assertEquals(null, vault.tokens[workAccount.tokenKey()])
            val profiles = profileRepository.profiles.first()
            assertEquals(listOf(Profile.DEFAULT), profiles.all)
            assertEquals(Profile.DEFAULT_ID, profiles.activeId)
        }

    @Test
    fun theLastProfileCannotBeDeleted() =
        runTest {
            val account = profileRepository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()

            assertFalse(profileRepository.deleteProfile(Profile.DEFAULT_ID))
            assertEquals(listOf(account), store.accounts.first())
        }

    @Test
    fun movingAnAccountKeepsItsToken() =
        runTest {
            val account = profileRepository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            val work = profileRepository.createProfile("Work")!!

            profileRepository.moveAccount(account, work.id)

            val moved = profileRepository.activeAccounts.first().single()
            assertEquals(account.id, moved.id)
            assertEquals("fake-token-123", vault.tokens[moved.tokenKey()])
        }
}
