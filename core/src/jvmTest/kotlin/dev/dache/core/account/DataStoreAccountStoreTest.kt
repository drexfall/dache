package dev.dache.core.account

import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreAccountStoreTest {
    private fun newStore(): DataStoreAccountStore =
        DataStoreAccountStore.create(
            Files.createTempDirectory("dache-accounts").resolve("accounts.preferences_pb").toString(),
        )

    @Test
    fun startsWithTheDefaultProfileActive() =
        runTest {
            assertEquals(Profiles(), newStore().profiles.first())
        }

    @Test
    fun profilesAndTheActiveOnePersist() =
        runTest {
            val store = newStore()
            val work = Profile("work", "Work")

            store.updateProfiles { Profiles(it.all + work, work.id) }

            assertEquals(Profiles(listOf(Profile.DEFAULT, work), work.id), store.profiles.first())
        }

    @Test
    fun emptyingTheProfilesBringsBackTheDefault() =
        runTest {
            val store = newStore()

            store.updateProfiles { Profiles(emptyList(), "gone") }

            assertEquals(Profiles(), store.profiles.first())
        }

    @Test
    fun accountsWithoutAProfileReadAsTheDefaultOne() =
        runTest {
            val store = newStore()
            store.update { listOf(ConnectedAccount("a", ProviderId.NETLIFY, "me", profileId = "work")) }

            assertEquals(
                "work",
                store.accounts
                    .first()
                    .single()
                    .profileId,
            )
            // An account saved before profiles existed has no profileId in its JSON.
            val legacy = """[{"id":"b","providerId":"netlify","displayName":"me"}]"""
            assertEquals(Profile.DEFAULT_ID, Json.decodeFromString<List<ConnectedAccount>>(legacy).single().profileId)
        }
}
