package com.me4hik.praktika.actiontoursmoke

/**
 * Pure contracts for production process lifecycle in black-box smoke.
 * Empty pid before launch is valid cold-start; not a crash.
 */
object SmokeProcessLifecycle {
    fun isPackageInstalled(pmPathOutput: String): Boolean =
        pmPathOutput.contains("package:")

    fun isPidPresent(pidofOutput: String): Boolean =
        pidofOutput.trim().isNotEmpty()

    /**
     * Whether empty pid is an allowed state for the given moment.
     * Pre-launch: always allowed. Post-launch: never (launch/entry failure).
     */
    fun emptyPidAllowedBeforeLaunch(): Boolean = true

    fun shouldFailEmptyPidAfterLaunch(): Boolean = true
}
