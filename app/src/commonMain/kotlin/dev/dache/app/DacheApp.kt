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
import dev.dache.app.actions.ActionDialog
import dev.dache.app.actions.ActionHost
import dev.dache.app.actions.ActionsViewModel
import dev.dache.app.actions.LocalActionHost
import dev.dache.app.alerts.BackgroundChecksCard
import dev.dache.app.alerts.BackgroundChecksPlatform
import dev.dache.app.dashboard.DashboardViewModel
import dev.dache.app.navigation.BackNavigation
import dev.dache.app.navigation.BackRequests
import dev.dache.app.navigation.BackStack
import dev.dache.app.navigation.BackStackSaver
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.navigation.Sidebar
import dev.dache.app.navigation.StateNavigator
import dev.dache.app.navigation.accountId
import dev.dache.app.navigation.current
import dev.dache.app.navigation.keepingAccounts
import dev.dache.app.navigation.push
import dev.dache.app.navigation.sidebarEntry
import dev.dache.app.overview.OverviewScreen
import dev.dache.app.plugins.PluginsScreen
import dev.dache.app.plugins.PluginsViewModel
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
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Resource
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.TokenPermission
import dev.dache.core.security.IntegrityChecker
import dev.dache.core.security.IntegritySignal
import dev.dache.core.settings.AppLockSettings
import kotlinx.coroutines.launch

/**
 * Pass [lockAuthenticator] on platforms with a screen lock; without one the app lock isn't offered. Pass
 * [integrityChecker] where the device can be checked for root; without one no warning is shown. Pass
 * [backgroundChecks] where the platform can check in the background and notify; without it they aren't offered.
 * [backRequests] carries back requests from outside the UI tree, such as desktop keyboard shortcuts.
 */
@Composable
fun DacheApp(
    container: AppContainer,
    lockAuthenticator: LockAuthenticator? = null,
    integrityChecker: IntegrityChecker? = null,
    backgroundChecks: BackgroundChecksPlatform? = null,
    backRequests: BackRequests? = null,
) {
    DacheTheme {
        val signals = rememberIntegritySignals(integrityChecker)
        // Above the lock, so the open pages are still there after unlocking.
        val stackState = rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(listOf(Destination.Overview)) }
        val platform = Platform(signals, backgroundChecks, lockAuthenticator, backRequests)
        if (lockAuthenticator == null) {
            DacheContent(container, stackState, platform, securityCard = null)
        } else {
            val lockState by container.appLock.state.collectAsStateWithLifecycle()
            when (lockState) {
                LockState.LOADING -> LockLoading()
                LockState.LOCKED -> LockGate(container.appLock, lockAuthenticator)
                LockState.UNLOCKED ->
                    DacheContent(container, stackState, platform) { AppLockSection(container, lockAuthenticator) }
            }
        }
    }
}

/** What the platform the app runs on provides, bundled so the content doesn't take six optional parameters. */
private class Platform(
    val signals: Set<IntegritySignal>,
    val backgroundChecks: BackgroundChecksPlatform?,
    val lockAuthenticator: LockAuthenticator?,
    val backRequests: BackRequests?,
)

/** Destructive actions ask for the device lock where there is one, and for the target's name otherwise. */
private fun actionsViewModel(
    container: AppContainer,
    lockAuthenticator: LockAuthenticator?,
): ActionsViewModel {
    val lock = lockAuthenticator?.takeIf { it.isAvailable() }
    return ActionsViewModel(container.actionService, lock?.let { { container.appLock.confirm(it::authenticate) } })
}

@Composable
private fun ActionDialogHost(viewModel: ActionsViewModel) {
    val step by viewModel.state.collectAsStateWithLifecycle()
    step?.let { ActionDialog(it, viewModel) }
}

@Composable
private fun PluginsPage(
    container: AppContainer,
    onOpenMenu: (() -> Unit)?,
) {
    val pluginsViewModel = viewModel { PluginsViewModel(container.plugins, container.registryClient) }
    val pluginsState by pluginsViewModel.state.collectAsStateWithLifecycle()
    PluginsScreen(pluginsViewModel, pluginsState, onOpenMenu)
}

private fun backgroundCard(
    container: AppContainer,
    platform: BackgroundChecksPlatform?,
): (@Composable () -> Unit)? = platform?.let { { BackgroundChecksSection(container, it) } }

@Composable
private fun BackgroundChecksSection(
    container: AppContainer,
    platform: BackgroundChecksPlatform,
) {
    val settings by container.alertStore.alertSettings.collectAsStateWithLifecycle(AlertSettings())
    BackgroundChecksCard(settings, platform.access, container::setAlertSettings, platform.footer)
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
    stackState: MutableState<BackStack>,
    platform: Platform,
    securityCard: (@Composable () -> Unit)?,
) {
    val dashboardViewModel =
        viewModel {
            DashboardViewModel(container.dashboardRepository)
        }
    val accountsViewModel =
        viewModel {
            AccountsViewModel(
                container.accountRepository,
                container.dashboardRepository,
            )
        }
    val actionsViewModel = viewModel { actionsViewModel(container, platform.lockAuthenticator) }
    val state by dashboardViewModel.state.collectAsStateWithLifecycle()
    val accountsState by accountsViewModel.state.collectAsStateWithLifecycle()
    val tokenChecks by accountsViewModel.tokenChecks.collectAsStateWithLifecycle()
    val writeLog by container.writeLog.entries.collectAsStateWithLifecycle(emptyList())
    val confirmLinks by container.settingsStore.confirmExternalLinks.collectAsStateWithLifecycle(
        initialValue = true,
    )
    var stack by stackState
    val destination = stack.current
    val navigator = remember(stackState) { StateNavigator(stackState) }
    val open: (Destination) -> Unit = { stack = stack.push(it) }

    LaunchedEffect(accountsViewModel) {
        accountsViewModel.connected.collect { open(Destination.Account(it.id)) }
    }
    LaunchedEffect(Unit) { container.updatePluginsInBackground() }
    // A removed account's pages have nothing left to show.
    LaunchedEffect(state.accounts, state.accountsLoaded) {
        if (state.accountsLoaded) stack = stack.keepingAccounts(state.accounts.map { it.id })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val formFactor = if (maxWidth >= EXPANDED_MIN_WIDTH) FormFactor.EXPANDED else FormFactor.COMPACT
        val actionHost = remember(state.accounts) { DashboardActionHost(container, state.accounts, actionsViewModel) }
        CompositionLocalProvider(
            LocalFormFactor provides formFactor,
            LocalAppNavigator provides navigator,
            LocalActionHost provides actionHost,
        ) {
            ExternalLinkHost(confirm = confirmLinks, onStopAsking = { container.setConfirmExternalLinks(false) }) {
                val sidebar: @Composable (onPicked: () -> Unit) -> Unit = { onPicked ->
                    Sidebar(
                        state = state,
                        current = destination.sidebarEntry,
                        onSelect = {
                            open(it)
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
                            )
                        is Destination.Account, is Destination.Service, is Destination.AccountSettings ->
                            state.accounts.firstOrNull { it.id == target.accountId }?.let { account ->
                                AccountPage(
                                    target = target,
                                    trail = stack,
                                    account = account,
                                    state = state,
                                    check = tokenChecks[account.id] ?: TokenCheckState(),
                                    settings = container.accountRepository.settings(account.providerId),
                                    permissions = container.accountRepository.tokenPermissions(account.providerId),
                                    onCheck = { accountsViewModel.checkAccount(account) },
                                    onRefresh = dashboardViewModel::refresh,
                                    onOpenMenu = onOpenMenu,
                                    onNavigate = open,
                                )
                            }
                        is Destination.Resource ->
                            // Keyed so each page starts scrolled to the top.
                            key(target) {
                                ResourceScreen(
                                    destination = target,
                                    trail = stack,
                                    state = state,
                                    onRefresh = dashboardViewModel::refresh,
                                    onOpenMenu = onOpenMenu,
                                    onNavigate = open,
                                )
                            }
                        Destination.Plugins -> PluginsPage(container, onOpenMenu)
                        Destination.ManageAccounts ->
                            AccountsScreen(
                                accounts = accountsState.accounts,
                                isRefreshing = state.isRefreshing,
                                onRefresh = dashboardViewModel::refresh,
                                onOpenMenu = onOpenMenu,
                                onAddAccount = accountsViewModel::startAdding,
                                onRemove = accountsViewModel::remove,
                                onOpenSettings = { open(Destination.AccountSettings(it.id)) },
                                confirmExternalLinks = confirmLinks,
                                onConfirmExternalLinksChange = container::setConfirmExternalLinks,
                                securityCard = {
                                    IntegrityNote(platform.signals)
                                    securityCard?.invoke()
                                },
                                backgroundCard = backgroundCard(container, platform.backgroundChecks),
                                writeLog = writeLog,
                            )
                    }
                }

                if (formFactor == FormFactor.EXPANDED) {
                    BackNavigation(stackState, platform.backRequests, closeDrawer = null)
                    PermanentNavigationDrawer(
                        drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { sidebar {} } },
                    ) { page(null) }
                } else {
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    // Back closes an open drawer before it leaves the page.
                    val closeDrawer: (() -> Unit)? =
                        if (drawerState.isOpen) ({ scope.launch { drawerState.close() } }) else null
                    BackNavigation(stackState, platform.backRequests, closeDrawer)
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = { ModalDrawerSheet { sidebar { scope.launch { drawerState.close() } } } },
                    ) { page { scope.launch { drawerState.open() } } }
                }

                IntegrityWarning(
                    signals = platform.signals,
                    hasAccounts = state.accounts.isNotEmpty(),
                    settingsStore = container.settingsStore,
                    onSave = container::setIntegrityWarning,
                )
                ActionDialogHost(actionsViewModel)
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
    trail: BackStack,
    account: ConnectedAccount,
    state: DashboardState,
    check: TokenCheckState,
    settings: List<AccountSetting>,
    permissions: List<TokenPermission>,
    onCheck: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    when (target) {
        is Destination.Service ->
            // Keyed so each page starts scrolled to the top.
            key(target) { ServiceScreen(target, trail, account, state, onRefresh, onOpenMenu, onNavigate) }
        is Destination.AccountSettings ->
            AccountSettingsScreen(
                trail = trail,
                account = account,
                settings = settings,
                permissions = permissions,
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

/** Offers each account's provider actions on its resources, and starts them in the shared dialog. */
private class DashboardActionHost(
    private val container: AppContainer,
    private val accounts: List<ConnectedAccount>,
    private val viewModel: ActionsViewModel,
) : ActionHost {
    override fun actionsFor(
        resource: Resource,
        onDeployments: Boolean,
    ): List<ActionInfo> =
        container.actionService.actionsFor(
            resource.providerId,
            resource.kind,
            onDeployments,
            (resource as? ServiceResource)?.service,
        )

    override fun start(
        resource: Resource,
        action: ActionInfo,
        target: ActionTarget,
        targetName: String,
    ) {
        accounts.firstOrNull { it.id == resource.accountId }?.let { viewModel.start(it, action, target, targetName) }
    }
}
