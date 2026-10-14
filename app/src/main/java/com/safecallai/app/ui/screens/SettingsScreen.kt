package com.safecallai.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.data.local.PreferencesManager
import com.safecallai.app.service.ScreeningNotificationHelper
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.SpamAmber
import com.safecallai.app.ui.viewmodels.SafeCallViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SafeCallViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    SettingsScreenContent(
        themeMode = uiState.themeMode,
        isRoleHeld = uiState.isRoleHeld,
        isProtectionEnabled = uiState.isProtectionEnabled,
        isAggressiveBlockingEnabled = uiState.isAggressiveBlockingEnabled,
        isOfflineModeForced = uiState.isOfflineModeForced,
        initialSupabaseUrl = viewModel.getRepository().preferences.supabaseUrl,
        initialSupabaseKey = viewModel.getRepository().preferences.supabaseAnonKey,
        connectedProviders = viewModel.getRepository().getConnectedProvidersInfo(),
        onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
        onRequestRole = {
            val intent = viewModel.getRoleManager().createRequestRoleIntent()
            if (intent != null && context is Activity) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Call Screening Service is bound automatically on this Android version", Toast.LENGTH_LONG).show()
            }
        },
        onTestNotification = {
            ScreeningNotificationHelper.simulateScreeningCall(context, "+919876543210") {
                Toast.makeText(context, "Heads-up notification triggered!", Toast.LENGTH_SHORT).show()
            }
        },
        onToggleProtection = { viewModel.toggleProtection(it) },
        onToggleAggressiveBlocking = { viewModel.toggleAggressiveBlocking(it) },
        onToggleOfflineMode = { viewModel.toggleOfflineMode(it) },
        onUpdateSupabaseConfig = { url, key -> viewModel.updateSupabaseConfig(url, key) },
        onClearHistory = { viewModel.clearHistory() },
        onResetAllData = {
            viewModel.clearHistory()
            coroutineScope.launch {
                val repo = viewModel.getRepository()
                repo.getTrustedCallers().forEach { repo.removeTrustedCaller(it.id) }
                viewModel.refreshState()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    themeMode: PreferencesManager.ThemeMode,
    isRoleHeld: Boolean,
    isProtectionEnabled: Boolean,
    isAggressiveBlockingEnabled: Boolean,
    isOfflineModeForced: Boolean,
    initialSupabaseUrl: String,
    initialSupabaseKey: String,
    connectedProviders: List<String>,
    onSetThemeMode: (PreferencesManager.ThemeMode) -> Unit,
    onRequestRole: () -> Unit = {},
    onTestNotification: () -> Unit = {},
    onToggleProtection: (Boolean) -> Unit,
    onToggleAggressiveBlocking: (Boolean) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onUpdateSupabaseConfig: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onResetAllData: () -> Unit
) {
    val scrollState = rememberScrollState()

    var showResetDialog by remember { mutableStateOf(false) }
    var supabaseUrlInput by remember { mutableStateOf(initialSupabaseUrl) }
    var supabaseKeyInput by remember { mutableStateOf(initialSupabaseKey) }
    var configSavedMessage by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Local Data?") },
            text = { Text("This will erase all screened call history, trusted callers list, and cached business registrations. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetAllData()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset Everything", color = HighRiskRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. APPEARANCE & THEME SECTION (MANDATORY PHASE 3)
            Text(
                text = "Appearance & Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose how SafeCall AI appears across all screens and system bars",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3-Option Segmented Control: System Default, Light, Dark
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeOptionChip(
                            label = "System",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == PreferencesManager.ThemeMode.SYSTEM_DEFAULT,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.SYSTEM_DEFAULT) }
                        )

                        ThemeOptionChip(
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == PreferencesManager.ThemeMode.LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.LIGHT) }
                        )

                        ThemeOptionChip(
                            label = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == PreferencesManager.ThemeMode.DARK,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.DARK) }
                        )
                    }
                }
            }

            // 2. INCOMING CALL SCREENING & NOTIFICATION EXPERIENCE (PHASE 4)
            Text(
                text = "Call Screening & Notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRoleHeld) Icons.Default.CheckCircle else Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isRoleHeld) LegitGreen else BrandBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRoleHeld) "Call Screening Active" else "Screening Service Registered",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!isRoleHeld) {
                            TextButton(onClick = onRequestRole) {
                                Text("Set Default", fontSize = 12.sp, color = BrandBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SafeCallScreeningService inspects incoming calls and displays heads-up verification alerts when numbers ring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Live Notification Button
                    Button(
                        onClick = onTestNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Incoming Call Notification")
                    }
                }
            }

            // 3. CALL SCREENING PREFERENCES
            Text(
                text = "Screening Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Master protection switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SafeCall Screening Service",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Evaluates incoming calls and prevents false spam blocking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isProtectionEnabled,
                            onCheckedChange = onToggleProtection,
                            colors = SwitchDefaults.colors(checkedThumbColor = BrandBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Aggressive blocking toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aggressive High-Risk Blocking",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAggressiveBlockingEnabled) "Active: Silences confirmed scam / fraud numbers" else "Disabled: Lets phone ring normally for all calls",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAggressiveBlockingEnabled) HighRiskRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAggressiveBlockingEnabled,
                            onCheckedChange = onToggleAggressiveBlocking,
                            colors = SwitchDefaults.colors(checkedThumbColor = HighRiskRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Force Offline Mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Force Offline Operation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Screen calls entirely on-device without cloud network lookups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isOfflineModeForced,
                            onCheckedChange = onToggleOfflineMode,
                            colors = SwitchDefaults.colors(checkedThumbColor = SpamAmber)
                        )
                    }
                }
            }

            // 4. CLOUD VERIFICATION BACKEND (SUPABASE)
            Text(
                text = "Cloud Verification Backend (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (supabaseUrlInput.isNotBlank()) Icons.Default.Cloud else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (supabaseUrlInput.isNotBlank()) LegitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (supabaseUrlInput.isNotBlank()) "Cloud Sync Configured" else "Local Standalone Mode Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect a Supabase project to synchronize community verified business registries. If left empty, SafeCall operates completely offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = supabaseUrlInput,
                        onValueChange = {
                            supabaseUrlInput = it
                            configSavedMessage = false
                        },
                        label = { Text("Supabase Project URL") },
                        placeholder = { Text("https://your-project.supabase.co") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = supabaseKeyInput,
                        onValueChange = {
                            supabaseKeyInput = it
                            configSavedMessage = false
                        },
                        label = { Text("Supabase Public Anon Key") },
                        placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5c...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onUpdateSupabaseConfig(supabaseUrlInput.trim(), supabaseKeyInput.trim())
                            configSavedMessage = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Configuration")
                    }

                    if (configSavedMessage) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✓ Configuration saved successfully.",
                            color = LegitGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 5. DATA MANAGEMENT
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Screened Call History")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HighRiskRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Local Data")
                    }
                }
            }

            // 6. PRIVACY COMMITMENTS CARD
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LegitGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy Guarantees",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• We do not collect or upload your contact address book.\n• SafeCall AI does not record audio or transcribe telephone conversations.\n• Phone numbers are normalized and checked against verifiable evidence.\n• You can purge all local data anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 7. ACTIVE VERIFICATION PROVIDERS INFO
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Verification Providers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    connectedProviders.forEach { provider ->
                        Text(
                            text = "• $provider",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SafeCall AI v1.0.0 (Production Build)\nEngine: Transparent Deterministic Rules v1.0",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) BrandBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Settings - Dark",
    showBackground = true,
    backgroundColor = 0xFF0B111E,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewSettingsDark() {
    com.safecallai.app.ui.theme.SafeCallAITheme(darkTheme = true) {
        SettingsScreenContent(
            themeMode = PreferencesManager.ThemeMode.DARK,
            isRoleHeld = true,
            isProtectionEnabled = true,
            isAggressiveBlockingEnabled = false,
            isOfflineModeForced = false,
            initialSupabaseUrl = "https://example.supabase.co",
            initialSupabaseKey = "anon-key-12345",
            connectedProviders = listOf("Mock Logistics Provider (Amazon, Flipkart)", "Mock Ride-Hailing Provider (Uber, Ola)", "Local SQLite Deterministic Cache"),
            onSetThemeMode = {},
            onRequestRole = {},
            onTestNotification = {},
            onToggleProtection = {},
            onToggleAggressiveBlocking = {},
            onToggleOfflineMode = {},
            onUpdateSupabaseConfig = { _, _ -> },
            onClearHistory = {},
            onResetAllData = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Settings - Light",
    showBackground = true,
    backgroundColor = 0xFFF8FAFC,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun PreviewSettingsLight() {
    com.safecallai.app.ui.theme.SafeCallAITheme(darkTheme = false) {
        SettingsScreenContent(
            themeMode = PreferencesManager.ThemeMode.LIGHT,
            isRoleHeld = false,
            isProtectionEnabled = true,
            isAggressiveBlockingEnabled = true,
            isOfflineModeForced = true,
            initialSupabaseUrl = "",
            initialSupabaseKey = "",
            connectedProviders = listOf("Local SQLite Deterministic Cache (Standalone)"),
            onSetThemeMode = {},
            onRequestRole = {},
            onTestNotification = {},
            onToggleProtection = {},
            onToggleAggressiveBlocking = {},
            onToggleOfflineMode = {},
            onUpdateSupabaseConfig = { _, _ -> },
            onClearHistory = {},
            onResetAllData = {}
        )
    }
}
