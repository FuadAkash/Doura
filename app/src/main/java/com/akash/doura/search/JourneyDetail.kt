package com.akash.doura.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.Doura
import com.akash.doura.TypeGlyph
import com.akash.doura.data.Journey
import com.akash.doura.data.JourneyLeg
import com.akash.doura.data.LegKind
import com.akash.doura.data.ticketHints

private val Amber = Color(0xFFE0A22B)
private val GUTTER = 34.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailSheet(
    journey: Journey,
    platforms: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { System.currentTimeMillis() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Handle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Header(journey)
            Spacer(Modifier.height(18.dp))
            TicketRow(journey)
            Spacer(Modifier.height(20.dp))

            journey.legs.forEachIndexed { index, leg ->
                val nextLeg = journey.legs.getOrNull(index + 1)
                LegBlock(
                    leg = leg,
                    isLastLeg = index == journey.legs.lastIndex,
                    nextLeg = nextLeg,
                    platform = platforms[index],
                    now = now
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Positions are estimated from the timetable, not from the vehicle itself.",
                color = Doura.TextMuted.copy(alpha = 0.8f),
                fontSize = 11.5.sp
            )
            Text(
                "Traffic data from Trafiklab.se",
                color = Doura.TextMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )
        }
    }
}

/* ----------------------------------------------------------------------------
 * Header + tickets
 * -------------------------------------------------------------------------- */

@Composable
private fun Header(journey: Journey) {
    Column {
        Text(
            "${journey.originName} → ${journey.destinationName}",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("${journey.departTime} – ${journey.arriveTime}")
                append(" · ")
                append(formatDuration(journey.durationMinutes))
                append(" · ")
                append(if (journey.changes == 0) "Direct" else "${journey.changes} change")
            },
            color = Doura.TextMuted,
            fontSize = 13.5.sp
        )
    }
}

@Composable
private fun TicketRow(journey: Journey) {
    val hints = ticketHints(journey)
    if (hints.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Doura.Field)
            .border(1.dp, Doura.FieldBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ConfirmationNumber,
                null,
                tint = Doura.Green,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                if (hints.size == 1) "Ticket" else "Tickets",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hints.forEach { hint ->
                Text(
                    hint.authority,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Doura.GreenDeep)
                        .border(1.dp, Doura.Green, RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        Text(
            if (hints.size > 1) {
                "This journey crosses more than one authority. Check whether a through-ticket covers it, or buy one per operator."
            } else {
                "Valid in ${hints.first().note ?: hints.first().authority}. Buy in the operator's own app."
            },
            color = Doura.TextMuted,
            fontSize = 12.sp
        )
    }
}

/* ----------------------------------------------------------------------------
 * One leg of the journey
 * -------------------------------------------------------------------------- */

@Composable
private fun LegBlock(
    leg: JourneyLeg,
    isLastLeg: Boolean,
    nextLeg: JourneyLeg?,
    platform: String?,
    now: Long
) {
    var expanded by remember { mutableStateOf(false) }

    val intermediate = leg.stops.drop(1).dropLast(1)
    val inProgress = leg.departMillis != null && leg.arriveMillis != null &&
            now in leg.departMillis!!..leg.arriveMillis!!
    val passedIndex = if (inProgress) {
        leg.stops.indexOfLast { it.millis != null && it.millis!! <= now }
    } else -1

    if (leg.kind == LegKind.WALK) {
        TimelineRow(
            gutter = { Gutter(top = true, bottom = true, style = DotStyle.WALK) },
            content = {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsWalk,
                            null,
                            tint = Doura.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (leg.minutes > 0) "Walk ${formatDuration(leg.minutes)}" else "Change on foot",
                            color = Doura.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (leg.fromName.isNotBlank() && leg.fromName != leg.toName) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${leg.fromName} → ${leg.toName}",
                            color = Doura.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        )
        return
    }

    // Boarding stop
    TimelineRow(
        gutter = { Gutter(top = false, bottom = true, style = DotStyle.MAJOR) },
        content = {
            StopLine(
                name = leg.fromName,
                time = leg.departTime,
                bold = true,
                detail = (platform ?: leg.fromTrack)?.let { "${platformWord(leg)} $it" },
                highlightDetail = true
            )
        }
    )

    // The vehicle itself
    TimelineRow(
        gutter = { Gutter(top = true, bottom = true, style = DotStyle.NONE) },
        content = {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Doura.GreenDeep)
                            .border(1.dp, Doura.Green, RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        leg.mode?.let {
                            TypeGlyph(it, Color.White, 14.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            leg.line ?: "—",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "mot ${leg.direction ?: leg.toName}",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                leg.operator?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "$it · ${formatDuration(leg.minutes)}",
                        color = Doura.TextMuted,
                        fontSize = 12.sp
                    )
                }

                if (intermediate.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            if (expanded) "Hide stops" else "${intermediate.size} stops on the way",
                            color = Doura.Green,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            null,
                            tint = Doura.Green,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    )

    // Intermediate stops
    if (expanded) {
        intermediate.forEachIndexed { index, stop ->
            val stopIndexInLeg = index + 1
            val isHere = inProgress && stopIndexInLeg == passedIndex
            TimelineRow(
                gutter = {
                    Gutter(
                        top = true,
                        bottom = true,
                        style = if (isHere) DotStyle.HERE else DotStyle.MINOR
                    )
                },
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 7.dp)
                    ) {
                        Text(
                            stop.name,
                            color = if (isHere) Color.White else Doura.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (isHere) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isHere) {
                            Text(
                                "around here now",
                                color = Amber,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                        Text(stop.time, color = Doura.TextMuted, fontSize = 12.5.sp)
                    }
                }
            )
        }
    }

    // Alighting stop
    val changing = !isLastLeg
    TimelineRow(
        gutter = { Gutter(top = true, bottom = !isLastLeg, style = DotStyle.MAJOR) },
        content = {
            Column {
                StopLine(
                    name = leg.toName,
                    time = leg.arriveTime,
                    bold = true,
                    detail = if (isLastLeg) "Get off here" else null
                )
                if (changing) {
                    val wait = connectionMinutes(leg, nextLeg)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            null,
                            tint = Amber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (wait != null) {
                                "Change here · ${formatDuration(wait)} to connect"
                            } else "Change here",
                            color = Amber,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    )
}

private fun connectionMinutes(leg: JourneyLeg, next: JourneyLeg?): Int? {
    val arrive = leg.arriveMillis ?: return null
    val depart = next?.departMillis ?: return null
    return ((depart - arrive) / 60_000L).toInt().takeIf { it >= 0 }
}

/** Swedish signage differs by mode: buses have stands, trains tracks. */
private fun platformWord(leg: JourneyLeg): String = when (leg.mode) {
    com.akash.doura.TransportType.TRAIN -> "Spår"
    com.akash.doura.TransportType.BUS -> "Läge"
    com.akash.doura.TransportType.FERRY -> "Kaj"
    else -> "Plattform"
}

@Composable
private fun StopLine(
    name: String,
    time: String,
    bold: Boolean,
    detail: String?,
    highlightDetail: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    color = if (highlightDetail) Doura.Green else Doura.TextMuted,
                    fontSize = 12.5.sp,
                    fontWeight = if (highlightDetail) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = if (highlightDetail) {
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Doura.Field)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else Modifier
                )
            }
        }
        Text(time, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/* ----------------------------------------------------------------------------
 * The o---o---o line
 * -------------------------------------------------------------------------- */

private enum class DotStyle { MAJOR, MINOR, HERE, WALK, NONE }

@Composable
private fun TimelineRow(
    gutter: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        gutter()
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Gutter(top: Boolean, bottom: Boolean, style: DotStyle) {
    val lineColour = Doura.FieldBorder

    Box(
        modifier = Modifier
            .width(GUTTER)
            .fillMaxHeight()
            .heightIn(min = 26.dp)
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val stroke = 2.dp.toPx()
                if (top) {
                    drawLine(lineColour, Offset(cx, 0f), Offset(cx, cy), strokeWidth = stroke)
                }
                if (bottom) {
                    drawLine(lineColour, Offset(cx, cy), Offset(cx, size.height), strokeWidth = stroke)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            DotStyle.MAJOR -> Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Doura.Sheet)
                    .border(2.5.dp, Doura.Green, CircleShape)
            )

            DotStyle.MINOR -> Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Doura.Sheet)
                    .border(1.5.dp, Doura.FieldBorder, CircleShape)
            )

            DotStyle.HERE -> Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Doura.Sheet)
                    .border(2.dp, Amber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Amber)
                )
            }

            DotStyle.WALK -> Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Doura.FieldBorder)
            )

            DotStyle.NONE -> Unit
        }
    }
}

@Composable
private fun Handle() {
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