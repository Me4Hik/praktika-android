// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik START
package com.me4hik.praktika.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceWindowDiagnosticsJsonTest {

    @Test
    fun putDeviceWindowDiagnostics_writesAllPresentFields() {
        val snapshot = DeviceWindowDiagnosticsSnapshot(
            windowWidthPx = 1200,
            windowHeightPx = 1920,
            screenWidthDp = 600,
            screenHeightDp = 960,
            smallestScreenWidthDp = 600,
            density = 2.0f,
            densityDpi = 320,
            fontScale = 1.0f,
            orientation = DeviceWindowDiagnosticsSnapshot.ORIENTATION_PORTRAIT,
            insetStatusBarsDp = 24,
            insetNavigationBarsDp = 48,
            insetTappableElementDp = 48,
            insetSystemGesturesDp = 24,
            questionWidthBucket = "Medium",
        )

        val json = JSONObject().also { it.putDeviceWindowDiagnostics(snapshot) }

        assertEquals(1200, json.getInt("windowWidthPx"))
        assertEquals(1920, json.getInt("windowHeightPx"))
        assertEquals(600, json.getInt("screenWidthDp"))
        assertEquals(960, json.getInt("screenHeightDp"))
        assertEquals(600, json.getInt("smallestScreenWidthDp"))
        assertEquals(2.0, json.getDouble("density"), 0.0)
        assertEquals(320, json.getInt("densityDpi"))
        assertEquals(1.0, json.getDouble("fontScale"), 0.0)
        assertEquals("portrait", json.getString("orientation"))
        assertEquals(24, json.getInt("insetStatusBarsDp"))
        assertEquals(48, json.getInt("insetNavigationBarsDp"))
        assertEquals(48, json.getInt("insetTappableElementDp"))
        assertEquals(24, json.getInt("insetSystemGesturesDp"))
        assertEquals("Medium", json.getString("questionWidthBucket"))
    }

    @Test
    fun putDeviceWindowDiagnostics_omitsUnavailableOptionalMetrics() {
        val json = JSONObject().also {
            it.putDeviceWindowDiagnostics(
                DeviceWindowDiagnosticsSnapshot(
                    orientation = DeviceWindowDiagnosticsSnapshot.ORIENTATION_UNDEFINED,
                ),
            )
        }

        assertEquals("undefined", json.getString("orientation"))
        assertFalse(json.has("windowWidthPx"))
        assertFalse(json.has("windowHeightPx"))
        assertFalse(json.has("screenWidthDp"))
        assertFalse(json.has("insetStatusBarsDp"))
        assertFalse(json.has("insetNavigationBarsDp"))
        assertFalse(json.has("insetTappableElementDp"))
        assertFalse(json.has("insetSystemGesturesDp"))
        assertFalse(json.has("questionWidthBucket"))
    }

    @Test
    fun providerFailure_fallsBackWithoutPrivacyFields() {
        val provider = DeviceWindowDiagnosticsProvider {
            error("window metrics boom")
        }
        val snapshot = runCatching { provider.collect() }
            .getOrElse { DeviceWindowDiagnosticsSnapshot() }
        val json = JSONObject().also { it.putDeviceWindowDiagnostics(snapshot) }

        assertTrue(json.has("orientation"))
        assertEquals("undefined", json.getString("orientation"))
        assertFalse(json.has("androidId"))
        assertFalse(json.has("serial"))
        assertFalse(json.has("advertisingId"))
        assertFalse(json.has("question_text"))
        assertFalse(json.has("answer_text"))
    }

    @Test
    fun metadataPolicy_stillForbidsUserContentKeys() {
        assertFalse(DiagnosticMetadataPolicy.isAllowedKey("question_text"))
        assertFalse(DiagnosticMetadataPolicy.isAllowedKey("answer_text"))
        assertTrue(DiagnosticMetadataPolicy.isAllowedKey("window_width_px"))
        assertTrue(DiagnosticMetadataPolicy.isAllowedKey("question_width_bucket"))
    }
}
// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik END
