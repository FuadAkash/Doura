package com.akash.doura.splash

/*
 * Doura splash.
 *
 * Just the runner mark pulsing gently on the app background — same feel as
 * the rest of the app, no illustration to jar against the dark UI.
 *
 * How it fits together:
 *   1. The system splash (Theme.Doura.Starting) shows the same mark on the
 *      same background from process start. Compose can't draw during that
 *      window, so the system splash covers it.
 *   2. When Compose is ready, this composable takes over and pulses the
 *      logo for ~1.4 seconds, then fades to HomeScreen.
 *   3. Colours match on both sides, so the handoff is invisible.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.doura.R
import kotlinx.coroutines.delay

private val SplashBackground = Color(0xFF000000)   // Doura.Background
private const val SPLASH_MS = 1400L

@Composable
fun DouraSplash(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    // Two synced pulses — scale (bip) and alpha (bip). ~700ms per beat, so
    // the user sees roughly two beats before the splash fades out.
    val pulse = rememberInfiniteTransition(label = "pulse")

    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(SPLASH_MS)
        visible = false
        delay(280)        // let the fade finish before switching screens
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(280))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
                .clickable(
                    // Tap to skip. No ripple, so the pulse stays clean.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { visible = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .scale(scale)
                        .alpha(alpha)
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    "Doura",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Aldrig missa bussen igen",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
