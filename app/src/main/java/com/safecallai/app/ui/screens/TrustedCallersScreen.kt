package com.safecallai.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.UserTrustedCaller
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.viewmodels.SafeCallViewModel

@Composable
fun TrustedCallersScreen(
    viewModel: SafeCallViewModel,
    onNavigateToCallerDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    TrustedCallersScreenContent(
        trustedCallers = uiState.trustedCallers,
        onAddTrustedCaller = { phone, label, notes ->
            viewModel.addTrustedCaller(phone, label, notes)
        },
        onRemoveTrustedCaller = { id ->
            viewModel.removeTrustedCaller(id)
        },
        onNavigateToCallerDetails = onNavigateToCallerDetails
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedCallersScreenContent(
    trustedCallers: List<UserTrustedCaller>,
    onAddTrustedCaller: (phone: String, label: String, notes: String?) -> Unit,
    onRemoveTrustedCaller: (id: Long) -> Unit,
    onNavigateToCallerDetails: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var inputPhone by remember { mutableStateOf("") }
    var inputLabel by remember { mutableStateOf("") }
    var inputNotes by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.trusted_add_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.trusted_add_dialog_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it },
                        label = { Text(stringResource(R.string.trusted_phone_label)) },
                        placeholder = { Text("e.g. +91 98765 43210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputLabel,
                        onValueChange = { inputLabel = it },
                        label = { Text(stringResource(R.string.trusted_contact_name_label)) },
                        placeholder = { Text(stringResource(R.string.trusted_contact_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputNotes,
                        onValueChange = { inputNotes = it },
                        label = { Text(stringResource(R.string.trusted_notes_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPhone.isNotBlank() && inputLabel.isNotBlank()) {
                            onAddTrustedCaller(inputPhone.trim(), inputLabel.trim(), inputNotes.trim().takeIf { it.isNotEmpty() })
                            inputPhone = ""
                            inputLabel = ""
                            inputNotes = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trusted_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandBlue,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.trusted_add_btn))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
        ) {
            // Principle Callout Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.trusted_principle_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (trustedCallers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.trusted_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.trusted_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trustedCallers) { caller ->
                        TrustedCallerCard(
                            caller = caller,
                            onDelete = { onRemoveTrustedCaller(caller.id) },
                            onClick = { onNavigateToCallerDetails(caller.phoneNumber) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Trusted Callers - Dark",
    showBackground = true,
    backgroundColor = 0xFF0B111E,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewTrustedCallersDark() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = true) {
        TrustedCallersScreenContent(
            trustedCallers = listOf(
                UserTrustedCaller(
                    id = 1L,
                    phoneNumber = "+919876500001",
                    normalizedPhoneNumber = "+919876500001",
                    customLabel = "Apartment Main Gate Security",
                    notes = "Building A visitor verification intercom",
                    createdAt = System.currentTimeMillis() - 86400000L * 3
                ),
                UserTrustedCaller(
                    id = 2L,
                    phoneNumber = "+919876500002",
                    normalizedPhoneNumber = "+919876500002",
                    customLabel = "Family Pediatric Clinic",
                    notes = "Dr. Mehta clinic front desk",
                    createdAt = System.currentTimeMillis() - 86400000L * 10
                )
            ),
            onAddTrustedCaller = { _, _, _ -> },
            onRemoveTrustedCaller = {},
            onNavigateToCallerDetails = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Trusted Callers - Light",
    showBackground = true,
    backgroundColor = 0xFFF8FAFC,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun PreviewTrustedCallersLight() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = false) {
        TrustedCallersScreenContent(
            trustedCallers = listOf(
                UserTrustedCaller(
                    id = 1L,
                    phoneNumber = "+919876500001",
                    normalizedPhoneNumber = "+919876500001",
                    customLabel = "Apartment Main Gate Security",
                    notes = "Building A visitor verification intercom",
                    createdAt = System.currentTimeMillis() - 86400000L * 3
                )
            ),
            onAddTrustedCaller = { _, _, _ -> },
            onRemoveTrustedCaller = {},
            onNavigateToCallerDetails = {}
        )
    }
}

@Composable
private fun TrustedCallerCard(
    caller: UserTrustedCaller,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val displayPhone = remember(caller.normalizedPhoneNumber) {
        PhoneNumberNormalizer.formatForDisplay(caller.normalizedPhoneNumber)
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
                        .background(LegitGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = LegitGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = caller.customLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = displayPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!caller.notes.isNullOrBlank()) {
                        Text(
                            text = caller.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
