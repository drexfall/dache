package dev.dache.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.dache.app.profiles.ProfileActions
import dev.dache.app.profiles.ProfileSwitcher
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ProfileSwitcherTest {
    @get:Rule
    val rule = createComposeRule()

    private val work = Profile("work", "Work")
    private val home = Profile("home", "Home")

    /** Shows a switcher whose actions change [profiles] the way the repository would. */
    private fun show(initial: Profiles): () -> Profiles {
        var profiles by mutableStateOf(initial)
        val actions =
            ProfileActions(
                onSwitch = { profiles = profiles.copy(activeId = it) },
                onCycle = { profiles = profiles.copy(activeId = profiles.next.id) },
                onCreate = { profiles = Profiles(profiles.all + Profile("new", it), "new") },
                onRename = { id, name ->
                    profiles =
                        profiles.copy(all = profiles.all.map { p -> if (p.id == id) p.copy(name = name) else p })
                },
                onDelete = { id -> profiles = Profiles(profiles.all.filterNot { it.id == id }).normalized() },
            )
        rule.setContent { TestHost { ProfileSwitcher(profiles, accountCount = 2, actions = actions) } }
        return { profiles }
    }

    @Test
    fun doubleTapCyclesThroughTheProfiles() {
        val profiles = show(Profiles(listOf(Profile.DEFAULT, work, home)))

        rule.onNodeWithContentDescription("Profile: Default").performTouchInput { doubleClick() }
        rule.waitForIdle()
        assertEquals(work.id, profiles().activeId)

        rule.onNodeWithContentDescription("Profile: Work").performTouchInput { doubleClick() }
        rule.onNodeWithContentDescription("Profile: Home").performTouchInput { doubleClick() }
        rule.waitForIdle()
        assertEquals(Profile.DEFAULT_ID, profiles().activeId)
    }

    @Test
    fun tapListsTheProfilesToSwitchTo() {
        val profiles = show(Profiles(listOf(Profile.DEFAULT, work)))

        rule.onNodeWithContentDescription("Profile: Default").performClick()
        rule.onNodeWithText("Work").performClick()

        assertEquals(work.id, profiles().activeId)
    }

    @Test
    fun aNewProfileIsNamedInADialog() {
        val profiles = show(Profiles())

        rule.onNodeWithContentDescription("Profile: Default").performClick()
        rule.onNodeWithText("Delete profile").assertIsNotEnabled()
        rule.onNodeWithText("New profile").performClick()
        rule.onNodeWithText("Profile name").performTextInput("Clients")
        rule.onNodeWithText("Create").performClick()

        assertEquals("Clients", profiles().active.name)
    }
}
