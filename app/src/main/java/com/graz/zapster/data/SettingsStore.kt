package com.graz.zapster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TvSettings(
    val host: String = "",
    val name: String = "",
    /**
     * Whether pairing has completed for the stored client certificate. The TV
     * refuses the remote port outright until it has, so this decides between
     * showing the remote and showing setup.
     */
    val paired: Boolean = false,
) {
    val isConfigured: Boolean get() = host.isNotBlank() && paired
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tv_remote")

class SettingsStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val NAME = stringPreferencesKey("name")
        val PAIRED = booleanPreferencesKey("paired")
    }

    val settings: Flow<TvSettings> = context.dataStore.data.map { prefs ->
        TvSettings(
            host = prefs[Keys.HOST].orEmpty(),
            name = prefs[Keys.NAME].orEmpty(),
            paired = prefs[Keys.PAIRED] ?: false,
        )
    }

    suspend fun save(settings: TvSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = settings.host.trim()
            prefs[Keys.NAME] = settings.name
            prefs[Keys.PAIRED] = settings.paired
        }
    }
}
