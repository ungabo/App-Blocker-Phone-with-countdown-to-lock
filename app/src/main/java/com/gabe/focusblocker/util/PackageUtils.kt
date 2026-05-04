package com.gabe.focusblocker.util

import android.content.pm.ApplicationInfo

object PackageUtils {
    fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }
}

