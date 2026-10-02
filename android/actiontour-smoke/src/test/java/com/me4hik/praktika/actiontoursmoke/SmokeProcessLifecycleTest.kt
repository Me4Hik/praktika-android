package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeProcessLifecycleTest {
    @Test
    fun packageInstalled_detectsPmPath() {
        assertTrue(SmokeProcessLifecycle.isPackageInstalled("package:/data/app/com.me4hik.praktika/base.apk"))
        assertFalse(SmokeProcessLifecycle.isPackageInstalled(""))
        assertFalse(SmokeProcessLifecycle.isPackageInstalled("Error: not found"))
    }

    @Test
    fun pidPresent_trimsAndChecks() {
        assertTrue(SmokeProcessLifecycle.isPidPresent("12345"))
        assertTrue(SmokeProcessLifecycle.isPidPresent(" 12345\n"))
        assertFalse(SmokeProcessLifecycle.isPidPresent(""))
        assertFalse(SmokeProcessLifecycle.isPidPresent("   \n"))
    }

    @Test
    fun coldStart_emptyPidBeforeLaunch_isValid() {
        assertTrue(SmokeProcessLifecycle.emptyPidAllowedBeforeLaunch())
        assertFalse(SmokeProcessLifecycle.isPidPresent(""))
        // Pre-launch: empty pid must not be treated as crash/precondition fail by itself.
        assertTrue(
            SmokeProcessLifecycle.emptyPidAllowedBeforeLaunch() &&
                !SmokeProcessLifecycle.isPidPresent(""),
        )
    }

    @Test
    fun afterLaunch_emptyPid_mustFail() {
        assertTrue(SmokeProcessLifecycle.shouldFailEmptyPidAfterLaunch())
        assertFalse(SmokeProcessLifecycle.isPidPresent(""))
    }

    @Test
    fun missingPackage_isPreconditionNotEntry() {
        assertFalse(SmokeProcessLifecycle.isPackageInstalled("\n"))
        // Entry/pid checks are orthogonal: package missing fails before launch.
        assertTrue(SmokeProcessLifecycle.emptyPidAllowedBeforeLaunch())
    }
}
