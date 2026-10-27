package dev.dache.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.io.File
import java.util.Base64

/**
 * Encrypts tokens with an AES-256-GCM keyset that is itself wrapped by an Android Keystore master key.
 * Only ciphertext is persisted. The token key is bound as associated data, so a ciphertext copied to
 * another slot will not decrypt. Create one instance per process.
 */
class TinkTokenVault(
    context: Context,
) : TokenVault {
    private val appContext = context.applicationContext

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager
            .Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(appContext.filesDir, STORE_FILE).absolutePath.toPath() },
        )

    override suspend fun put(
        key: TokenKey,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val ciphertext = aead.encrypt(token.toByteArray(), key.associatedData())
        store.edit { it[key.prefKey()] = Base64.getEncoder().encodeToString(ciphertext) }
        Unit
    }

    override suspend fun get(key: TokenKey): String? =
        withContext(Dispatchers.IO) {
            val encoded = store.data.first()[key.prefKey()] ?: return@withContext null
            String(aead.decrypt(Base64.getDecoder().decode(encoded), key.associatedData()))
        }

    override suspend fun delete(key: TokenKey) =
        withContext(Dispatchers.IO) {
            store.edit { it.remove(key.prefKey()) }
            Unit
        }

    private fun TokenKey.prefKey() = stringPreferencesKey("$pluginId:$accountId")

    private fun TokenKey.associatedData() = "$pluginId:$accountId".toByteArray()

    private companion object {
        const val KEYSET_NAME = "dache_token_keyset"
        const val KEYSET_PREFS = "dache_token_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://dache_token_master_key"
        const val STORE_FILE = "token_vault.preferences_pb"
    }
}
