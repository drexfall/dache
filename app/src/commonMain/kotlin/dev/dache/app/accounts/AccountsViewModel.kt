package dev.dache.app.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddAccountState(
    val provider: ProviderId = ProviderId.NETLIFY,
    val token: String = "",
    val isValidating: Boolean = false,
    val error: Throwable? = null,
    /** The selected provider serves sample data and accepts any token. */
    val sampleData: Boolean = false,
) {
    val canSubmit: Boolean get() = token.isNotBlank() && !isValidating
}

data class AccountsUiState(
    val accounts: List<ConnectedAccount> = emptyList(),
    val addAccount: AddAccountState? = null,
)

class AccountsViewModel(
    private val accounts: AccountRepository,
    private val dashboard: DashboardRepository,
    private val sampleDataProviders: Set<ProviderId> = ProviderId.entries.toSet(),
) : ViewModel() {
    private val addAccount = MutableStateFlow<AddAccountState?>(null)
    private val connectedEvents = MutableSharedFlow<ConnectedAccount>(extraBufferCapacity = 1)

    /** Emits each newly connected account once, so the UI can open its page. */
    val connected: SharedFlow<ConnectedAccount> = connectedEvents

    val state: StateFlow<AccountsUiState> =
        combine(accounts.accounts, addAccount, ::AccountsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsUiState())

    fun startAdding() {
        val provider = AddAccountState().provider
        addAccount.value = AddAccountState(provider = provider, sampleData = provider in sampleDataProviders)
    }

    fun cancelAdding() {
        addAccount.value = null
    }

    fun selectProvider(provider: ProviderId) {
        addAccount.update { it?.copy(provider = provider, error = null, sampleData = provider in sampleDataProviders) }
    }

    fun updateToken(token: String) {
        addAccount.update { it?.copy(token = token, error = null) }
    }

    fun submit() {
        val current = addAccount.value ?: return
        if (!current.canSubmit) return
        addAccount.value = current.copy(isValidating = true, error = null)
        viewModelScope.launch {
            accounts
                .connect(current.provider, current.token)
                .onSuccess { account ->
                    addAccount.value = null
                    connectedEvents.tryEmit(account)
                    dashboard.refresh()
                }.onFailure { error -> addAccount.update { it?.copy(isValidating = false, error = error) } }
        }
    }

    fun remove(account: ConnectedAccount) {
        viewModelScope.launch { accounts.remove(account) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
