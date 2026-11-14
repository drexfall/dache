package dev.dache.app.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.dache.app.TestGraph
import dev.dache.app.accounts.AccountsViewModel
import dev.dache.app.accounts.AddAccountFlow
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.PermissionCheck
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission
import dev.dache.core.provider.DevOpsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AddAccountFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val acme = ProviderId("acme", "Acme")

    /** Accepts tokens starting with "good"; "limited" works but lacks the required permission. */
    private class AcmeProvider : DevOpsProvider {
        override val id = ProviderId("acme", "Acme")
        override val capabilities = setOf(Capability.SITES)
        private val sites = TokenPermission("Sites read", required = true)
        private val billing = TokenPermission("Billing read", required = false)
        override val tokenPermissions = listOf(sites, billing)

        override suspend fun validateCredentials(
            token: String,
            settings: Map<String, String>,
        ) = if (token.startsWith("good")) {
            Result.success(AccountInfo("dev@acme"))
        } else {
            Result.failure(ProviderError.Unauthorized())
        }

        override suspend fun checkToken(
            token: String,
            settings: Map<String, String>,
        ) = Result.success(
            when {
                token.startsWith("good") ->
                    TokenCheck("dev@acme", listOf(PermissionCheck(sites, true), PermissionCheck(billing, true)))
                token == "limited" ->
                    TokenCheck("dev@acme", listOf(PermissionCheck(sites, false), PermissionCheck(billing, true)))
                else -> TokenCheck(null, listOf(PermissionCheck(sites, null), PermissionCheck(billing, null)))
            },
        )

        override suspend fun fetchSnapshot(account: ConnectedAccount) = Result.success(ProviderSnapshot())
    }

    @Test
    fun checklistGatesConnectAndConnectingReportsTheAccount() {
        val graph = TestGraph(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { mapOf(acme to AcmeProvider()) }
        val viewModel = AccountsViewModel(graph.accounts, graph.dashboard)
        val connected = mutableListOf<ConnectedAccount>()
        rule.setContent {
            TestHost {
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.connected.collect { connected += it } }
                state.addAccount?.let {
                    AddAccountFlow(
                        state = it,
                        onProviderSelected = viewModel::selectProvider,
                        onTokenChanged = viewModel::updateToken,
                        onSettingChanged = viewModel::updateSetting,
                        onSubmit = viewModel::submit,
                        onDismiss = viewModel::cancelAdding,
                    )
                }
            }
        }
        viewModel.startAdding()
        rule.waitUntil(TIMEOUT) { viewModel.state.value.addAccount != null }

        // Pick the provider; the permissions it checks are listed before any token is typed.
        rule.onNodeWithText("Acme").performClick()
        rule.onNodeWithText("Sites read", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Connect").assertIsNotEnabled()

        // A token that lacks the required permission: the debounced check says so and Connect stays off.
        rule.onNodeWithText("API token").performTextInput("limited")
        rule.waitUntil(TIMEOUT) {
            viewModel.state.value.addAccount
                ?.check
                ?.result != null
        }
        rule
            .onNodeWithText("The token is missing a required permission", substring = true, useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("Connect").assertIsNotEnabled()

        // A token with everything: the check passes and connecting hands the account over.
        rule.onNodeWithText("API token").performTextClearance()
        rule.onNodeWithText("API token").performTextInput("good-token")
        rule.waitUntil(TIMEOUT) {
            viewModel.state.value.addAccount
                ?.check
                ?.result
                ?.usable == true
        }
        rule.onNodeWithText("Token works for dev@acme", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Connect").assertIsEnabled().performClick()
        rule.waitUntil(TIMEOUT) { connected.size == 1 }

        assertEquals(acme, connected.single().providerId)
        assertEquals("dev@acme", connected.single().displayName)
        rule.waitUntil(TIMEOUT) { viewModel.state.value.addAccount == null }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
