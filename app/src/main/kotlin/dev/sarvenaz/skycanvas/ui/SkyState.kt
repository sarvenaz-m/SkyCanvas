package dev.sarvenaz.skycanvas.ui

import dev.sarvenaz.skycanvas.weather.*

enum class SkyTab(val label: String) {
    TODAY("Today"),
    HOURS("Forecast"),
    PLACES("Places"),
    STUDIO("Studio"),
}

enum class ChartMode {
    TEMPERATURE,
    RAIN,
}

data class SkyState(
    val tab: SkyTab = SkyTab.TODAY,
    val place: Place = Places.all.first(),
    val record: WeatherRecord? = null,
    val records: Map<String, WeatherRecord> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String = "",
    val fahrenheit: Boolean = false,
    val motion: Boolean = true,
    val selectedDay: Long? = null,
    val selectedHour: Long? = null,
    val chart: ChartMode = ChartMode.TEMPERATURE,
    val studioScene: Scene = Scene.CLEAR,
    val studioPlace: String = "isfahan",
    val studioDay: Boolean = true,
    val now: Long = System.currentTimeMillis() / 1000,
) {
    val hours: List<Hour>
        get() {
            val forecast = record?.forecast ?: return emptyList()
            val day = forecast.days.firstOrNull { it.time == selectedDay }
            return if (day != null) forecast.hoursOn(day) else forecast.upcoming()
        }

    val focus: Hour?
        get() = hours.firstOrNull { it.time == selectedHour } ?: hours.firstOrNull()
}

data class SkyActions(
    val tab: (SkyTab) -> Unit = {},
    val place: (Place) -> Unit = {},
    val refresh: () -> Unit = {},
    val fahrenheit: (Boolean) -> Unit = {},
    val motion: (Boolean) -> Unit = {},
    val day: (Long?) -> Unit = {},
    val hour: (Long) -> Unit = {},
    val chart: (ChartMode) -> Unit = {},
    val scene: (Scene) -> Unit = {},
    val studioPlace: (String) -> Unit = {},
    val daylight: (Boolean) -> Unit = {},
    val resetFrames: () -> Unit = {},
    val exportFrames: () -> Unit = {},
)
