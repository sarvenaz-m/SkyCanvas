package dev.sarvenaz.skycanvas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomStoreTest {
    @Test
    fun cacheSurvivesDatabaseReopenWithExactSourceAndTimestamp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "weather-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
        var db = open()
        val row = SavedForecast("isfahan", "{sample}", 1_792_500_123, "NETWORK")
        db.forecasts().put(row)
        db.close()
        db = open()
        assertEquals(row, db.forecasts().get("isfahan"))
        assertNull(db.forecasts().get("tehran"))
        val updated = row.copy(payload = "{new sample}", fetchedAt = row.fetchedAt + 60)
        db.forecasts().put(updated)
        assertEquals(updated, db.forecasts().get("isfahan"))
        db.forecasts().delete("isfahan")
        assertNull(db.forecasts().get("isfahan"))
        db.close()
        context.deleteDatabase(name)
        Unit
    }
}
