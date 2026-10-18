package com.safecallai.app.ui.call

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.CommunityClassification
import com.safecallai.app.domain.models.CommunityReputation
import com.safecallai.app.domain.models.ContactInfo
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.service.CallState
import com.safecallai.app.service.CallUiState
import com.safecallai.app.service.QuickReplyManager
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
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit = {},
    hasSmsPermission: Boolean = false,
    isSendingSms: Boolean = false,
    onRequestSmsPermission: (message: String) -> Unit = {},
    onSendQuickReply: (recipient: String, message: String) -> Unit = { _, _ -> },
    onLaunchMessagingFallback: (recipient: String, message: String) -> Unit = { _, _ -> },
    communityReputation: CommunityReputation? = null,
    isSubmittingVote: Boolean = false,
    submittedVoteResult: CommunityReputation? = null,
    userAlreadyVoted: Boolean = false,
    savedUserVote: String? = null,
    voteErrorMessage: String? = null,
    onVoteSpam: (phoneNumber: String) -> Unit = {},
    onVoteNotSpam: (phoneNumber: String) -> Unit = {},
    onRetryVote: () -> Unit = {},
    onDismissPostCallFeedback: () -> Unit = {}
) {
    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var pendingConfirmationMessage by remember { mutableStateOf<String?>(null) }
    var pendingDispatchText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (uiState.callState) {
                CallState.RINGING -> {
                    RingingCallView(
                        uiState = uiState,
                        onAnswer = onAnswer,
                        onDecline = onDecline,
                        onOpenQuickReply = { showQuickReplySheet = true }
                    )
                }
                CallState.DIALING, CallState.ACTIVE, CallState.HOLDING -> {
                    ActiveCallView(
                        uiState = uiState,
                        onHangUp = onHangUp,
                        onToggleMute = onToggleMute,
                        onToggleSpeaker = onToggleSpeaker,
                        onToggleHold = onToggleHold
                    )
                }
                CallState.DISCONNECTED -> {
                    DisconnectedCallView(
                        uiState = uiState,
                        communityReputation = communityReputation,
                        isSubmittingVote = isSubmittingVote,
                        submittedVoteResult = submittedVoteResult,
                        userAlreadyVoted = userAlreadyVoted,
                        savedUserVote = savedUserVote,
                        voteErrorMessage = voteErrorMessage,
                        onVoteSpam = onVoteSpam,
                        onVoteNotSpam = onVoteNotSpam,
                        onRetryVote = onRetryVote,
                        onDismissFeedback = onDismissPostCallFeedback
                    )
                }
                CallState.IDLE -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.call_no_active),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tap-to-dismiss background scrim when Quick Reply sheet is visible
        if (showQuickReplySheet && uiState.callState == CallState.RINGING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showQuickReplySheet = false }
            )
        }

        // In-window Animated Quick Reply Bottom Sheet
        AnimatedVisibility(
            visible = showQuickReplySheet && uiState.callState == CallState.RINGING,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            QuickReplySheet(
                uiState = uiState,
                isSendingSms = isSendingSms,
                onDismiss = { showQuickReplySheet = false },
                onRequestSend = { message ->
                    pendingConfirmationMessage = message
                }
            )
        }
    }

    // Confirmation dialog before sending quick reply and declining call
    if (pendingConfirmationMessage != null) {
        val messageToSend = pendingConfirmationMessage!!
        val isRecipientValid = QuickReplyManager.isValidRecipient(uiState.rawNumber)

        AlertDialog(
            onDismissRequest = { pendingConfirmationMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.call_send_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    val displayName = uiState.contactInfo.name ?: uiState.displayNumber.ifEmpty { uiState.rawNumber }
                    Text(
                        text = "To: $displayName (${uiState.displayNumber.ifEmpty { uiState.rawNumber }})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$messageToSend\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.call_send_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = messageToSend
                        pendingConfirmationMessage = null
                        showQuickReplySheet = false

                        if (!isRecipientValid) {
                            return@Button
                        }

                        if (hasSmsPermission) {
                            onSendQuickReply(uiState.rawNumber, msg)
                        } else {
                            pendingDispatchText = msg
                            showPermissionRationaleDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.call_send_sms_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmationMessage = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Permission rationale dialog if SEND_SMS permission is not yet granted
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.call_sms_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.call_sms_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        onRequestSmsPermission(pendingDispatchText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text(stringResource(R.string.call_grant_permission))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showPermissionRationaleDialog = false
                            onLaunchMessagingFallback(uiState.rawNumber, pendingDispatchText)
                        }
                    ) {
                        Text(stringResource(R.string.call_open_messaging))
                    }
                    TextButton(onClick = { showPermissionRationaleDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun RingingCallView(
    uiState: CallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onOpenQuickReply: () -> Unit
) {
    val scrollState = rememberScrollState()
    val contact = uiState.contactInfo
    val result = uiState.classificationResult
    val identity = uiState.callerIdentity

    val unknownCallerStr = stringResource(R.string.badge_unknown)
    val primaryTitle = when {
        contact.isSavedContact && !contact.name.isNullOrBlank() -> contact.name
        identity != null && identity.hasReliableName -> identity.displayName ?: ""
        result?.callerName != null -> result.callerName
        result?.businessName != null -> result.businessName
        else -> uiState.displayNumber.ifEmpty { uiState.rawNumber.ifEmpty { unknownCallerStr } }
    }

    val subtitle = when {
        contact.isSavedContact -> stringResource(R.string.identity_source_saved_contact)
        identity != null && identity.sourceLabel.isNotBlank() -> identity.sourceLabel
        result?.falseSpamRecovered == true -> stringResource(R.string.subtitle_false_spam_recovered)
        result?.classification == CallerClassification.VERIFIED_LEGITIMATE -> stringResource(R.string.subtitle_verified_business)
        result?.classification == CallerClassification.LIKELY_LEGITIMATE -> stringResource(R.string.subtitle_likely_legit)
        result?.classification == CallerClassification.POTENTIAL_SPAM -> stringResource(R.string.subtitle_reported_suspicious)
        result?.classification == CallerClassification.HIGH_RISK -> stringResource(R.string.subtitle_critical_scam)
        else -> stringResource(R.string.identity_source_no_match)
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
                    text = if (uiState.isSimulated) stringResource(R.string.call_simulated) else stringResource(R.string.call_incoming),
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

            // Subtitle / Source Label
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

            if (identity?.attribution != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = identity.attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Phone Number
            Text(
                text = uiState.displayNumber.ifEmpty { uiState.rawNumber },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trustify Evidence & Classification Card
        if (result != null || contact.isSavedContact) {
            val isSaved = contact.isSavedContact && !contact.name.isNullOrBlank()
            val effectiveClassification = when {
                isSaved && (result == null || result.classification == CallerClassification.UNKNOWN) -> CallerClassification.VERIFIED_LEGITIMATE
                else -> result?.classification ?: CallerClassification.VERIFIED_LEGITIMATE
            }
            val effectiveStatus = when {
                isSaved && (result == null || result.verificationStatus == VerificationStatus.UNVERIFIED) -> VerificationStatus.USER_TRUSTED
                else -> result?.verificationStatus ?: VerificationStatus.USER_TRUSTED
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (effectiveClassification) {
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
                        ClassificationBadge(classification = effectiveClassification)
                        VerificationStatusChip(status = effectiveStatus)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val explanationText = when {
                        isSaved && (result == null || result.warnings.isEmpty()) ->
                            stringResource(R.string.explanation_saved_contact)
                        result != null -> result.explanation
                        else -> stringResource(R.string.explanation_saved_contact)
                    }

                    Text(
                        text = explanationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    val primaryEvidence = result?.evidenceList?.firstOrNull()
                    if (primaryEvidence != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• ${primaryEvidence.title}: ${primaryEvidence.description}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (isSaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• ${stringResource(R.string.subtitle_saved_contact)}: ${contact.name}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (result?.warnings?.isNotEmpty() == true) {
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

        Spacer(modifier = Modifier.height(20.dp))

        // Actions Section: Quick Reply Pill Button + Decline & Answer Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Send Message / Quick Reply Action Button
            Surface(
                onClick = onOpenQuickReply,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = stringResource(R.string.call_send_message),
                        tint = BrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.call_send_message),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Call Action Buttons: Decline (Red) and Answer (Green)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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
                            contentDescription = stringResource(R.string.call_decline),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.call_decline),
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
                            contentDescription = stringResource(R.string.call_answer),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.call_answer),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Modern In-Window Quick Reply Bottom Sheet.
 * Safely runs over Android lockscreens without token permission errors.
 * Provides 6 predefined messages and a full-featured custom message composer.
 */
@Composable
private fun QuickReplySheet(
    uiState: CallUiState,
    isSendingSms: Boolean,
    onDismiss: () -> Unit,
    onRequestSend: (message: String) -> Unit
) {
    var isCustomMode by remember { mutableStateOf(false) }
    var customMessageText by remember { mutableStateOf("") }
    val isRecipientValid = QuickReplyManager.isValidRecipient(uiState.rawNumber)
    val contact = uiState.contactInfo

    val callerTitle = when {
        contact.isSavedContact && !contact.name.isNullOrBlank() -> contact.name
        else -> uiState.displayNumber.ifEmpty { uiState.rawNumber.ifEmpty { "Caller" } }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Drag indicator handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row: Navigation / Title / Dismiss Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCustomMode) {
                    IconButton(
                        onClick = { isCustomMode = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCustomMode) stringResource(R.string.call_write_custom_title) else stringResource(R.string.call_quick_reply_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "To: $callerTitle • ${uiState.displayNumber.ifEmpty { uiState.rawNumber }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Invalid / Unavailable Recipient Alert
            if (!isRecipientValid) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SpamAmber.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, SpamAmber.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SpamAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.call_invalid_recipient),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Mode 1: Write Custom Message View
            if (isCustomMode) {
                val smsInfo = QuickReplyManager.calculateSmsSegmentInfo(customMessageText)

                OutlinedTextField(
                    value = customMessageText,
                    onValueChange = { customMessageText = it },
                    placeholder = { Text(stringResource(R.string.call_custom_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 5,
                    enabled = !isSendingSms
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Character Counter and Segment Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (smsInfo.segmentCount <= 1) {
                            stringResource(R.string.call_chars_left, smsInfo.remainingInCurrentSegment)
                        } else {
                            stringResource(R.string.call_multi_segment, smsInfo.charCount, smsInfo.segmentCount, smsInfo.remainingInCurrentSegment)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (smsInfo.segmentCount > 1) SpamAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    if (smsInfo.isUnicode) {
                        Text(
                            text = stringResource(R.string.call_unicode_encoding),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Cancel and Send Message
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isCustomMode = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 10.dp),
                        enabled = !isSendingSms
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }

                    Button(
                        onClick = { onRequestSend(customMessageText.trim()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        enabled = customMessageText.isNotBlank() && isRecipientValid && !isSendingSms
                    ) {
                        if (isSendingSms) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.call_sending))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.call_send_message))
                        }
                    }
                }

            } else {
                // Mode 2: Predefined Quick Reply Messages List
                val scroll = rememberScrollState()
                val context = LocalContext.current
                val predefinedMessages = remember(context) { QuickReplyManager.getPredefinedMessages(context) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .verticalScroll(scroll)
                ) {
                    // The 6 Predefined Messages (Localized in current language)
                    predefinedMessages.forEach { message ->
                        Card(
                            onClick = {
                                if (isRecipientValid && !isSendingSms) {
                                    onRequestSend(message)
                                }
                            },
                            enabled = isRecipientValid && !isSendingSms,
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = stringResource(R.string.call_send_btn),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Write Your Own Message Button Card
                    Card(
                        onClick = { isCustomMode = true },
                        enabled = isRecipientValid && !isSendingSms,
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = BrandBlue.copy(alpha = 0.1f)
                        ),
                        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.call_write_custom_btn),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveCallView(
    uiState: CallUiState,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit
) {
    val contact = uiState.contactInfo
    val result = uiState.classificationResult
    val identity = uiState.callerIdentity

    val unknownCallerStr = stringResource(R.string.badge_unknown)
    val primaryTitle = when {
        contact.isSavedContact && !contact.name.isNullOrBlank() -> contact.name
        identity != null && identity.hasReliableName -> identity.displayName ?: ""
        result?.callerName != null -> result.callerName
        result?.businessName != null -> result.businessName
        else -> uiState.displayNumber.ifEmpty { uiState.rawNumber.ifEmpty { unknownCallerStr } }
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
            val isOnHold = uiState.isOnHold || uiState.callState == CallState.HOLDING
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isOnHold -> SpamAmber.copy(alpha = 0.2f)
                            isDialing -> BrandBlue.copy(alpha = 0.15f)
                            else -> LegitGreen.copy(alpha = 0.15f)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        isOnHold -> stringResource(R.string.call_holding)
                        isDialing -> stringResource(R.string.call_calling)
                        else -> stringResource(R.string.call_in_progress)
                    },
                    color = when {
                        isOnHold -> SpamAmber
                        isDialing -> BrandBlue
                        else -> LegitGreen
                    },
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

            val sourceLabel = when {
                contact.isSavedContact -> stringResource(R.string.identity_source_saved_contact)
                identity != null && identity.sourceLabel.isNotBlank() -> identity.sourceLabel
                else -> null
            }
            if (sourceLabel != null) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = uiState.displayNumber.ifEmpty { uiState.rawNumber },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Mid in-call tools: Mute, Hold (if supported), Speaker
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
                        contentDescription = stringResource(R.string.call_mute)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isMuted) stringResource(R.string.call_muted) else stringResource(R.string.call_mute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Hold / Resume Toggle (supported across active and holding calls)
            val showHold = uiState.canHold || uiState.callState == CallState.ACTIVE || uiState.callState == CallState.HOLDING || uiState.isSimulated
            if (showHold) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onToggleHold,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (uiState.isOnHold) SpamAmber else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (uiState.isOnHold) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (uiState.isOnHold) stringResource(R.string.call_resume) else stringResource(R.string.call_hold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isOnHold) stringResource(R.string.call_resume) else stringResource(R.string.call_hold),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        contentDescription = stringResource(R.string.call_speaker)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isSpeakerOn) stringResource(R.string.call_speaker_on) else stringResource(R.string.call_speaker),
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
                    contentDescription = stringResource(R.string.call_end),
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.call_end),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DisconnectedCallView(
    uiState: CallUiState,
    communityReputation: CommunityReputation?,
    isSubmittingVote: Boolean,
    submittedVoteResult: CommunityReputation?,
    userAlreadyVoted: Boolean = false,
    savedUserVote: String? = null,
    voteErrorMessage: String? = null,
    onVoteSpam: (String) -> Unit,
    onVoteNotSpam: (String) -> Unit,
    onRetryVote: () -> Unit = {},
    onDismissFeedback: () -> Unit
) {
    val isValidNumber = PhoneNumberNormalizer.isValid(uiState.rawNumber)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = null,
                tint = HighRiskRed,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.call_ended),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        val contactName = uiState.contactInfo.name
        val identityName = uiState.callerIdentity?.displayName
        val callerDisplay = contactName
            ?: identityName
            ?: uiState.displayNumber.ifEmpty { uiState.rawNumber }

        Text(
            text = callerDisplay,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val disconnectedSourceLabel = when {
            contactName != null -> stringResource(R.string.identity_source_saved_contact)
            uiState.callerIdentity?.sourceLabel?.isNotBlank() == true -> uiState.callerIdentity.sourceLabel
            else -> null
        }
        if (disconnectedSourceLabel != null) {
            Text(
                text = disconnectedSourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if ((contactName != null || identityName != null) && uiState.displayNumber.isNotEmpty()) {
            Text(
                text = uiState.displayNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isValidNumber) {
            // Post-Call Feedback Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (submittedVoteResult != null) {
                        // Success confirmation view — only confirmed after backend response
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (submittedVoteResult.classification == CommunityClassification.SPAM) HighRiskRed else LegitGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.community_report_recorded),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.community_spam_count_format,
                                submittedVoteResult.spamCount,
                                submittedVoteResult.threshold
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: ${stringResource(submittedVoteResult.classification.labelRes)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (submittedVoteResult.classification == CommunityClassification.SPAM) HighRiskRed else LegitGreen
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onDismissFeedback,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    } else if (voteErrorMessage != null) {
                        // Useful error display and retry option
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = HighRiskRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.community_vote_failed),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = voteErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRetryVote,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.community_vote_retry), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onDismissFeedback,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.community_decide_later))
                            }
                        }
                    } else if (userAlreadyVoted) {
                        // Previously voted — suppress repeat prompt; strictly no re-voting or Change Vote
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (savedUserVote == "spam") HighRiskRed else LegitGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.community_already_voted),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.community_user_voted_indicator,
                                if (savedUserVote == "spam") stringResource(R.string.community_report_spam) else stringResource(R.string.community_not_spam)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (savedUserVote == "spam") HighRiskRed else LegitGreen
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onDismissFeedback,
                            modifier = Modifier.fillMaxWidth(0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    } else {
                        // Feedback prompt: How would you classify this caller?
                        Text(
                            text = stringResource(R.string.community_feedback_question),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val currentRep = communityReputation
                        if (currentRep != null && currentRep.spamCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.community_spam_count_format,
                                    currentRep.spamCount,
                                    currentRep.threshold
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = SpamAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        if (isSubmittingVote) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = BrandBlue,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onVoteSpam(uiState.rawNumber) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = HighRiskRed),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSubmittingVote
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.community_report_spam),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { onVoteNotSpam(uiState.rawNumber) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = LegitGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSubmittingVote
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.community_not_spam),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = onDismissFeedback,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.community_decide_later),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
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
