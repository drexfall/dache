package dev.dache.core.actions

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * One write Dache sent: when, with which plugin and account, what it did to what, and whether it worked. Never
 * the token, inputs or the request, which can hold values the user wouldn't want kept.
 */
@Serializable
data class WriteLogEntry(
    val atMillis: Long,
    val pluginId: String,
    val accountId: String,
    val accountName: String,
    val action: String,
    val target: String,
    val ok: Boolean,
    val error: String? = null,
)

/** Kept on the device only, newest first, up to [MAX_ENTRIES]. */
interface WriteLog {
    val entries: Flow<List<WriteLogEntry>>

    suspend fun append(entry: WriteLogEntry)

    companion object {
        const val MAX_ENTRIES = 200
    }
}
