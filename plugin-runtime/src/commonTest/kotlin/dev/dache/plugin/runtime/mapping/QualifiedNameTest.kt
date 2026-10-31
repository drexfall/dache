package dev.dache.plugin.runtime.mapping

import kotlin.test.Test
import kotlin.test.assertEquals

class QualifiedNameTest {
    @Test
    fun relativeNamesGetTheirZone() {
        assertEquals("www.example.com", qualified("www", "example.com"))
        assertEquals("example.com", qualified("@", "example.com"))
        assertEquals("example.com", qualified("", "example.com"))
    }

    @Test
    fun fullNamesStayAsTheyAre() {
        assertEquals("www.example.com", qualified("www.example.com", "example.com"))
        assertEquals("Example.com", qualified("Example.com.", "example.com"))
        assertEquals("example.com", qualified("example.com", "example.com"))
    }

    @Test
    fun aNameThatOnlyEndsLikeTheZoneIsRelative() {
        assertEquals("notexample.com.example.com", qualified("notexample.com", "example.com"))
    }
}
