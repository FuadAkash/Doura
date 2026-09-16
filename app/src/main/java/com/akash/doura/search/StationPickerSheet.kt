package com.akash.doura.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.Doura
import com.akash.doura.TypeGlyph
import com.akash.doura.data.StationHit
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPickerSheet(
    title: String,
    state: StationSearchState,
    locating: Boolean,
    onQueryChange: (String) -> Unit,
    onUseMyLocation: () -> Unit,
    onPick: (StationHit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember2()

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { PickerHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search any stop in Sweden", color = Doura.TextMuted, fontSize = 15.sp) },
                leadingIcon = {
                    if (state.loading) {
                        CircularProgressIndicator(
                            color = Doura.Green,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Doura.TextMuted,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onQueryChange("") }
                                .padding(8.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Doura.Field,
                    unfocusedContainerColor = Doura.Field,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Doura.Green,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Doura.FieldBorder, RoundedCornerShape(14.dp))
                    .focusRequester(focusRequester)
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.query.isBlank()) {
                    item {
                        PickerRow(
                            leading = {
                                if (locating) {
                                    CircularProgressIndicator(
                                        color = Doura.Green,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        null,
                                        tint = Doura.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    if (locating) "Finding your nearest stop" else "Use my location",
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            subtitle = "Nearest stop to you",
                            onClick = onUseMyLocation
                        )
                    }

                    if (state.recents.isNotEmpty()) {
                        item {
                            Text(
                                "Recent",
                                color = Doura.TextMuted,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(state.recents, key = { "recent-" + it.id }) { hit ->
                            PickerRow(
                                leading = {
                                    Icon(
                                        Icons.Default.History,
                                        null,
                                        tint = Doura.TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        hit.name,
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                subtitle = hit.modes.joinToString(" · ") { it.label },
                                modes = hit.modes,
                                onClick = { onPick(hit) }
                            )
                        }
                    }
                } else if (state.error != null) {
                    item {
                        Text(
                            state.error,
                            color = Doura.TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp)
                        )
                    }
                } else if (state.results.isEmpty() && !state.loading && state.query.trim().length >= 2) {
                    item {
                        Text(
                            "Nothing found. Swedish spelling helps — try Göteborg rather than Gothenburg.",
                            color = Doura.TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp)
                        )
                    }
                } else {
                    items(state.results, key = { it.id }) { hit ->
                        PickerRow(
                            leading = {
                                hit.modes.firstOrNull()?.let { TypeGlyph(it, Doura.TextMuted, 20.dp) }
                            },
                            title = { Highlighted(hit.name, state.query) },
                            subtitle = if (hit.isMetaStop) "All stops in the area" else
                                hit.modes.joinToString(" · ") { it.label },
                            modes = hit.modes,
                            onClick = { onPick(hit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    subtitle: String,
    modes: List<com.akash.doura.TransportType> = emptyList(),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Doura.Field),
            contentAlignment = Alignment.Center
        ) { leading() }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            title()
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Doura.TextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (modes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.take(3).forEach { TypeGlyph(it, Doura.TextMuted, 15.dp) }
            }
        }
    }
}

@Composable
private fun Highlighted(name: String, query: String) {
    val q = query.trim()
    val index = name.lowercase(Locale.getDefault()).indexOf(q.lowercase(Locale.getDefault()))
    val text = if (q.isEmpty() || index < 0) {
        buildAnnotatedString { append(name) }
    } else {
        buildAnnotatedString {
            append(name.substring(0, index))
            withStyle(SpanStyle(color = Doura.Green, fontWeight = FontWeight.Bold)) {
                append(name.substring(index, index + q.length))
            }
            append(name.substring(index + q.length))
        }
    }
    Text(text, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable
private fun PickerHandle() {
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
private fun remember2(): FocusRequester =
    androidx.compose.runtime.remember { FocusRequester() }
