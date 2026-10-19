package com.safecallai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.ui.theme.DarkAccentMint
import com.safecallai.app.ui.theme.DarkBorder
import com.safecallai.app.ui.theme.DarkError
import com.safecallai.app.ui.theme.DarkInfo
import com.safecallai.app.ui.theme.DarkSurfaceElevated
import com.safecallai.app.ui.theme.DarkSurfacePrimary
import com.safecallai.app.ui.theme.DarkSurfaceSecondary
import com.safecallai.app.ui.theme.DarkWarning
import com.safecallai.app.ui.theme.PhoneNumberTextStyle
import com.safecallai.app.ui.theme.TrustifyRadius
import com.safecallai.app.ui.theme.TrustifySpacing

// ============================================================================
// QUIET PREMIUM — MOTION SPECIFICATIONS
// Short, disciplined, lifecycle-aware animation timings
// ============================================================================

object TrustifyMotion {
    // Button press: 100ms to 140ms
    val ButtonPressSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)

    // Small state change: 150ms to 200ms
    val StateChangeSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

    // Content transition: 200ms to 260ms
    val ContentTransitionSpec = tween<Float>(durationMillis = 220, easing = LinearOutSlowInEasing)

    // Bottom sheet transition: 220ms to 300ms
    val SheetTransitionSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

    // Theme transition: 200ms to 300ms
    val ThemeTransitionSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    // Tactile spring feedback for controls
    val QuickSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

// ============================================================================
// 1. TRUSTIFY BRAND MARK (Restrained Minimalist Identity)
// Clean security shield with telephone arc • No neon, no pulsing flares
// ============================================================================

@Composable
fun TrustifyBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    primaryColor: Color = tint,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    isPulsing: Boolean = false
) {
    val effectiveColor = if (primaryColor != MaterialTheme.colorScheme.primary) primaryColor else tint
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val width = this.size.width
            val height = this.size.height

            // Shield Outline
            val shieldPath = Path().apply {
                moveTo(width * 0.5f, height * 0.08f)
                cubicTo(
                    width * 0.74f, height * 0.10f,
                    width * 0.88f, height * 0.18f,
                    width * 0.90f, height * 0.30f
                )
                cubicTo(
                    width * 0.90f, height * 0.62f,
                    width * 0.68f, height * 0.85f,
                    width * 0.5f, height * 0.94f
                )
                cubicTo(
                    width * 0.32f, height * 0.85f,
                    width * 0.10f, height * 0.62f,
                    width * 0.10f, height * 0.30f
                )
                cubicTo(
                    width * 0.12f, height * 0.18f,
                    width * 0.26f, height * 0.10f,
                    width * 0.5f, height * 0.08f
                )
                close()
            }

            drawPath(
                path = shieldPath,
                color = effectiveColor,
                style = Stroke(
                    width = width * 0.08f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Inner Communication Wave / Phone Arc
            val arcPath = Path().apply {
                moveTo(width * 0.32f, height * 0.44f)
                cubicTo(
                    width * 0.40f, height * 0.34f,
                    width * 0.60f, height * 0.34f,
                    width * 0.68f, height * 0.44f
                )
            }

            drawPath(
                path = arcPath,
                color = effectiveColor,
                style = Stroke(
                    width = width * 0.07f,
                    cap = StrokeCap.Round
                )
            )

            // Center Signal Dot
            drawCircle(
                color = effectiveColor,
                radius = width * 0.065f,
                center = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.58f)
            )
        }
    }
}

// ============================================================================
// 2. TRUSTIFY CARD (Solid, Disciplined Surfaces)
// Material 3 compliant • Subtle 1dp border • No neon glow, no gradients
// ============================================================================

@Composable
fun TrustifyCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = TrustifyRadius.card,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

// ============================================================================
// 3. TRUSTIFY BUTTON (Accessible, Tactile Controls)
// No glowing borders • 100-140ms feedback • High-contrast labels
// ============================================================================

enum class TrustifyButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    DANGER
}

@Composable
fun TrustifyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TrustifyButtonVariant = TrustifyButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = TrustifyMotion.ButtonPressSpec,
        label = "btnScale"
    )

    when (variant) {
        TrustifyButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .scale(scale)
                    .height(height),
                shape = RoundedCornerShape(TrustifyRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                interactionSource = interactionSource,
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                ButtonContent(icon, text, MaterialTheme.colorScheme.onPrimary)
            }
        }
        TrustifyButtonVariant.SECONDARY -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .scale(scale)
                    .height(height),
                shape = RoundedCornerShape(TrustifyRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                interactionSource = interactionSource,
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                ButtonContent(icon, text, MaterialTheme.colorScheme.onSurface)
            }
        }
        TrustifyButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .scale(scale)
                    .height(height),
                shape = RoundedCornerShape(TrustifyRadius.control),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                interactionSource = interactionSource,
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                ButtonContent(icon, text, MaterialTheme.colorScheme.onSurface)
            }
        }
        TrustifyButtonVariant.DANGER -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .scale(scale)
                    .height(height),
                shape = RoundedCornerShape(TrustifyRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                interactionSource = interactionSource,
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                ButtonContent(icon, text, Color.White)
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector?, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

// ============================================================================
// 4. TRUSTIFY STATUS BADGE (Disciplined Semantic Pill)
// ============================================================================

enum class TrustifyStatusType {
    VERIFIED,
    LIKELY_SAFE,
    SPAM,
    HOLDING,
    NEUTRAL,
    CALLING
}

@Composable
fun TrustifyBadge(
    label: String,
    statusType: TrustifyStatusType,
    modifier: Modifier = Modifier,
    showPulsingDot: Boolean = false
) {
    val (textColor, containerColor) = when (statusType) {
        TrustifyStatusType.VERIFIED -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        TrustifyStatusType.LIKELY_SAFE -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        TrustifyStatusType.SPAM -> Pair(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        TrustifyStatusType.HOLDING -> Pair(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.surfaceVariant
        )
        TrustifyStatusType.NEUTRAL -> Pair(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        TrustifyStatusType.CALLING -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TrustifyRadius.pill),
        color = containerColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ============================================================================
// 5. CALLER AVATAR (Restrained & Native)
// ============================================================================

@Composable
fun TrustifyAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isActiveCall: Boolean = false
) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtle 1dp border indicating active or verified status without neon
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(
                width = if (isActiveCall) 1.5.dp else 1.dp,
                color = if (isActiveCall) accentColor else MaterialTheme.colorScheme.outline
            )
        ) {}
    }
}

// ============================================================================
// 6. SECTION HEADER (Consistent Hierarchy)
// ============================================================================

@Composable
fun TrustifySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onActionClick() }
            )
        } else if (action != null) {
            action()
        }
    }
}

// ============================================================================
// 7. EMPTY STATE (Clean, Honest, Communicative)
// ============================================================================

@Composable
fun TrustifyEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (actionButton != null) {
            Spacer(modifier = Modifier.height(20.dp))
            actionButton()
        }
    }
}
