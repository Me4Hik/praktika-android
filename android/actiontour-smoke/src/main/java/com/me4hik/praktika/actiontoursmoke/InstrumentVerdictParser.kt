package com.me4hik.praktika.actiontoursmoke

/**
 * Parses `am instrument -w -r` raw output.
 *
 * Final [INSTRUMENTATION_CODE] `-1` is [android.app.Activity.RESULT_OK] — not a test failure.
 * Per-test outcomes use [INSTRUMENTATION_STATUS_CODE]: `0` OK, `-1` error, `-2` failure, `1` started.
 */
object InstrumentVerdictParser {
    enum class Verdict { PASS, FAIL }

    data class Result(
        val verdict: Verdict,
        val statusCodes: List<Int>,
        val hasJunitFailuresBanner: Boolean,
        val hasJunitOk: Boolean,
        val instrumentationCode: Int?,
        val reason: String,
    )

    private val statusCodeRegex = Regex("""INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)""")
    private val finalCodeRegex = Regex("""(?m)^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$""")

    fun parse(raw: String?): Result {
        if (raw.isNullOrBlank()) {
            return Result(
                verdict = Verdict.FAIL,
                statusCodes = emptyList(),
                hasJunitFailuresBanner = false,
                hasJunitOk = false,
                instrumentationCode = null,
                reason = "empty instrumentation output",
            )
        }
        val statusCodes = statusCodeRegex.findAll(raw).map { it.groupValues[1].toInt() }.toList()
        val finalCode = finalCodeRegex.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        val failuresBanner = raw.contains("FAILURES!!!")
        val junitOk = Regex("""OK\s*\(\d+\s+tests?\)""").containsMatchIn(raw)
        val hasFailureOrErrorStatus = statusCodes.any { it == -1 || it == -2 }

        val verdict = when {
            failuresBanner || hasFailureOrErrorStatus -> Verdict.FAIL
            junitOk -> Verdict.PASS
            finalCode == null && statusCodes.isEmpty() -> Verdict.FAIL
            else -> Verdict.FAIL
        }
        val reason = when (verdict) {
            Verdict.PASS -> "junit OK; no failure/error STATUS_CODE (final CODE=$finalCode ignored as RESULT_OK)"
            Verdict.FAIL -> when {
                raw.isBlank() -> "empty output"
                failuresBanner -> "FAILURES!!! banner"
                hasFailureOrErrorStatus -> "STATUS_CODE failure/error in $statusCodes"
                !junitOk -> "missing OK (N tests)"
                else -> "unclassified failure"
            }
        }
        return Result(
            verdict = verdict,
            statusCodes = statusCodes,
            hasJunitFailuresBanner = failuresBanner,
            hasJunitOk = junitOk,
            instrumentationCode = finalCode,
            reason = reason,
        )
    }

    /**
     * Stage FAIL forces suite FAIL even if instrument stream looks OK.
     * PENDING does not force FAIL.
     */
    fun combineWithStages(
        instrument: Result,
        stageResults: Map<String, String>,
        relevantStages: Collection<String>,
    ): Verdict {
        val stageFail = relevantStages.any { key ->
            stageResults[key]?.equals("FAIL", ignoreCase = true) == true
        }
        return if (stageFail || instrument.verdict == Verdict.FAIL) Verdict.FAIL else Verdict.PASS
    }
}
