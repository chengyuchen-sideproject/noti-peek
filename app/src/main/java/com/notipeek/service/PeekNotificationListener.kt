package com.notipeek.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notipeek.data.CapturedMessage
import com.notipeek.data.MessageRepository
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

    /** Stable key so a repeated conversation line collapses to a single row. */
    private fun dedupeKey(pkg: String, title: String, sender: String, text: String, time: Long): String =
        "$pkg|$title|$sender|$text|$time".hashCode().toString()

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
        /** True if the user has granted this app notification access. */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = ComponentName(context, PeekNotificationListener::class.java)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == me
            }
        }
    }
}
