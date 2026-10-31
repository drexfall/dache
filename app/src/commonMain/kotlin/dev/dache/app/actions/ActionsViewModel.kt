package dev.dache.app.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.actions.ActionService
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionPreview
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ConnectedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an action is: filling in its inputs, under review, running, or finished. */
sealed interface ActionStep {
    val account: ConnectedAccount
    val action: ActionInfo
    val target: ActionTarget
    val targetName: String

    data class Form(
        override val account: ConnectedAccount,
        override val action: ActionInfo,
        override val target: ActionTarget,
        override val targetName: String,
        val values: Map<String, String> = emptyMap(),
        val loading: Boolean = true,
        val error: Throwable? = null,
    ) : ActionStep

    data class Review(
        override val account: ConnectedAccount,
        override val action: ActionInfo,
        override val target: ActionTarget,
        override val targetName: String,
        val values: Map<String, String>,
        val preview: ActionPreview,
        /** What the user typed to confirm a destructive action where there's no device lock to ask for. */
        val typed: String = "",
        val running: Boolean = false,
        val error: Throwable? = null,
    ) : ActionStep

    data class Done(
        override val account: ConnectedAccount,
        override val action: ActionInfo,
        override val target: ActionTarget,
        override val targetName: String,
    ) : ActionStep
}

/**
 * Runs one action at a time through form, review and run. Nothing is sent before the review screen's confirm.
 * A destructive action also needs [confirmWithLock], the device lock, at that point; without one (desktop, or a
 * device with no screen lock) the user types the target's name instead.
 */
class ActionsViewModel(
    private val service: ActionService,
    private val confirmWithLock: (suspend () -> Boolean)? = null,
) : ViewModel() {
    /** Destructive actions are confirmed by typing the target's name, as there's no device lock to ask for. */
    val confirmsByTyping: Boolean get() = confirmWithLock == null

    private val step = MutableStateFlow<ActionStep?>(null)
    val state: StateFlow<ActionStep?> = step.asStateFlow()

    fun start(
        account: ConnectedAccount,
        action: ActionInfo,
        target: ActionTarget,
        targetName: String,
    ) {
        val form = ActionStep.Form(account, action, target, targetName)
        step.value = form
        viewModelScope.launch {
            val defaults = service.defaults(account, ActionRequest(action.id, target))
            step.update { current ->
                if (current != form) {
                    current
                } else {
                    form.copy(
                        values = defaults.getOrDefault(emptyMap()),
                        loading = false,
                        error = defaults.exceptionOrNull(),
                    )
                }
            }
            // An action without inputs has nothing to fill in, so it goes straight to review.
            if (action.inputs.isEmpty() && defaults.isSuccess) review()
        }
    }

    fun setValue(
        key: String,
        value: String,
    ) {
        step.update { (it as? ActionStep.Form)?.copy(values = it.values + (key to value), error = null) ?: it }
    }

    fun review() {
        val form = step.value as? ActionStep.Form ?: return
        viewModelScope.launch {
            service
                .preview(form.account, ActionRequest(form.action.id, form.target, form.values))
                .onSuccess { preview ->
                    step.value =
                        ActionStep.Review(form.account, form.action, form.target, form.targetName, form.values, preview)
                }.onFailure { error -> step.value = form.copy(loading = false, error = error) }
        }
    }

    /** Back from review to the form, keeping what was entered. */
    fun edit() {
        val review = step.value as? ActionStep.Review ?: return
        step.value =
            ActionStep.Form(review.account, review.action, review.target, review.targetName, review.values, false)
    }

    fun setTyped(text: String) {
        step.update { (it as? ActionStep.Review)?.copy(typed = text) ?: it }
    }

    /** Whether the review's Run button may be pressed. */
    fun canRun(review: ActionStep.Review): Boolean =
        !review.running && (!review.action.destructive || !confirmsByTyping || review.typed.trim() == review.targetName)

    fun confirm() {
        val review = step.value as? ActionStep.Review ?: return
        if (!canRun(review)) return
        viewModelScope.launch {
            val lock = confirmWithLock
            if (review.action.destructive && lock != null && !lock()) return@launch
            step.value = review.copy(running = true, error = null)
            service
                .run(review.account, ActionRequest(review.action.id, review.target, review.values), review.targetName)
                .onSuccess {
                    step.value =
                        ActionStep.Done(review.account, review.action, review.target, review.targetName)
                }.onFailure { error -> step.value = review.copy(running = false, error = error) }
        }
    }

    fun dismiss() {
        step.value = null
    }
}
