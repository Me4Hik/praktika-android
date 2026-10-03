// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik START
package com.me4hik.praktika.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused coverage for window/layout fields that DiagnosticSnapshotBuilder merges
 * into the shared snapshot JSON (local file + Sentry extra).
 */
class DiagnosticSnapshotWindowFieldsTest {

    @Test
    fun fakeProviderMetrics_mergeIntoSnapshotJson() {
        val provider = DeviceWindowDiagnosticsProvider {
            DeviceWindowDiagnosticsSnapshot(
                windowWidthPx = 1120,
                windowHeightPx = 1800,
                screenWidthDp = 560,
                screenHeightDp = 900,
                smallestScreenWidthDp = 560,
                density = 2.0f,
                densityDpi = 320,
                fontScale = 1.15f,
                orientation = DeviceWindowDiagnosticsSnapshot.ORIENTATION_PORTRAIT,
                insetStatusBarsDp = 24,
                insetNavigationBarsDp = 40,
                insetTappableElementDp = 40,
                insetSystemGesturesDp = 16,
                questionWidthBucket = "Medium",
            )
        }

        val snapshot = JSONObject().apply {
            put("deviceManufacturer", "samsung")
            put("deviceModel", "SM-T220")
            put("androidRelease", "14")
            put("androidSdk", 34)
            put("versionCode", 18)
            put("versionName", "1.0")
            put("runtimeMode", "production")
            put("currentRoute", "question/7")
            putDeviceWindowDiagnostics(provider.collect())
        }

        assertEquals("samsung", snapshot.getString("deviceManufacturer"))
        assertEquals("SM-T220", snapshot.getString("deviceModel"))
        assertEquals("14", snapshot.getString("androidRelease"))
        assertEquals(34, snapshot.getInt("androidSdk"))
        assertEquals(18, snapshot.getInt("versionCode"))
        assertEquals("1.0", snapshot.getString("versionName"))
        assertEquals("production", snapshot.getString("runtimeMode"))
        assertEquals("question/7", snapshot.getString("currentRoute"))
        assertEquals(1120, snapshot.getInt("windowWidthPx"))
        assertEquals(1800, snapshot.getInt("windowHeightPx"))
        assertEquals(560, snapshot.getInt("screenWidthDp"))
        assertEquals(900, snapshot.getInt("screenHeightDp"))
        assertEquals(560, snapshot.getInt("smallestScreenWidthDp"))
        assertEquals(2.0, snapshot.getDouble("density"), 0.0)
        assertEquals(320, snapshot.getInt("densityDpi"))
        assertEquals(1.15, snapshot.getDouble("fontScale"), 0.0001)
        assertEquals("portrait", snapshot.getString("orientation"))
        assertEquals(24, snapshot.getInt("insetStatusBarsDp"))
        assertEquals(40, snapshot.getInt("insetNavigationBarsDp"))
        assertEquals(40, snapshot.getInt("insetTappableElementDp"))
        assertEquals(16, snapshot.getInt("insetSystemGesturesDp"))
        assertEquals("Medium", snapshot.getString("questionWidthBucket"))

        assertFalse(snapshot.has("serial"))
        assertFalse(snapshot.has("androidId"))
        assertFalse(snapshot.has("advertisingId"))
        assertFalse(snapshot.has("questionText"))
        assertFalse(snapshot.has("answerText"))
    }

    @Test
    fun missingOptionalWindowMetrics_keepCoreFields() {
        val snapshot = JSONObject().apply {
            put("deviceManufacturer", "Google")
            put("deviceModel", "sdk_gphone")
            put("androidRelease", "17")
            put("androidSdk", 37)
            putDeviceWindowDiagnostics(DeviceWindowDiagnosticsSnapshot(orientation = "landscape"))
        }

        assertTrue(snapshot.has("deviceManufacturer"))
        assertTrue(snapshot.has("androidRelease"))
        assertEquals("landscape", snapshot.getString("orientation"))
        assertFalse(snapshot.has("windowWidthPx"))
        assertFalse(snapshot.has("insetNavigationBarsDp"))
    }
}
// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik END
