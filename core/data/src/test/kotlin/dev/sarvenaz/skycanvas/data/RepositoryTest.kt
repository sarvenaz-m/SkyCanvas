package dev.sarvenaz.skycanvas.data

import dev.sarvenaz.skycanvas.weather.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    private val place = Places.all.first()

    private fun seed(p: Place = place) =
        SavedForecast(
            p.id,
            File("../../data/${p.id}.json").readText(),
            1_792_400_000,
            Origin.RECORDED.name,
        )

    private class Store : WeatherStore {
        val rows = mutableMapOf<String, SavedForecast>()
        var fail = false

        override suspend fun get(id: String) = rows[id]

        override suspend fun put(row: SavedForecast) {
            if (fail) throw IOException("disk full")
            rows[row.placeId] = row
        }

        override suspend fun delete(id: String) {
            rows.remove(id)
        }
    }

    private fun api(block: suspend (Place) -> String) =
        object : ForecastApi {
            override suspend fun fetch(place: Place) = block(place)
        }

    @Test
    fun firstOpenStoresTheOriginalReceiveTime() = runBlocking {
        val s = Store()
        val repo = WeatherRepository(s, api { error("No network needed") }, ::seed)
        val record = repo.open(place).record
        assertEquals(seed().fetchedAt, record.fetchedAt)
        assertEquals(Origin.RECORDED, record.origin)
        assertEquals(seed(), s.rows[place.id])
    }

    @Test
    fun invalidSavedDataFallsBackWithAVisibleMessage() = runBlocking {
        val s = Store()
        s.rows[place.id] = seed().copy(payload = "broken")
        val opened = WeatherRepository(s, api { error("unused") }, ::seed).open(place)
        assertTrue(opened.message.isNotBlank())
        assertEquals(seed().payload, opened.record.payload)
    }

    @Test
    fun successfulRefreshCommitsPayloadAndReceiveTimeTogether() = runBlocking {
        val s = Store()
        val repo = WeatherRepository(s, api { seed(it).payload }, ::seed, { 1_792_500_000 })
        repo.open(place)
        val result = repo.refresh(place) as RefreshResult.Updated
        assertEquals(Origin.NETWORK, result.record.origin)
        assertEquals(1_792_500_000, s.rows[place.id]!!.fetchedAt)
        assertEquals(result.record.payload, s.rows[place.id]!!.payload)
    }

    @Test
    fun offlineRefreshKeepsTheExactSavedRecord() = runBlocking {
        val s = Store()
        val repo = WeatherRepository(s, api { throw IOException("offline") }, ::seed)
        repo.open(place)
        val before = s.rows[place.id]
        assertTrue(repo.refresh(place) is RefreshResult.Kept)
        assertEquals(before, s.rows[place.id])
    }

    @Test
    fun malformedResponseDoesNotPoisonTheCache() = runBlocking {
        val s = Store()
        val repo = WeatherRepository(s, api { "{\"error\":true}" }, ::seed)
        repo.open(place)
        assertTrue(repo.refresh(place) is RefreshResult.Kept)
        assertEquals(seed(), s.rows[place.id])
    }

    @Test
    fun storageFailureDoesNotClaimAnUnsavedUpdate() = runBlocking {
        val s = Store()
        val repo = WeatherRepository(s, api { seed(it).payload }, ::seed)
        repo.open(place)
        s.fail = true
        assertTrue(repo.refresh(place) is RefreshResult.Kept)
        assertEquals(seed(), s.rows[place.id])
    }

    @Test
    fun rateLimitAppliesAcrossPlacesUntilRetryTime() = runBlocking {
        var calls = 0
        var time = 1_792_500_000L
        val repo =
            WeatherRepository(
                Store(),
                api {
                    calls++
                    throw RateLimit(time + 60)
                },
                ::seed,
                { time },
            )
        repo.refresh(place)
        repo.refresh(Places.all[1])
        assertEquals(1, calls)
        time += 61
        repo.refresh(Places.all[1])
        assertEquals(2, calls)
    }

    @Test
    fun concurrentRefreshesForOnePlaceShareTheCooldown() = runBlocking {
        var calls = 0
        val repo =
            WeatherRepository(
                Store(),
                api {
                    calls++
                    delay(40)
                    seed(it).payload
                },
                ::seed,
                { 1_792_500_000 },
            )
        val results = awaitAll(async { repo.refresh(place) }, async { repo.refresh(place) })
        assertEquals(1, calls)
        assertEquals(1, results.count { it is RefreshResult.Updated })
    }

    @Test
    fun cancellationLeavesTheSavedRecordIntact() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val s = Store()
        val repo =
            WeatherRepository(
                s,
                api {
                    started.complete(Unit)
                    awaitCancellation()
                },
                ::seed,
            )
        repo.open(place)
        val job = launch { repo.refresh(place) }
        started.await()
        job.cancelAndJoin()
        assertEquals(seed(), s.rows[place.id])
    }
}
