package com.notipeek.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notipeek.data.AppSummary
import com.notipeek.data.CaptureSettings
import com.notipeek.data.CapturedMessage
import com.notipeek.data.MessageRepository
import com.notipeek.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MessageViewModel(
    private val repo: MessageRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val appSummaries: Flow<List<AppSummary>> = repo.observeAppSummaries()

    /** Reactive view of the per-app capture filter. */
    val captureSettings: Flow<CaptureSettings> = settings.observe()

    fun messagesForApp(packageName: String): Flow<List<CapturedMessage>> =
        repo.observeByApp(packageName)

    fun markRead(id: Long) = viewModelScope.launch { repo.markRead(id) }
    fun clearApp(packageName: String) = viewModelScope.launch { repo.clearApp(packageName) }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }

    // --- Capture filter settings ------------------------------------------
    fun setOnlySelected(only: Boolean) { settings.onlySelected = only }
    fun setPackageSelected(packageName: String, selected: Boolean) =
        settings.setPackageSelected(packageName, selected)

    class Factory(
        private val repo: MessageRepository,
        private val settings: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MessageViewModel(repo, settings) as T
    }
}
