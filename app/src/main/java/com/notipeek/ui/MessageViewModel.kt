package com.notipeek.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notipeek.data.AppSummary
import com.notipeek.data.CapturedMessage
import com.notipeek.data.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MessageViewModel(private val repo: MessageRepository) : ViewModel() {

    val appSummaries: Flow<List<AppSummary>> = repo.observeAppSummaries()

    fun messagesForApp(packageName: String): Flow<List<CapturedMessage>> =
        repo.observeByApp(packageName)

    fun markRead(id: Long) = viewModelScope.launch { repo.markRead(id) }
    fun clearApp(packageName: String) = viewModelScope.launch { repo.clearApp(packageName) }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }

    class Factory(private val repo: MessageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MessageViewModel(repo) as T
    }
}
