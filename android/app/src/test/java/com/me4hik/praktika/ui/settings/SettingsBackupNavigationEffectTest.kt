package com.me4hik.praktika.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host navigation/effect model checks: backup picker channel is separate from
 * SettingsNavigationEvent and restore overlay contracts.
 */
class SettingsBackupNavigationEffectTest {
    @Test
    fun backupEffect_isNotNavigationEvent() {
        val effect: Any = SettingsBackupEffect.LaunchFolderPicker(BackupPickerMode.SETUP)
        assertTrue(effect is SettingsBackupEffect)
        assertFalse(effect is SettingsNavigationEvent)
    }

    @Test
    fun pickerModes_doNotLeakIntoUiStateModel() {
        val fields = BackupSettingsUiState::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("uri", ignoreCase = true) })
        assertFalse(fields.any { it.contains("token", ignoreCase = true) })
        assertFalse(fields.any { it.contains("receipt", ignoreCase = true) })
        assertFalse(fields.any { it.contains("pickerMode", ignoreCase = true) })
    }
}
