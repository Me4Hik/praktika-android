package com.me4hik.praktika.measurement

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger

/**
 * Thin seam over Meta App Events so unit tests never touch the real SDK.
 */
interface MetaAppEventsClient {
    fun logEvent(name: String, params: Map<String, String>)
}

/**
 * Facebook SDK operations used by [DefaultMetaAppEventsClient].
 * Kept tiny so JVM tests can assert init order without loading the real SDK.
 */
interface MetaSdkBootstrap {
    fun fullyInitialize()
    fun activateApp(application: Application)
    fun newLogger(context: Context): MetaSdkEventLogger
}

interface MetaSdkEventLogger {
    fun logEvent(name: String)
    fun logEvent(name: String, parameters: Bundle)
}

class RealMetaSdkBootstrap : MetaSdkBootstrap {
    override fun fullyInitialize() {
        FacebookSdk.fullyInitialize()
    }

    override fun activateApp(application: Application) {
        AppEventsLogger.activateApp(application)
    }

    override fun newLogger(context: Context): MetaSdkEventLogger {
        val logger = AppEventsLogger.newLogger(context)
        return object : MetaSdkEventLogger {
            override fun logEvent(name: String) {
                logger.logEvent(name)
            }

            override fun logEvent(name: String, parameters: Bundle) {
                logger.logEvent(name, parameters)
            }
        }
    }
}

class DefaultMetaAppEventsClient(
    context: Context,
    private val sdk: MetaSdkBootstrap = RealMetaSdkBootstrap(),
) : MetaAppEventsClient {
    private val appContext = context.applicationContext
    private val initLock = Any()
    @Volatile
    private var activated = false

    private val logger: MetaSdkEventLogger by lazy {
        ensureActivated()
        sdk.newLogger(appContext)
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        if (params.isEmpty()) {
            logger.logEvent(name)
        } else {
            val bundle = Bundle()
            params.forEach { (key, value) ->
                bundle.putString(key, value)
            }
            logger.logEvent(name, bundle)
        }
    }

    private fun ensureActivated() {
        if (activated) {
            return
        }
        synchronized(initLock) {
            if (activated) {
                return
            }
            val application = appContext as? Application
                ?: error("Meta App Events requires an Application context")
            // Keep manifest AutoInitEnabled=false; never enable AutoInit via SDK API.
            sdk.fullyInitialize()
            sdk.activateApp(application)
            activated = true
        }
    }
}
