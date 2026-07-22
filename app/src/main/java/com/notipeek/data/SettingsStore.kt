package com.notipeek.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * App settings backed by SharedPreferences (no extra dependency).
 *
 * Holds the notification capture filter:
 *   - [onlySelected] == false (default): capture every app, unchanged behaviour.
 *   - [onlySelected] == true: capture only packages listed in [selectedPackages].
 *
 * The listener service reads [shouldCapture] synchronously on every posted
 * notification; the UI observes [observe] for reactive updates. Both run in the
 * same process and share one SharedPreferences instance, so a change made in the
 * UI takes effect on the very next captured notification.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var onlySelected: Boolean
        get() = prefs.getBoolean(KEY_ONLY_SELECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONLY_SELECTED, value).apply()

    var selectedPackages: Set<String>
        // getStringSet returns a shared, must-not-modify set: copy it defensively.
        get() = prefs.getStringSet(KEY_SELECTED, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_SELECTED, value).apply()

    fun setPackageSelected(pkg: String, selected: Boolean) {
        val next = selectedPackages.toMutableSet()
        if (selected) next.add(pkg) else next.remove(pkg)
        selectedPackages = next
    }

    /** Synchronous filter decision used by the listener service. */
    fun shouldCapture(pkg: String): Boolean =
        !onlySelected || selectedPackages.contains(pkg)

    private fun snapshot(): CaptureSettings = CaptureSettings(onlySelected, selectedPackages)

    /** Emits the current settings, then again whenever any value changes. */
    fun observe(): Flow<CaptureSettings> = callbackFlow {
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFS = "notipeek_settings"
        private const val KEY_ONLY_SELECTED = "only_selected"
        private const val KEY_SELECTED = "selected_packages"

        fun from(context: Context): SettingsStore = SettingsStore(context)
    }
}

/** Immutable snapshot of the capture filter, for reactive UI collection. */
data class CaptureSettings(
    val onlySelected: Boolean,
    val selectedPackages: Set<String>,
)
