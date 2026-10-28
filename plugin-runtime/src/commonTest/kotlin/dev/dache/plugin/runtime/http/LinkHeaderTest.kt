package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LinkHeaderTest {
    private val base = Url("https://api.example.com/repos?per_page=2")

    @Test
    fun findsTheNextLinkAmongOthers() {
        val header =
            "<https://api.example.com/repos?per_page=2&page=1>; rel=\"prev\", " +
                "<https://api.example.com/repos?per_page=2&page=3>; rel=\"next\", " +
                "<https://api.example.com/repos?per_page=2&page=9>; rel=\"last\""

        assertEquals(Url("https://api.example.com/repos?per_page=2&page=3"), nextLink(header, base))
    }

    @Test
    fun acceptsUnquotedAndMultiValueRel() {
        assertEquals(Url("https://api.example.com/a"), nextLink("<https://api.example.com/a>; rel=next", base))
        assertEquals(
            Url("https://api.example.com/b"),
            nextLink("<https://api.example.com/b>; title=\"x\"; rel=\"next last\"", base),
        )
    }

    @Test
    fun resolvesRelativeLinks() {
        assertEquals(Url("https://api.example.com/repos?page=2"), nextLink("</repos?page=2>; rel=\"next\"", base))
    }

    @Test
    fun noNextLinkIsNull() {
        assertNull(nextLink(null, base))
        assertNull(nextLink("", base))
        assertNull(nextLink("<https://api.example.com/repos?page=1>; rel=\"prev\"", base))
    }

    @Test
    fun malformedNextLinkIsUnknown() {
        assertFailsWith<ProviderError.Unknown> { nextLink("<https://api.example.com:port/>; rel=\"next\"", base) }
    }
}
