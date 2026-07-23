package com.notipeek.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * On aggressive OEM ROMs (MIUI / HyperOS, EMUI, ColorOS, Funtouch, ...) the
 * system does not keep a NotificationListenerService bound in the background
 * unless the app also has "Autostart" and is exempt from battery optimisation.
 * Without those, the OS kills our process and stops delivering notifications, so
 * messages that arrive while the app is not open are lost. No app-side code can
 * override this; the user must grant it in system settings. This screen detects
 * such a ROM and offers one-tap shortcuts into the relevant settings pages.
 */
object BackgroundAccessHelp {

    /** Manufacturers known to require Autostart / battery exemption for listeners. */
    private val RESTRICTED_OEMS = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor",
        "oppo", "oneplus", "realme", "vivo", "iqoo", "meizu",
    )

    fun isLikelyRestrictedOem(): Boolean {
        val make = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return RESTRICTED_OEMS.any { it in make || it in brand }
    }

    /** Open the OEM "Autostart" manager, falling back to this app's detail page. */
    fun openAutostart(context: Context) {
        val candidates = listOf(
            // MIUI / HyperOS
            componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // ColorOS / OPPO / Realme
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            // Funtouch / vivo
            componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // EMUI / Huawei / Honor
            componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        startFirstResolvable(context, candidates + appDetailsIntent(context))
    }

    /** Open the battery-optimisation exemption list, falling back to app details. */
    fun openBatterySettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        startFirstResolvable(context, candidates + appDetailsIntent(context))
    }

    /** Open this app's system detail page (always resolvable as a last resort). */
    fun openAppDetails(context: Context) {
        startFirstResolvable(context, listOf(appDetailsIntent(context)))
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Try each intent in order; launch the first that succeeds. We attempt
     * startActivity directly instead of gating on resolveActivity(): on Android
     * 11+ (targetSdk 34) package-visibility rules make resolveActivity() return
     * null for another app's component even when it exists, which would wrongly
     * skip a valid OEM settings page. The final candidate is always this app's
     * detail page, which is guaranteed to resolve.
     */
    private fun startFirstResolvable(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Not present on this ROM; try the next candidate.
            }
        }
    }
}

/**
 * A card shown on the home screen (only on restricted OEMs, once access is
 * granted) explaining that background delivery may be throttled and offering
 * shortcuts into Autostart / battery settings.
 */
@Composable
fun BackgroundRestrictionCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "背景收訊可能被系統限制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "偵測到你的手機（${Build.MANUFACTURER}）預設會在背景關閉通知監聽。" +
                    "若要在 App 沒開啟時也能持續收到訊息，請開啟「自啟動」並將電池設為「無限制」，" +
                    "並在最近工作列把本 App 鎖住。未設定時，App 被系統關閉期間的訊息會漏收。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { BackgroundAccessHelp.openAutostart(context) }) {
                    Text("開啟自啟動")
                }
                OutlinedButton(onClick = { BackgroundAccessHelp.openBatterySettings(context) }) {
                    Text("電池不限制")
                }
            }
        }
    }
}
