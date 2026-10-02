package com.me4hik.praktika.actiontoursmoke

/** Production package under black-box control (never instrumented). */
object ProductionTourTargets {
    const val PRODUCTION_PACKAGE = "com.me4hik.praktika"
    const val PRODUCTION_MAIN_ACTIVITY = "com.me4hik.praktika.MainActivity"

    const val HOME_SETTINGS = "Настройки"
    const val HOME_ARCHIVE = "Архив ответов"
    const val ARCHIVE_TITLE = "Архив"
    const val ARCHIVE_OPEN_DAYS = "Архив по дням"
    const val ARCHIVE_DAYS_INFO_TIP = "Здесь будут собраны ваши ответы по дням. Нажмите „Далее“."
    const val ARCHIVE_EMPTY = "Архив пока пуст"
    const val ABOUT_SECTION = "О приложении"
    const val VERSION_PREFIX = "Версия"
    const val START_TOUR = "Интерактивное обучение (тест)"
    const val TOUR_NEXT = "Далее"
    const val TOUR_SKIP = "Пропустить"
    const val TOUR_EXIT = "Выйти"
    const val GATE_CONTINUE = "Посмотреть остальное"
    const val GATE_FINISH = "Закончить"
    const val TIME_PICKER_CONFIRM = "Готово"
    const val NOTIFICATIONS_ENTRY = "Уведомления"
    const val SOUND_LIBRARY_ENTRY = "Звук уведомления"
    const val SOUND_TASK_TIP = "Выберите любой звук уведомления."
    const val SOUND_SELECTED_CD = "Выбрано"
    const val SCHEDULE_CHANGE_CD_PREFIX = "Изменить время"
    const val SLOT_CHANGE = "Изменить"

    const val WORDING_MASCULINE = "В мужском роде"
    const val WORDING_FEMININE = "В женском роде"
    const val WORDING_NEUTRAL = "Без указания рода"

    const val DEFER_5 = "5 минут"
    const val DEFER_10 = "10 минут"
    const val DEFER_15 = "15 минут"
    const val DEFER_30 = "30 минут"

    val WORDING_OPTIONS = listOf(WORDING_MASCULINE, WORDING_FEMININE, WORDING_NEUTRAL)
    val DEFER_OPTIONS = listOf(DEFER_5, DEFER_10, DEFER_15, DEFER_30)

    fun progressText(ordinal: Int, count: Int = 5): String = "$ordinal из $count"
}

enum class SmokeStage {
    START_ENTRY,
    INTRO,
    TASK_1_ARCHIVE,
    TASK_2_SCHEDULE,
    TASK_3_WORDING,
    TASK_4_SOUND,
    TASK_5_DEFER,
    GATE,
    TIMEOUT_REGRESSION,
}

enum class StageResult {
    PASS,
    FAIL,
    SKIPPED,
    PENDING,
}

object SmokeReportFormatter {
    fun formatReport(
        device: String,
        serial: String,
        packageId: String,
        versionCode: String,
        stages: Map<SmokeStage, StageResult>,
        processCrash: Boolean,
        failedStage: SmokeStage? = null,
        expected: String? = null,
        actual: String? = null,
        selector: String? = null,
        screenshot: String? = null,
    ): String {
        val overall = when {
            processCrash -> "FAIL"
            stages.filterKeys { it != SmokeStage.TIMEOUT_REGRESSION }
                .any { it.value == StageResult.FAIL } -> "FAIL"
            stages[SmokeStage.TIMEOUT_REGRESSION] == StageResult.FAIL -> "FAIL"
            else -> "PASS"
        }
        return buildString {
            appendLine("DEVICE = $device")
            appendLine("SERIAL = $serial")
            appendLine("PACKAGE = $packageId")
            appendLine("VERSION_CODE = $versionCode")
            appendLine()
            for (stage in SmokeStage.entries) {
                val result = stages[stage] ?: StageResult.PENDING
                appendLine("${stage.name} = $result")
            }
            appendLine()
            appendLine("PROCESS_CRASH = ${if (processCrash) "YES" else "NO"}")
            appendLine()
            appendLine("ACTION_TOUR_DEVICE_SMOKE = $overall")
            if (failedStage != null) {
                appendLine()
                appendLine("FAILED_STAGE = ${failedStage.name}")
                if (expected != null) appendLine("EXPECTED = $expected")
                if (actual != null) appendLine("ACTUAL = $actual")
                if (selector != null) appendLine("SELECTOR = $selector")
                if (screenshot != null) appendLine("SCREENSHOT = $screenshot")
            }
        }
    }
}
