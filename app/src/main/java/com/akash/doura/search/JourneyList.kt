package com.akash.doura.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.Doura
import com.akash.doura.TypeGlyph
import com.akash.doura.data.Journey
import com.akash.doura.data.JourneyLeg
import com.akash.doura.data.LegKind

private val Amber = Color(0xFFE0A22B)

fun LazyListScope.journeySection(
    state: JourneyState,
    onLoadMore: () -> Unit,
    onOpen: (Journey) -> Unit
) {
    if (state.loading) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Doura.Green, strokeWidth = 2.dp)
            }
        }
        return
    }

    state.error?.let { message ->
        item {
            Text(
                message,
                color = Doura.TextMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 24.dp)
            )
        }
    }

    if (state.journeys.isEmpty()) return

    item {
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${state.fromName} → ${state.toName}",
                color = Doura.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${state.journeys.size} options", color = Doura.TextMuted, fontSize = 13.sp)
        }
    }

    items(state.journeys, key = { it.key }) { journey ->
        JourneyCard(journey) { onOpen(journey) }
    }

    item {
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onLoadMore,
            enabled = !state.loadingMore,
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
            if (state.loadingMore) {
                CircularProgressIndicator(color = Doura.Green, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text("Show later journeys", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            "Traffic data from Trafiklab.se",
            color = Doura.TextMuted.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun JourneyCard(journey: Journey, onClick: () -> Unit) {
    Surface(
        color = Doura.Card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Doura.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    journey.departTime,
                    color = if (journey.delayMinutes > 0) Amber else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f)
                        .height(1.dp)
                        .background(Doura.FieldBorder)
                )
                Text(
                    journey.arriveTime,
                    color = Doura.Green,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                journey.legs.forEachIndexed { index, leg ->
                    if (index > 0) {
                        Icon(
                            Icons.Default.ArrowForward,
                            null,
                            tint = Doura.TextMuted,
                            modifier = Modifier
                                .padding(horizontal = 5.dp)
                                .size(12.dp)
                        )
                    }
                    LegChip(leg)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(formatDuration(journey.durationMinutes))
                        append(" · ")
                        append(if (journey.changes == 0) "Direct" else "${journey.changes} change")
                        journey.originTrack?.let { append(" · Läge $it") }
                    },
                    color = Doura.TextMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (journey.delayMinutes > 0) {
                    Text(
                        "${journey.delayMinutes} min late",
                        color = Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Details", color = Doura.Green, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LegChip(leg: JourneyLeg) {
    if (leg.kind == LegKind.WALK) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Doura.Field)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DirectionsWalk, null, tint = Doura.TextMuted, modifier = Modifier.size(13.dp))
            if (leg.minutes > 0) {
                Spacer(Modifier.width(4.dp))
                Text("${leg.minutes}", color = Doura.TextMuted, fontSize = 11.5.sp)
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Doura.GreenDeep)
            .border(1.dp, Doura.Green, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leg.mode?.let {
            TypeGlyph(it, Color.White, 13.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            leg.line ?: "—",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}