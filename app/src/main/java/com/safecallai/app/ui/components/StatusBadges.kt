package com.safecallai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.HighRiskRedContainer
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.LegitGreenContainer
import com.safecallai.app.ui.theme.LikelyLegitContainer
import com.safecallai.app.ui.theme.LikelyLegitTeal
import com.safecallai.app.ui.theme.SpamAmber
import com.safecallai.app.ui.theme.SpamAmberContainer
import com.safecallai.app.ui.theme.UnknownGray
import com.safecallai.app.ui.theme.UnknownGrayContainer

@Composable
fun ClassificationBadge(
    classification: CallerClassification,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, icon, text) = when (classification) {
        CallerClassification.VERIFIED_LEGITIMATE -> Quad(
            LegitGreenContainer,
            LegitGreen,
            Icons.Default.CheckCircle,
            "VERIFIED LEGITIMATE"
        )
        CallerClassification.LIKELY_LEGITIMATE -> Quad(
            LikelyLegitContainer,
            LikelyLegitTeal,
            Icons.Default.Shield,
            "LIKELY LEGITIMATE"
        )
        CallerClassification.UNKNOWN -> Quad(
            UnknownGrayContainer,
            Color(0xFFCBD5E1),
            Icons.Default.Help,
            "UNKNOWN CALLER"
        )
        CallerClassification.POTENTIAL_SPAM -> Quad(
            SpamAmberContainer,
            SpamAmber,
            Icons.Default.Warning,
            "POTENTIAL SPAM"
        )
        CallerClassification.HIGH_RISK -> Quad(
            HighRiskRedContainer,
            HighRiskRed,
            Icons.Default.Warning,
            "HIGH-RISK ALERT"
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = fgColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun VerificationStatusChip(
    status: VerificationStatus,
    modifier: Modifier = Modifier
) {
    val label = when (status) {
        VerificationStatus.VERIFIED_BUSINESS -> "Corporate Registered"
        VerificationStatus.ACTIVE_BOOKING_ASSOCIATED -> "Active Dispatch Linked"
        VerificationStatus.USER_TRUSTED -> "Personal Trust"
        VerificationStatus.COMMUNITY_REPUTATION -> "Community Reports"
        VerificationStatus.UNVERIFIED -> "Unverified"
        VerificationStatus.EXPIRED -> "Expired Registry"
        VerificationStatus.CONFLICTING_SIGNALS -> "Conflicting Signals"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
