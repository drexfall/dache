package dev.dache.plugin.runtime.security

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostGuardTest {
    private val guard = HostGuard(listOf("api.netlify.com"))

    @Test
    fun allowsDeclaredHostOverHttps() {
        assertNull(guard.violation("https://api.netlify.com/api/v1/sites?page=2"))
        assertNull(guard.violation("https://API.Netlify.com/api/v1/sites"))
        assertNull(guard.violation("https://api.netlify.com:443/api/v1/sites"))
    }

    @Test
    fun rejectsPlainHttp() {
        assertEquals("only https is allowed, got http", guard.violation("http://api.netlify.com/api/v1/sites"))
    }

    @Test
    fun rejectsOtherHostsSubdomainsAndLookalikes() {
        listOf(
            "https://netlify.com/",
            "https://evil.api.netlify.com/",
            "https://api.netlify.com.evil.com/",
            "https://api.netlify.com./",
            "https://api.netlify.com@evil.com/",
        ).forEach { url -> assertTrue(guard.violation(url) != null, url) }
    }

    @Test
    fun rejectsCredentialsAndOtherPorts() {
        assertEquals("credentials in URLs are not allowed", guard.violation("https://user:pw@api.netlify.com/"))
        assertEquals("only the default https port is allowed", guard.violation("https://api.netlify.com:8443/"))
    }

    @Test
    fun rejectsNonUrls() {
        assertEquals("not a valid URL", guard.violation("api.netlify.com/api/v1"))
    }

    @Test
    fun checkThrowsWithoutLeakingTheQuery() {
        val error = assertFailsWith<HostNotAllowedException> { guard.check(Url("https://evil.com/x?token=secret")) }
        assertFalse("secret" in error.message.orEmpty())
    }

    @Test
    fun hostPattern() {
        assertTrue(HostGuard.isValidHost("api.netlify.com"))
        assertFalse(HostGuard.isValidHost("*.netlify.com"))
        assertFalse(HostGuard.isValidHost("https://api.netlify.com"))
        assertFalse(HostGuard.isValidHost("API.netlify.com"))
        assertFalse(HostGuard.isValidHost("localhost"))
        assertFalse(HostGuard.isValidHost("api.netlify.com:443"))
    }
}
