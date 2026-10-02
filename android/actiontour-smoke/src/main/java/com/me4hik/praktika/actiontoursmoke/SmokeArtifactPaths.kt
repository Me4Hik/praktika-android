package com.me4hik.praktika.actiontoursmoke

/**
 * Host ↔ instrumentation artifact layout (smoke package only — never production).
 */
object SmokeArtifactPaths {
    const val SMOKE_APP_ID = "com.me4hik.praktika.actiontoursmoke"
    const val ROOT_FOLDER = "action-tour-device-smoke"
    const val ARG_RUN_ID = "smoke_run_id"
    const val ARG_OUTPUT_DIR = "smoke_output_dir"

    /** Relative under app external Documents: action-tour-device-smoke/<runId> */
    fun relativeRunDir(runId: String): String = "$ROOT_FOLDER/$runId"

    /**
     * Canonical adb pull path for app-specific external files (Documents).
     * Matches [android.content.Context.getExternalFilesDir](DIRECTORY_DOCUMENTS).
     */
    fun adbPullPath(runId: String): String =
        "/sdcard/Android/data/$SMOKE_APP_ID/files/Documents/${relativeRunDir(runId)}"

    fun isForbiddenShellTmpPath(path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        return normalized.contains("/data/local/tmp")
    }

    fun requireValidRunId(runId: String?): String {
        val id = runId?.trim().orEmpty()
        require(id.isNotEmpty()) { "smoke_run_id is blank" }
        require(id.matches(Regex("^[A-Za-z0-9._-]+$"))) {
            "smoke_run_id has illegal characters: $id"
        }
        return id
    }
}
