package com.akash.doura

/*
 * Floating bottom bar + the two extra pages it opens:
 *   - Set journey: alarm for a departure on a chosen date (30 / 15 / 5 min + now)
 *   - History: previously searched routes; tapping one re-runs it from "now"
 */

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.reminders.HistoryEntry
import com.akash.doura.reminders.PLANNED_OFFSETS
import com.akash.doura.reminders.Reminder
import com.akash.doura.reminders.ReminderScheduler
import com.akash.doura.reminders.ReminderStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Default.Search),
    SET("Set journey", Icons.Default.Alarm),
    HISTORY("History", Icons.Default.History)
}

/* ----------------------------------------------------------------------------
 * Floating bar
 * -------------------------------------------------------------------------- */

@Composable
fun DouraBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xF2080B08))
            .border(1.dp, Doura.FieldBorder, RoundedCornerShape(30.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppTab.values().forEach { tab ->
            val active = tab == selected
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (active) Doura.GreenDeep else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label,
                    tint = if (active) Doura.Green else Doura.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    tab.label,
                    color = if (active) Doura.Green else Doura.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------------
 * Notification permission gate (Android 13+)
 * -------------------------------------------------------------------------- */

/**
 * Returns a function that runs [action] once notifications are allowed to be
 * asked about. The action runs either way: alarms can be stored even if the
 * user declines, and we tell them nothing will show.
 */
@Composable
fun rememberNotifyGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Notifications are off — reminders won't show.", Toast.LENGTH_LONG).show()
        }
        pending?.invoke()
        pending = null
    }
    return { action ->
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needs) {
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
}

/* ----------------------------------------------------------------------------
 * Set journey
 * -------------------------------------------------------------------------- */

@Composable
fun SetJourneyPage(
    from: String,
    to: String,
    dayOffset: Int,
    minutes: Int,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
    onSwap: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifyGate = rememberNotifyGate()
    val store = remember { ReminderStore(context) }
    var upcoming by remember { mutableStateOf(store.all()) }
    var error by remember { mutableStateOf<String?>(null) }

    val departMillis = departureMillis(dayOffset, minutes)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Set a journey alarm", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a route and the day you leave. You'll get a notification 30, 15 and 5 minutes before, and again at departure.",
                color = Doura.TextMuted, fontSize = 13.sp
            )
        }
        item {
            RouteCard(
                from = from, to = to,
                onPickFrom = onPickFrom, onPickTo = onPickTo,
                onClearFrom = onClearFrom, onClearTo = onClearTo, onSwap = onSwap
            )
        }
        item {
            PillButton(
                icon = Icons.Default.Schedule,
                label = "${dayLabel(dayOffset)}, ${formatTime(minutes)}",
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickTime
            )
        }
        if (!ReminderScheduler.canScheduleExact(context)) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Doura.Card)
                        .border(1.dp, Doura.CardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "Allow exact alarms so reminders arrive on the minute. Without it they can be a few minutes late.",
                        color = Doura.TextMuted, fontSize = 12.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open settings",
                        color = Doura.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }
                    )
                }
            }
        }
        item {
            error?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = {
                    if (departMillis <= System.currentTimeMillis()) {
                        error = "That time has already passed — pick a later one."
                    } else {
                        error = null
                        notifyGate {
                            val reminder = Reminder(
                                id = ReminderScheduler.newId(),
                                originName = from, destName = to,
                                departMillis = departMillis, offsets = PLANNED_OFFSETS
                            )
                            ReminderScheduler.schedule(context, reminder)
                            upcoming = store.all()
                            Toast.makeText(context, "Alarm set", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = from.isNotBlank() && to.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Doura.Green, contentColor = Color.White,
                    disabledContainerColor = Doura.GreenDeep,
                    disabledContentColor = Color.White.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Alarm, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Set alarm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (upcoming.isNotEmpty()) {
            item {
                Text("Upcoming alarms", color = Doura.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            items(upcoming, key = { it.id }) { r ->
                ReminderRow(r) {
                    ReminderScheduler.cancel(context, r)
                    upcoming = store.all()
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, onDelete: () -> Unit) {
    val whenText = remember(r.departMillis) {
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(r.departMillis))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Doura.Card)
            .border(1.dp, Doura.CardBorder, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${r.originName} → ${r.destName}", color = Color.White, fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "$whenText · ${r.offsets.filter { it > 0 }.joinToString(", ")} min + now",
                color = Doura.TextMuted, fontSize = 12.sp
            )
        }
        Icon(
            Icons.Default.Close, "Delete alarm", tint = Doura.TextMuted,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDelete).padding(10.dp)
        )
    }
}

/* ----------------------------------------------------------------------------
 * History
 * -------------------------------------------------------------------------- */

@Composable
fun HistoryPage(
    entries: List<HistoryEntry>,
    onPick: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Previous journeys", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap one to see departures from right now.",
                        color = Doura.TextMuted, fontSize = 13.sp
                    )
                }
                if (entries.isNotEmpty()) {
                    Text(
                        "Clear", color = Doura.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClearAll).padding(8.dp)
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Routes you search will show up here.",
                    color = Doura.TextMuted, fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 20.dp)
                )
            }
        }
        items(entries, key = { "${it.originId}>${it.destId}" }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Doura.Card)
                    .border(1.dp, Doura.CardBorder, RoundedCornerShape(16.dp))
                    .clickable { onPick(e) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, null, tint = Doura.Green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.originName, color = Color.White, fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "→ ${e.destName}", color = Doura.TextMuted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.Close, "Remove", tint = Doura.TextMuted,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onDelete(e) }.padding(10.dp)
                )
            }
        }
    }
}
