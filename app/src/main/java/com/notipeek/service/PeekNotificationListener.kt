package com.notipeek.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notipeek.data.CapturedMessage
import com.notipeek.data.MessageRepository
import com.notipeek.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The heart of the app. The system binds to this service once the user grants
 * "Notification access", after which every posted notification is delivered to
 * [onNotificationPosted]. Reading a notification here is a system-level action
 * that the source app never sees, so a LINE message read this way is NOT marked
 * as read in LINE.
 *
 * Extraction strategy, in priority order:
 *   1. MessagingStyle (LINE, WhatsApp, Messenger, ...): the notification carries
 *      each individual message with its sender + timestamp. We store every line.
 *   2. Inbox / big-text / plain fallbacks for everything else.
 *
 * Messaging apps re-post the whole conversation on each new message, so the same
 * line is delivered repeatedly; deduplication happens at the DB layer via a
 * unique [CapturedMessage.dedupeKey].
 */
class PeekNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo by lazy { MessageRepository.from(applicationContext) }
    private val settings by lazy { SettingsStore(applicationContext) }

    /**
     * The system has bound us and will now deliver notifications. Nothing to do
     * here yet, but overriding it documents the lifecycle alongside the
     * disconnect handler below.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
    }

    /**
     * The system tore down the binding (low memory, app update, Doze, or a
     * routine reclaim). Android does NOT reliably rebind on its own, so without
     * this the service silently stops receiving notifications and every message
     * that arrives until the next manual reopen is lost. Ask for a rebind so
     * capture resumes automatically.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "listener disconnected, requesting rebind")
        ensureBound(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldCapture(sbn)) return

        val appLabel = appLabelFor(sbn.packageName)
        val postTime = sbn.postTime
        val captured = extractMessages(sbn, appLabel, postTime)
        if (captured.isEmpty()) return

        scope.launch {
            captured.forEach { repo.save(it) }
        }
    }

    // --- Filtering ---------------------------------------------------------

    private fun shouldCapture(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false          // never our own
        val n = sbn.notification ?: return false
        val flags = n.flags
        // Skip ongoing / foreground-service chrome and the group summary shell.
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        // User-configured per-app filter (default: capture everything).
        if (!settings.shouldCapture(sbn.packageName)) return false
        return true
    }

    // --- Extraction --------------------------------------------------------

    private fun extractMessages(
        sbn: StatusBarNotification,
        appLabel: String,
        postTime: Long,
    ): List<CapturedMessage> {
        val n = sbn.notification
        val extras = n.extras ?: return emptyList()

        // 1) MessagingStyle - the richest source (per-message sender + time).
        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(n)
        if (messagingStyle != null) {
            val title = messagingStyle.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: appLabel
            return messagingStyle.messages.mapNotNull { msg ->
                val text = msg.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return@mapNotNull null
                val sender = msg.person?.name?.toString() ?: title
                buildMessage(sbn.packageName, appLabel, title, sender, text, msg.timestamp, postTime)
            }
        }

        // 2) Fallbacks for non-messaging notifications.
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appLabel

        // InboxStyle: multiple lines in one notification.
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            val out = lines.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                .map { line ->
                    buildMessage(sbn.packageName, appLabel, title, title, line, postTime, postTime)
                }
            if (out.isNotEmpty()) return out
        }

        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return emptyList()
        return listOf(
            buildMessage(sbn.packageName, appLabel, title, title, body, postTime, postTime)
        )
    }

    private fun buildMessage(
        pkg: String,
        appLabel: String,
        title: String,
        sender: String,
        text: String,
        messageTime: Long,
        postTime: Long,
    ): CapturedMessage {
        val effectiveTime = if (messageTime > 0) messageTime else postTime
        return CapturedMessage(
            packageName = pkg,
            appLabel = appLabel,
            conversationTitle = title,
            sender = sender,
            text = text,
            messageTime = effectiveTime,
            capturedTime = System.currentTimeMillis(),
            dedupeKey = dedupeKey(pkg, title, sender, text, effectiveTime),
        )
    }

    /**
     * Stable key so a repeated conversation line collapses to a single row.
     *
     * We use the raw composite string, not its 32-bit [String.hashCode]: with the
     * unique index + IGNORE-on-conflict, a hash collision between two genuinely
     * different messages would silently drop the second one. SQLite indexes the
     * full text cheaply, so there is no reason to risk the collision.
     */
    private fun dedupeKey(pkg: String, title: String, sender: String, text: String, time: Long): String =
        "$pkg|$title|$sender|$text|$time"

    private fun appLabelFor(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "NotiPeek"

        private fun componentName(context: Context): ComponentName =
            ComponentName(context, PeekNotificationListener::class.java)

        /** True if the user has granted this app notification access. */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = componentName(context)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == me
            }
        }

        /**
         * Ask the system to (re)bind the listener if access is granted. Safe to
         * call any time; used both from [onListenerDisconnected] and from the UI
         * on resume as a belt-and-suspenders recovery for the case where the
         * whole service process was killed and [onListenerDisconnected] never
         * fired. A no-op when access has not been granted.
         */
        fun ensureBound(context: Context) {
            if (!isEnabled(context)) return
            Log.i(TAG, "ensureBound: forcing listener rebind")
            val cn = componentName(context)
            // On stock Android requestRebind() is enough. On several OEM ROMs —
            // notably MIUI / HyperOS — the system leaves the listener *granted*
            // but never actually binds it (it is absent from the "Live
            // notification listeners" set), and requestRebind() does not force
            // it. Toggling the component's enabled state disabled -> enabled
            // makes the platform tear down and re-establish the binding, which
            // reliably reconnects the listener. DONT_KILL_APP keeps our process
            // (and this very call) alive across the toggle; we always end in the
            // ENABLED state so the component is never left disabled.
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Exception) {
                // Best-effort; fall through to requestRebind below.
            }
            try {
                NotificationListenerService.requestRebind(cn)
            } catch (_: Exception) {
                // requestRebind can throw if the component is momentarily in a
                // bad state; the next resume will retry.
            }
        }
    }
}
