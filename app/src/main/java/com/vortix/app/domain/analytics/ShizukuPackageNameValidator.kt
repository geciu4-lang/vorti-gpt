package com.vortix.app.domain.analytics

object ShizukuPackageNameValidator {
    fun isValid(packageName: String): Boolean =
        packageName.length in 3..255 &&
            packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) &&
            !packageName.contains("..")
}
