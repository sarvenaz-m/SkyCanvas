package dev.sarvenaz.skycanvas.data

import dev.sarvenaz.skycanvas.weather.Place
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

class RateLimit(val retryAt: Long) : IOException("The weather service is busy.")

class BadForecast : IOException("The forecast response could not be read.")

interface ForecastApi {
    suspend fun fetch(place: Place): String
}

class OpenMeteoApi(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build(),
    private val baseUrl: String = "https://api.open-meteo.com/",
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : ForecastApi {
    override suspend fun fetch(place: Place): String = suspendCancellableCoroutine { continuation ->
        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegments("v1/forecast")
                .addQueryParameter("latitude", place.latitude.toString())
                .addQueryParameter("longitude", place.longitude.toString())
                .addQueryParameter("timezone", place.zone)
                .addQueryParameter("timeformat", "unixtime")
                .addQueryParameter("forecast_days", "7")
                .addQueryParameter("temperature_unit", "celsius")
                .addQueryParameter("wind_speed_unit", "kmh")
                .addQueryParameter("precipitation_unit", "mm")
                .addQueryParameter(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m",
                )
                .addQueryParameter(
                    "hourly",
                    "temperature_2m,precipitation_probability,precipitation,weather_code,is_day",
                )
                .addQueryParameter(
                    "daily",
                    "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,precipitation_probability_max",
                )
                .build()
        val call =
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (it.code == 429) {
                                val header = it.header("Retry-After")
                                val seconds =
                                    header?.toLongOrNull()
                                        ?: runCatching {
                                                ZonedDateTime.parse(
                                                        header,
                                                        DateTimeFormatter.RFC_1123_DATE_TIME,
                                                    )
                                                    .toEpochSecond() - now()
                                            }
                                            .getOrDefault(60)
                                throw RateLimit(now() + seconds.coerceIn(5, 3600))
                            }
                            if (!it.isSuccessful)
                                throw IOException("The weather service is temporarily unavailable.")
                            val content = it.body ?: throw BadForecast()
                            val type = content.contentType()?.subtype.orEmpty()
                            if (type != "json" && !type.endsWith("+json")) throw BadForecast()
                            if (content.contentLength() > 1_048_576) throw BadForecast()
                            val source = content.source()
                            source.request(1_048_577)
                            if (source.buffer.size > 1_048_576) throw BadForecast()
                            source.buffer.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        )
    }
}
