package dev.sarvenaz.skycanvas.weather

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String = "Asia/Tehran",
)

object Places {
    val all =
        listOf(
            Place("isfahan", "Isfahan", 32.6546, 51.6680),
            Place("shiraz", "Shiraz", 29.5918, 52.5837),
            Place("tehran", "Tehran", 35.6892, 51.3890),
            Place("tabriz", "Tabriz", 38.0800, 46.2919),
        )

    fun find(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

enum class Scene {
    CLEAR,
    CLOUD,
    FOG,
    RAIN,
    SNOW,
    STORM,
}

data class Condition(val label: String, val scene: Scene)

fun condition(code: Int?): Condition =
    when (code) {
        0 -> Condition("Clear sky", Scene.CLEAR)
        1 -> Condition("Mainly clear", Scene.CLEAR)
        2 -> Condition("Partly cloudy", Scene.CLOUD)
        3 -> Condition("Overcast", Scene.CLOUD)
        45 -> Condition("Fog", Scene.FOG)
        48 -> Condition("Rime fog", Scene.FOG)
        51 -> Condition("Light drizzle", Scene.RAIN)
        53 -> Condition("Moderate drizzle", Scene.RAIN)
        55 -> Condition("Dense drizzle", Scene.RAIN)
        56,
        57 -> Condition("Freezing drizzle", Scene.RAIN)
        61 -> Condition("Slight rain", Scene.RAIN)
        63 -> Condition("Moderate rain", Scene.RAIN)
        65 -> Condition("Heavy rain", Scene.RAIN)
        66,
        67 -> Condition("Freezing rain", Scene.RAIN)
        71 -> Condition("Slight snow", Scene.SNOW)
        73 -> Condition("Moderate snow", Scene.SNOW)
        75 -> Condition("Heavy snow", Scene.SNOW)
        77 -> Condition("Snow grains", Scene.SNOW)
        80,
        81,
        82 -> Condition("Rain showers", Scene.RAIN)
        85,
        86 -> Condition("Snow showers", Scene.SNOW)
        95,
        97 -> Condition("Thunderstorm", Scene.STORM)
        96,
        99 -> Condition("Thunderstorm with hail", Scene.STORM)
        else -> Condition("Conditions unavailable", Scene.CLOUD)
    }

data class Current(
    val time: Long,
    val temperature: Double?,
    val feelsLike: Double?,
    val humidity: Double?,
    val wind: Double?,
    val code: Int?,
    val isDay: Boolean?,
)

data class Hour(
    val time: Long,
    val temperature: Double?,
    val rain: Double?,
    val probability: Double?,
    val code: Int?,
    val isDay: Boolean?,
)

data class Day(
    val time: Long,
    val low: Double?,
    val high: Double?,
    val rain: Double?,
    val probability: Double?,
    val sunrise: Long?,
    val sunset: Long?,
    val code: Int?,
)

data class Forecast(
    val place: Place,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
) {
    fun localDate(epoch: Long): LocalDate =
        Instant.ofEpochSecond(epoch).atZone(ZoneId.of(place.zone)).toLocalDate()

    fun hoursOn(day: Day): List<Hour> = hours.filter { localDate(it.time) == localDate(day.time) }

    fun upcoming(): List<Hour> = hours.filter { it.time >= current.time }.take(24)

    fun dayFor(epoch: Long) = days.firstOrNull { localDate(it.time) == localDate(epoch) }
}

enum class Origin {
    RECORDED,
    NETWORK,
}

data class WeatherRecord(
    val forecast: Forecast,
    val fetchedAt: Long,
    val origin: Origin,
    val payload: String,
) {
    fun ageSeconds(now: Long): Long = (now - fetchedAt).coerceAtLeast(0)

    fun stale(now: Long): Boolean =
        origin == Origin.RECORDED || now < fetchedAt || ageSeconds(now) >= 1800
}

fun temperature(celsius: Double?, fahrenheit: Boolean = false): String =
    celsius?.let {
        (if (fahrenheit) it * 9 / 5 + 32 else it).roundToInt().toString() + "°"
    } ?: "—"

fun decimal(value: Double?, unit: String = ""): String =
    value?.let { String.format(Locale.ROOT, "%.1f", it) + unit } ?: "—"

fun localTime(epoch: Long, zone: String, pattern: String = "HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
        .format(Instant.ofEpochSecond(epoch).atZone(ZoneId.of(zone)))

data class FrameSample(val durationMs: Double, val budgetMs: Double)

data class FrameSummary(
    val count: Int = 0,
    val p50Ms: Double? = null,
    val p95Ms: Double? = null,
    val overBudget: Int = 0,
    val callbacksMissed: Long = 0,
)

class FrameWindow(private val capacity: Int = 600) {
    private val samples = ArrayDeque<FrameSample>()
    private var missed = 0L

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun add(durationMs: Double, budgetMs: Double, callbacksMissed: Int = 0) {
        require(
            durationMs.isFinite() &&
                durationMs >= 0 &&
                budgetMs.isFinite() &&
                budgetMs > 0 &&
                callbacksMissed >= 0
        )
        if (samples.size == capacity) samples.removeFirst()
        samples.addLast(FrameSample(durationMs, budgetMs))
        missed += callbacksMissed
    }

    @Synchronized
    fun reset() {
        samples.clear()
        missed = 0
    }

    @Synchronized
    fun summary(): FrameSummary {
        if (samples.isEmpty()) return FrameSummary(callbacksMissed = missed)
        val sorted = samples.map { it.durationMs }.sorted()
        fun percentile(p: Double) = sorted[(ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
        return FrameSummary(
            samples.size,
            percentile(.5),
            percentile(.95),
            samples.count { it.durationMs > it.budgetMs },
            missed,
        )
    }
}
