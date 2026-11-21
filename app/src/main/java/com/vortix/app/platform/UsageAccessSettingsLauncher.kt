package com.vortix.app.platform

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens Android's Usage Access app-op screen. Never falls back to Battery Saver settings. */
object UsageAccessSettingsLauncher {
    const val ACTION: String = Settings.ACTION_USAGE_ACCESS_SETTINGS

    fun open(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
