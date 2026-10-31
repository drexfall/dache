package dev.dache.app.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.TokenCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A token check under way or done. Once it is done, exactly one of [result] and [error] is set. */
data class TokenCheckState(
    val isChecking: Boolean = false,
    val result: TokenCheck? = null,
    val error: Throwable? = null,
) {
    companion object {
        fun of(result: Result<TokenCheck>) =
            TokenCheckState(result = result.getOrNull(), error = result.exceptionOrNull())
    }
}

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
    /** Checked while the user types, so missing permissions show before connecting. */
    val check: TokenCheckState = TokenCheckState(),
) {
    /** A token and every required setting are filled in. */
    val isFilledIn: Boolean
        get() = token.isNotBlank() && settings.none { it.required && settingValues[it.key].isNullOrBlank() }

    /** A token known to lack a required permission can't refresh, so it isn't connected. */
    val canSubmit: Boolean
        get() = isFilledIn && !isValidating && check.result?.missingRequired != true

    // The generated toString would print the token into any log or crash report that dumps the state.
    override fun toString(): String =
        "AddAccountState(provider=$provider, token=${if (token.isEmpty()) "" else "***"}, " +
            "settingValues=$settingValues, isValidating=$isValidating, error=$error, sampleData=$sampleData, " +
            "check=$check)"
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
    private val accountChecks = MutableStateFlow<Map<String, TokenCheckState>>(emptyMap())
    private var checkJob: Job? = null

    /** Token checks of connected accounts, by account id, run from their settings pages. */
    val tokenChecks: StateFlow<Map<String, TokenCheckState>> = accountChecks.asStateFlow()

    /** Emits each newly connected account once, so the UI can open its page. */
    val connected: SharedFlow<ConnectedAccount> = connectedEvents

    val state: StateFlow<AccountsUiState> =
        combine(accounts.accounts, addAccount, ::AccountsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsUiState())

    fun startAdding() {
        checkJob?.cancel()
        addAccount.value = forProvider(AddAccountState(), AddAccountState().provider)
    }

    fun cancelAdding() {
        checkJob?.cancel()
        addAccount.value = null
    }

    fun selectProvider(provider: ProviderId) {
        addAccount.update { it?.let { state -> forProvider(state, provider) } }
        scheduleCheck()
    }

    fun updateToken(token: String) {
        addAccount.update { it?.copy(token = token, error = null) }
        scheduleCheck()
    }

    fun updateSetting(
        key: String,
        value: String,
    ) {
        addAccount.update { it?.copy(settingValues = it.settingValues + (key to value), error = null) }
        scheduleCheck()
    }

    /** Checks the token once typing pauses, so each keystroke doesn't send requests. Any earlier check is dropped. */
    private fun scheduleCheck() {
        checkJob?.cancel()
        val current = addAccount.value ?: return
        addAccount.update { it?.copy(check = TokenCheckState(isChecking = current.isFilledIn)) }
        if (!current.isFilledIn) return
        checkJob =
            viewModelScope.launch {
                delay(CHECK_DELAY_MS)
                val result = accounts.checkToken(current.provider, current.token, current.settingValues)
                addAccount.update { it?.copy(check = TokenCheckState.of(result)) }
            }
    }

    /** Checks a connected account's stored token, for its settings page. */
    fun checkAccount(account: ConnectedAccount) {
        accountChecks.update { it + (account.id to TokenCheckState(isChecking = true)) }
        viewModelScope.launch {
            val result = accounts.checkToken(account)
            accountChecks.update { it + (account.id to TokenCheckState.of(result)) }
        }
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
        const val CHECK_DELAY_MS = 600L
    }
}
