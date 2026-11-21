package com.vortix.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShizukuPackageCommandFactoryTest {
    @Test fun forceStopUsesSeparateValidatedArguments() {
        assertEquals(
            listOf("/system/bin/am", "force-stop", "com.example.reader"),
            ShizukuPackageCommandFactory.forceStop("com.example.reader")
        )
    }

    @Test fun restrictBackgroundUsesAppOpsArgumentList() {
        assertEquals(
            listOf("/system/bin/cmd", "appops", "set", "com.example.reader", "RUN_ANY_IN_BACKGROUND", "ignore"),
            ShizukuPackageCommandFactory.restrictBackground("com.example.reader")
        )
    }

    @Test fun rejectsShellMetacharactersBeforeCommandCreation() {
        assertNull(ShizukuPackageCommandFactory.forceStop("com.example;reboot"))
        assertNull(ShizukuPackageCommandFactory.restrictBackground("$(id)"))
    }
}
