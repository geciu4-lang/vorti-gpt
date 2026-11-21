package com.vortix.app.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAccessSettingsLauncherTest {
    @Test
    fun usageAccessLauncherUsesOnlyTheUsageAccessSettingsAction() {
        assertEquals("android.settings.USAGE_ACCESS_SETTINGS", UsageAccessSettingsLauncher.ACTION)
    }
}
