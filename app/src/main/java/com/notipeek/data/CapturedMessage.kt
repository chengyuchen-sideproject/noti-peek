package com.notipeek.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One captured notification message, stored locally.
 *
 * A [dedupeKey] carries a stable hash of the meaningful fields. Messaging apps
 * such as LINE re-post the whole conversation on every new message, so the same
 * line arrives many times; a unique index on [dedupeKey] + IGNORE-on-conflict
 * keeps exactly one copy of each.
 */
@Entity(
    tableName = "captured_messages",
    indices = [Index(value = ["dedupeKey"], unique = true)],
)
data class CapturedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    /** Conversation / chat title, e.g. the LINE room or friend name. */
    val conversationTitle: String,
    /** Sender within the conversation (group chats); may equal the title. */
    val sender: String,
    val text: String,
    /** When the message was sent, per the notification (falls back to postTime). */
    val messageTime: Long,
    /** When we captured it. */
    val capturedTime: Long,
    val dedupeKey: String,
    val isRead: Boolean = false,
)
