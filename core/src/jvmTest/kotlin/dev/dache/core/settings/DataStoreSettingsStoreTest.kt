package dev.dache.core.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreSettingsStoreTest {
    @Test
    fun confirmLinksDefaultsOnAndPersists() =
        runTest {
            val file = Files.createTempDirectory("dache-settings").resolve("settings.preferences_pb").toString()
            val store = DataStoreSettingsStore.create(file)

            assertTrue(store.confirmExternalLinks.first())
            store.setConfirmExternalLinks(false)
            assertFalse(store.confirmExternalLinks.first())
            store.setConfirmExternalLinks(true)
            assertTrue(store.confirmExternalLinks.first())
        }
}
