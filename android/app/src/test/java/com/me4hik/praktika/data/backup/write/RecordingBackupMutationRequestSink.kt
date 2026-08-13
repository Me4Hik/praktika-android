package com.me4hik.praktika.data.backup.write

import java.util.concurrent.CopyOnWriteArrayList

class RecordingBackupMutationRequestSink : BackupMutationRequestSink {
    val reasons: CopyOnWriteArrayList<BackupRequestReason> = CopyOnWriteArrayList()

    override fun requestBackup(reason: BackupRequestReason) {
        reasons.add(reason)
    }

    fun clear() {
        reasons.clear()
    }

    fun countOf(reason: BackupRequestReason): Int = reasons.count { it == reason }
}

class ThrowingBackupMutationRequestSink(
    private val error: Exception = RuntimeException("sink_boom"),
) : BackupMutationRequestSink {
    override fun requestBackup(reason: BackupRequestReason) {
        throw error
    }
}
