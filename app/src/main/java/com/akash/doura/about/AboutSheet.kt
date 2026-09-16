package com.akash.doura.about

/*
 * About / credits sheet.
 *
 * Trafiklab's data is CC-BY licensed, which requires visible attribution.
 * The journey list and departure board carry a one-line credit; this sheet
 * is the full version, with the licence named and a link out.
 *
 * Change DEVELOPER below to your own name.
 */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.BuildConfig
import com.akash.doura.Doura
import com.akash.doura.R

private const val DEVELOPER = "Muhammed Arif Fuad Akash"
private const val DEVELOPER_INSTITUTE = "Uppsala University"
private const val LINKEDIN = "https://www.linkedin.com/in/arif-fuad-akash/"
private const val TRAFIKLAB_URL = "https://www.trafiklab.se"
private const val LICENCE_URL = "https://creativecommons.org/licenses/by/4.0/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Doura.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Handle() }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // App identity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Doura.Field),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Doura",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        color = Doura.TextMuted,
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Journey planning across Swedish public transport — SL, UL and every " +
                        "other operator in one search.",
                color = Doura.TextMuted,
                fontSize = 13.5.sp
            )

            Spacer(Modifier.height(24.dp))

            SectionTitle("Data")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Doura.Field)
                    .border(1.dp, Doura.FieldBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "Traffic data from Trafiklab.se",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Timetables, realtime departures and journey planning are provided " +
                            "by Trafiklab, operated by Samtrafiken i Sverige AB. Data covers " +
                            "all Swedish public transport operators.",
                    color = Doura.TextMuted,
                    fontSize = 12.5.sp
                )
                Spacer(Modifier.height(14.dp))
                LinkRow("trafiklab.se") { open(TRAFIKLAB_URL) }
                Spacer(Modifier.height(8.dp))
                LinkRow("Licensed under CC BY 4.0") { open(LICENCE_URL) }
            }

            Spacer(Modifier.height(22.dp))

            SectionTitle("Built by")

            Text(
                DEVELOPER,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(2.dp))

            Text(
                DEVELOPER_INSTITUTE,
                color = Doura.TextMuted,
                fontSize = 12.5.sp
            )

            Spacer(Modifier.height(10.dp))

            LinkRow("LinkedIn") { open(LINKEDIN) }

            Spacer(Modifier.height(22.dp))

            SectionTitle("Note")

            Text(
                "Vehicle positions shown on a journey are estimated from the timetable, " +
                        "not from the vehicle itself. Ticket information names the operators " +
                        "a journey touches — buy tickets in their own apps.",
                color = Doura.TextMuted,
                fontSize = 12.5.sp
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Doura.Green,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp)
    ) {
        Text(label, color = Doura.Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = null,
            tint = Doura.Green,
            modifier = Modifier.size(13.dp)
        )
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