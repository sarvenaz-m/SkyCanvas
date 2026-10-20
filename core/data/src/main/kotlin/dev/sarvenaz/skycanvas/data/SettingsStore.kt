package dev.sarvenaz.skycanvas.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.skyPreferences by preferencesDataStore("skycanvas")

data class SkyPreferences(
    val placeId: String = "isfahan",
    val fahrenheit: Boolean = false,
    val motion: Boolean = true,
)

interface SkySettings {
    val values: kotlinx.coroutines.flow.Flow<SkyPreferences>

    suspend fun setPlace(value: String)

    suspend fun setFahrenheit(value: Boolean)

    suspend fun setMotion(value: Boolean)
}

class SettingsStore(
    private val context: Context,
    private val defaults: SkyPreferences = SkyPreferences(),
) : SkySettings {
    private val place = stringPreferencesKey("place")
    private val fahrenheit = booleanPreferencesKey("fahrenheit")
    private val motion = booleanPreferencesKey("motion")
    override val values =
        context.skyPreferences.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map {
                SkyPreferences(
                    it[place] ?: defaults.placeId,
                    it[fahrenheit] ?: defaults.fahrenheit,
                    it[motion] ?: defaults.motion,
                )
            }

    override suspend fun setPlace(value: String) {
        context.skyPreferences.edit { it[place] = value }
    }

    override suspend fun setFahrenheit(value: Boolean) {
        context.skyPreferences.edit { it[fahrenheit] = value }
    }

    override suspend fun setMotion(value: Boolean) {
        context.skyPreferences.edit { it[motion] = value }
    }
}
