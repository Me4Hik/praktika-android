package com.me4hik.praktika.actiontoursmoke

/**
 * Diagnostics must never replace the primary test failure.
 */
object SmokeFailSafe {
    fun runBestEffort(primary: Throwable, vararg steps: () -> Unit) {
        for (step in steps) {
            try {
                step()
            } catch (t: Throwable) {
                primary.addSuppressed(t)
            }
        }
    }
}
