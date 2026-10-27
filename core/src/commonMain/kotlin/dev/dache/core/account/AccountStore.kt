package dev.dache.core.account

import dev.dache.core.model.ConnectedAccount
import kotlinx.coroutines.flow.Flow

/** Persists non-secret account metadata. */
interface AccountStore {
    val accounts: Flow<List<ConnectedAccount>>

    suspend fun update(transform: (List<ConnectedAccount>) -> List<ConnectedAccount>)
}
