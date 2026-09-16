package com.akash.doura

/*
 * Screen background: a repeating image tinted down with a dark fade so the
 * green cards and white text still read cleanly.
 *
 * The image (res/drawable/screen_bg.png, 407x612) is small and repeats to
 * fill any device. On top sits a translucent black wash — 78% by default,
 * which leaves the pattern faintly visible without letting it fight the UI.
 *
 * Modals stay untouched because bottom sheets and dialogs paint their own
 * container over this background; wrap only full-screen surfaces.
 *
 * Usage:
 *
 *   ScreenBackground {
 *       Scaffold(...) { inner -> ... }
 *   }
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun ScreenBackground(
    fadeAlpha: Float = 0.78f,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Doura.Background)) {
        Image(
            painter = painterResource(id = R.drawable.screen_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fadeAlpha.coerceIn(0f, 1f)))
        )
        content()
    }
}
