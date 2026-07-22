package com.notipeek.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Thin data-access layer shared by the service (writes) and the UI (reads). */
class MessageRepository(private val dao: MessageDao) {

    fun observeAll(): Flow<List<CapturedMessage>> = dao.observeAll()
    fun observeByApp(packageName: String): Flow<List<CapturedMessage>> = dao.observeByApp(packageName)
    fun observeAppSummaries(): Flow<List<AppSummary>> = dao.observeAppSummaries()

    /** @return true if the message was new (stored), false if a duplicate. */
    suspend fun save(message: CapturedMessage): Boolean = dao.insert(message) != -1L

    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun clearApp(packageName: String) = dao.clearApp(packageName)
    suspend fun clearAll() = dao.clearAll()

    companion object {
        fun from(context: Context): MessageRepository =
            MessageRepository(AppDatabase.get(context).messageDao())
    }
}
