package com.vortix.app.domain.analytics

/** Builds argv arrays for the Shizuku shell user service; never returns a shell command string. */
object ShizukuPackageCommandFactory {
    fun forceStop(packageName: String): List<String>? {
        if (!ShizukuPackageNameValidator.isValid(packageName)) return null
        return listOf("/system/bin/am", "force-stop", packageName)
    }

    fun restrictBackground(packageName: String): List<String>? {
        if (!ShizukuPackageNameValidator.isValid(packageName)) return null
        return listOf(
            "/system/bin/cmd", "appops", "set", packageName,
            "RUN_ANY_IN_BACKGROUND", "ignore"
        )
    }
}
