package com.notipeek

import android.app.Application
import com.notipeek.data.MessageRepository

/** App entry point; owns the singleton repository. */
class NotiPeekApp : Application() {
    val repository: MessageRepository by lazy { MessageRepository.from(this) }
}
