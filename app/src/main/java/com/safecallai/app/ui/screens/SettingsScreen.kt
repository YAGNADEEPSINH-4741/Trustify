package com.safecallai.app.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.data.local.AppLanguage
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
    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
        if (!viewModel.getDefaultDialerManager().isDefaultDialer()) {
            showRestrictedSettingsDialog = true
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    if (showRestrictedSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Allow Default Phone App Access",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Because Trustify was installed via direct download or sideloading, Android restricts setting default apps until you allow Restricted Settings in App Info.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quick 3-Step Setup:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                            Text(
                                text = "1. Tap \"1. Open App Info\" below.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "2. Tap the 3 dots (⋮) in the top-right corner of the App Info page.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "3. Tap \"Allow restricted settings\" and unlock with fingerprint or PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "4. Return here and tap \"Set Default Phone App\".",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(viewModel.getDefaultDialerManager().createAppInfoIntent())
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Settings > Apps > Trustify", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Open App Info (3 Dots ⋮)", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(viewModel.getDefaultDialerManager().createDefaultAppsIntent())
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Settings > Apps > Default apps", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Open Default Apps", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = {
                            showRestrictedSettingsDialog = false
                            viewModel.refreshState()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close / Done")
                    }
                }
            },
            dismissButton = null
        )
    }

    SettingsScreenContent(
        themeMode = uiState.themeMode,
        appLanguage = uiState.appLanguage,
        isRoleHeld = uiState.isRoleHeld,
        isDefaultDialer = uiState.isDefaultDialer,
        isProtectionEnabled = uiState.isProtectionEnabled,
        isAggressiveBlockingEnabled = uiState.isAggressiveBlockingEnabled,
        connectedProviders = viewModel.getRepository().getConnectedProvidersInfo(),
        onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
        onSetLanguage = { lang -> viewModel.setLanguage(lang, context) },
        onRequestRole = {
            val intent = viewModel.getRoleManager().createRequestRoleIntent()
            if (intent != null) {
                try {
                    roleLauncher.launch(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Call Screening role is not available on this device", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Call Screening role is not available on this device", Toast.LENGTH_LONG).show()
            }
        },
        onRequestDefaultDialer = {
            if (viewModel.getDefaultDialerManager().isDefaultDialer()) {
                Toast.makeText(context, "Trustify is already your Default Phone App", Toast.LENGTH_SHORT).show()
            } else {
                val intent = viewModel.getDefaultDialerManager().createRequestDefaultDialerIntent()
                if (intent != null) {
                    try {
                        defaultDialerLauncher.launch(intent)
                    } catch (_: Exception) {
                        try {
                            defaultDialerLauncher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Please set Trustify in Settings > Apps > Default apps", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    try {
                        defaultDialerLauncher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Please set Trustify in Settings > Apps > Default apps", Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
        onShowRestrictedSettingsGuide = {
            showRestrictedSettingsDialog = true
        },
        onSimulateCustomCall = { phone, name ->
            viewModel.simulateCustomCall(context, phone, name)
        },
        onTestNotification = {
            ScreeningNotificationHelper.simulateScreeningCall(context, "+919876543210") {
                Toast.makeText(context, "Heads-up notification triggered!", Toast.LENGTH_SHORT).show()
            }
        },
        onToggleProtection = { viewModel.toggleProtection(it) },
        onToggleAggressiveBlocking = { viewModel.toggleAggressiveBlocking(it) },
        onClearHistory = { viewModel.clearHistory() },
        onClearBusinessCache = {
            coroutineScope.launch {
                viewModel.getRepository().clearBusinessCache()
                Toast.makeText(context, "Local business cache purged", Toast.LENGTH_SHORT).show()
            }
        },
        onResetAllData = {
            viewModel.purgeAllLocalData()
            Toast.makeText(context, "All local Trustify data cleared", Toast.LENGTH_SHORT).show()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    themeMode: PreferencesManager.ThemeMode,
    appLanguage: AppLanguage,
    isRoleHeld: Boolean,
    isDefaultDialer: Boolean = false,
    isProtectionEnabled: Boolean,
    isAggressiveBlockingEnabled: Boolean,
    connectedProviders: List<String>,
    onSetThemeMode: (PreferencesManager.ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onRequestRole: () -> Unit = {},
    onRequestDefaultDialer: () -> Unit = {},
    onShowRestrictedSettingsGuide: () -> Unit = {},
    onSimulateCustomCall: (phone: String, contactName: String?) -> Unit = { _, _ -> },
    onTestNotification: () -> Unit = {},
    onToggleProtection: (Boolean) -> Unit,
    onToggleAggressiveBlocking: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearBusinessCache: () -> Unit = {},
    onResetAllData: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetAllData()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_reset_confirm), color = HighRiskRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. APPEARANCE & THEME & LANGUAGE SECTION
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Theme Preferences
                    Text(
                        text = stringResource(R.string.settings_theme_pref),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3-Option Segmented Control: System Default, Light, Dark
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeOptionChip(
                            label = stringResource(R.string.settings_theme_system),
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == PreferencesManager.ThemeMode.SYSTEM_DEFAULT,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.SYSTEM_DEFAULT) }
                        )

                        ThemeOptionChip(
                            label = stringResource(R.string.settings_theme_light),
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == PreferencesManager.ThemeMode.LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.LIGHT) }
                        )

                        ThemeOptionChip(
                            label = stringResource(R.string.settings_theme_dark),
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == PreferencesManager.ThemeMode.DARK,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetThemeMode(PreferencesManager.ThemeMode.DARK) }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Language Preference Section
                    Text(
                        text = stringResource(R.string.language_settings_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.language_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3-Option Segmented Control: English, Hindi, Gujarati
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LanguageOptionChip(
                            label = "English",
                            flag = "🇬🇧",
                            isSelected = appLanguage == AppLanguage.ENGLISH,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetLanguage(AppLanguage.ENGLISH) }
                        )

                        LanguageOptionChip(
                            label = "हिंदी",
                            flag = "🇮🇳",
                            isSelected = appLanguage == AppLanguage.HINDI,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetLanguage(AppLanguage.HINDI) }
                        )

                        LanguageOptionChip(
                            label = "ગુજરાતી",
                            flag = "🇮🇳",
                            isSelected = appLanguage == AppLanguage.GUJARATI,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetLanguage(AppLanguage.GUJARATI) }
                        )
                    }
                }
            }

            // 2. CALL SCREENING & PROTECTION PREFERENCES
            Text(
                text = stringResource(R.string.settings_protection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Master protection switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_screening_service),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_screening_service_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isProtectionEnabled,
                            onCheckedChange = onToggleProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Aggressive blocking toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_aggressive_blocking),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAggressiveBlockingEnabled)
                                    stringResource(R.string.settings_aggressive_active)
                                else
                                    stringResource(R.string.settings_aggressive_disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAggressiveBlockingEnabled) HighRiskRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAggressiveBlockingEnabled,
                            onCheckedChange = onToggleAggressiveBlocking,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HighRiskRed
                            )
                        )
                    }
                }
            }

            // 3. PERMISSIONS & SYSTEM ROLES
            Text(
                text = stringResource(R.string.settings_permissions_roles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Default Dialer Role Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.Call,
                                contentDescription = null,
                                tint = if (isDefaultDialer) LegitGreen else BrandBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDefaultDialer)
                                    stringResource(R.string.settings_default_phone_active)
                                else
                                    stringResource(R.string.settings_default_phone_inactive),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!isDefaultDialer) {
                            TextButton(onClick = onRequestDefaultDialer) {
                                Text(stringResource(R.string.dashboard_set_default_btn), fontSize = 12.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDefaultDialer)
                            stringResource(R.string.settings_default_phone_active_desc)
                        else
                            stringResource(R.string.settings_default_phone_inactive_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isDefaultDialer) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowRestrictedSettingsGuide() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_restricted_settings_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_restricted_settings_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onShowRestrictedSettingsGuide) {
                                    Text(stringResource(R.string.settings_restricted_fix_btn), fontSize = 12.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    // Call Screening Service Role
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
                                text = if (isRoleHeld)
                                    stringResource(R.string.settings_screening_role_active)
                                else
                                    stringResource(R.string.settings_screening_role_inactive),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!isRoleHeld) {
                            TextButton(onClick = onRequestRole) {
                                Text(stringResource(R.string.settings_enable_role), fontSize = 12.sp, color = BrandBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_screening_role_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. DEDICATED PRIVACY & ON-DEVICE SECURITY SECTION
            Text(
                text = stringResource(R.string.settings_privacy_security_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = LegitGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_privacy_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.settings_privacy_card_content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // 5. LOCAL DATA MANAGEMENT
            Text(
                text = stringResource(R.string.settings_data_management),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_history))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onClearBusinessCache,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_cache))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HighRiskRed),
                        border = BorderStroke(1.dp, HighRiskRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_reset_all), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 6. ABOUT TRUSTIFY
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.app_project_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.settings_active_providers),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    connectedProviders.forEach { provider ->
                        Text(
                            text = "• $provider",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_version_info),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
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
    val selectedBg = MaterialTheme.colorScheme.primary
    val selectedFg = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) selectedBg else Color.Transparent)
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
                tint = if (isSelected) selectedFg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedFg else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageOptionChip(
    label: String,
    flag: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectedBg = MaterialTheme.colorScheme.primary
    val selectedFg = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = flag,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedFg else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
