// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupSnapshotMetadataFactoryTest {
    @Test
    fun metadataFactory_usesWallClockExactlyOnce() {
        val clock = CountingBackupClock(fixedEpochMillis = 1_800_000_000_123L)
        val factory = BackupSnapshotMetadataFactory(
            clock = clock,
            appMetadataProvider = FixedBackupAppMetadataProvider(
                BackupSourceAppMetadata(versionCode = 7, versionName = "1.0"),
            ),
        )

        val context = factory.capture()

        assertEquals(1, clock.callCount)
        assertEquals(1_800_000_000_123L, context.createdAtEpochMillis)
    }

    @Test
    fun metadataFactory_usesProviderValuesExactly() {
        val factory = BackupSnapshotMetadataFactory(
            clock = FixedBackupClock(1_700_000_000_000L),
            appMetadataProvider = FixedBackupAppMetadataProvider(
                BackupSourceAppMetadata(versionCode = 123, versionName = "1.2.3-test"),
            ),
        )

        val context = factory.capture()

        assertEquals(123, context.sourceAppVersionCode)
        assertEquals("1.2.3-test", context.sourceAppVersionName)
    }

    @Test
    fun metadataFactory_doesNotKnowPracticeTime() {
        val source = BackupSnapshotMetadataFactory::class.java.name
        assertFalse(source.contains("TimeProvider"))
        assertFalse(source.contains("AcceleratedTimeProvider"))
        assertFalse(source.contains("CycleRepository"))
    }

    private class CountingBackupClock(
        private val fixedEpochMillis: Long,
    ) : BackupClock {
        var callCount: Int = 0
            private set

        override fun nowEpochMillis(): Long {
            callCount++
            return fixedEpochMillis
        }
    }

    private class FixedBackupClock(
        private val fixedEpochMillis: Long,
    ) : BackupClock {
        override fun nowEpochMillis(): Long = fixedEpochMillis
    }

    private class FixedBackupAppMetadataProvider(
        private val metadata: BackupSourceAppMetadata,
    ) : BackupAppMetadataProvider {
        override fun current(): BackupSourceAppMetadata = metadata
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
