// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - accelerated-private A/B URI stash
package com.me4hik.praktika.ui.acceptance

import android.content.Context

interface DeviceAcceptanceUriStashStore {
    fun stashA(uriString: String)
    fun stashB(uriString: String)
    fun loadA(): String?
    fun loadB(): String?
    fun clear()
}

class InMemoryDeviceAcceptanceUriStash : DeviceAcceptanceUriStashStore {
    private var a: String? = null
    private var b: String? = null

    override fun stashA(uriString: String) {
        a = uriString
    }

    override fun stashB(uriString: String) {
        b = uriString
    }

    override fun loadA(): String? = a

    override fun loadB(): String? = b

    override fun clear() {
        a = null
        b = null
    }
}

/**
 * Private stash for prior folder URIs. Never logged, never reported, not production BackupWriteState.
 */
class DeviceAcceptanceUriStash(
    context: Context,
) : DeviceAcceptanceUriStashStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun stashA(uriString: String) {
        prefs.edit().putString(KEY_A, uriString).apply()
    }

    override fun stashB(uriString: String) {
        prefs.edit().putString(KEY_B, uriString).apply()
    }

    override fun loadA(): String? = prefs.getString(KEY_A, null)?.takeIf { it.isNotBlank() }

    override fun loadB(): String? = prefs.getString(KEY_B, null)?.takeIf { it.isNotBlank() }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "device_acceptance_uri_stash"
        private const val KEY_A = "stash_a"
        private const val KEY_B = "stash_b"
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
