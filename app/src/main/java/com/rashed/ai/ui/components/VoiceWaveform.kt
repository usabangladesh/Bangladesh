package com.rashed.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Animated real-time voice waveform bars reflecting audio energy levels.
 */
@Composable
fun VoiceWaveform(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    tintColor: Color = Color(0xFF00E5FF)
) {
    val barAmplitudes = remember(barCount) {
        List(barCount) { Animatable(0.12f) }
    }

    LaunchedEffect(audioLevel, isActive) {
        if (!isActive) {
            barAmplitudes.forEach { anim ->
                anim.animateTo(0.08f, tween(300, easing = FastOutSlowInEasing))
            }
        } else {
            barAmplitudes.forEachIndexed { index, anim ->
                // Variation across bars to create realistic sound wave frequency bands
                val distanceFromCenter = kotlin.math.abs(index - barCount / 2f) / (barCount / 2f)
                val shapeFactor = 1f - (distanceFromCenter * 0.5f)
                val noise = Random.nextFloat() * 0.35f + 0.65f
                val targetHeight = (audioLevel * shapeFactor * noise).coerceIn(0.1f, 1f)

                anim.animateTo(
                    targetValue = targetHeight,
                    animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val totalSpacing = width * 0.3f
        val barWidth = (width - totalSpacing) / barCount
        val barSpacing = totalSpacing / (barCount - 1)

        val gradient = Brush.verticalGradient(
            colors = listOf(
                tintColor,
                tintColor.copy(alpha = 0.5f),
                Color(0xFF3B82F6)
            )
        )

        for (i in 0 until barCount) {
            val barAmp = barAmplitudes[i].value
            val currentBarHeight = (height * barAmp).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + barSpacing)
            val y = (height - currentBarHeight) / 2f

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
