// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

class DiagnosticUncaughtExceptionHandler(
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().recordSync(
                    category = DiagnosticCategory.ERROR,
                    name = "uncaught_exception",
                    metadata = mapOf(
                        "thread" to thread.name,
                        "exception_class" to throwable.javaClass.name,
                        "message" to (throwable.message?.take(200) ?: ""),
                    ),
                )
            }
        } catch (_: Exception) {
            // Never block crash propagation.
        } finally {
            delegate?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install() {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is DiagnosticUncaughtExceptionHandler) {
                return
            }
            Thread.setDefaultUncaughtExceptionHandler(
                DiagnosticUncaughtExceptionHandler(current),
            )
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
