package dev.sarvenaz.skycanvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sarvenaz.skycanvas.data.*
import dev.sarvenaz.skycanvas.ui.*
import dev.sarvenaz.skycanvas.weather.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SkyViewModel(
    private val repository: WeatherRepository,
    private val preferences: SkySettings,
    private val automaticRefresh: Boolean = true,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {
    private val mutable = MutableStateFlow(SkyState(now = clock()))
    val state = mutable.asStateFlow()
    private var load: Job? = null
    private var refresh: Job? = null
    private var generation = 0L
    val actions =
        SkyActions(
            tab = { value -> mutable.update { it.copy(tab = value) } },
            place = { place ->
                select(place)
                viewModelScope.launch {
                    runCatching { preferences.setPlace(place.id) }
                        .onFailure { message("Your place could not be saved for the next visit.") }
                }
            },
            refresh = ::refresh,
            fahrenheit = { value ->
                viewModelScope.launch {
                    runCatching { preferences.setFahrenheit(value) }
                        .onFailure { message("The temperature preference could not be saved.") }
                }
            },
            motion = { value ->
                viewModelScope.launch {
                    runCatching { preferences.setMotion(value) }
                        .onFailure { message("The motion preference could not be saved.") }
                }
            },
            day = { value -> mutable.update { it.copy(selectedDay = value, selectedHour = null) } },
            hour = { value -> mutable.update { it.copy(selectedHour = value) } },
            chart = { value -> mutable.update { it.copy(chart = value) } },
            scene = { value -> mutable.update { it.copy(studioScene = value) } },
            studioPlace = { id ->
                if (Places.all.any { it.id == id }) mutable.update { it.copy(studioPlace = id) }
            },
            daylight = { value -> mutable.update { it.copy(studioDay = value) } },
        )

    init {
        viewModelScope.launch {
            preferences.values.collect { value ->
                mutable.update { it.copy(fahrenheit = value.fahrenheit, motion = value.motion) }
                if (state.value.record == null || state.value.place.id != value.placeId)
                    select(Places.find(value.placeId))
            }
        }
        viewModelScope.launch {
            for (place in Places.all) {
                runCatching { repository.open(place).record }
                    .onSuccess { record ->
                        mutable.update { it.copy(records = it.records + (place.id to record)) }
                    }
            }
        }
    }

    fun message(value: String) {
        mutable.update { it.copy(message = value) }
    }

    fun resumed() {
        mutable.update { it.copy(now = clock()) }
    }

    private fun select(place: Place) {
        load?.cancel()
        refresh?.cancel()
        val token = ++generation
        mutable.update {
            it.copy(
                place = place,
                studioPlace = place.id,
                record = null,
                loading = true,
                refreshing = false,
                message = "",
                selectedDay = null,
                selectedHour = null,
                tab = if (it.tab == SkyTab.PLACES) SkyTab.TODAY else it.tab,
            )
        }
        load = viewModelScope.launch {
            try {
                val opened = repository.open(place)
                if (token != generation) return@launch
                mutable.update {
                    it.copy(
                        record = opened.record,
                        records = it.records + (place.id to opened.record),
                        loading = false,
                        message = opened.message,
                    )
                }
                if (automaticRefresh && opened.record.stale(state.value.now)) refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (token == generation)
                    mutable.update {
                        it.copy(
                            loading = false,
                            message = "The forecast could not be opened. Please try refreshing.",
                        )
                    }
            }
        }
    }

    private fun refresh() {
        if (refresh?.isActive == true) return
        val place = state.value.place
        val token = generation
        refresh = viewModelScope.launch {
            mutable.update { it.copy(refreshing = true, message = "") }
            try {
                val result = repository.refresh(place)
                if (token != generation) return@launch
                when (result) {
                    is RefreshResult.Updated ->
                        mutable.update {
                            it.copy(
                                record = result.record,
                                records = it.records + (place.id to result.record),
                                message = "Forecast updated and saved.",
                                loading = false,
                                selectedDay = null,
                                selectedHour = null,
                                now = clock(),
                            )
                        }
                    is RefreshResult.Kept -> message(result.message)
                }
            } finally {
                if (token == generation) mutable.update { it.copy(refreshing = false) }
            }
        }
    }
}
