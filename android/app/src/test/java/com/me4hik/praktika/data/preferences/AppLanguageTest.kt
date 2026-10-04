package com.me4hik.praktika.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun defaultIsRussian() {
        assertEquals(AppLanguage.RU, AppLanguage.DEFAULT)
        assertEquals("ru", AppLanguage.DEFAULT.tag)
    }

    @Test
    fun displayOrder_isUkEnPlRu() {
        assertEquals(
            listOf(AppLanguage.UK, AppLanguage.EN, AppLanguage.PL, AppLanguage.RU),
            AppLanguage.DISPLAY_ORDER,
        )
    }

    @Test
    fun enumDeclarationOrder_unchanged() {
        assertEquals(
            listOf(AppLanguage.RU, AppLanguage.UK, AppLanguage.EN, AppLanguage.PL),
            AppLanguage.entries.toList(),
        )
    }

    @Test
    fun fromStorage_knownTags() {
        assertEquals(AppLanguage.RU, AppLanguage.fromStorage("ru"))
        assertEquals(AppLanguage.UK, AppLanguage.fromStorage("uk"))
        assertEquals(AppLanguage.EN, AppLanguage.fromStorage("en"))
        assertEquals(AppLanguage.PL, AppLanguage.fromStorage("pl"))
        assertEquals(AppLanguage.EN, AppLanguage.fromStorage("EN"))
    }

    @Test
    fun fromStorage_unknownFallsBackToDefault() {
        assertEquals(AppLanguage.RU, AppLanguage.fromStorage(null))
        assertEquals(AppLanguage.RU, AppLanguage.fromStorage(""))
        assertEquals(AppLanguage.RU, AppLanguage.fromStorage("de"))
        assertEquals(AppLanguage.RU, AppLanguage.fromStorage("russian"))
    }

    @Test
    fun tags_unchanged() {
        assertEquals("ru", AppLanguage.RU.tag)
        assertEquals("uk", AppLanguage.UK.tag)
        assertEquals("en", AppLanguage.EN.tag)
        assertEquals("pl", AppLanguage.PL.tag)
    }
}
