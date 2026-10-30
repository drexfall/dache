package dev.dache.app.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.AccountSetting
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
    /** What the selected provider asks for besides the token. */
    val settings: List<AccountSetting> = emptyList(),
    val settingValues: Map<String, String> = emptyMap(),
    val isValidating: Boolean = false,
    val error: Throwable? = null,
    /** The selected provider serves sample data and accepts any token. */
    val sampleData: Boolean = false,
) {
    val canSubmit: Boolean
        get() =
            token.isNotBlank() &&
                !isValidating &&
                settings.none { it.required && settingValues[it.key].isNullOrBlank() }

    // The generated toString would print the token into any log or crash report that dumps the state.
    override fun toString(): String =
        "AddAccountState(provider=$provider, token=${if (token.isEmpty()) "" else "***"}, " +
            "settingValues=$settingValues, isValidating=$isValidating, error=$error, sampleData=$sampleData)"
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
        addAccount.value = forProvider(AddAccountState(), AddAccountState().provider)
    }

    fun cancelAdding() {
        addAccount.value = null
    }

    fun selectProvider(provider: ProviderId) {
        addAccount.update { it?.let { state -> forProvider(state, provider) } }
    }

    fun updateToken(token: String) {
        addAccount.update { it?.copy(token = token, error = null) }
    }

    fun updateSetting(
        key: String,
        value: String,
    ) {
        addAccount.update { it?.copy(settingValues = it.settingValues + (key to value), error = null) }
    }

    /** Settings belong to one provider, so switching clears them; the token is kept. */
    private fun forProvider(
        state: AddAccountState,
        provider: ProviderId,
    ) = state.copy(
        provider = provider,
        settings = accounts.settings(provider),
        settingValues = emptyMap(),
        error = null,
        sampleData = provider in sampleDataProviders,
    )

    fun submit() {
        val current = addAccount.value ?: return
        if (!current.canSubmit) return
        addAccount.value = current.copy(isValidating = true, error = null)
        viewModelScope.launch {
            accounts
                .connect(current.provider, current.token, current.settingValues)
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
