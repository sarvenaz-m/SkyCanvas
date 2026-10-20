package dev.sarvenaz.skycanvas.data

import dev.sarvenaz.skycanvas.weather.Places
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpApiTest {
    private val server = MockWebServer()

    @Before
    fun start() {
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun api() = OpenMeteoApi(baseUrl = server.url("/").toString(), now = { 1_792_500_000 })

    @Test
    fun realHttpClientRequestsUnambiguousTimesAndMetricUnits() = runBlocking {
        val payload = File("../../data/isfahan.json").readText()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(payload)
        )
        assertEquals(payload, api().fetch(Places.all.first()))
        val request = server.takeRequest()
        assertEquals("/v1/forecast", request.requestUrl!!.encodedPath)
        assertEquals("unixtime", request.requestUrl!!.queryParameter("timeformat"))
        assertEquals("Asia/Tehran", request.requestUrl!!.queryParameter("timezone"))
        assertEquals("celsius", request.requestUrl!!.queryParameter("temperature_unit"))
    }

    @Test
    fun retryAfterIsReturnedWithoutAnAutomaticRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        try {
            api().fetch(Places.all.first())
            fail("Expected rate limit")
        } catch (error: RateLimit) {
            assertEquals(1_792_500_120, error.retryAt)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun htmlErrorPagesAreRejectedBeforeParsing() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Unavailable</html>")
        )
        try {
            api().fetch(Places.all.first())
            fail("Expected unreadable response")
        } catch (_: BadForecast) {}
    }

    @Test
    fun responseSizeIsBounded() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("x".repeat(1_048_577))
        )
        try {
            api().fetch(Places.all.first())
            fail("Expected size limit")
        } catch (_: BadForecast) {}
    }
}
