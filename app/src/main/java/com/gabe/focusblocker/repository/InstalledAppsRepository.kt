package com.gabe.focusblocker.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.gabe.focusblocker.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val icon: Bitmap
)

class InstalledAppsRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        resolveInfos
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    label = resolveInfo.loadLabel(context.packageManager)?.toString()
                        ?: appInfo.packageName,
                    isSystemApp = PackageUtils.isSystemApp(appInfo),
                    icon = resolveInfo.loadIcon(context.packageManager).toBitmap(width = 96, height = 96)
                )
            }
            .sortedWith(compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName })
    }
}

