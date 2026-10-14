package com.safecallai.app.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.service.ScreeningNotificationHelper
import com.safecallai.app.ui.components.ClassificationBadge
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.LikelyLegitTeal
import com.safecallai.app.ui.theme.SafeCallAITheme
import com.safecallai.app.ui.theme.SpamAmber
import com.safecallai.app.ui.viewmodels.DashboardUiState
import com.safecallai.app.ui.viewmodels.SafeCallViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: SafeCallViewModel,
    onNavigateToCallerDetails: (String) -> Unit,
    onNavigateToActivity: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    DashboardContent(
        uiState = uiState,
        onRequestRole = {
            val intent = viewModel.getRoleManager().createRequestRoleIntent()
            if (intent != null) {
                roleLauncher.launch(intent)
            } else {
                Toast.makeText(context, "Call Screening Service is bound automatically on this Android version", Toast.LENGTH_LONG).show()
            }
        },
        onToggleProtection = { viewModel.toggleProtection(it) },
        onToggleExpectingDelivery = { viewModel.toggleUserExpectingDelivery(it) },
        onTestNotification = {
            ScreeningNotificationHelper.simulateScreeningCall(context, "+919876543210") {
                Toast.makeText(context, "Heads-up notification triggered!", Toast.LENGTH_SHORT).show()
            }
        },
        onNavigateToCallerDetails = onNavigateToCallerDetails,
        onNavigateToActivity = onNavigateToActivity
    )
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onRequestRole: () -> Unit,
    onToggleProtection: (Boolean) -> Unit,
    onToggleExpectingDelivery: (Boolean) -> Unit,
    onTestNotification: () -> Unit = {},
    onNavigateToCallerDetails: (String) -> Unit,
    onNavigateToActivity: () -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SafeCall AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Verifiable Legitimate Caller Detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Protection Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (uiState.isProtectionEnabled) LegitGreen.copy(alpha = 0.15f) else HighRiskRed.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isProtectionEnabled) LegitGreen else HighRiskRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isProtectionEnabled) "SHIELD ACTIVE" else "PAUSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isProtectionEnabled) LegitGreen else HighRiskRed
                        )
                    }
                }
            }
        }

        // Master Protection Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Call Screening Protection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.isProtectionEnabled)
                                        "Screening incoming calls to prevent false spam blocking"
                                    else
                                        "Protection paused — calls ring through unfiltered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.isProtectionEnabled,
                            onCheckedChange = onToggleProtection,
                            colors = SwitchDefaults.colors(checkedThumbColor = BrandBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Call Notification Button
                    OutlinedButton(
                        onClick = onTestNotification,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Incoming Call Notification")
                    }
                }
            }
        }

        // Honest Stats Summary Grid (Actual Counts from SQLite)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Screened",
                    value = uiState.totalScreenedCount.toString(),
                    modifier = Modifier.weight(1f),
                    accentColor = BrandBlue
                )
                StatCard(
                    title = "False Spam Recovered",
                    value = uiState.falseSpamRecoveredCount.toString(),
                    modifier = Modifier.weight(1f),
                    accentColor = LegitGreen
                )
                StatCard(
                    title = "Verified",
                    value = uiState.verifiedCount.toString(),
                    modifier = Modifier.weight(1f),
                    accentColor = LikelyLegitTeal
                )
            }
        }

        // Active Delivery Expectation Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeliveryDining,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Expecting a Delivery or Cab?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Cross-references active dispatches to prevent blocking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isExpectingDelivery,
                        onCheckedChange = onToggleExpectingDelivery,
                        colors = SwitchDefaults.colors(checkedThumbColor = BrandBlue)
                    )
                }
            }
        }

        // Instant Number Lookup Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Instant Number Lookup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Enter a phone number to inspect evidence and false spam recovery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputQuery,
                            onValueChange = { inputQuery = it },
                            placeholder = { Text("e.g. +91 98765 43210") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputQuery.isNotBlank()) {
                                    onNavigateToCallerDetails(inputQuery.trim())
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            enabled = inputQuery.isNotBlank()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Lookup")
                        }
                    }

                    // Sample quick test scenarios
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Test Scenarios:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickDemoChip(label = "Amazon (+919876543210)") {
                            onNavigateToCallerDetails("+919876543210")
                        }
                        QuickDemoChip(label = "HDFC Bank (+9118002026161)") {
                            onNavigateToCallerDetails("+9118002026161")
                        }
                        QuickDemoChip(label = "Uber (+919988776655)") {
                            onNavigateToCallerDetails("+919988776655")
                        }
                    }
                }
            }
        }

        // Recent Screening Activity Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Screening Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.recentCalls.isNotEmpty()) {
                    Text(
                        text = "See All (${uiState.recentCalls.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToActivity() }
                    )
                }
            }
        }

        // Recent Activity List or Empty State
        if (uiState.recentCalls.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Calls Screened Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "When incoming calls arrive or you test numbers above, SafeCall AI's classification records will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.recentCalls.take(4)) { record ->
                RecentCallItem(
                    record = record,
                    onClick = { onNavigateToCallerDetails(record.phoneNumber) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun QuickDemoChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun RecentCallItem(
    record: ScreenedCallRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }
    val formattedTime = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }
    val displayPhone = remember(record.normalizedPhoneNumber) {
        PhoneNumberNormalizer.formatForDisplay(record.normalizedPhoneNumber)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (record.falseSpamRecovered) LegitGreen.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (record.falseSpamRecovered) Icons.Default.CheckCircle else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (record.falseSpamRecovered) LegitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = record.callerName ?: displayPhone,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (record.callerName != null) {
                        Text(
                            text = displayPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                ClassificationBadge(classification = record.classification)
                if (record.falseSpamRecovered) {
                    Text(
                        text = "★ False Spam Recovered",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LegitGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Dashboard - Dark Theme", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun DashboardScreenDarkPreview() {
    SafeCallAITheme(darkTheme = true) {
        DashboardContent(
            uiState = DashboardUiState(
                isRoleHeld = true,
                totalScreenedCount = 14,
                falseSpamRecoveredCount = 5,
                verifiedCount = 8,
                isExpectingDelivery = true,
                recentCalls = listOf(
                    ScreenedCallRecord(
                        id = 1,
                        phoneNumber = "+919876543210",
                        normalizedPhoneNumber = "+919876543210",
                        callerName = "Amazon Logistics",
                        classification = CallerClassification.VERIFIED_LEGITIMATE,
                        verificationStatus = VerificationStatus.ACTIVE_BOOKING_ASSOCIATED,
                        reason = "Active dispatch linked",
                        falseSpamRecovered = true
                    ),
                    ScreenedCallRecord(
                        id = 2,
                        phoneNumber = "+9118002026161",
                        normalizedPhoneNumber = "+9118002026161",
                        callerName = "HDFC Bank Support",
                        classification = CallerClassification.VERIFIED_LEGITIMATE,
                        verificationStatus = VerificationStatus.VERIFIED_BUSINESS,
                        reason = "Corporate registered"
                    )
                )
            ),
            onRequestRole = {},
            onToggleProtection = {},
            onToggleExpectingDelivery = {},
            onNavigateToCallerDetails = {},
            onNavigateToActivity = {}
        )
    }
}

@Preview(name = "Dashboard - Light Theme", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun DashboardScreenLightPreview() {
    SafeCallAITheme(darkTheme = false) {
        DashboardContent(
            uiState = DashboardUiState(
                isRoleHeld = false,
                totalScreenedCount = 0,
                falseSpamRecoveredCount = 0,
                verifiedCount = 0,
                isExpectingDelivery = false,
                recentCalls = emptyList()
            ),
            onRequestRole = {},
            onToggleProtection = {},
            onToggleExpectingDelivery = {},
            onNavigateToCallerDetails = {},
            onNavigateToActivity = {}
        )
    }
}
