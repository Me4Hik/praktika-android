package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeArtifactPathsTest {
    @Test
    fun relativeRunDir_isolatesRunId() {
        assertEquals(
            "action-tour-device-smoke/20261002_030204",
            SmokeArtifactPaths.relativeRunDir("20261002_030204"),
        )
    }

    @Test
    fun adbPullPath_usesSmokeExternalDocuments() {
        val path = SmokeArtifactPaths.adbPullPath("run1")
        assertTrue(path.startsWith("/sdcard/Android/data/com.me4hik.praktika.actiontoursmoke/"))
        assertTrue(path.contains("/files/Documents/action-tour-device-smoke/run1"))
        assertFalse(path.contains("/data/local/tmp"))
        assertFalse(path.contains("com.me4hik.praktika/"))
    }

    @Test
    fun forbiddenShellTmp_detected() {
        assertTrue(SmokeArtifactPaths.isForbiddenShellTmpPath("/data/local/tmp/action-tour-smoke-x"))
        assertTrue(SmokeArtifactPaths.isForbiddenShellTmpPath("""C:\data\local\tmp\x"""))
        assertFalse(
            SmokeArtifactPaths.isForbiddenShellTmpPath(
                "/sdcard/Android/data/com.me4hik.praktika.actiontoursmoke/files/Documents/x",
            ),
        )
    }

    @Test
    fun requireValidRunId_acceptsTimestamp() {
        assertEquals("20261002_030204", SmokeArtifactPaths.requireValidRunId("20261002_030204"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidRunId_rejectsBlank() {
        SmokeArtifactPaths.requireValidRunId("  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidRunId_rejectsPathTraversal() {
        SmokeArtifactPaths.requireValidRunId("../evil")
    }
}
