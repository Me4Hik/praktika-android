package com.me4hik.praktika.ui.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationRestoreOwnershipTest {
    @Test
    fun appNavigation_usesSingleRestoreViewModelForHostAndEffects() {
        val source = readSource("src/main/java/com/me4hik/praktika/navigation/AppNavigation.kt")
            .replace("\r\n", "\n")
        assertTrue(source.contains("val restoreViewModel: ProductionRestoreViewModel = viewModel("))
        assertTrue(source.contains("ProductionRestoreEffects("))
        assertTrue(source.contains("viewModel = restoreViewModel"))
        assertTrue(source.contains("ProductionRestoreSessionHost("))
        assertTrue(source.contains("viewModel = restoreViewModel"))
        assertFalse(source.contains("ProductionRestoreSessionHost(\n            activity ="))
    }

    @Test
    fun appNavigation_wiresPublicOnboardingRestoreActivation() {
        val source = readSource("src/main/java/com/me4hik/praktika/navigation/AppNavigation.kt")
            .replace("\r\n", "\n")
        assertTrue(source.contains("onRestoreBackup = restoreViewModel::onOpenRestore"))
        assertTrue(source.contains("discardOnboardingDraftFromPersistedSchedule()"))
        assertTrue(source.contains("onRestoreCommitted"))
    }

    @Test
    fun onboardingScreen_declaresRestoreCallback() {
        val source = readSource("src/main/java/com/me4hik/praktika/ui/OnboardingScreen.kt")
            .replace("\r\n", "\n")
        assertTrue(source.contains("onRestoreBackup: () -> Unit"))
        assertTrue(source.contains("ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA"))
        assertTrue(source.contains("R.string.onboarding_restore_backup"))
    }

    @Test
    fun sessionHost_doesNotCreateInternalViewModel() {
        val source = readSource("src/main/java/com/me4hik/praktika/ui/restore/ProductionRestoreSessionHost.kt")
        assertFalse(source.contains("viewModel("))
        assertFalse(source.contains("ProductionRestoreViewModelFactory"))
        assertTrue(source.contains("viewModel: ProductionRestoreViewModel"))
    }

    private fun readSource(relativePath: String): String {
        val baseDir = System.getProperty("user.dir")
        val candidates = listOf(
            java.nio.file.Paths.get(baseDir, "app", relativePath),
            java.nio.file.Paths.get(baseDir, relativePath),
        )
        val path = candidates.firstOrNull { java.nio.file.Files.exists(it) }
            ?: error("Could not find $relativePath from $baseDir")
        return String(java.nio.file.Files.readAllBytes(path), Charsets.UTF_8)
    }
}
