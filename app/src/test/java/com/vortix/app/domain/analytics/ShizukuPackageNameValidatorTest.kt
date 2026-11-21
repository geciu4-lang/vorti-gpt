package com.vortix.app.domain.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuPackageNameValidatorTest {
    @Test fun acceptsRegularPackageNames() {
        assertTrue(ShizukuPackageNameValidator.isValid("com.example.app"))
    }

    @Test fun rejectsShellInjectionAndMalformedPackages() {
        assertFalse(ShizukuPackageNameValidator.isValid("com.example;reboot"))
        assertFalse(ShizukuPackageNameValidator.isValid("com..example"))
        assertFalse(ShizukuPackageNameValidator.isValid("/system/bin/sh"))
        assertFalse(ShizukuPackageNameValidator.isValid("x"))
    }
}
