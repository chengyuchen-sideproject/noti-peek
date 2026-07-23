package com.notipeek.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app the user can pick in the capture filter. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Launchable apps (those with a home-screen launcher entry), for the capture
 * filter picker. Backed by the <queries> LAUNCHER declaration in the manifest,
 * so it needs no QUERY_ALL_PACKAGES permission. Every messaging app has a
 * launcher entry, so this covers the real use case while excluding invisible
 * service packages. Our own package is filtered out. Runs off the main thread.
 */
suspend fun launchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .asSequence()
        .map { it.activityInfo.packageName }
        .filter { it != context.packageName }
        .distinct()
        .map { pkg ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            InstalledApp(pkg, label)
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}
