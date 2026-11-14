package dev.dache.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.dache.app.TestGraph
import dev.dache.app.actions.ActionDialog
import dev.dache.app.actions.ActionStep
import dev.dache.app.actions.ActionsViewModel
import dev.dache.core.actions.ActionService
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionInput
import dev.dache.core.model.ActionInputType
import dev.dache.core.model.ActionPreview
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.FieldChange
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef
import dev.dache.core.provider.DevOpsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ActionDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val acme = ProviderId("acme", "Acme")
    private val token = "good-secret-token"
    private val input = ActionInput("content", "Content", ActionInputType.TEXT, required = true, options = emptyList())
    private val edit =
        ActionInfo(
            id = "edit-record",
            label = "Edit record",
            kind = ResourceKind.DNS_RECORD,
            onDeployments = false,
            destructive = true,
            description = "Changes the record on Acme.",
            inputs = listOf(input),
            permission = "DNS:Edit",
        )

    /** One destructive action with one input; counts how often it runs and what it was sent. */
    private inner class AcmeProvider : DevOpsProvider {
        override val id = acme
        override val capabilities = setOf(Capability.DNS)
        override val actions = listOf(edit)
        val runs = mutableListOf<ActionRequest>()

        override suspend fun validateCredentials(
            token: String,
            settings: Map<String, String>,
        ) = Result.success(AccountInfo("dev@acme"))

        override suspend fun fetchSnapshot(account: ConnectedAccount) = Result.success(ProviderSnapshot())

        override suspend fun actionDefaults(
            account: ConnectedAccount,
            request: ActionRequest,
        ) = Result.success(mapOf("content" to "1.2.3.4"))

        override suspend fun previewAction(
            account: ConnectedAccount,
            request: ActionRequest,
        ) = Result.success(
            ActionPreview(
                action = edit,
                method = "PATCH",
                url = "https://api.acme.dev/zones/z1/records/r1  (Authorization: Bearer <redacted>)",
                body = """{"content":"${request.inputs["content"]}"}""",
                changes = listOf(FieldChange("Content", "1.2.3.4", request.inputs["content"].orEmpty())),
            ),
        )

        override suspend fun runAction(
            account: ConnectedAccount,
            request: ActionRequest,
        ): Result<Unit> {
            runs += request
            return Result.success(Unit)
        }
    }

    @Test
    fun reviewShowsTheChangeAndTheRedactedRequestAndSendsOnlyOnConfirm() {
        val provider = AcmeProvider()
        val graph = TestGraph(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { mapOf(acme to provider) }
        val account = runBlocking { graph.accounts.connect(acme, token).getOrThrow() }
        val service = ActionService(graph.accounts, graph.dashboard, InMemoryWriteLog())
        // No device lock on the desktop: destructive actions are confirmed by typing the target's name.
        val viewModel = ActionsViewModel(service)
        rule.setContent {
            TestHost {
                val step by viewModel.state.collectAsState()
                step?.let { ActionDialog(it, viewModel) }
            }
        }
        val target = ActionTarget(ResourceRef(acme, account.id, "r1", ResourceKind.DNS_RECORD))
        viewModel.start(account, edit, target, "www A")
        rule.waitUntil(TIMEOUT) { (viewModel.state.value as? ActionStep.Form)?.loading == false }

        // The form starts from the current value; change it and go to review.
        rule.onNodeWithText("Content").performTextClearance()
        rule.onNodeWithText("Content").performTextInput("5.6.7.8")
        rule.onNodeWithText("Continue").performClick()
        rule.waitUntil(TIMEOUT) { viewModel.state.value is ActionStep.Review }

        rule.onNodeWithText("Review: Edit record: www A").assertExists()
        rule.onNodeWithText("1.2.3.4").assertExists()
        rule.onNodeWithText("5.6.7.8").assertExists()
        rule.onNodeWithText("Bearer <redacted>", substring = true).assertExists()
        rule.onAllNodesWithText(token, substring = true).assertCountEquals(0)
        // Destructive: the run button is off until the target's name is typed, and nothing has been sent.
        rule.onNodeWithText("Edit record").assertIsNotEnabled()
        assertEquals(0, provider.runs.size)

        rule.onNodeWithText("Type www A to confirm").performTextInput("www A")
        rule.onNodeWithText("Edit record").assertIsEnabled().performClick()
        rule.waitUntil(TIMEOUT) { viewModel.state.value is ActionStep.Done }

        rule.onNodeWithText("Done. The dashboard is refreshing to show the change.").assertExists()
        assertEquals(1, provider.runs.size)
        assertEquals("5.6.7.8", provider.runs.single().inputs["content"])
        // Confirming again from the done step sends nothing.
        viewModel.confirm()
        rule.waitForIdle()
        assertEquals(1, provider.runs.size)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
