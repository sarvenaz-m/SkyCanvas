package dev.sarvenaz.skycanvas.data

import android.content.Context
import androidx.room.*
import dev.sarvenaz.skycanvas.weather.*

@Entity(tableName = "forecasts")
data class SavedForecast(
    @PrimaryKey val placeId: String,
    val payload: String,
    val fetchedAt: Long,
    val origin: String,
)

@Dao
interface ForecastDao {
    @Query("SELECT * FROM forecasts WHERE placeId = :id")
    suspend fun get(id: String): SavedForecast?

    @Upsert suspend fun put(row: SavedForecast)

    @Query("DELETE FROM forecasts WHERE placeId = :id") suspend fun delete(id: String)
}

@Database(entities = [SavedForecast::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecasts(): ForecastDao

    companion object {
        fun open(context: Context, name: String = "skycanvas.db") =
            Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
    }
}

interface WeatherStore {
    suspend fun get(id: String): SavedForecast?

    suspend fun put(row: SavedForecast)

    suspend fun delete(id: String)
}

class RoomWeatherStore(private val dao: ForecastDao) : WeatherStore {
    override suspend fun get(id: String) = dao.get(id)

    override suspend fun put(row: SavedForecast) = dao.put(row)

    override suspend fun delete(id: String) = dao.delete(id)
}
