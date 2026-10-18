package com.safecallai.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safecallai.app.R
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.viewmodels.SafeCallViewModel

@Composable
fun RecentActivityScreen(
    viewModel: SafeCallViewModel,
    onNavigateToCallerDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    RecentActivityScreenContent(
        recentCalls = uiState.recentCalls,
        falseSpamRecoveredCount = uiState.falseSpamRecoveredCount,
        verifiedCount = uiState.verifiedCount,
        onClearHistory = { viewModel.clearHistory() },
        onNavigateToCallerDetails = onNavigateToCallerDetails
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentActivityScreenContent(
    recentCalls: List<com.safecallai.app.domain.models.ScreenedCallRecord>,
    falseSpamRecoveredCount: Int,
    verifiedCount: Int,
    onClearHistory: () -> Unit,
    onNavigateToCallerDetails: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredCalls = remember(recentCalls, selectedFilter) {
        when (selectedFilter) {
            "FALSE_SPAM" -> recentCalls.filter { it.falseSpamRecovered }
            "VERIFIED" -> recentCalls.filter { it.classification == CallerClassification.VERIFIED_LEGITIMATE }
            "POTENTIAL_SPAM" -> recentCalls.filter { it.classification == CallerClassification.POTENTIAL_SPAM || it.classification == CallerClassification.HIGH_RISK }
            else -> recentCalls
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.activity_clear_dialog_title)) },
            text = { Text(stringResource(R.string.activity_clear_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.activity_clear_confirm), color = HighRiskRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (recentCalls.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.activity_clear_history))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
        ) {
            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text(stringResource(R.string.activity_filter_all_count, recentCalls.size)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandBlue.copy(alpha = 0.2f),
                            selectedLabelColor = BrandBlue
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "FALSE_SPAM",
                        onClick = { selectedFilter = "FALSE_SPAM" },
                        label = { Text(stringResource(R.string.activity_filter_false_spam_count, falseSpamRecoveredCount)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "VERIFIED",
                        onClick = { selectedFilter = "VERIFIED" },
                        label = { Text(stringResource(R.string.activity_filter_verified_count, verifiedCount)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "POTENTIAL_SPAM",
                        onClick = { selectedFilter = "POTENTIAL_SPAM" },
                        label = { Text(stringResource(R.string.activity_filter_spam_risk)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.activity_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.activity_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (filteredCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.activity_no_matching_filter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCalls) { record ->
                        RecentCallRowItem(
                            record = record,
                            onClick = { onNavigateToCallerDetails(record.phoneNumber) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Recent Activity - Dark",
    showBackground = true,
    backgroundColor = 0xFF0B111E,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewRecentActivityDark() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = true) {
        RecentActivityScreenContent(
            recentCalls = listOf(
                com.safecallai.app.domain.models.ScreenedCallRecord(
                    id = 1L,
                    phoneNumber = "+919876543210",
                    normalizedPhoneNumber = "+919876543210",
                    callerName = "Amazon Logistics",
                    classification = CallerClassification.VERIFIED_LEGITIMATE,
                    verificationStatus = com.safecallai.app.domain.models.VerificationStatus.ACTIVE_BOOKING_ASSOCIATED,
                    reason = "Verified delivery driver for active order #AMZ-9921",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    wasSilenced = false,
                    falseSpamRecovered = true
                ),
                com.safecallai.app.domain.models.ScreenedCallRecord(
                    id = 2L,
                    phoneNumber = "+918049202020",
                    normalizedPhoneNumber = "+918049202020",
                    callerName = "Suspected Telemarketing",
                    classification = CallerClassification.POTENTIAL_SPAM,
                    verificationStatus = com.safecallai.app.domain.models.VerificationStatus.UNVERIFIED,
                    reason = "Unsolicited promotional calls flagged by multiple subscribers",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                    wasSilenced = true,
                    falseSpamRecovered = false
                )
            ),
            falseSpamRecoveredCount = 1,
            verifiedCount = 1,
            onClearHistory = {},
            onNavigateToCallerDetails = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Recent Activity - Light",
    showBackground = true,
    backgroundColor = 0xFFF8FAFC,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun PreviewRecentActivityLight() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = false) {
        RecentActivityScreenContent(
            recentCalls = listOf(
                com.safecallai.app.domain.models.ScreenedCallRecord(
                    id = 1L,
                    phoneNumber = "+919876543210",
                    normalizedPhoneNumber = "+919876543210",
                    callerName = "Amazon Logistics",
                    classification = CallerClassification.VERIFIED_LEGITIMATE,
                    verificationStatus = com.safecallai.app.domain.models.VerificationStatus.ACTIVE_BOOKING_ASSOCIATED,
                    reason = "Verified delivery driver for active order #AMZ-9921",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    wasSilenced = false,
                    falseSpamRecovered = true
                )
            ),
            falseSpamRecoveredCount = 1,
            verifiedCount = 1,
            onClearHistory = {},
            onNavigateToCallerDetails = {}
        )
    }
}
