package dev.sarvenaz.skycanvas.data

import dev.sarvenaz.skycanvas.weather.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OpenForecast(val record: WeatherRecord, val message: String = "")

sealed interface RefreshResult {
    data class Updated(val record: WeatherRecord) : RefreshResult

    data class Kept(val message: String) : RefreshResult
}

class WeatherRepository(
    private val store: WeatherStore,
    private val api: ForecastApi,
    private val recorded: (Place) -> SavedForecast,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val locks = Places.all.associate { it.id to Mutex() }
    private val lastAttempt = mutableMapOf<String, Long>()
    private var blockedUntil = 0L
    private val attempts = Mutex()

    private fun unpack(row: SavedForecast, place: Place): WeatherRecord {
        require(row.placeId == place.id && row.fetchedAt > 0)
        return WeatherRecord(
            ForecastCodec.decode(row.payload, place),
            row.fetchedAt,
            Origin.valueOf(row.origin),
            row.payload,
        )
    }

    suspend fun open(place: Place): OpenForecast =
        locks.getValue(place.id).withLock {
            withContext(Dispatchers.IO) {
                var message = ""
                val cached =
                    try {
                        store.get(place.id)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        message =
                            "Saved forecasts could not be opened. A recorded forecast is shown."
                        null
                    }
                if (cached != null) {
                    try {
                        return@withContext OpenForecast(unpack(cached, place))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        message =
                            "The saved forecast could not be read. A recorded forecast is shown."
                    }
                }
                val seed = recorded(place)
                val fallback = unpack(seed, place)
                try {
                    store.put(seed)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = "This forecast could not be saved on the device."
                }
                OpenForecast(fallback, message)
            }
        }

    suspend fun refresh(place: Place): RefreshResult =
        locks.getValue(place.id).withLock {
            val limited = attempts.withLock {
                when {
                    now() < blockedUntil ->
                        "The weather service asked us to wait. Try again after ${localTime(blockedUntil,place.zone)}."
                    lastAttempt[place.id]?.let { now() - it in 0..4 } == true ->
                        "The last request just finished. Please wait a moment."
                    else -> {
                        lastAttempt[place.id] = now()
                        null
                    }
                }
            }
            if (limited != null) return@withLock RefreshResult.Kept(limited)
            val payload =
                try {
                    api.fetch(place)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    return@withLock when (error) {
                        is RateLimit -> {
                            attempts.withLock { blockedUntil = maxOf(blockedUntil, error.retryAt) }
                            RefreshResult.Kept(
                                "The weather service is busy. Try again after ${localTime(error.retryAt,place.zone)}. Your saved forecast is still available."
                            )
                        }
                        is BadForecast ->
                            RefreshResult.Kept(
                                "The forecast response could not be read. Your saved forecast has been kept."
                            )
                        else ->
                            RefreshResult.Kept(
                                "Could not reach the forecast service. Your saved forecast is still available."
                            )
                    }
                }
            val forecast =
                try {
                    withContext(Dispatchers.Default) { ForecastCodec.decode(payload, place) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    return@withLock RefreshResult.Kept(
                        "The forecast format was unexpected. Your saved forecast has been kept."
                    )
                }
            val fetched = now()
            try {
                withContext(Dispatchers.IO) {
                    store.put(SavedForecast(place.id, payload, fetched, Origin.NETWORK.name))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                return@withLock RefreshResult.Kept(
                    "The new forecast could not be saved. Your previous forecast is still available."
                )
            }
            RefreshResult.Updated(WeatherRecord(forecast, fetched, Origin.NETWORK, payload))
        }
}
