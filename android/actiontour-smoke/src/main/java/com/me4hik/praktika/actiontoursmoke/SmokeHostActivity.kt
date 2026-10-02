package com.me4hik.praktika.actiontoursmoke

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Minimal host activity for the smoke APK.
 * Instrumentation targets this package; UiAutomator drives production separately.
 */
class SmokeHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "ActionTour smoke host — do not use for product UX"
            setPadding(48, 48, 48, 48)
        }
        setContentView(label)
    }
}
