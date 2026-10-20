package dev.sarvenaz.skycanvas

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.sarvenaz.skycanvas.data.*
import dev.sarvenaz.skycanvas.weather.*
import java.time.Instant
import org.json.JSONObject

open class SkyApplication : Application() {
    lateinit var graph: SkyGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraph()
    }

    protected open fun createGraph() = SkyGraph(this)
}

class SkyGraph(
    context: Context,
    api: ForecastApi = OpenMeteoApi(),
    val automaticRefresh: Boolean = true,
    defaults: SkyPreferences = SkyPreferences(),
    settings: SkySettings? = null,
    databaseName: String = "skycanvas.db",
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    val database = WeatherDatabase.open(context, databaseName)
    val preferences: SkySettings = settings ?: SettingsStore(context, defaults)
    private val metadata =
        JSONObject(context.assets.open("provenance.json").bufferedReader().use { it.readText() })
            .getJSONArray("snapshots")
    val repository =
        WeatherRepository(
            RoomWeatherStore(database.forecasts()),
            api,
            { place ->
                val info =
                    (0 until metadata.length())
                        .map { metadata.getJSONObject(it) }
                        .first { it.getString("placeId") == place.id }
                SavedForecast(
                    place.id,
                    context.assets.open("snapshots/${place.id}.json").bufferedReader().use {
                        it.readText()
                    },
                    Instant.parse(info.getString("retrievedAt")).epochSecond,
                    Origin.RECORDED.name,
                )
            },
            now,
        )
    val factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SkyViewModel::class.java))
                return SkyViewModel(repository, preferences, automaticRefresh, now) as T
            }
        }
}
