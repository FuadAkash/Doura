package com.akash.doura.nearby

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akash.doura.data.NearbyRepository
import com.akash.doura.data.NearbyStop
import com.akash.doura.location.LocationProvider
import kotlinx.coroutines.launch
import java.io.IOException

data class NearbyUiState(
    val loading: Boolean = false,
    val placeName: String? = null,
    val stops: List<NearbyStop> = emptyList(),
    val needsPermission: Boolean = false,
    val error: String? = null
)

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    private val location = LocationProvider(app)
    private val repository = NearbyRepository()

    var state by mutableStateOf(NearbyUiState())
        private set

    fun onPermissionDenied() {
        state = state.copy(needsPermission = true, loading = false)
    }

    /** Fix position, name it, then ask Trafiklab what's within 2 km. */
    fun refresh(radiusMetres: Int = 500) {
        if (!location.hasPermission()) {
            state = state.copy(needsPermission = true)
            return
        }

        state = state.copy(loading = true, needsPermission = false, error = null)

        viewModelScope.launch {
            val fix = location.current()
            if (fix == null) {
                state = state.copy(
                    loading = false,
                    error = "Couldn't get a position. Check that location is switched on, then try again."
                )
                return@launch
            }

            // Name the place first so the header can fill in while the network call runs.
            val name = location.placeName(fix)
            state = state.copy(placeName = name)

            try {
                val stops = repository.stopsWithin(fix.latitude, fix.longitude, radiusMetres)
                state = state.copy(
                    loading = false,
                    stops = stops,
                    // Street name missing? The nearest stop is a fine substitute.
                    placeName = name ?: stops.firstOrNull()?.name,
                    error = if (stops.isEmpty()) "No stops within ${radiusMetres / 1000} km." else null
                )
            } catch (e: IOException) {
                state = state.copy(loading = false, error = "No connection. Try again when you're back online.")
            } catch (e: Exception) {
                state = state.copy(loading = false, error = "Trafiklab didn't answer. Try again in a moment.")
            }
        }
    }
}
