package com.rashed.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rashed.ai.live.SessionStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * Large voice-first interactive AI Orb.
 * Renders distinct dynamic visual waveforms and energy halos mapped directly to SessionStatus.
 */
@Composable
fun AIOrb(
    status: SessionStatus,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing)
        ),
        label = "Rotation"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleAlpha"
    )

    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleScale"
    )

    // Palette mapped to state
    val (primaryColor, secondaryColor, glowColor) = when (status) {
        SessionStatus.Disconnected -> Triple(
            Color(0xFF334155),
            Color(0xFF1E293B),
            Color(0x33475569)
        )
        SessionStatus.Connecting -> Triple(
            Color(0xFF38BDF8),
            Color(0xFF6366F1),
            Color(0x6638BDF8)
        )
        SessionStatus.Listening -> Triple(
            Color(0xFF06B6D4),
            Color(0xFF3B82F6),
            Color(0x8806B6D4)
        )
        SessionStatus.Thinking -> Triple(
            Color(0xFFA855F7),
            Color(0xFFEC4899),
            Color(0x88A855F7)
        )
        SessionStatus.Executing -> Triple(
            Color(0xFFF59E0B),
            Color(0xFFEAB308),
            Color(0x88F59E0B)
        )
        SessionStatus.Speaking -> Triple(
            Color(0xFF10B981),
            Color(0xFF06B6D4),
            Color(0x9910B981)
        )
        SessionStatus.CameraActive -> Triple(
            Color(0xFF00E5FF),
            Color(0xFF2979FF),
            Color(0x9900E5FF)
        )
        SessionStatus.Error -> Triple(
            Color(0xFFEF4444),
            Color(0xFFB91C1C),
            Color(0x88EF4444)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 3.4f
            val audioScaleBoost = (audioLevel * 0.45f).coerceIn(0f, 0.5f)
            val dynamicRadius = baseRadius * pulse * (1f + audioScaleBoost)

            // Outer ripple for active speech/listening
            if (status == SessionStatus.Listening || status == SessionStatus.Speaking) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = rippleAlpha), Color.Transparent),
                        center = center,
                        radius = dynamicRadius * rippleScale
                    ),
                    center = center,
                    radius = dynamicRadius * rippleScale
                )
            }

            // Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = dynamicRadius * 1.55f
                ),
                center = center,
                radius = dynamicRadius * 1.55f
            )

            // Main Core Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (status == SessionStatus.Disconnected) 0.3f else 0.85f),
                        primaryColor,
                        secondaryColor
                    ),
                    center = Offset(center.x - dynamicRadius * 0.2f, center.y - dynamicRadius * 0.2f),
                    radius = dynamicRadius
                ),
                center = center,
                radius = dynamicRadius
            )

            // Dynamic Orbital Rings
            val ringCount = 3
            for (i in 0 until ringCount) {
                val ringAngle = (rotation + i * 120f) * (Math.PI / 180f)
                val ringOffset = Offset(
                    center.x + (cos(ringAngle) * 6f).toFloat(),
                    center.y + (sin(ringAngle) * 6f).toFloat()
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.4f - (i * 0.1f)),
                    center = ringOffset,
                    radius = dynamicRadius + (i * 12.dp.toPx() * (1f + audioLevel * 0.5f)),
                    style = Stroke(width = (2.5f - i * 0.5f).dp.toPx())
                )
            }
        }
    }
}
