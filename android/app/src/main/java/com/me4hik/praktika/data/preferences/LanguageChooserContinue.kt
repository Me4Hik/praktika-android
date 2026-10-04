package com.me4hik.praktika.data.preferences

/**
 * Next step after persisting a language choice and calling [AppLocaleController.apply].
 *
 * [AwaitActivityRecreate] — AppCompat will recreate; do not navigate.
 * [ReenterBootstrap] — locale already matched; tear down NavHost and re-run bootstrap under Loading.
 */
enum class LanguageChooserContinue {
    AwaitActivityRecreate,
    ReenterBootstrap,
}

fun languageChooserContinueAfterApply(result: LocaleApplyResult): LanguageChooserContinue =
    when (result) {
        LocaleApplyResult.CHANGED -> LanguageChooserContinue.AwaitActivityRecreate
        LocaleApplyResult.UNCHANGED -> LanguageChooserContinue.ReenterBootstrap
    }
