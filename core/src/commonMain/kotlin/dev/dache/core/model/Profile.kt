package dev.dache.core.model

import kotlinx.serialization.Serializable

/**
 * A named group of connected accounts, such as "Work" and "Personal". Only the active profile's accounts are shown,
 * refreshed and checked in the background. A blank [name] is the unnamed default profile, which the UI labels itself.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
) {
    companion object {
        /** The profile accounts connected before profiles existed belong to. */
        const val DEFAULT_ID = "default"

        val DEFAULT = Profile(DEFAULT_ID)
    }
}

/** Every profile, in the order the user made them, and which one is shown. Never empty. */
data class Profiles(
    val all: List<Profile> = listOf(Profile.DEFAULT),
    val activeId: String = Profile.DEFAULT_ID,
) {
    /** The shown profile; the first one if [activeId] names none (it was deleted elsewhere). */
    val active: Profile get() = all.firstOrNull { it.id == activeId } ?: all.first()

    /** The profile after the active one, wrapping around; the active one itself when it is alone. */
    val next: Profile get() = all[(all.indexOf(active) + 1) % all.size]

    /** Puts back the default profile when [all] is empty, and points [activeId] at a profile that exists. */
    fun normalized(): Profiles {
        val profiles = all.ifEmpty { listOf(Profile.DEFAULT) }
        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
        return Profiles(profiles, active.id)
    }
}
