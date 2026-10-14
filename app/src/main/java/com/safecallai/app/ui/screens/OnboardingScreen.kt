package com.safecallai.app.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.SafeCallAITheme
import com.safecallai.app.ui.theme.Slate800
import com.safecallai.app.ui.viewmodels.SafeCallViewModel

@Composable
fun OnboardingScreen(
    viewModel: SafeCallViewModel,
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    OnboardingContent(
        isRoleHeld = uiState.isRoleHeld,
        onRequestRole = {
            val intent = viewModel.getRoleManager().createRequestRoleIntent()
            if (intent != null) {
                roleLauncher.launch(intent)
            } else {
                viewModel.refreshState()
            }
        },
        onContinue = {
            viewModel.completeOnboarding()
            onFinish()
        }
    )
}

@Composable
fun OnboardingContent(
    isRoleHeld: Boolean,
    onRequestRole: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Hero Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(BrandBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SafeCall AI",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "AI-Powered False Spam Recovery & Caller Identification",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Problem Explanation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "The False Spam Dilemma",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Delivery partners, cab drivers, and recruiters call dozens of people daily. Other apps blindly mark them as spam, causing you to miss crucial packages and rides.\n\nSafeCall AI cross-verifies active booking dispatches to recover legitimate callers without compromising your safety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Guarantees Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = LegitGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy First",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• No contact list uploading or scraping\n• No call audio recording or eavesdropping\n• Core rules-based engine runs 100% offline\n• Independent evidence is separated from assumptions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Role Setup Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRoleHeld) LegitGreen.copy(alpha = 0.12f) else Slate800
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Call Screening Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRoleHeld) "Active: Selected as Call Screening App" else "Permission needed to evaluate incoming calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRoleHeld) LegitGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isRoleHeld) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = LegitGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (!isRoleHeld) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onRequestRole,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Call Screening Role")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Continue to Dashboard",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(name = "Onboarding - Dark Theme", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun OnboardingScreenDarkPreview() {
    SafeCallAITheme(darkTheme = true) {
        OnboardingContent(isRoleHeld = false, onRequestRole = {}, onContinue = {})
    }
}

@Preview(name = "Onboarding - Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun OnboardingScreenLightPreview() {
    SafeCallAITheme(darkTheme = false) {
        OnboardingContent(isRoleHeld = true, onRequestRole = {}, onContinue = {})
    }
}
