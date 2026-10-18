package com.safecallai.app.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.service.ScreeningNotificationHelper
import com.safecallai.app.ui.components.ClassificationBadge
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.BrandBlueLight
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.LikelyLegitTeal
import com.safecallai.app.ui.theme.SpamAmber
import com.safecallai.app.ui.theme.TrustifyTheme
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
    val preferences = viewModel.getRepository().preferences

    var hasContactsPermission by remember {
        mutableStateOf(com.safecallai.app.domain.engine.ContactLookupHelper.hasPermission(context))
    }
    var hasDismissedContactsPrompt by remember {
        mutableStateOf(preferences.hasDismissedContactsPrompt)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
        if (!isGranted) {
            preferences.hasDismissedContactsPrompt = true
            hasDismissedContactsPrompt = true
        } else {
            viewModel.refreshState()
        }
    }

    DashboardContent(
        uiState = uiState,
        showContactsPrompt = (!hasContactsPermission && !hasDismissedContactsPrompt),
        onRequestContactsPermission = { contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
        onDismissContactsPrompt = {
            preferences.hasDismissedContactsPrompt = true
            hasDismissedContactsPrompt = true
        },
        onRequestRole = {
            val intent = viewModel.getRoleManager().createRequestRoleIntent()
            if (intent != null) {
                roleLauncher.launch(intent)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_screening_service_bound), Toast.LENGTH_LONG).show()
            }
        },
        onToggleProtection = { viewModel.toggleProtection(it) },
        onToggleExpectingDelivery = { viewModel.toggleUserExpectingDelivery(it) },
        onTestNotification = {
            ScreeningNotificationHelper.simulateScreeningCall(context, "+919876543210") {
                Toast.makeText(context, context.getString(R.string.toast_heads_up_triggered), Toast.LENGTH_SHORT).show()
            }
        },
        onLaunchCustomCallScreen = {
            viewModel.simulateCustomCall(context, "+919876543210", "Amazon Logistics")
        },
        onNavigateToCallerDetails = onNavigateToCallerDetails,
        onNavigateToActivity = onNavigateToActivity
    )
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    showContactsPrompt: Boolean = false,
    onRequestContactsPermission: () -> Unit = {},
    onDismissContactsPrompt: () -> Unit = {},
    onRequestRole: () -> Unit,
    onToggleProtection: (Boolean) -> Unit,
    onToggleExpectingDelivery: (Boolean) -> Unit,
    onTestNotification: () -> Unit = {},
    onLaunchCustomCallScreen: () -> Unit = {},
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
        // 1. REFINED HEADER WITH EMBEDDED STATUS PILL
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Trustify",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            letterSpacing = (-0.5).sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.dashboard_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Compact protection pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (uiState.isProtectionEnabled) LegitGreen.copy(alpha = 0.12f) else HighRiskRed.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isProtectionEnabled) LegitGreen.copy(alpha = 0.35f) else HighRiskRed.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isProtectionEnabled) LegitGreen else HighRiskRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isProtectionEnabled) stringResource(R.string.dashboard_pill_protected) else stringResource(R.string.dashboard_pill_paused),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = if (uiState.isProtectionEnabled) LegitGreen else HighRiskRed
                        )
                    }
                }
            }
        }

        // 2. UNIFIED PROTECTION & CONFIGURATION BANNER
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Master Switch Row
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_call_screening_protection),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.isProtectionEnabled)
                                        stringResource(R.string.dashboard_screening_active_desc)
                                    else
                                        stringResource(R.string.dashboard_screening_paused_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.isProtectionEnabled,
                            onCheckedChange = onToggleProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandBlue
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Role Setup Indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.isRoleHeld) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (uiState.isRoleHeld) LegitGreen else SpamAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isRoleHeld) stringResource(R.string.dashboard_service_active) else stringResource(R.string.dashboard_role_needs_setup),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (uiState.isRoleHeld) LegitGreen else SpamAmber
                            )
                        }

                        if (!uiState.isRoleHeld) {
                            Text(
                                text = stringResource(R.string.dashboard_setup_btn),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue,
                                modifier = Modifier
                                    .clickable { onRequestRole() }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2B. CONTACTS PERMISSION PROMPT (Explain purpose, graceful denial, non-repeated)
        if (showContactsPrompt) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.contacts_permission_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.contacts_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismissContactsPrompt) {
                                Text(stringResource(R.string.contacts_permission_not_now))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRequestContactsPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(stringResource(R.string.contacts_permission_enable))
                            }
                        }
                    }
                }
            }
        }

        // 3. NUMBER LOOKUP SECTION
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_instant_lookup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.dashboard_lookup_desc),
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
                            placeholder = { Text(stringResource(R.string.dashboard_search_phone_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (inputQuery.isNotEmpty()) {
                                    IconButton(onClick = { inputQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (inputQuery.isNotBlank()) {
                                    onNavigateToCallerDetails(inputQuery.trim())
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            enabled = inputQuery.isNotBlank(),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                        }
                    }

                    // Test Scenarios Strip
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dashboard_sample_scenarios),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickLookupPill(label = "Amazon Logistics") {
                            onNavigateToCallerDetails("+919876543210")
                        }
                        QuickLookupPill(label = "HDFC Bank") {
                            onNavigateToCallerDetails("+9118002026161")
                        }
                        QuickLookupPill(label = "Uber Driver") {
                            onNavigateToCallerDetails("+919988776655")
                        }
                    }
                }
            }
        }

        // 4. CONTEXT: EXPECTING DELIVERY SWITCH
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeliveryDining,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_expecting_delivery_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.dashboard_expecting_delivery_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isExpectingDelivery,
                        onCheckedChange = onToggleExpectingDelivery,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandBlue
                        )
                    )
                }
            }
        }

        // 5. HONEST REAL METRICS (SQLITE DATABASE STATS)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricCell(
                        value = uiState.totalScreenedCount.toString(),
                        label = stringResource(R.string.dashboard_stat_screened),
                        valueColor = BrandBlue
                    )

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    )

                    MetricCell(
                        value = uiState.falseSpamRecoveredCount.toString(),
                        label = stringResource(R.string.dashboard_stat_recovered),
                        valueColor = LegitGreen
                    )

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    )

                    MetricCell(
                        value = uiState.verifiedCount.toString(),
                        label = stringResource(R.string.dashboard_stat_verified),
                        valueColor = LikelyLegitTeal
                    )
                }
            }
        }

        // 6. ON-DEVICE PRIVACY GUARANTEE BANNER
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = LegitGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dashboard_privacy_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }


        // 8. RECENT SCREENING ACTIVITY HEADER
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (uiState.recentCalls.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_view_all_count, uiState.recentCalls.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToActivity() }
                    )
                }
            }
        }

        // 9. RECENT ACTIVITY LIST OR HONEST EMPTY STATE
        if (uiState.recentCalls.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.dashboard_no_recent_calls),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dashboard_no_recent_calls_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(uiState.recentCalls.take(5)) { record ->
                RecentCallRowItem(
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
private fun MetricCell(
    value: String,
    label: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickLookupPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun RecentCallRowItem(
    record: ScreenedCallRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }
    val formattedTime = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }
    val displayPhone = remember(record.normalizedPhoneNumber) {
        PhoneNumberNormalizer.formatForDisplay(record.normalizedPhoneNumber)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
                            if (record.falseSpamRecovered) LegitGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (record.falseSpamRecovered) Icons.Default.CheckCircle else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (record.falseSpamRecovered) LegitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = record.callerName ?: displayPhone,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                ClassificationBadge(classification = record.classification)
                if (record.falseSpamRecovered) {
                    Text(
                        text = stringResource(R.string.subtitle_false_spam_recovered),
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
    TrustifyTheme(darkTheme = true) {
        DashboardContent(
            uiState = DashboardUiState(
                isRoleHeld = true,
                totalScreenedCount = 12,
                falseSpamRecoveredCount = 4,
                verifiedCount = 7,
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
                        callerName = "HDFC Bank Official Support",
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
    TrustifyTheme(darkTheme = false) {
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
