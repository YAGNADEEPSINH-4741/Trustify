package com.safecallai.app.ui.call

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.ContactInfo
import com.safecallai.app.service.CallState
import com.safecallai.app.service.CallUiState
import com.safecallai.app.ui.components.ClassificationBadge
import com.safecallai.app.ui.components.VerificationStatusChip
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.SpamAmber

@Composable
fun IncomingCallScreen(
    uiState: CallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (uiState.callState) {
            CallState.RINGING -> {
                RingingCallView(
                    uiState = uiState,
                    onAnswer = onAnswer,
                    onDecline = onDecline
                )
            }
            CallState.DIALING, CallState.ACTIVE -> {
                ActiveCallView(
                    uiState = uiState,
                    onHangUp = onHangUp,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker
                )
            }
            CallState.DISCONNECTED -> {
                DisconnectedCallView(uiState = uiState)
            }
            CallState.IDLE -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active call",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RingingCallView(
    uiState: CallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    val contact = uiState.contactInfo
    val result = uiState.classificationResult

    val primaryTitle = when {
        contact.isSavedContact && !contact.name.isNullOrBlank() -> contact.name
        result?.callerName != null -> result.callerName
        result?.businessName != null -> result.businessName
        else -> "Unknown Caller"
    }

    val subtitle = when {
        contact.isSavedContact -> "Saved in Phone Contacts"
        result?.falseSpamRecovered == true -> "★ False Spam Recovered"
        result?.classification == CallerClassification.VERIFIED_LEGITIMATE -> "Verified Business Entity"
        result?.classification == CallerClassification.LIKELY_LEGITIMATE -> "Likely Legitimate Caller"
        result?.classification == CallerClassification.POTENTIAL_SPAM -> "Reported Suspicious"
        result?.classification == CallerClassification.HIGH_RISK -> "Critical Scam / Fraud Alert"
        else -> "Unverified Number"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Incoming Call Tag
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (result?.classification) {
                            CallerClassification.HIGH_RISK -> HighRiskRed.copy(alpha = 0.2f)
                            CallerClassification.POTENTIAL_SPAM -> SpamAmber.copy(alpha = 0.2f)
                            CallerClassification.VERIFIED_LEGITIMATE -> LegitGreen.copy(alpha = 0.2f)
                            else -> BrandBlue.copy(alpha = 0.15f)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (uiState.isSimulated) "SIMULATED INCOMING CALL" else "INCOMING CALL",
                    color = when (result?.classification) {
                        CallerClassification.HIGH_RISK -> HighRiskRed
                        CallerClassification.POTENTIAL_SPAM -> SpamAmber
                        CallerClassification.VERIFIED_LEGITIMATE -> LegitGreen
                        else -> BrandBlue
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Avatar / Contact Photo
            ContactAvatar(
                contact = contact,
                classification = result?.classification ?: CallerClassification.UNKNOWN
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Caller Name
            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = when (result?.classification) {
                    CallerClassification.HIGH_RISK -> HighRiskRed
                    CallerClassification.POTENTIAL_SPAM -> SpamAmber
                    CallerClassification.VERIFIED_LEGITIMATE -> LegitGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Phone Number
            Text(
                text = uiState.displayNumber.ifEmpty { uiState.rawNumber },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SafeCall AI Evidence & Classification Card
        if (result != null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (result.classification) {
                        CallerClassification.HIGH_RISK -> HighRiskRed.copy(alpha = 0.12f)
                        CallerClassification.POTENTIAL_SPAM -> SpamAmber.copy(alpha = 0.12f)
                        CallerClassification.VERIFIED_LEGITIMATE -> LegitGreen.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClassificationBadge(classification = result.classification)
                        VerificationStatusChip(status = result.verificationStatus)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    val primaryEvidence = result.evidenceList.firstOrNull()
                    if (primaryEvidence != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• ${primaryEvidence.title}: ${primaryEvidence.description}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (result.warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = SpamAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = result.warnings.first().message,
                                fontSize = 11.sp,
                                color = SpamAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Call Action Buttons: Decline (Red) and Answer (Green)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onDecline,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = HighRiskRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(76.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Answer Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onAnswer,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LegitGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(76.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer Call",
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Answer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ActiveCallView(
    uiState: CallUiState,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val contact = uiState.contactInfo
    val result = uiState.classificationResult

    val primaryTitle = when {
        contact.isSavedContact && !contact.name.isNullOrBlank() -> contact.name
        result?.callerName != null -> result.callerName
        result?.businessName != null -> result.businessName
        else -> "Unknown Caller"
    }

    val minutes = uiState.durationSeconds / 60
    val seconds = uiState.durationSeconds % 60
    val durationText = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            val isDialing = uiState.callState == CallState.DIALING
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDialing) BrandBlue.copy(alpha = 0.15f)
                        else LegitGreen.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isDialing) "CALLING..." else "CALL IN PROGRESS",
                    color = if (isDialing) BrandBlue else LegitGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isDialing && uiState.durationSeconds == 0L) "Connecting..." else durationText,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContactAvatar(
                contact = contact,
                classification = result?.classification ?: CallerClassification.UNKNOWN
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = uiState.displayNumber.ifEmpty { uiState.rawNumber },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Mid in-call tools: Mute, Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mute Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onToggleMute,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isMuted) HighRiskRed else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isMuted) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isMuted) "Muted" else "Mute",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Speaker Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onToggleSpeaker,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isSpeakerOn) BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isSpeakerOn) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isSpeakerOn) "Speaker On" else "Speaker",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // End Call Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 28.dp)
        ) {
            FilledIconButton(
                onClick = onHangUp,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = HighRiskRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "End Call",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DisconnectedCallView(uiState: CallUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = null,
                tint = HighRiskRed,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Call Ended",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = uiState.displayNumber.ifEmpty { uiState.rawNumber },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactAvatar(
    contact: ContactInfo,
    classification: CallerClassification,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(contact.photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(contact.photoUri) {
        val uri = contact.photoUri
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        } else {
            bitmap = null
        }
    }

    val avatarColor = when (classification) {
        CallerClassification.VERIFIED_LEGITIMATE -> LegitGreen
        CallerClassification.LIKELY_LEGITIMATE -> BrandBlue
        CallerClassification.POTENTIAL_SPAM -> SpamAmber
        CallerClassification.HIGH_RISK -> HighRiskRed
        else -> Color(0xFF64748B)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Contact Photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(96.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (contact.isSavedContact && !contact.name.isNullOrBlank()) {
                val initial = contact.name.trim().first().uppercaseChar()
                Text(
                    text = initial.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            } else if (classification == CallerClassification.VERIFIED_LEGITIMATE) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Verified Shield",
                    tint = avatarColor,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Caller",
                    tint = avatarColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
