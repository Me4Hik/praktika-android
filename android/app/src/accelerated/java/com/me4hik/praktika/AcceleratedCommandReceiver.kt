// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - ADB broadcast receiver
package com.me4hik.praktika

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.accelerated.AcceleratedCommandProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AcceleratedCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            return
        }
        val pendingResult = goAsync()
        val command = intent.getStringExtra(EXTRA_COMMAND)
        if (command.isNullOrBlank()) {
            Log.e(TAG, "Missing command extra")
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AcceleratedCommandProcessor(context.applicationContext).process(command, intent)
            } catch (exception: Exception) {
                Log.e(TAG, "${exception.javaClass.simpleName}: ${exception.message}", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.me4hik.praktika.accelerated.ACCELERATED_COMMAND"
        const val EXTRA_COMMAND = "command"
        private const val TAG = "AcceleratedCommand"
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
