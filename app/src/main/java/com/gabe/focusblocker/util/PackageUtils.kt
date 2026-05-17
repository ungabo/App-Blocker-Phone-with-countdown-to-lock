package com.gabe.focusblocker.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object PackageUtils {
    fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return applicationInfo.flags and systemFlags != 0
    }

    fun isSystemPackage(context: Context, packageName: String): Boolean {
        val applicationInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return false

        return isSystemApp(applicationInfo)
    }
}
