package com.akash.doura.nearby

/*
 * Drop-in replacement for the static "Nearest station" branch in HomeScreen.
 *
 * In MainActivity.kt:
 *   1. delete the `data class NearbyStation` and the `nearbyStations` list
 *   2. delete the old NearestHeader() and StationCard() composables
 *   3. inside HomeScreen, above Scaffold:
 *          val nearbyViewModel: NearbyViewModel = viewModel()
 *          val permissionLauncher = rememberLocationPermissionLauncher(nearbyViewModel)
 *   4. replace the `} else {` branch of the LazyColumn with:
 *          nearestStationSection(
 *              state = nearbyViewModel.state,
 *              onLocate = { permissionLauncher() },
 *              onStopPicked = { stop -> from = stop.name; mode = SearchMode.ROUTE }
 *          )
 *
 * Doura.* colours and TypeGlyph() are reused from MainActivity.kt — make them
 * internal rather than private there, or move this file into the same package.
 */

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.Doura
import com.akash.doura.TypeGlyph
import com.akash.doura.data.NearbyStop

/** Asks for location permission if needed, then refreshes. Returns a callable. */
@Composable
fun rememberLocationPermissionLauncher(viewModel: NearbyViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) viewModel.refresh() else viewModel.onPermissionDenied()
    }

    return {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

fun LazyListScope.nearestStationSection(
    state: NearbyUiState,
    onLocate: () -> Unit,
    onStopPicked: (NearbyStop) -> Unit
) {
    item {
        LocateButton(
            label = state.placeName?.let { "Near $it" } ?: "Use my location",
            loading = state.loading,
            onClick = onLocate
        )
    }

    if (state.needsPermission) {
        item {
            Text(
                "Doura needs your location to find stops around you. Your position is sent to Trafiklab to look up nearby stops and isn't stored.",
                color = Doura.TextMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp)
            )
        }
    }

    state.error?.let { message ->
        item {
            Text(
                message,
                color = Doura.TextMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp)
            )
        }
    }

    items(state.stops, key = { it.id }) { stop ->
        NearbyStopCard(stop) { onStopPicked(stop) }
    }
}

@Composable
private fun LocateButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Doura.Green, RoundedCornerShape(16.dp))
            .background(Doura.GreenDeep.copy(alpha = 0.35f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Doura.Green,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(Icons.Default.MyLocation, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (loading) "Finding stops around you" else label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NearbyStopCard(stop: NearbyStop, onClick: () -> Unit) {
    Surface(
        color = Doura.Card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Doura.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stop.types.forEach { TypeGlyph(it, Doura.TextMuted, 16.dp) }
                }
            }
            Text(
                if (stop.metres < 1000) "${stop.metres} m" else "%.1f km".format(stop.metres / 1000.0),
                color = Doura.Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
