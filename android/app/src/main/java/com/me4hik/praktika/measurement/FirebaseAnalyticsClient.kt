package com.me4hik.praktika.measurement

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin seam over Firebase Analytics so unit tests never touch the real SDK.
 */
interface FirebaseAnalyticsClient {
    fun logEvent(name: String, params: Map<String, String>)
}

class DefaultFirebaseAnalyticsClient(
    context: Context,
) : FirebaseAnalyticsClient {
    private val appContext = context.applicationContext
    private val analytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(appContext)
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            bundle.putString(key, value.take(GA4_MAX_PARAM_VALUE_LENGTH))
        }
        analytics.logEvent(name, bundle)
    }

    companion object {
        const val GA4_MAX_PARAM_VALUE_LENGTH = 100
    }
}
