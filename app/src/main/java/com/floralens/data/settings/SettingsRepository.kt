package com.floralens.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.floralens.domain.model.IdentificationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "floralens_settings",
)

/**
 * Read-only settings surface for consumers that just need to react to a
 * preference (currently: IdentifyViewModel checking identification mode).
 * Same interface-over-concrete-class pattern as LocationProvider /
 * NetworkStatus / CareDefaults — lets those consumers be unit-tested
 * without a real Android Context.
 */
interface SettingsProvider {
    val identificationMode: Flow<IdentificationMode>
}

/**
 * Small preferences store — first-launch flag + identification mode.
 * DataStore (not SharedPreferences): async by default, no main-thread IO.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsProvider {
    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val IDENTIFICATION_MODE = stringPreferencesKey("identification_mode")
    }

    /** False until the user finishes onboarding. */
    val onboarded: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.ONBOARDED] ?: false }

    suspend fun setOnboarded() {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ONBOARDED] = true }
    }

    /**
     * Online by default, disclosed in onboarding. Preserve saved user choices,
     * including OFFLINE, across updates. Changeable later from About & Help.
     */
    override val identificationMode: Flow<IdentificationMode> = context.settingsDataStore.data
        .map { prefs ->
            IdentificationMode.fromStored(prefs[Keys.IDENTIFICATION_MODE])
        }

    suspend fun setIdentificationMode(mode: IdentificationMode) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.IDENTIFICATION_MODE] = mode.name }
    }
}
