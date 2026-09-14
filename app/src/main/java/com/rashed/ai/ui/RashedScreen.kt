package com.rashed.ai.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rashed.ai.camera.CameraLens
import com.rashed.ai.live.SessionStatus
import com.rashed.ai.ui.components.AIOrb
import com.rashed.ai.ui.components.StatusIndicator
import com.rashed.ai.ui.components.VoiceWaveform
import com.rashed.ai.viewmodel.RashedViewModel
import com.rashed.ai.viewmodel.ScreenDestination

@Composable
fun RashedScreen(
    viewModel: RashedViewModel,
    modifier: Modifier = Modifier
) {
    val liveState by viewModel.liveState.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF090D16)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Subtle futuristic background glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF090D16),
                                Color(0xFF05070D)
                            )
                        )
                    )
            )

            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header: Title & Action icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RASHED AI",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Gemini Live Assistant",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.navigateTo(ScreenDestination.PERMISSIONS) },
                            modifier = Modifier
                                .testTag("btn_permissions")
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permissions",
                                tint = if (permissionState.isCoreReady) Color(0xFF38BDF8) else Color(0xFFEF4444)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.navigateTo(ScreenDestination.SETTINGS) },
                            modifier = Modifier
                                .testTag("btn_settings")
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Status Chips
                StatusIndicator(
                    status = liveState.status,
                    isMicrophoneActive = liveState.isMicrophoneActive,
                    isCameraActive = cameraState.isStreaming,
                    sessionDurationSeconds = liveState.sessionDurationSeconds,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Error Notice Banner
                AnimatedVisibility(
                    visible = liveState.errorMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = liveState.errorMessage ?: "",
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.toggleLiveSession() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Centerpiece: Large Animated AI Orb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val activeAudioLevel = if (liveState.status == SessionStatus.Speaking) {
                        liveState.audioOutputLevel
                    } else {
                        liveState.audioInputLevel
                    }

                    AIOrb(
                        status = liveState.status,
                        audioLevel = activeAudioLevel,
                        size = 230.dp,
                        onClick = {
                            if (liveState.status == SessionStatus.Speaking) {
                                viewModel.interruptSpeech()
                            } else {
                                viewModel.toggleLiveSession()
                            }
                        },
                        modifier = Modifier.testTag("ai_orb")
                    )

                    // Floating Camera preview overlay if active
                    if (cameraState.isStreaming) {
                        Box(
                            modifier = Modifier
                                .size(130.dp, 170.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        previewViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Camera controls inside preview
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { viewModel.switchCameraLens(lifecycleOwner, previewViewRef) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "Switch Lens",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.toggleCamera(lifecycleOwner, null) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Status & Transcript Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusText = when (liveState.status) {
                        SessionStatus.Disconnected -> "Tap orb or mic to start"
                        SessionStatus.Connecting -> "Connecting to Gemini Live..."
                        SessionStatus.Listening -> "Listening..."
                        SessionStatus.Thinking -> "Thinking..."
                        SessionStatus.Executing -> liveState.lastActionReport.ifBlank { "Executing command..." }
                        SessionStatus.Speaking -> "Speaking..."
                        SessionStatus.CameraActive -> "Camera vision active"
                        SessionStatus.Error -> "Disconnected"
                    }

                    Text(
                        text = statusText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (liveState.status) {
                            SessionStatus.Listening -> Color(0xFF00E5FF)
                            SessionStatus.Speaking -> Color(0xFF10B981)
                            SessionStatus.Executing -> Color(0xFFF59E0B)
                            SessionStatus.Error -> Color(0xFFEF4444)
                            else -> Color(0xFF94A3B8)
                        },
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Voice Waveform animation
                    VoiceWaveform(
                        audioLevel = if (liveState.status == SessionStatus.Speaking) liveState.audioOutputLevel else liveState.audioInputLevel,
                        isActive = liveState.status == SessionStatus.Listening || liveState.status == SessionStatus.Speaking,
                        tintColor = if (liveState.status == SessionStatus.Speaking) Color(0xFF10B981) else Color(0xFF00E5FF)
                    )

                    // Transcript card when conversation exists
                    if (liveState.lastModelTranscript.isNotBlank() || liveState.lastActionReport.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp)
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (liveState.lastActionReport.isNotBlank()) {
                                    Text(
                                        text = "⚡ Action: ${liveState.lastActionReport}",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                if (liveState.lastModelTranscript.isNotBlank()) {
                                    Text(
                                        text = liveState.lastModelTranscript,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera Vision Button
                    FloatingActionButton(
                        onClick = { viewModel.toggleCamera(lifecycleOwner, previewViewRef) },
                        containerColor = if (cameraState.isStreaming) Color(0xFF0284C7) else Color(0xFF1E293B),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("fab_camera")
                    ) {
                        Icon(
                            imageVector = if (cameraState.isStreaming) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Camera",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Primary Voice Mic / Session Button
                    FloatingActionButton(
                        onClick = { viewModel.toggleLiveSession() },
                        containerColor = if (liveState.isSessionActive) Color(0xFFEF4444) else Color(0xFF00E5FF),
                        contentColor = if (liveState.isSessionActive) Color.White else Color.Black,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("fab_primary_session")
                    ) {
                        Icon(
                            imageVector = if (liveState.isSessionActive) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (liveState.isSessionActive) "Stop Session" else "Start Session",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Interrupt / Mute Button
                    FloatingActionButton(
                        onClick = {
                            if (liveState.isModelSpeaking) {
                                viewModel.interruptSpeech()
                            }
                        },
                        containerColor = if (liveState.isModelSpeaking) Color(0xFFF59E0B) else Color(0xFF1E293B),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("fab_interrupt")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Interrupt",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
