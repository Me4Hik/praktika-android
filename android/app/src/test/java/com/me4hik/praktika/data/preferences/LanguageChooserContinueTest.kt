package com.me4hik.praktika.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChooserContinueTest {

    @Test
    fun changed_awaitsActivityRecreate() {
        assertEquals(
            LanguageChooserContinue.AwaitActivityRecreate,
            languageChooserContinueAfterApply(LocaleApplyResult.CHANGED),
        )
    }

    @Test
    fun unchanged_reentersBootstrap() {
        assertEquals(
            LanguageChooserContinue.ReenterBootstrap,
            languageChooserContinueAfterApply(LocaleApplyResult.UNCHANGED),
        )
    }
}
