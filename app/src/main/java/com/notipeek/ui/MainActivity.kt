package com.notipeek.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notipeek.NotiPeekApp
import com.notipeek.R
import com.notipeek.data.AppSummary
import com.notipeek.data.CaptureSettings
import com.notipeek.data.CapturedMessage
import com.notipeek.data.InstalledApp
import com.notipeek.data.launchableApps
import com.notipeek.service.PeekNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NotiPeekApp
        setContent {
            NotiPeekTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: MessageViewModel =
                        viewModel(factory = MessageViewModel.Factory(app.repository, app.settings))
                    AppRoot(vm)
                }
            }
        }
    }
}

/** Minimal navigation without pulling in navigation-compose. */
private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Conversation(val packageName: String, val appLabel: String) : Screen
}

@Composable
private fun AppRoot(vm: MessageViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            vm,
            onOpenApp = { pkg, label -> screen = Screen.Conversation(pkg, label) },
            onOpenSettings = { screen = Screen.Settings },
        )
        is Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Home })
        is Screen.Conversation -> ConversationScreen(
            vm, s.packageName, s.appLabel, onBack = { screen = Screen.Home }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: MessageViewModel,
    onOpenApp: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val granted = remember { mutableStateOf(PeekNotificationListener.isEnabled(context)) }
    val summaries by vm.appSummaries.collectAsState(initial = emptyList())

    // Re-check the permission whenever we return to the foreground (e.g. after
    // the user grants access in system settings), so the banner updates itself.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted.value = PeekNotificationListener.isEnabled(context)
                // Belt-and-suspenders: if the service process was killed while
                // we were away, the listener may be silently disconnected with
                // no onListenerDisconnected callback. Nudge the system to rebind
                // so capture resumes the moment the user reopens the app.
                PeekNotificationListener.ensureBound(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "設定要抓哪些 App")
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!granted.value) {
                PermissionCard(onGrant = {
                    // The ON_RESUME observer above refreshes the state on return.
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            } else if (BackgroundAccessHelp.isLikelyRestrictedOem()) {
                // Access granted, but this OEM throttles background listeners.
                BackgroundRestrictionCard()
            }
            if (summaries.isEmpty()) {
                EmptyHint(granted.value)
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(summaries, key = { it.packageName }) { s ->
                        AppRow(s, onClick = { onOpenApp(s.packageName, s.appLabel) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "需要「通知存取」權限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "授權後，本 App 會在背景收下每則通知並存在本機。讀取通知不會讓 LINE 等 App " +
                    "把訊息標記為已讀。訊息不會離開這支手機。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant) { Text("前往開啟通知存取") }
        }
    }
}

@Composable
private fun EmptyHint(granted: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (granted) "尚未收到任何通知。\n收到新訊息後就會出現在這裡。"
            else "請先開啟通知存取權限。",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AppRow(summary: AppSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(summary.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${summary.messageCount} 則訊息", style = MaterialTheme.typography.bodySmall)
            }
            Text(formatTime(summary.lastMessageTime), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: MessageViewModel, onBack: () -> Unit) {
    val settings by vm.captureSettings.collectAsState(initial = CaptureSettings(false, emptySet()))
    val summaries by vm.appSummaries.collectAsState(initial = emptyList())

    // Installed launchable apps, so the user can pick apps (e.g. LINE) before any
    // notification has arrived. Loaded off the main thread via produceState.
    val context = LocalContext.current
    val installed by produceState(initialValue = emptyList<InstalledApp>()) {
        value = launchableApps(context)
    }
    // Packages we have actually captured from, for a "已收到訊息" hint.
    val seenPackages = remember(summaries) { summaries.associate { it.packageName to it.appLabel } }
    // Merge: installed apps + any seen-but-not-launchable app + any already-selected
    // package (so a selection is never hidden), de-duplicated and sorted by label.
    val rows = remember(installed, summaries, settings.selectedPackages) {
        val map = LinkedHashMap<String, String>()
        installed.forEach { map[it.packageName] = it.label }
        summaries.forEach { map.putIfAbsent(it.packageName, it.appLabel) }
        settings.selectedPackages.forEach { map.putIfAbsent(it, it) }
        map.entries.map { InstalledApp(it.key, it.value) }.sortedBy { it.label.lowercase() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("擷取設定") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Mode toggle: capture everything (default) vs. only the checked apps.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "只擷取選取的 App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (settings.onlySelected) "目前只會抓下方勾選的 App。"
                        else "目前會抓所有 App 的通知。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.onlySelected, onCheckedChange = { vm.setOnlySelected(it) })
            }
            HorizontalDivider()

            if (rows.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "載入 App 清單中…",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                Text(
                    "選擇要擷取的 App（可從已安裝的 App 直接挑選）",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.packageName }) { app ->
                        val checked = settings.selectedPackages.contains(app.packageName)
                        val seen = seenPackages.containsKey(app.packageName)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { vm.setPackageSelected(app.packageName, !checked) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (seen) "${app.packageName} · 已收到訊息" else app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { vm.setPackageSelected(app.packageName, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    vm: MessageViewModel,
    packageName: String,
    appLabel: String,
    onBack: () -> Unit,
) {
    val messages by remember(packageName) { vm.messagesForApp(packageName) }
        .collectAsState(initial = emptyList())

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(appLabel) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { vm.clearApp(packageName) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "清除此 App 的訊息")
                }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            items(messages, key = { it.id }) { m -> MessageRow(m) }
        }
    }
}

@Composable
private fun MessageRow(m: CapturedMessage) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (m.sender.isNotBlank() && m.sender != m.conversationTitle)
                        "${m.conversationTitle} · ${m.sender}" else m.conversationTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(formatTime(m.messageTime), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(m.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(epoch))
