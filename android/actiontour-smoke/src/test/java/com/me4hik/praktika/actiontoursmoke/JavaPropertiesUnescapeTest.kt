package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Test

class JavaPropertiesUnescapeTest {
    @Test
    fun unescapesCurrentLocalPropertiesSdkDir() {
        val raw = """C\:\\Users\\user\\AppData\\Local\\Android\\Sdk"""
        assertEquals(
            """C:\Users\user\AppData\Local\Android\Sdk""",
            JavaPropertiesUnescape.unescape(raw),
        )
    }

    @Test
    fun unescapesEqualsAndColon() {
        assertEquals("a=b:c", JavaPropertiesUnescape.unescape("""a\=b\:c"""))
    }

    @Test
    fun leavesTextWithoutBackslashEscapesUnchanged() {
        assertEquals("hello-world", JavaPropertiesUnescape.unescape("hello-world"))
    }
}
