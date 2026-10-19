package com.safecallai.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ScreenedCallRecord
import com.safecallai.app.ui.components.ClassificationBadge
import com.safecallai.app.ui.components.TrustifyAvatar
import com.safecallai.app.ui.components.TrustifyBadge
import com.safecallai.app.ui.components.TrustifyBrandMark
import com.safecallai.app.ui.components.TrustifyCard
import com.safecallai.app.ui.components.TrustifyDialPad
import com.safecallai.app.ui.components.TrustifyEmptyState
import com.safecallai.app.ui.components.TrustifySectionHeader
import com.safecallai.app.ui.components.TrustifyStatusType
import com.safecallai.app.ui.theme.PhoneNumberTextStyle
import com.safecallai.app.ui.viewmodels.DashboardUiState
import com.safecallai.app.ui.viewmodels.SafeCallViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    // Quick Dial Pad Bottom Sheet State
    var showDialPadSheet by remember { mutableStateOf(false) }
    var dialPadNumber by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        onNavigateToCallerDetails = onNavigateToCallerDetails,
        onNavigateToActivity = onNavigateToActivity,
        onOpenDialPad = { showDialPadSheet = true }
    )

    // Quick Dial Pad Modal Sheet
    if (showDialPadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDialPadSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.add_call_tab_dialpad),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TrustifyDialPad(
                    number = dialPadNumber,
                    onNumberChange = { dialPadNumber = it },
                    onCallClick = {
                        val trimmed = dialPadNumber.trim()
                        if (trimmed.isNotEmpty()) {
                            showDialPadSheet = false
                            try {
                                val callIntent = Intent(Intent.ACTION_CALL).apply {
                                    data = Uri.parse("tel:${Uri.encode(trimmed)}")
                                }
                                context.startActivity(callIntent)
                            } catch (e: Exception) {
                                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${Uri.encode(trimmed)}")
                                }
                                context.startActivity(dialIntent)
                            }
                        }
                    }
                )
            }
        }
    }
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
    onNavigateToCallerDetails: (String) -> Unit,
    onNavigateToActivity: () -> Unit,
    onOpenDialPad: () -> Unit = {}
) {
    var inputQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. RESTRAINED TOP HEADER (Quiet Brand Mark + Wordmark + Live Status)
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrustifyBrandMark(
                        size = 32.dp,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        isPulsing = false
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Trustify",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "CALL INTELLIGENCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Live Status Pill
                TrustifyBadge(
                    label = if (uiState.isProtectionEnabled) {
                        stringResource(R.string.dashboard_pill_protected)
                    } else {
                        stringResource(R.string.dashboard_pill_paused)
                    },
                    statusType = if (uiState.isProtectionEnabled) TrustifyStatusType.VERIFIED else TrustifyStatusType.HOLDING,
                    showPulsingDot = uiState.isProtectionEnabled
                )
            }
        }

        // 2. PRIMARY PROTECTION STATUS CARD (Real State, Clean Surface)
        item {
            TrustifyCard(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Protection Switch Row
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isProtectionEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (uiState.isProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )

                    // Role Setup Status Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.isRoleHeld) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (uiState.isRoleHeld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isRoleHeld) stringResource(R.string.dashboard_service_active) else stringResource(R.string.dashboard_role_needs_setup),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (uiState.isRoleHeld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (!uiState.isRoleHeld) {
                            Text(
                                text = stringResource(R.string.dashboard_setup_btn),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onRequestRole() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real On-Device SQLite Statistics
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricCell(
                                value = uiState.totalScreenedCount.toString(),
                                label = stringResource(R.string.dashboard_stat_screened),
                                valueColor = MaterialTheme.colorScheme.primary
                            )

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            )

                            MetricCell(
                                value = uiState.falseSpamRecoveredCount.toString(),
                                label = stringResource(R.string.dashboard_stat_recovered),
                                valueColor = MaterialTheme.colorScheme.secondary
                            )

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            )

                            MetricCell(
                                value = uiState.verifiedCount.toString(),
                                label = stringResource(R.string.dashboard_stat_verified),
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 3. QUICK ACTIONS STRIP (Working Destinations Only)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionItem(
                    icon = Icons.Default.Dialpad,
                    label = stringResource(R.string.add_call_tab_dialpad),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDialPad
                )
                QuickActionItem(
                    icon = Icons.Default.History,
                    label = stringResource(R.string.nav_activity),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToActivity
                )
                QuickActionItem(
                    icon = Icons.Default.Favorite,
                    label = stringResource(R.string.nav_trusted),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCallerDetails("+919876543210") }
                )
            }
        }

        // 4. CALLER LOOKUP / IDENTIFICATION
        item {
            TrustifyCard(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            placeholder = {
                                Text(
                                    stringResource(R.string.dashboard_search_phone_hint),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = PhoneNumberTextStyle.copy(fontSize = 15.sp),
                            trailingIcon = {
                                if (inputQuery.isNotEmpty()) {
                                    IconButton(onClick = { inputQuery = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.cd_clear),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = inputQuery.isNotBlank(),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), modifier = Modifier.size(20.dp))
                        }
                    }

                    // Test Scenarios Strip
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.dashboard_sample_scenarios),
                        style = MaterialTheme.typography.labelSmall,
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

        // 5. EXPECTING DELIVERY CONTEXTUAL SWITCH
        item {
            TrustifyCard(
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth()
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeliveryDining,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
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
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        // 6. CONTACTS PERMISSION PROMPT (If Needed)
        if (showContactsPrompt) {
            item {
                TrustifyCard(
                    cornerRadius = 14.dp,
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.contacts_permission_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.contacts_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(stringResource(R.string.contacts_permission_enable))
                            }
                        }
                    }
                }
            }
        }

        // 7. 100% ON-DEVICE PRIVACY GUARANTEE BANNER
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
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
            TrustifySectionHeader(
                title = stringResource(R.string.dashboard_recent_activity_title),
                actionText = if (uiState.recentCalls.isNotEmpty()) {
                    stringResource(R.string.dashboard_view_all_count, uiState.recentCalls.size)
                } else null,
                onActionClick = onNavigateToActivity
            )
        }

        // 9. RECENT ACTIVITY COMPACT LIST (No Heavy Cards per Row)
        if (uiState.recentCalls.isEmpty()) {
            item {
                TrustifyEmptyState(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.dashboard_no_recent_calls),
                    description = stringResource(R.string.dashboard_no_recent_calls_desc)
                )
            }
        } else {
            item {
                TrustifyCard(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val displayCalls = uiState.recentCalls.take(5)
                        displayCalls.forEachIndexed { index, record ->
                            RecentCallCompactRow(
                                record = record,
                                onClick = { onNavigateToCallerDetails(record.phoneNumber) }
                            )
                            if (index < displayCalls.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TrustifyCard(
        cornerRadius = 14.dp,
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
            style = PhoneNumberTextStyle.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickLookupPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/**
 * Compact Recent Call Row.
 * Designed as a clean list item with avatar, tabular phone number, timestamp, and status.
 */
@Composable
fun RecentCallCompactRow(
    record: ScreenedCallRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }
    val formattedTime = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }
    val displayPhone = remember(record.normalizedPhoneNumber) {
        PhoneNumberNormalizer.formatForDisplay(record.normalizedPhoneNumber)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrustifyAvatar(
                name = record.callerName ?: displayPhone,
                size = 38.dp,
                accentColor = if (record.falseSpamRecovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                isActiveCall = false
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                Text(
                    text = record.callerName ?: displayPhone,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.callerName != null) {
                    Text(
                        text = displayPhone,
                        style = PhoneNumberTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            ClassificationBadge(classification = record.classification)
            if (record.falseSpamRecovered) {
                Text(
                    text = stringResource(R.string.subtitle_false_spam_recovered),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Backward-compatibility wrapper for RecentCallRowItem.
 */
@Composable
fun RecentCallRowItem(
    record: ScreenedCallRecord,
    onClick: () -> Unit
) {
    RecentCallCompactRow(record = record, onClick = onClick)
}
