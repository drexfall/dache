package dev.dache.core.linking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NormalizeTest {
    @Test
    fun hostStripsSchemePortPathAndTrailingDot() {
        assertEquals("app.acme.dev", normalizeHost("https://App.Acme.dev:8443/login?next=/#top"))
        assertEquals("acme.dev", normalizeHost("acme.dev."))
        assertEquals("acme.dev", normalizeHost("https://user@acme.dev"))
        assertNull(normalizeHost("   "))
    }

    @Test
    fun withinDomainMatchesSubdomainsOnly() {
        assertTrue(isWithinDomain("docs.acme.dev", "acme.dev"))
        assertTrue(isWithinDomain("acme.dev", "acme.dev"))
        assertFalse(isWithinDomain("notacme.dev", "acme.dev"))
    }

    @Test
    fun repoUrlFormsAreEquivalent() {
        val expected = "github.com/acme/web"
        assertEquals(expected, normalizeRepoUrl("https://github.com/Acme/Web"))
        assertEquals(expected, normalizeRepoUrl("https://github.com/acme/web.git"))
        assertEquals(expected, normalizeRepoUrl("https://github.com/acme/web/"))
        assertEquals(expected, normalizeRepoUrl("git@github.com:acme/web.git"))
        assertEquals(expected, normalizeRepoUrl("https://token@github.com/acme/web"))
    }
}
