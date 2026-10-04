package com.me4hik.praktika.data.preferences

enum class AppLanguage(val tag: String) {
    RU("ru"),
    UK("uk"),
    EN("en"),
    PL("pl"),
    ;

    companion object {
        val DEFAULT: AppLanguage = RU

        /** UI display order for Language chooser and Settings. Not storage/bootstrap order. */
        val DISPLAY_ORDER: List<AppLanguage> = listOf(UK, EN, PL, RU)

        fun fromStorage(raw: String?): AppLanguage {
            if (raw.isNullOrBlank()) {
                return DEFAULT
            }
            return entries.firstOrNull { it.tag.equals(raw, ignoreCase = true) } ?: DEFAULT
        }
    }
}
