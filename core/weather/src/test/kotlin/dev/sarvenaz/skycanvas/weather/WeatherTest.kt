package dev.sarvenaz.skycanvas.weather

import java.io.File
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
    private val place = Places.all.first()

    private fun raw() = File("../../data/isfahan.json").readText()

    private fun change(section: String, key: String, value: JsonElement): String {
        val root = Json.parseToJsonElement(raw()).jsonObject.toMutableMap()
        val child = root.getValue(section).jsonObject.toMutableMap()
        child[key] = value
        root[section] = JsonObject(child)
        return JsonObject(root).toString()
    }

    @Test
    fun publicForecastRetainsItsUnitsAndCompleteTimeSeries() {
        val f = ForecastCodec.decode(raw(), place)
        assertEquals(168, f.hours.size)
        assertEquals(7, f.days.size)
        assertEquals(27.5, f.current.temperature!!, 0.0001)
        assertEquals(24, f.upcoming().size)
        assertTrue(f.upcoming().all { it.time >= f.current.time })
    }

    @Test
    fun everyRecordedPlaceMatchesItsRequestedCoordinates() {
        for (p in Places.all) assertEquals(
            p,
            ForecastCodec.decode(File("../../data/${p.id}.json").readText(), p).place,
        )
    }

    @Test
    fun daySelectionUsesThePlacesDate() {
        val f = ForecastCodec.decode(raw(), place)
        for (day in f.days) {
            assertEquals(24, f.hoursOn(day).size)
            assertTrue(f.hoursOn(day).all { f.localDate(it.time) == f.localDate(day.time) })
        }
    }

    @Test
    fun localTimePreservesTehransHalfHourOffset() {
        assertEquals(
            "03:30",
            localTime(Instant.parse("2026-10-19T00:00:00Z").epochSecond, "Asia/Tehran"),
        )
    }

    @Test
    fun nullValuesRemainUnavailable() {
        val f = ForecastCodec.decode(change("current", "temperature_2m", JsonNull), place)
        assertNull(f.current.temperature)
        assertEquals("—", temperature(f.current.temperature))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fahrenheitPayloadCannotEnterACelsiusCache() {
        ForecastCodec.decode(change("hourly_units", "temperature_2m", JsonPrimitive("°F")), place)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedColumnsAreRejected() {
        ForecastCodec.decode(change("hourly", "precipitation", JsonArray(emptyList())), place)
    }

    @Test(expected = IllegalArgumentException::class)
    fun probabilityAboveOneHundredIsRejected() {
        val values =
            Json.parseToJsonElement(raw())
                .jsonObject
                .getValue("hourly")
                .jsonObject
                .getValue("precipitation_probability")
                .jsonArray
                .toMutableList()
        values[0] = JsonPrimitive(101)
        ForecastCodec.decode(
            change("hourly", "precipitation_probability", JsonArray(values)),
            place,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedTimeIsRejected() {
        val times =
            Json.parseToJsonElement(raw())
                .jsonObject
                .getValue("hourly")
                .jsonObject
                .getValue("time")
                .jsonArray
                .toMutableList()
        times[1] = times[0]
        ForecastCodec.decode(change("hourly", "time", JsonArray(times)), place)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anotherPlacesResponseIsRejected() {
        ForecastCodec.decode(raw(), Places.all.last())
    }

    @Test
    fun wmoGroupsKeepFreezingAndHailDescriptions() {
        assertEquals(Scene.RAIN, condition(67).scene)
        assertEquals(Scene.SNOW, condition(86).scene)
        assertEquals(Scene.STORM, condition(97).scene)
        assertTrue(condition(99).label.contains("hail"))
        assertEquals("Conditions unavailable", condition(null).label)
    }

    @Test
    fun conversionsDoNotChangeStoredCelsius() {
        val c = 27.5
        assertEquals("28°", temperature(c))
        assertEquals("82°", temperature(c, true))
        assertEquals(27.5, c, 0.0)
    }

    @Test
    fun cacheAgeDoesNotTurnRecordedDataIntoLiveData() {
        val f = ForecastCodec.decode(raw(), place)
        assertTrue(WeatherRecord(f, 100, Origin.RECORDED, raw()).stale(100))
        val live = WeatherRecord(f, 100, Origin.NETWORK, raw())
        assertFalse(live.stale(1899))
        assertTrue(live.stale(1900))
        assertTrue(live.stale(99))
        assertEquals(0, live.ageSeconds(99))
    }

    @Test
    fun frameWindowRetainsLatestSamplesAndRealBudgets() {
        val w = FrameWindow(3)
        w.add(6.0, 16.7)
        w.add(8.0, 8.3)
        w.add(20.0, 16.7, 2)
        var s = w.summary()
        assertEquals(3, s.count)
        assertEquals(8.0, s.p50Ms!!, 0.0)
        assertEquals(20.0, s.p95Ms!!, 0.0)
        assertEquals(1, s.overBudget)
        assertEquals(2, s.callbacksMissed)
        w.add(7.0, 16.7)
        s = w.summary()
        assertEquals(8.0, s.p50Ms!!, 0.0)
        assertEquals(3, s.count)
        w.reset()
        assertEquals(FrameSummary(), w.summary())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFrameTimingsAreRejected() {
        FrameWindow().add(Double.NaN, 16.7)
    }
}
