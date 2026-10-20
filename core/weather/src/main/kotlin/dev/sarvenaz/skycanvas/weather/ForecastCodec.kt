package dev.sarvenaz.skycanvas.weather

import java.time.ZoneId
import kotlin.math.abs
import kotlinx.serialization.json.*

/** Validates the requested units and time series before data can replace a saved forecast. */
object ForecastCodec {
    private val json = Json { isLenient = false }

    fun decode(payload: String, place: Place): Forecast {
        require(payload.toByteArray().size <= 1_048_576) { "The forecast response is too large." }
        val root = json.parseToJsonElement(payload).jsonObject
        require(root["error"]?.jsonPrimitive?.booleanOrNull != true) {
            "The forecast service could not complete the request."
        }
        require(root["timezone"]?.jsonPrimitive?.content == place.zone) {
            "The forecast timezone does not match this place."
        }
        ZoneId.of(place.zone)
        require(
            abs(root.getValue("latitude").jsonPrimitive.double - place.latitude) < .75 &&
                abs(root.getValue("longitude").jsonPrimitive.double - place.longitude) < .75
        ) {
            "The forecast grid does not match this place."
        }
        fun unit(section: String, key: String, expected: String) {
            require(root.getValue(section).jsonObject[key]?.jsonPrimitive?.content == expected) {
                "Unexpected forecast units for $key."
            }
        }
        unit("current_units", "time", "unixtime")
        unit("hourly_units", "time", "unixtime")
        unit("daily_units", "time", "unixtime")
        for (section in listOf("current_units", "hourly_units")) {
            unit(section, "temperature_2m", "°C")
            unit(section, "precipitation", "mm")
        }
        unit("current_units", "apparent_temperature", "°C")
        unit("current_units", "relative_humidity_2m", "%")
        unit("current_units", "wind_speed_10m", "km/h")
        unit("hourly_units", "precipitation_probability", "%")
        unit("daily_units", "temperature_2m_max", "°C")
        unit("daily_units", "temperature_2m_min", "°C")
        unit("daily_units", "precipitation_sum", "mm")
        unit("daily_units", "precipitation_probability_max", "%")
        fun JsonElement?.number(
            min: Double = -Double.MAX_VALUE,
            max: Double = Double.MAX_VALUE,
        ): Double? {
            if (this == null || this == JsonNull) return null
            val value = jsonPrimitive.doubleOrNull
            require(value != null && value.isFinite() && value in min..max) {
                "A forecast value is outside its valid range."
            }
            return value
        }
        fun JsonElement?.integer(): Int? {
            if (this == null || this == JsonNull) return null
            return jsonPrimitive.intOrNull?.also { require(it in 0..999) }
                ?: error("Invalid condition code.")
        }
        fun JsonElement?.dayFlag(): Boolean? {
            val value = integer() ?: return null
            require(value in 0..1) { "Invalid day/night flag." }
            return value == 1
        }
        fun JsonElement?.epoch(optional: Boolean = false): Long? {
            if ((this == null || this == JsonNull) && optional) return null
            return this?.jsonPrimitive?.longOrNull?.also { require(it > 0) }
                ?: error("A forecast timestamp is invalid.")
        }
        val current = root.getValue("current").jsonObject
        val now =
            Current(
                current["time"].epoch()!!,
                current["temperature_2m"].number(-150.0, 100.0),
                current["apparent_temperature"].number(-150.0, 150.0),
                current["relative_humidity_2m"].number(0.0, 100.0),
                current["wind_speed_10m"].number(0.0),
                current["weather_code"].integer(),
                current["is_day"].dayFlag(),
            )
        val hourly = root.getValue("hourly").jsonObject
        val times = hourly.getValue("time").jsonArray
        require(times.size in 1..384)
        for (key in
            listOf(
                "temperature_2m",
                "precipitation",
                "precipitation_probability",
                "weather_code",
                "is_day",
            )) require(hourly.getValue(key).jsonArray.size == times.size) {
            "Hourly forecast columns have different lengths."
        }
        fun value(key: String, i: Int) = hourly.getValue(key).jsonArray[i]
        val hours =
            times.indices.map { i ->
                Hour(
                    times[i].epoch()!!,
                    value("temperature_2m", i).number(-150.0, 100.0),
                    value("precipitation", i).number(0.0),
                    value("precipitation_probability", i).number(0.0, 100.0),
                    value("weather_code", i).integer(),
                    value("is_day", i).dayFlag(),
                )
            }
        require(hours.zipWithNext().all { (a, b) -> b.time - a.time == 3600L }) {
            "Hourly forecast times are not continuous."
        }
        require(now.time in hours.first().time..(hours.last().time + 3600)) {
            "Current conditions fall outside the forecast."
        }
        val daily = root.getValue("daily").jsonObject
        val daysTime = daily.getValue("time").jsonArray
        require(daysTime.size in 1..16)
        for (key in
            listOf(
                "temperature_2m_min",
                "temperature_2m_max",
                "precipitation_sum",
                "precipitation_probability_max",
                "sunrise",
                "sunset",
                "weather_code",
            )) require(daily.getValue(key).jsonArray.size == daysTime.size) {
            "Daily forecast columns have different lengths."
        }
        fun dailyValue(key: String, i: Int) = daily.getValue(key).jsonArray[i]
        val days =
            daysTime.indices.map { i ->
                val low = dailyValue("temperature_2m_min", i).number(-150.0, 100.0)
                val high = dailyValue("temperature_2m_max", i).number(-150.0, 100.0)
                require(low == null || high == null || low <= high)
                Day(
                    daysTime[i].epoch()!!,
                    low,
                    high,
                    dailyValue("precipitation_sum", i).number(0.0),
                    dailyValue("precipitation_probability_max", i).number(0.0, 100.0),
                    dailyValue("sunrise", i).epoch(true),
                    dailyValue("sunset", i).epoch(true),
                    dailyValue("weather_code", i).integer(),
                )
            }
        val forecast = Forecast(place, now, hours, days)
        require(
            days.zipWithNext().all { (a, b) ->
                forecast.localDate(b.time) == forecast.localDate(a.time).plusDays(1)
            }
        ) {
            "Daily forecast dates are not consecutive."
        }
        return forecast
    }
}
