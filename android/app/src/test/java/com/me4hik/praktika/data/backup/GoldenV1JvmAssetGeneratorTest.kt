// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik START - one-shot JVM golden JSON asset generator
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import java.io.File
import org.junit.Test

class GoldenV1JvmAssetGeneratorTest {
    @Test
    fun generateFrozenJvmJsonAsset() {
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(BackupGoldenFixtures.goldenEnvelopeWithChecksum())
        val target = File(
            "src/androidTestAcceleratedDebug/assets/datavault/golden-v1-jvm.json",
        )
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
    }
}
// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik END
