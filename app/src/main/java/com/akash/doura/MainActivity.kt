package com.akash.doura

/*
 * Doura — home screen
 *
 * Required Gradle dependencies (module build.gradle.kts):
 *   implementation(platform("androidx.compose:compose-bom:2024.09.00"))
 *   implementation("androidx.compose.material3:material3")
 *   implementation("androidx.compose.material:material-icons-extended")
 *   implementation("androidx.activity:activity-compose:1.9.2")
 *
 * Station search, nearby stops, departures and route planning all come from
 * Trafiklab. Because those APIs are nationwide, SL and UL (and everyone else)
 * are covered by the same calls — there is no per-operator dataset to maintain.
 */

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.akash.doura.splash.DouraSplash
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.doura.about.AboutSheet
import com.akash.doura.board.BoardUiState
import com.akash.doura.board.DepartureBoardSheet
import com.akash.doura.board.DepartureBoardViewModel
import com.akash.doura.nearby.NearbyUiState
import com.akash.doura.nearby.NearbyViewModel
import com.akash.doura.nearby.nearestStationSection
import com.akash.doura.nearby.rememberLocationPermissionLauncher
import com.akash.doura.search.JourneyViewModel
import com.akash.doura.search.StationPickerSheet
import com.akash.doura.search.StationSearchState
import com.akash.doura.search.StationSearchViewModel
import com.akash.doura.search.JourneyState
import com.akash.doura.search.journeySection
import com.akash.doura.search.JourneyDetailSheet
import com.akash.doura.search.JourneyDetailViewModel
import com.akash.doura.data.Journey
import com.akash.doura.ui.theme.DouraTheme
import java.util.Calendar
import java.util.Locale

/* ----------------------------------------------------------------------------
 * Palette
 * -------------------------------------------------------------------------- */

object Doura {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF0C0F0C)
    val CardBorder = Color(0xFF1B241D)
    val Field = Color(0xFF10291B)
    val FieldBorder = Color(0xFF1E5537)
    val Green = Color(0xFF27C46B)
    val GreenDeep = Color(0xFF0F3A23)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF8FA396)
    val Sheet = Color(0xFF0A0D0A)
}

private val DouraColors = darkColorScheme(
    primary = Doura.Green,
    onPrimary = Color.White,
    background = Doura.Background,
    onBackground = Doura.TextPrimary,
    surface = Doura.Card,
    onSurface = Doura.TextPrimary,
    surfaceVariant = Doura.Field,
    onSurfaceVariant = Doura.TextPrimary,
    outline = Doura.FieldBorder
)

/* ----------------------------------------------------------------------------
 * Model
 * -------------------------------------------------------------------------- */

enum class SearchMode { ROUTE, NEAREST }

enum class PickerTarget { FROM, TO }

enum class TransportType(val label: String, val icon: ImageVector?) {
    TRAIN("Train", Icons.Default.Train),
    METRO("Metro", null),               // drawn as the rounded T badge
    BUS("Bus", Icons.Default.DirectionsBus),
    TRAM("Tram", Icons.Default.Tram),
    FERRY("Ferry", Icons.Default.DirectionsBoat)
}

/* ----------------------------------------------------------------------------
 * Activity
 * -------------------------------------------------------------------------- */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run BEFORE super.onCreate. This is also what swaps the
        // activity from Theme.Doura.Starting onto postSplashScreenTheme —
        // skip it and the splash theme's action bar sticks around.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        setContent {
            DouraTheme {
                MaterialTheme(colorScheme = DouraColors) {
                    var showSplash by remember { mutableStateOf(true) }

                    if (showSplash) {
                        DouraSplash(onFinished = { showSplash = false })
                    } else {
                        HomeScreen()
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------------
 * Home screen
 * -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() = ScreenBackground {
    val nowMinutes = remember { nowInMinutes() }

    var mode by remember { mutableStateOf(SearchMode.ROUTE) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }

    var departAt by remember { mutableStateOf(nowMinutes) }
    var dayOffset by remember { mutableStateOf(0) }
    var usingNow by remember { mutableStateOf(true) }

    var selectedTypes by remember { mutableStateOf(emptySet<TransportType>()) } // empty = all
    var fewerChanges by remember { mutableStateOf(false) }

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showTypeSheet by remember { mutableStateOf(false) }

    // Set when the picker's "Use my location" is tapped, cleared once a stop lands.
    var fillFromLocation by remember { mutableStateOf(false) }

    // viewModel() needs a ViewModelStoreOwner, which @Preview does not provide.
    val nearbyViewModel: NearbyViewModel? =
        if (LocalInspectionMode.current) null else viewModel()
    val nearbyState = nearbyViewModel?.state ?: NearbyUiState()
    val locate: () -> Unit =
        if (nearbyViewModel != null) rememberLocationPermissionLauncher(nearbyViewModel) else ({ })

    val boardViewModel: DepartureBoardViewModel? =
        if (LocalInspectionMode.current) null else viewModel()
    val boardState = boardViewModel?.state ?: BoardUiState()

    val stationViewModel: StationSearchViewModel? =
        if (LocalInspectionMode.current) null else viewModel()
    val stationState = stationViewModel?.state ?: StationSearchState()

    val journeyViewModel: JourneyViewModel? =
        if (LocalInspectionMode.current) null else viewModel()
    val journeyState = journeyViewModel?.state ?: JourneyState()
    var openJourney by remember { mutableStateOf<Journey?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    // "Use my location" inside the picker: wait for the nearby result, take the
    // closest stop, and drop it into whichever field is open.
    LaunchedEffect(nearbyState.stops, fillFromLocation) {
        if (fillFromLocation) {
            nearbyState.stops.firstOrNull()?.let { nearest ->
                if (picker == PickerTarget.TO) {
                    to = nearest.name
                    toId = nearest.id
                } else {
                    from = nearest.name
                    fromId = nearest.id
                }
                fillFromLocation = false
                picker = null
                stationViewModel?.clear()
            }
        }
    }

    // Load once when the tab is opened, not on every recomposition.
    LaunchedEffect(mode) {
        if (mode == SearchMode.NEAREST && nearbyState.stops.isEmpty()) locate()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Doura",
                        color = Doura.Green,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "About Doura",
                        tint = Doura.TextMuted,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { showAbout = true }
                            .padding(9.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // A scrim rather than full transparency: the background
                    // image still shows through, but list items scrolling
                    // underneath don't read through the title.
                    containerColor = Color.Black.copy(alpha = 0.82f),
                    scrolledContainerColor = Color.Black.copy(alpha = 0.82f),
                    titleContentColor = Doura.Green
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ModeSwitch(
                    mode = mode,
                    onModeChange = { mode = it }
                )
            }

            if (mode == SearchMode.ROUTE) {
                item {
                    RouteCard(
                        from = from,
                        to = to,
                        onPickFrom = { picker = PickerTarget.FROM },
                        onPickTo = { picker = PickerTarget.TO },
                        onClearFrom = { from = ""; fromId = null },
                        onClearTo = { to = ""; toId = null },
                        onSwap = {
                            val oldName = from
                            val oldId = fromId
                            from = to
                            fromId = toId
                            to = oldName
                            toId = oldId
                        }
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            icon = Icons.Default.Schedule,
                            label = if (usingNow) "Leave now" else "${dayLabel(dayOffset)}, ${formatTime(departAt)}",
                            modifier = Modifier.weight(1f),
                            onClick = { showTimeSheet = true }
                        )
                        PillButton(
                            icon = null,
                            iconContent = { TypeGlyph(TransportType.TRAIN, Doura.TextPrimary, 18.dp) },
                            label = typeSummary(selectedTypes),
                            modifier = Modifier.weight(1f),
                            onClick = { showTypeSheet = true }
                        )
                    }
                }

                item {
                    val anythingToClear = from.isNotBlank() || to.isNotBlank() ||
                            selectedTypes.isNotEmpty() || fewerChanges || !usingNow || journeyState.searched

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val origin = fromId
                                val destination = toId
                                if (origin != null && destination != null) {
                                    journeyViewModel?.search(
                                        originId = origin,
                                        destId = destination,
                                        originName = from,
                                        destName = to,
                                        departMillis = departureMillis(dayOffset, departAt),
                                        types = selectedTypes,
                                        maxChange = if (fewerChanges) 1 else null
                                    )
                                }
                            },
                            enabled = fromId != null && toId != null && !journeyState.loading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Doura.Green,
                                contentColor = Color.White,
                                disabledContainerColor = Doura.GreenDeep,
                                disabledContentColor = Color.White.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Search", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (anythingToClear) {
                            Box(
                                modifier = Modifier
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Doura.Green, RoundedCornerShape(16.dp))
                                    .clickable {
                                        from = ""
                                        to = ""
                                        fromId = null
                                        toId = null
                                        selectedTypes = emptySet()
                                        fewerChanges = false
                                        departAt = nowInMinutes()
                                        dayOffset = 0
                                        usingNow = true
                                        openJourney = null
                                        journeyViewModel?.reset()
                                    }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Clear",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (!journeyState.searched) {
                    item {
                        Text(
                            "Pick a start and a destination to see live journeys across SL, UL and the rest of Sweden.",
                            color = Doura.TextMuted,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    journeySection(
                        state = journeyState,
                        onLoadMore = { journeyViewModel?.loadMore() },
                        onOpen = { openJourney = it }
                    )
                }
            } else {
                nearestStationSection(
                    state = nearbyState,
                    onLocate = locate,
                    onStopPicked = { stop -> boardViewModel?.open(stop.id, stop.name) }
                )
            }
        }
    }

    picker?.let { target ->
        StationPickerSheet(
            title = if (target == PickerTarget.FROM) "Where are you starting?" else "Where are you going?",
            state = stationState,
            locating = fillFromLocation && nearbyState.loading,
            onQueryChange = { stationViewModel?.onQueryChange(it) },
            onUseMyLocation = {
                fillFromLocation = true
                locate()
            },
            onPick = { hit ->
                if (target == PickerTarget.FROM) {
                    from = hit.name
                    fromId = hit.id
                } else {
                    to = hit.name
                    toId = hit.id
                }
                stationViewModel?.remember(hit)
                stationViewModel?.clear()
                picker = null
            },
            onDismiss = {
                fillFromLocation = false
                stationViewModel?.clear()
                picker = null
            }
        )
    }

    if (showTimeSheet) {
        TimeSheet(
            initialMinutes = departAt,
            initialDayOffset = dayOffset,
            onDismiss = { showTimeSheet = false },
            onNow = {
                departAt = nowInMinutes()
                dayOffset = 0
                usingNow = true
                showTimeSheet = false
            },
            onConfirm = { minutes, day ->
                departAt = minutes
                dayOffset = day
                usingNow = false
                showTimeSheet = false
            }
        )
    }

    if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
    }

    openJourney?.let { journey ->
        val detailViewModel: JourneyDetailViewModel? =
            if (LocalInspectionMode.current) null else viewModel()
        LaunchedEffect(journey.key) { detailViewModel?.load(journey) }

        JourneyDetailSheet(
            journey = journey,
            platforms = detailViewModel?.platforms ?: emptyMap(),
            onDismiss = { openJourney = null }
        )
    }

    if (boardViewModel != null && boardState.areaId != null) {
        DepartureBoardSheet(
            state = boardState,
            onDismiss = { boardViewModel.close() },
            onFilter = { boardViewModel.setFilter(it) },
            onLoadMore = { boardViewModel.loadMore() },
            onPlanFromHere = {
                from = boardState.stopName
                fromId = boardState.areaId
                mode = SearchMode.ROUTE
                boardViewModel.close()
            }
        )
    }

    if (showTypeSheet) {
        TypeSheet(
            selected = selectedTypes,
            fewerChanges = fewerChanges,
            onDismiss = { showTypeSheet = false },
            onChange = { selectedTypes = it },
            onFewerChanges = { fewerChanges = it }
        )
    }
}

/* ----------------------------------------------------------------------------
 * Top mode switch
 * -------------------------------------------------------------------------- */

@Composable
private fun ModeSwitch(mode: SearchMode, onModeChange: (SearchMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Doura.Card)
            .border(1.dp, Doura.CardBorder, RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ModeTab("Select route", Icons.Default.Place, mode == SearchMode.ROUTE, Modifier.weight(1f)) {
            onModeChange(SearchMode.ROUTE)
        }
        ModeTab("Nearest station", Icons.Default.NearMe, mode == SearchMode.NEAREST, Modifier.weight(1f)) {
            onModeChange(SearchMode.NEAREST)
        }
    }
}

@Composable
private fun ModeTab(
    text: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Doura.Green else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color.White else Doura.TextMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = if (active) Color.White else Doura.TextMuted,
            fontSize = 13.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/* ----------------------------------------------------------------------------
 * From / To card — tapping a field opens the station picker
 * -------------------------------------------------------------------------- */

@Composable
private fun RouteCard(
    from: String,
    to: String,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
    onSwap: () -> Unit
) {
    var swapped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (swapped) 180f else 0f,
        animationSpec = tween(300),
        label = "swap"
    )

    Surface(
        color = Doura.Card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Doura.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StationSlot(
                    value = from,
                    placeholder = "From",
                    icon = Icons.Default.RadioButtonUnchecked,
                    onClick = onPickFrom,
                    onClear = onClearFrom
                )
                StationSlot(
                    value = to,
                    placeholder = "To",
                    icon = Icons.Default.Place,
                    onClick = onPickTo,
                    onClear = onClearTo
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Doura.GreenDeep)
                    .border(1.dp, Doura.Green, CircleShape)
                    .clickable {
                        onSwap()
                        swapped = !swapped
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Swap start and destination",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun StationSlot(
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Doura.Field)
            .border(1.dp, Doura.FieldBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.ifBlank { placeholder },
            color = if (value.isBlank()) Doura.TextMuted else Color.White,
            fontSize = 15.sp,
            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotBlank()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear $placeholder",
                tint = Doura.TextMuted,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(7.dp)
            )
        }
    }
}

/* ----------------------------------------------------------------------------
 * Filter pills
 * -------------------------------------------------------------------------- */

@Composable
private fun PillButton(
    icon: ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Doura.Green, RoundedCornerShape(16.dp))
            .background(Doura.GreenDeep.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        } else {
            iconContent?.invoke()
        }
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.KeyboardArrowDown, null, tint = Doura.TextMuted, modifier = Modifier.size(18.dp))
    }
}

/* ----------------------------------------------------------------------------
 * Time sheet — round clock dial, with a keyboard fallback
 * -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(
    initialMinutes: Int,
    initialDayOffset: Int,
    onDismiss: () -> Unit,
    onNow: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var day by remember { mutableStateOf(initialDayOffset) }
    var showCalendar by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }

    val timeState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )

    val clockColors = TimePickerDefaults.colors(
        clockDialColor = Doura.Field,
        clockDialSelectedContentColor = Color.White,
        clockDialUnselectedContentColor = Color.White,
        selectorColor = Doura.Green,
        containerColor = Doura.Sheet,
        timeSelectorSelectedContainerColor = Doura.GreenDeep,
        timeSelectorUnselectedContainerColor = Doura.Field,
        timeSelectorSelectedContentColor = Color.White,
        timeSelectorUnselectedContentColor = Doura.TextMuted
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "When do you want to leave?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (typing) Icons.Default.Schedule else Icons.Default.Keyboard,
                    contentDescription = if (typing) "Use the clock dial" else "Type the time",
                    tint = Doura.TextMuted,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { typing = !typing }
                        .padding(9.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0..6).forEach { offset ->
                    DayChip(dayLabel(offset), day == offset) { day = offset }
                }
                DayChip("Other date", false, Icons.Default.CalendarMonth) { showCalendar = true }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (typing) {
                    TimeInput(state = timeState, colors = clockColors)
                } else {
                    TimePicker(state = timeState, colors = clockColors)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedAction("Leave now", Modifier.weight(1f), onNow)
                Button(
                    onClick = { onConfirm(timeState.hour * 60 + timeState.minute, day) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Doura.Green, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text("Set time", fontWeight = FontWeight.SemiBold) }
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    if (showCalendar) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            colors = DatePickerDefaults.colors(containerColor = Doura.Sheet),
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { day = daysFromToday(it) }
                    showCalendar = false
                }) { Text("Choose", color = Doura.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) {
                    Text("Cancel", color = Doura.TextMuted)
                }
            }
        ) {
            DatePicker(
                state = dateState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Doura.Sheet,
                    selectedDayContainerColor = Doura.Green,
                    selectedDayContentColor = Color.White,
                    todayContentColor = Doura.Green,
                    todayDateBorderColor = Doura.Green,
                    dayContentColor = Doura.TextPrimary,
                    weekdayContentColor = Doura.TextMuted
                )
            )
        }
    }
}

@Composable
private fun DayChip(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Doura.Green else Doura.Field)
            .border(1.dp, if (selected) Doura.Green else Doura.FieldBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

/* ----------------------------------------------------------------------------
 * Transport type sheet
 * -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(
    selected: Set<TransportType>,
    fewerChanges: Boolean,
    onDismiss: () -> Unit,
    onChange: (Set<TransportType>) -> Unit,
    onFewerChanges: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            SheetTitle("Travel with")

            TypeRow(
                label = "All from this station",
                selected = selected.isEmpty(),
                glyph = { Icon(Icons.Default.MyLocation, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                onClick = { onChange(emptySet()) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(Doura.CardBorder)
            )

            TransportType.values().forEach { type ->
                TypeRow(
                    label = type.label,
                    selected = type in selected,
                    glyph = { TypeGlyph(type, Color.White, 20.dp) },
                    onClick = { onChange(if (type in selected) selected - type else selected + type) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(Doura.CardBorder)
            )

            TypeRow(
                label = "At most one change",
                selected = fewerChanges,
                glyph = {
                    Icon(
                        Icons.Default.SwapVert,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { onFewerChanges(!fewerChanges) }
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Doura.Green, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Apply", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TypeRow(
    label: String,
    selected: Boolean,
    glyph: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Doura.Field else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        glyph()
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Doura.Green, modifier = Modifier.size(20.dp))
        }
    }
}

/** Metro gets the rounded-square T badge; everything else uses its icon. */
@Composable
internal fun TypeGlyph(type: TransportType, tint: Color, size: Dp) {
    if (type == TransportType.METRO) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(Doura.Green),
            contentAlignment = Alignment.Center
        ) {
            Text("T", color = Color.White, fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Icon(type.icon!!, null, tint = tint, modifier = Modifier.size(size))
    }
}

/* ----------------------------------------------------------------------------
 * Small shared pieces
 * -------------------------------------------------------------------------- */

@Composable
private fun SheetHandle() {
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

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun OutlinedAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Doura.Green, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

/* ----------------------------------------------------------------------------
 * Static data + helpers (replace with the real repository later)
 * -------------------------------------------------------------------------- */

private fun nowInMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun formatTime(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", (minutes / 60) % 24, minutes % 60)

private fun dayLabel(offset: Int): String = when (offset) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        "${days[c.get(Calendar.DAY_OF_WEEK) - 1]} ${c.get(Calendar.DAY_OF_MONTH)}"
    }
}

private fun daysFromToday(millis: Long): Int {
    val day = 24 * 60 * 60 * 1000L
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return (((millis - today) / day).toInt()).coerceAtLeast(0)
}

/** Turns the picked day offset + minute-of-day into an epoch timestamp. */
private fun departureMillis(dayOffset: Int, minutesOfDay: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        set(Calendar.MINUTE, minutesOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun typeSummary(selected: Set<TransportType>): String = when {
    selected.isEmpty() -> "All types"
    selected.size == 1 -> selected.first().label
    else -> "${selected.size} types"
}

/* ----------------------------------------------------------------------------
 * Preview
 * -------------------------------------------------------------------------- */

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
fun HomeScreenPreview() {
    MaterialTheme(colorScheme = DouraColors) {
        HomeScreen()
    }
}