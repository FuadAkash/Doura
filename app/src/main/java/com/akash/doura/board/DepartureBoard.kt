package com.akash.doura.board

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akash.doura.Doura
import com.akash.doura.TransportType
import com.akash.doura.TypeGlyph
import com.akash.doura.data.DepartureRepository
import com.akash.doura.data.StopDeparture
import kotlinx.coroutines.launch
import java.io.IOException

/* ----------------------------------------------------------------------------
 * State
 * -------------------------------------------------------------------------- */

data class BoardUiState(
    val areaId: String? = null,
    val stopName: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val departures: List<StopDeparture> = emptyList(),
    val modes: List<TransportType> = emptyList(),
    val filter: TransportType? = null,
    val nextQueryTime: String? = null,
    val coveredUntil: String? = null,
    val error: String? = null
) {
    val visible: List<StopDeparture>
        get() = filter?.let { mode -> departures.filter { it.mode == mode } } ?: departures
}

class DepartureBoardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DepartureRepository()

    var state by mutableStateOf(BoardUiState())
        private set

    fun open(areaId: String, stopName: String) {
        state = BoardUiState(areaId = areaId, stopName = stopName, loading = true)
        viewModelScope.launch { fetch(areaId, null, append = false) }
    }

    /** Each call walks the window forward another 60 minutes. */
    fun loadMore() {
        val areaId = state.areaId ?: return
        val time = state.nextQueryTime ?: return
        if (state.loadingMore) return
        state = state.copy(loadingMore = true)
        viewModelScope.launch { fetch(areaId, time, append = true) }
    }

    fun setFilter(mode: TransportType?) {
        state = state.copy(filter = mode)
    }

    fun close() {
        state = BoardUiState()
    }

    private suspend fun fetch(areaId: String, time: String?, append: Boolean) {
        try {
            val page = repository.page(areaId, time)
            val merged = if (append) {
                val seen = state.departures.map { it.key }.toSet()
                state.departures + page.departures.filterNot { it.key in seen }
            } else {
                page.departures
            }

            state = state.copy(
                loading = false,
                loadingMore = false,
                stopName = page.stopName ?: state.stopName,
                departures = merged,
                modes = if (append) state.modes else page.modes,
                nextQueryTime = page.nextQueryTime,
                coveredUntil = page.windowEnd,
                error = if (merged.isEmpty()) "No departures in the next hour." else null
            )
        } catch (e: IOException) {
            state = state.copy(loading = false, loadingMore = false, error = "No connection.")
        } catch (e: Exception) {
            state = state.copy(loading = false, loadingMore = false, error = "Couldn't load departures.")
        }
    }
}

/* ----------------------------------------------------------------------------
 * Sheet
 * -------------------------------------------------------------------------- */

private val Amber = Color(0xFFE0A22B)
private val Red = Color(0xFFE05C5C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartureBoardSheet(
    state: BoardUiState,
    onDismiss: () -> Unit,
    onFilter: (TransportType?) -> Unit,
    onLoadMore: () -> Unit,
    onPlanFromHere: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BoardHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.stopName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.coveredUntil?.let { "Departures until $it" } ?: "Departures",
                        color = Doura.TextMuted,
                        fontSize = 12.5.sp
                    )
                }
                Text(
                    "Plan from here",
                    color = Doura.Green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPlanFromHere)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            if (state.modes.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill("All", state.filter == null) { onFilter(null) }
                    state.modes.forEach { mode ->
                        FilterPill(mode.label, state.filter == mode) { onFilter(mode) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Doura.Green, strokeWidth = 2.dp)
                    }
                }

                state.visible.isEmpty() -> {
                    Text(
                        state.error ?: "Nothing scheduled in this hour.",
                        color = Doura.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 28.dp)
                    )
                    if (state.nextQueryTime != null) {
                        LaterButton(loading = state.loadingMore, onClick = onLoadMore)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.visible, key = { it.key }) { departure ->
                            DepartureRow(departure)
                        }

                        item {
                            Spacer(Modifier.height(10.dp))
                            if (state.nextQueryTime != null) {
                                LaterButton(loading = state.loadingMore, onClick = onLoadMore)
                            } else {
                                Text(
                                    "That's the last departure today.",
                                    color = Doura.TextMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 14.dp)
                                )
                            }
                            Text(
                                "Traffic data from Trafiklab.se",
                                color = Doura.TextMuted.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(d: StopDeparture) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Doura.GreenDeep)
                .border(1.dp, Doura.Green, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TypeGlyph(d.mode, Color.White, 15.dp)
            Spacer(Modifier.width(7.dp))
            Text(d.line, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                d.destination,
                color = if (d.canceled) Doura.TextMuted else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.canceled) TextDecoration.LineThrough else null
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val detail = buildString {
                    d.platform?.let { append("Plats $it") }
                    if (d.platform != null && d.stopName != null) append(" · ")
                    d.stopName?.let { append(it) }
                }
                if (detail.isNotBlank()) {
                    Text(detail, color = Doura.TextMuted, fontSize = 12.sp, maxLines = 1)
                }
                if (d.alert != null) {
                    if (detail.isNotBlank()) Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Warning, null, tint = Amber, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            when {
                d.canceled -> Text("Cancelled", color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                d.minutesFromNow != null && d.minutesFromNow <= 20 -> Text(
                    if (d.minutesFromNow <= 0) "Now" else "${d.minutesFromNow} min",
                    color = if (d.isDelayed) Amber else Doura.Green,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                else -> Text(
                    d.expectedTime,
                    color = if (d.isDelayed) Amber else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (d.isDelayed && !d.canceled) {
                Text(
                    d.scheduledTime,
                    color = Doura.TextMuted,
                    fontSize = 11.5.sp,
                    textDecoration = TextDecoration.LineThrough
                )
            } else if (!d.isRealtime && !d.canceled) {
                Text("Timetable", color = Doura.TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Doura.Green else Doura.Field)
            .border(1.dp, if (selected) Doura.Green else Doura.FieldBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun LaterButton(loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Doura.GreenDeep,
            contentColor = Color.White,
            disabledContainerColor = Doura.GreenDeep,
            disabledContentColor = Doura.TextMuted
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Doura.Green, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("Show later departures", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BoardHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Doura.FieldBorder)
        )
    }
}
