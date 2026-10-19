package com.safecallai.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.ui.components.TrustifyBrandMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quiet Premium Startup Animation for Trustify.
 *
 * Sequence (1.1 - 1.3 seconds):
 * 1. Clean theme background.
 * 2. Trustify shield/telephone identity mark fades in and scales smoothly from 0.92 to 1.0.
 * 3. A subtle single signal ring expands once and fades out.
 * 4. "TRUSTIFY" wordmark fades into place.
 * 5. Smooth handoff to main dashboard.
 */
@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit
) {
    val logoScale = remember { Animatable(0.92f) }
    val logoAlpha = remember { Animatable(0f) }
    val signalRadius = remember { Animatable(0f) }
    val signalAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(12f) }

    val accentColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    LaunchedEffect(Unit) {
        // Step 2: Mark fades in and scales from 0.92 to 1.0
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1.0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }

        delay(260L)

        // Step 3: Subtle signal ring appears once
        launch {
            signalAlpha.snapTo(0.35f)
            signalAlpha.animateTo(0f, animationSpec = tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            signalRadius.animateTo(52f, animationSpec = tween(520, easing = FastOutSlowInEasing))
        }

        delay(180L)

        // Step 4: Wordmark fades in
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            textOffsetY.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        }

        // Hold and complete (total ~1.15s)
        delay(420L)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Subtle single signal ring expanding once
        if (signalAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.46f)
                drawCircle(
                    color = accentColor.copy(alpha = signalAlpha.value),
                    radius = 48.dp.toPx() + signalRadius.value,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Identity Mark
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                TrustifyBrandMark(
                    size = 72.dp,
                    primaryColor = accentColor,
                    isPulsing = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trustify Wordmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset(0, textOffsetY.value.toInt()) }
                    .alpha(textAlpha.value)
            ) {
                Text(
                    text = "TRUSTIFY",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "CALL INTELLIGENCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
