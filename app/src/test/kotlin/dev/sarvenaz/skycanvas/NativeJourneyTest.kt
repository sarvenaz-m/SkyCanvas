package dev.sarvenaz.skycanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.sarvenaz.skycanvas.data.*
import dev.sarvenaz.skycanvas.ui.*
import dev.sarvenaz.skycanvas.weather.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class TestSkyApplication : SkyApplication() {
    var failRequests = false

    override fun createGraph() =
        SkyGraph(
            this,
            object : ForecastApi {
                override suspend fun fetch(place: Place): String {
                    if (failRequests) throw IOException("offline")
                    return assets.open("snapshots/${place.id}.json").bufferedReader().use {
                        it.readText()
                    }
                }
            },
            automaticRefresh = false,
            settings =
                object : SkySettings {
                    override val values = MutableStateFlow(SkyPreferences(motion = false))

                    override suspend fun setPlace(value: String) {
                        values.update { it.copy(placeId = value) }
                    }

                    override suspend fun setFahrenheit(value: Boolean) {
                        values.update { it.copy(fahrenheit = value) }
                    }

                    override suspend fun setMotion(value: Boolean) {
                        values.update { it.copy(motion = value) }
                    }
                },
            databaseName = "test-${java.util.UUID.randomUUID()}.db",
            now = { 1_792_500_000 },
        )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi", application = TestSkyApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: SkyViewModel

    @Before
    fun ready() {
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[SkyViewModel::class.java] }
        compose.waitUntil(30_000) {
            model.state.value.record != null &&
                !model.state.value.loading &&
                !model.state.value.motion
        }
        compose.waitForIdle()
    }

    private fun tab(tab: SkyTab) {
        compose.onNodeWithTag("tab-${tab.name}").performClick()
        compose.waitForIdle()
    }

    private fun scroll(text: String) {
        compose.onNodeWithTag("workspace").performScrollToNode(hasText(text))
    }

    @Test
    fun actualScreensRenderThePublicForecastAndSourceLabels() {
        capture("android-today-debug")
        compose.onNodeWithText("Isfahan").assertIsDisplayed()
        compose.onNodeWithText("RECORDED FORECAST").assertExists()
        capture("android-today")
        tab(SkyTab.HOURS)
        capture("android-forecast")
        tab(SkyTab.PLACES)
        capture("android-places")
        tab(SkyTab.STUDIO)
        capture("android-studio")
        assertEquals(168, model.state.value.record!!.forecast.hours.size)
    }

    @Test
    fun choosingAPlaceKeepsItsOwnForecast() {
        tab(SkyTab.PLACES)
        scroll("Tehran")
        compose.onNodeWithText("Tehran").performClick()
        awaitState {
            model.state.value.place.id == "tehran" &&
                model.state.value.record?.forecast?.place?.id == "tehran"
        }
        assertEquals("Asia/Tehran", model.state.value.record!!.forecast.place.zone)
        compose.onNodeWithText("Tehran").assertIsDisplayed()
    }

    @Test
    fun aFailedRefreshKeepsTheLastSavedForecast() {
        val before = model.state.value.record!!.payload
        (compose.activity.application as TestSkyApplication).failRequests = true
        compose.onNodeWithText("Refresh").performClick()
        compose.waitUntil(15_000) { model.state.value.message.contains("Could not reach") }
        assertEquals(before, model.state.value.record!!.payload)
        assertEquals(Origin.RECORDED, model.state.value.record!!.origin)
    }

    @Test
    fun refreshingSavesAReceiveTimeAndSource() {
        compose.onNodeWithText("Refresh").performClick()
        awaitState { model.state.value.record?.origin == Origin.NETWORK }
        assertTrue(model.state.value.record!!.fetchedAt > 0)
        compose.onNodeWithText("SAVED FORECAST").assertExists()
    }

    @Test
    fun sceneStudiesNeverChangeTheForecast() {
        val before = model.state.value.record!!.forecast
        tab(SkyTab.STUDIO)
        scroll("Night")
        compose.onNodeWithText("Night").performClick()
        compose.onNodeWithText("Rain", substring = false).performClick()
        compose.waitUntil(10_000) {
            !model.state.value.studioDay && model.state.value.studioScene == Scene.RAIN
        }
        assertEquals(before, model.state.value.record!!.forecast)
        capture("android-studio-night")
    }

    @Test
    fun fahrenheitChangesPresentationAndRetainsCelsiusData() {
        val before = model.state.value.record!!.forecast.current.temperature
        tab(SkyTab.STUDIO)
        scroll("Use Fahrenheit")
        compose.onNodeWithContentDescription("Use Fahrenheit").performClick()
        compose.waitUntil(10_000) { model.state.value.fahrenheit }
        tab(SkyTab.TODAY)
        compose.onNodeWithText("82°").assertExists()
        assertEquals(before, model.state.value.record!!.forecast.current.temperature)
    }

    @Test
    fun gentleMotionFollowsItsSavedSetting() {
        val before = model.state.value.record!!.forecast
        tab(SkyTab.STUDIO)
        scroll("Gentle motion")
        compose.onNodeWithContentDescription("Gentle motion").performClick()
        awaitState { model.state.value.motion }
        tab(SkyTab.TODAY)
        tab(SkyTab.STUDIO)
        scroll("Gentle motion")
        compose.onNodeWithContentDescription("Gentle motion").assertIsOn().performClick()
        awaitState { !model.state.value.motion }
        assertEquals(before, model.state.value.record!!.forecast)
    }

    @Test
    fun framePanelDoesNotInventDeviceMeasurements() {
        tab(SkyTab.STUDIO)
        scroll("A measured kind of motion")
        compose.onNodeWithText("Export timings").assertIsNotEnabled()
        compose.onNodeWithText("No device frames measured yet.", substring = true).assertExists()
    }

    private fun awaitState(predicate: () -> Boolean) {
        try {
            compose.waitUntil(15_000) {
                compose.runOnIdle {}
                predicate()
            }
        } catch (error: Throwable) {
            val s = model.state.value
            println(
                "NATIVE STATE place=${s.place.id} forecast=${s.record?.forecast?.place?.id} loading=${s.loading} refreshing=${s.refreshing} origin=${s.record?.origin} message=${s.message}"
            )
            throw error
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/native-captures/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(file.length() > 10_000)
        }
    }
}
