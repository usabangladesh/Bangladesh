package com.rashed.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rashed.ai.live.SessionStatus

@Composable
fun StatusIndicator(
    status: SessionStatus,
    isMicrophoneActive: Boolean,
    isCameraActive: Boolean,
    sessionDurationSeconds: Long,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (status) {
        SessionStatus.Disconnected -> "Disconnected" to Color(0xFF94A3B8)
        SessionStatus.Connecting -> "Connecting..." to Color(0xFF38BDF8)
        SessionStatus.Listening -> "Listening" to Color(0xFF00E5FF)
        SessionStatus.Thinking -> "Thinking..." to Color(0xFFA855F7)
        SessionStatus.Executing -> "Executing Action..." to Color(0xFFF59E0B)
        SessionStatus.Speaking -> "Speaking" to Color(0xFF10B981)
        SessionStatus.CameraActive -> "Camera Vision Active" to Color(0xFF00E5FF)
        SessionStatus.Error -> "Connection Error" to Color(0xFFEF4444)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = statusLabel,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (sessionDurationSeconds > 0) {
                val minutes = sessionDurationSeconds / 60
                val seconds = sessionDurationSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        // Mic Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isMicrophoneActive) Color(0xFF064E3B).copy(alpha = 0.9f)
                    else Color(0xFF1E293B).copy(alpha = 0.85f)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isMicrophoneActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Microphone Status",
                tint = if (isMicrophoneActive) Color(0xFF34D399) else Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
        }

        // Camera Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isCameraActive) Color(0xFF0C4A6E).copy(alpha = 0.9f)
                    else Color(0xFF1E293B).copy(alpha = 0.85f)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = "Camera Status",
                tint = if (isCameraActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
