package com.akash.doura.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.doura.TransportType
import com.akash.doura.data.DepartureRepository
import com.akash.doura.data.Journey
import com.akash.doura.data.LegKind
import com.akash.doura.data.RouteRepository
import com.akash.doura.data.StationHit
import com.akash.doura.data.StopSearchRepository
import com.akash.doura.data.TripQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Calendar

/* ----------------------------------------------------------------------------
 * Station search
 * -------------------------------------------------------------------------- */

data class StationSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<StationHit> = emptyList(),
    val recents: List<StationHit> = emptyList(),
    val error: String? = null
)

class StationSearchViewModel : ViewModel() {

    private val repository = StopSearchRepository()
    private var job: Job? = null

    var state by mutableStateOf(StationSearchState())
        private set

    /** Debounced: one request per pause in typing, not one per keystroke. */
    fun onQueryChange(query: String) {
        state = state.copy(query = query)
        job?.cancel()

        if (query.trim().length < 2) {
            state = state.copy(results = emptyList(), loading = false, error = null)
            return
        }

        job = viewModelScope.launch {
            delay(250)
            state = state.copy(loading = true, error = null)
            try {
                state = state.copy(loading = false, results = repository.search(query))
            } catch (e: IOException) {
                state = state.copy(loading = false, error = "No connection.")
            } catch (e: Exception) {
                state = state.copy(loading = false, error = "Search failed. Try again.")
            }
        }
    }

    fun clear() {
        job?.cancel()
        state = state.copy(query = "", results = emptyList(), loading = false, error = null)
    }

    fun remember(hit: StationHit) {
        val updated = (listOf(hit) + state.recents.filterNot { it.id == hit.id }).take(5)
        state = state.copy(recents = updated)
    }
}

/* ----------------------------------------------------------------------------
 * Journey search
 * -------------------------------------------------------------------------- */

data class JourneyState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val journeys: List<Journey> = emptyList(),
    val searched: Boolean = false,
    val error: String? = null,
    val fromName: String = "",
    val toName: String = ""
)

class JourneyViewModel : ViewModel() {

    private val repository = RouteRepository()

    private var originId: String? = null
    private var destId: String? = null
    private var types: Set<TransportType> = emptySet()
    private var maxChange: Int? = null

    var state by mutableStateOf(JourneyState())
        private set

    fun search(
        originId: String,
        destId: String,
        originName: String,
        destName: String,
        departMillis: Long,
        types: Set<TransportType>,
        maxChange: Int? = null
    ) {
        this.originId = originId
        this.destId = destId
        this.types = types
        this.maxChange = maxChange

        state = JourneyState(loading = true, searched = true, fromName = originName, toName = destName)

        viewModelScope.launch {
            try {
                val journeys = repository.journeys(
                    originId = originId,
                    destId = destId,
                    date = TripQuery.date(departMillis),
                    time = TripQuery.time(departMillis),
                    types = types,
                    maxChange = maxChange
                )
                state = state.copy(
                    loading = false,
                    journeys = journeys,
                    error = if (journeys.isEmpty()) {
                        "No journeys found. Try a later time, or allow more transport types."
                    } else null
                )
            } catch (e: IOException) {
                state = state.copy(loading = false, error = "No connection.")
            } catch (e: Exception) {
                state = state.copy(loading = false, error = "Couldn't plan that journey.")
            }
        }
    }

    /**
     * numF caps a response at 6 journeys, so paging means asking again from one
     * minute after the last result departed.
     */
    fun loadMore() {
        val origin = originId ?: return
        val dest = destId ?: return
        val last = state.journeys.lastOrNull() ?: return
        if (state.loadingMore) return

        val next = Calendar.getInstance().apply {
            timeInMillis = last.departMillis
            add(Calendar.MINUTE, 1)
        }.timeInMillis

        state = state.copy(loadingMore = true)

        viewModelScope.launch {
            try {
                val more = repository.journeys(
                    originId = origin,
                    destId = dest,
                    date = TripQuery.date(next),
                    time = TripQuery.time(next),
                    types = types,
                    maxChange = maxChange
                )
                val seen = state.journeys.map { it.key }.toSet()
                state = state.copy(
                    loadingMore = false,
                    journeys = state.journeys + more.filterNot { it.key in seen }
                )
            } catch (e: Exception) {
                state = state.copy(loadingMore = false)
            }
        }
    }

    fun reset() {
        state = JourneyState()
    }
}


/* ----------------------------------------------------------------------------
 * Journey detail: platform lookup
 *
 * The route planner does not carry platform or stand designations. The
 * departure board does, so for each vehicle leg we ask the board at that stop,
 * at that minute, and match on line + scheduled time. That is how a bus at
 * Uppsala C resolves to stand A3 rather than just "Uppsala Centralstation".
 * -------------------------------------------------------------------------- */

class JourneyDetailViewModel : ViewModel() {

    private val departures = DepartureRepository()
    private var loadedKey: String? = null

    /** Leg index to platform designation. Fills in progressively. */
    var platforms by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    var loading by mutableStateOf(false)
        private set

    fun load(journey: Journey) {
        if (loadedKey == journey.key) return
        loadedKey = journey.key
        platforms = emptyMap()
        loading = true

        viewModelScope.launch {
            val found = mutableMapOf<Int, String>()

            journey.legs.forEachIndexed { index, leg ->
                if (leg.kind != LegKind.VEHICLE) return@forEachIndexed
                val areaId = leg.fromId ?: return@forEachIndexed
                val departAt = leg.departMillis ?: return@forEachIndexed

                val platform = runCatching {
                    // Query a few minutes early: the board window runs forward
                    // from the requested time, so asking exactly on the minute
                    // can miss a departure that has just been called.
                    val page = departures.page(areaId, TripQuery.iso(departAt - 4 * 60_000L))
                    val exact = page.departures.firstOrNull {
                        it.scheduledTime == leg.departTime && sameLine(it.line, leg.line)
                    }
                    (exact ?: page.departures.firstOrNull { sameLine(it.line, leg.line) })?.platform
                }.getOrNull()

                if (platform != null) {
                    found[index] = platform
                    platforms = found.toMap()
                }
            }

            loading = false
        }
    }
}

private fun sameLine(boardLine: String?, legLine: String?): Boolean {
    if (boardLine == null || legLine == null) return false
    return boardLine.trim().equals(legLine.trim(), ignoreCase = true)
}