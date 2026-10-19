package com.safecallai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.ui.theme.PhoneNumberTextStyle

/**
 * Quiet Premium Trustify Dial Pad.
 *
 * Implements a balanced, tactile number grid with clean surfaces,
 * tabular numeral digits, accessible touch targets, and a solid mint call action.
 * Free of glowing rings, gradient overlays, or distracting decorations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrustifyDialPad(
    number: String,
    onNumberChange: (String) -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    showCallButton: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Tabular Number Display Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.ifEmpty { stringResource(R.string.add_call_enter_number) },
                    style = PhoneNumberTextStyle.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = if (number.isNotEmpty()) 1.sp else 0.sp
                    ),
                    color = if (number.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 12-key Dialpad Grid (64dp buttons, 12dp spacing)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: 1, 2, 3
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "1", letters = "") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "1")
                }
                DialpadKey(digit = "2", letters = "ABC") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "2")
                }
                DialpadKey(digit = "3", letters = "DEF") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "3")
                }
            }

            // Row 2: 4, 5, 6
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "4", letters = "GHI") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "4")
                }
                DialpadKey(digit = "5", letters = "JKL") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "5")
                }
                DialpadKey(digit = "6", letters = "MNO") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "6")
                }
            }

            // Row 3: 7, 8, 9
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "7", letters = "PQRS") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "7")
                }
                DialpadKey(digit = "8", letters = "TUV") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "8")
                }
                DialpadKey(digit = "9", letters = "WXYZ") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "9")
                }
            }

            // Row 4: *, 0 (+), #
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "*", letters = "") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "*")
                }
                DialpadKey(
                    digit = "0",
                    letters = "+",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNumberChange(number + "+")
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNumberChange(number + "0")
                    }
                )
                DialpadKey(digit = "#", letters = "") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberChange(number + "#")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Row: Balanced Spacer, Solid Mint Call Button, Backspace / Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to balance backspace button width
            Box(modifier = Modifier.size(64.dp))

            // Primary Call Action Button (Solid Mint Surface, No Gradients)
            if (showCallButton) {
                val callInteraction = remember { MutableInteractionSource() }
                val isCallPressed by callInteraction.collectIsPressedAsState()
                val callScale by animateFloatAsState(
                    targetValue = if (isCallPressed) 0.94f else 1f,
                    animationSpec = TrustifyMotion.QuickSpring,
                    label = "callScale"
                )

                Box(
                    modifier = Modifier
                        .scale(callScale)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCallPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable(
                            interactionSource = callInteraction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCallClick()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.add_call_dialpad_call),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Box(modifier = Modifier.size(64.dp))
            }

            // Backspace / Clear Button (Accessible touch target, single tap drop 1, long-press clear all)
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    val backspaceInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = backspaceInteraction,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNumberChange(number.dropLast(1))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNumberChange("")
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = stringResource(R.string.add_call_dialpad_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadKey(
    digit: String,
    letters: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = TrustifyMotion.QuickSpring,
        label = "keyScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            1.dp,
            if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = PhoneNumberTextStyle.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
