package dev.dache.app.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Profiles
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The profiles, which one is shown, and switching between them; for the sidebar's switcher and account moves. */
class ProfilesViewModel(
    private val accounts: AccountRepository,
) : ViewModel() {
    val profiles: StateFlow<Profiles> =
        accounts.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, Profiles())

    /** What the switcher calls; built once, since its callbacks never change. */
    val actions =
        ProfileActions(
            onSwitch = ::switch,
            onCycle = ::cycle,
            onCreate = ::create,
            onRename = ::rename,
            onDelete = ::delete,
        )

    fun switch(id: String) {
        viewModelScope.launch { accounts.switchProfile(id) }
    }

    /** Moves to the next profile, wrapping around; what a double tap on the switcher does. */
    fun cycle() {
        viewModelScope.launch { accounts.cycleProfile() }
    }

    fun create(name: String) {
        viewModelScope.launch { accounts.createProfile(name) }
    }

    fun rename(
        id: String,
        name: String,
    ) {
        viewModelScope.launch { accounts.renameProfile(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { accounts.deleteProfile(id) }
    }

    fun moveAccount(
        account: ConnectedAccount,
        profileId: String,
    ) {
        viewModelScope.launch { accounts.moveAccount(account, profileId) }
    }
}
