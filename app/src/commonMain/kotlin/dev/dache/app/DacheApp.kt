package dev.dache.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dache.app.account.AccountScreen
import dev.dache.app.account.AccountSettingsScreen
import dev.dache.app.accounts.AccountsScreen
import dev.dache.app.accounts.AccountsViewModel
import dev.dache.app.accounts.AddAccountFlow
import dev.dache.app.accounts.TokenCheckState
import dev.dache.app.dashboard.DashboardViewModel
import dev.dache.app.navigation.AppNavigator
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.DestinationSaver
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.navigation.Sidebar
import dev.dache.app.navigation.accountId
import dev.dache.app.navigation.openResource
import dev.dache.app.navigation.sidebarEntry
import dev.dache.app.overview.OverviewScreen
import dev.dache.app.resource.ResourceScreen
import dev.dache.app.security.AppLockCard
import dev.dache.app.security.AppLockController
import dev.dache.app.security.IntegrityNote
import dev.dache.app.security.IntegrityWarning
import dev.dache.app.security.LockAuthenticator
import dev.dache.app.security.LockLoading
import dev.dache.app.security.LockScreen
import dev.dache.app.security.LockState
import dev.dache.app.security.rememberIntegritySignals
import dev.dache.app.service.ServiceScreen
import dev.dache.app.ui.components.ExternalLinkHost
import dev.dache.app.ui.layout.EXPANDED_MIN_WIDTH
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.theme.DacheTheme
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ResourceRef
import dev.dache.core.security.IntegrityChecker
import dev.dache.core.security.IntegritySignal
import dev.dache.core.settings.AppLockSettings
import kotlinx.coroutines.launch

/**
 * Pass [lockAuthenticator] on platforms with a screen lock; without one the app lock isn't offered. Pass
 * [integrityChecker] where the device can be checked for root; without one no warning is shown.
 */
@Composable
fun DacheApp(
    container: AppContainer,
    lockAuthenticator: LockAuthenticator? = null,
    integrityChecker: IntegrityChecker? = null,
) {
    DacheTheme {
        val signals = rememberIntegritySignals(integrityChecker)
        // Above the lock, so the open page is still there after unlocking.
        val destinationState =
            rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf<Destination>(Destination.Overview) }
        if (lockAuthenticator == null) {
            DacheContent(container, destinationState, signals, securityCard = null)
        } else {
            val lockState by container.appLock.state.collectAsStateWithLifecycle()
            when (lockState) {
                LockState.LOADING -> LockLoading()
                LockState.LOCKED -> LockGate(container.appLock, lockAuthenticator)
                LockState.UNLOCKED ->
                    DacheContent(container, destinationState, signals) { AppLockSection(container, lockAuthenticator) }
            }
        }
    }
}

@Composable
private fun LockGate(
    lock: AppLockController,
    authenticator: LockAuthenticator,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // A lock left on after the device's screen lock was removed has nothing to ask for.
        if (authenticator.isAvailable()) lock.authenticate(authenticator::authenticate) else lock.unlockWithoutPrompt()
    }
    LockScreen(onUnlock = { scope.launch { lock.authenticate(authenticator::authenticate) } })
}

@Composable
private fun AppLockSection(
    container: AppContainer,
    authenticator: LockAuthenticator,
) {
    val settings by container.settingsStore.appLock.collectAsStateWithLifecycle(AppLockSettings())
    val scope = rememberCoroutineScope()
    val available = authenticator.isAvailable()
    AppLockCard(
        settings = settings,
        available = available,
        onToggleLock = {
            scope.launch {
                // Asking both ways checks the prompt works before relying on it, and stops someone holding an
                // unlocked phone from switching the lock off for later.
                if (!available || container.appLock.authenticate(authenticator::authenticate)) {
                    container.setAppLock(settings.copy(enabled = !settings.enabled))
                }
            }
        },
        onChange = container::setAppLock,
    )
}

@Composable
private fun DacheContent(
    container: AppContainer,
    destinationState: MutableState<Destination>,
    signals: Set<IntegritySignal>,
    securityCard: (@Composable () -> Unit)?,
) {
    val dashboardViewModel =
        viewModel {
            DashboardViewModel(
                container.dashboardRepository,
                container.accountRepository,
                container.sampleDataProviders,
            )
        }
    val accountsViewModel =
        viewModel {
            AccountsViewModel(
                container.accountRepository,
                container.dashboardRepository,
                container.sampleDataProviders,
            )
        }
    val state by dashboardViewModel.state.collectAsStateWithLifecycle()
    val accountsState by accountsViewModel.state.collectAsStateWithLifecycle()
    val tokenChecks by accountsViewModel.tokenChecks.collectAsStateWithLifecycle()
    val confirmLinks by container.settingsStore.confirmExternalLinks.collectAsStateWithLifecycle(
        initialValue = true,
    )
    var destination by destinationState
    val navigator = remember(destinationState) { StateNavigator(destinationState) }

    LaunchedEffect(accountsViewModel) {
        accountsViewModel.connected.collect { destination = Destination.Account(it.id) }
    }
    // A removed account's pages have nothing left to show.
    val currentAccountId = destination.accountId
    if (currentAccountId != null &&
        state.lastUpdated != null &&
        state.accounts.none { it.id == currentAccountId }
    ) {
        destination = Destination.Overview
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val formFactor = if (maxWidth >= EXPANDED_MIN_WIDTH) FormFactor.EXPANDED else FormFactor.COMPACT
        CompositionLocalProvider(LocalFormFactor provides formFactor, LocalAppNavigator provides navigator) {
            ExternalLinkHost(confirm = confirmLinks, onStopAsking = { container.setConfirmExternalLinks(false) }) {
                val sidebar: @Composable (onPicked: () -> Unit) -> Unit = { onPicked ->
                    Sidebar(
                        state = state,
                        current = destination.sidebarEntry,
                        onSelect = {
                            destination = it
                            onPicked()
                        },
                        onAddAccount = {
                            accountsViewModel.startAdding()
                            onPicked()
                        },
                    )
                }
                val page: @Composable (onOpenMenu: (() -> Unit)?) -> Unit = { onOpenMenu ->
                    when (val target = destination) {
                        Destination.Overview ->
                            OverviewScreen(
                                state = state,
                                onRefresh = dashboardViewModel::refresh,
                                onOpenMenu = onOpenMenu,
                                onConnectAccount = accountsViewModel::startAdding,
                                onTryDemo =
                                    dashboardViewModel::loadDemoAccounts.takeIf {
                                        container.sampleDataProviders.isNotEmpty()
                                    },
                            )
                        is Destination.Account, is Destination.Service, is Destination.AccountSettings ->
                            state.accounts.firstOrNull { it.id == target.accountId }?.let { account ->
                                AccountPage(
                                    target = target,
                                    account = account,
                                    state = state,
                                    check = tokenChecks[account.id] ?: TokenCheckState(),
                                    settings = { container.accountRepository.settings(account.providerId) },
                                    onCheck = { accountsViewModel.checkAccount(account) },
                                    onRefresh = dashboardViewModel::refresh,
                                    onOpenMenu = onOpenMenu,
                                    onNavigate = { destination = it },
                                )
                            }
                        is Destination.Resource ->
                            // Keyed so each page starts scrolled to the top.
                            key(target) {
                                ResourceScreen(
                                    destination = target,
                                    state = state,
                                    onRefresh = dashboardViewModel::refresh,
                                    onOpenMenu = onOpenMenu,
                                    onNavigate = { destination = it },
                                )
                            }
                        Destination.ManageAccounts ->
                            AccountsScreen(
                                accounts = accountsState.accounts,
                                isRefreshing = state.isRefreshing,
                                onRefresh = dashboardViewModel::refresh,
                                onOpenMenu = onOpenMenu,
                                onAddAccount = accountsViewModel::startAdding,
                                onRemove = accountsViewModel::remove,
                                onOpenSettings = { destination = Destination.AccountSettings(it.id) },
                                confirmExternalLinks = confirmLinks,
                                onConfirmExternalLinksChange = container::setConfirmExternalLinks,
                                securityCard = {
                                    IntegrityNote(signals)
                                    securityCard?.invoke()
                                },
                            )
                    }
                }

                if (formFactor == FormFactor.EXPANDED) {
                    PermanentNavigationDrawer(
                        drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { sidebar {} } },
                    ) { page(null) }
                } else {
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = { ModalDrawerSheet { sidebar { scope.launch { drawerState.close() } } } },
                    ) { page { scope.launch { drawerState.open() } } }
                }

                IntegrityWarning(
                    signals = signals,
                    hasAccounts = state.accounts.isNotEmpty(),
                    settingsStore = container.settingsStore,
                    onSave = container::setIntegrityWarning,
                )
                accountsState.addAccount?.let { addState ->
                    AddAccountFlow(
                        state = addState,
                        onProviderSelected = accountsViewModel::selectProvider,
                        onTokenChanged = accountsViewModel::updateToken,
                        onSettingChanged = accountsViewModel::updateSetting,
                        onSubmit = accountsViewModel::submit,
                        onDismiss = accountsViewModel::cancelAdding,
                    )
                }
            }
        }
    }
}

/** An account's dashboard, one of its service pages, or its settings. [target] is one of those three. */
@Composable
private fun AccountPage(
    target: Destination,
    account: ConnectedAccount,
    state: DashboardState,
    check: TokenCheckState,
    settings: () -> List<AccountSetting>,
    onCheck: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    when (target) {
        is Destination.Service ->
            // Keyed so each page starts scrolled to the top.
            key(target) { ServiceScreen(target, account, state, onRefresh, onOpenMenu, onNavigate) }
        is Destination.AccountSettings ->
            AccountSettingsScreen(
                destination = target,
                account = account,
                settings = settings(),
                state = state,
                check = check,
                onCheck = onCheck,
                onRefresh = onRefresh,
                onOpenMenu = onOpenMenu,
                onNavigate = onNavigate,
            )
        else -> AccountScreen(account, state, onRefresh, onOpenMenu, onNavigate)
    }
}

private class StateNavigator(
    private val destination: MutableState<Destination>,
) : AppNavigator {
    override fun openAccount(accountId: String) {
        destination.value = Destination.Account(accountId)
    }

    override fun openResource(ref: ResourceRef) {
        destination.value = destination.value.openResource(ref)
    }

    override fun openManageAccounts() {
        destination.value = Destination.ManageAccounts
    }
}
