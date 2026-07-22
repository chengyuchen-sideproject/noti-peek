package com.notipeek

import android.app.Application
import com.notipeek.data.MessageRepository
import com.notipeek.data.SettingsStore

/** App entry point; owns the singleton repository and settings. */
class NotiPeekApp : Application() {
    val repository: MessageRepository by lazy { MessageRepository.from(this) }
    val settings: SettingsStore by lazy { SettingsStore.from(this) }
}
