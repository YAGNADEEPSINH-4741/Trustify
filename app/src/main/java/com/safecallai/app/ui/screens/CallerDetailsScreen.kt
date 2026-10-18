package com.safecallai.app.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.CallerClassification
import com.safecallai.app.domain.models.ClassificationResult
import com.safecallai.app.domain.models.Evidence
import com.safecallai.app.domain.models.EvidenceType
import com.safecallai.app.domain.models.VerificationStatus
import com.safecallai.app.domain.models.Warning
import com.safecallai.app.domain.models.WarningSeverity
import com.safecallai.app.ui.components.ClassificationBadge
import com.safecallai.app.ui.components.VerificationStatusChip
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.theme.SafeCallAITheme
import com.safecallai.app.ui.theme.SpamAmber
import com.safecallai.app.ui.viewmodels.SafeCallViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallerDetailsScreen(
    phoneNumber: String,
    viewModel: SafeCallViewModel,
    onBack: () -> Unit,
    onNavigateToReport: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isTrusted by remember { mutableStateOf(false) }
    var loadedResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val defaultContactName = stringResource(R.string.trusted_default_contact_name)

    LaunchedEffect(phoneNumber) {
        isLoading = true
        val repo = viewModel.getRepository()
        isTrusted = repo.isNumberTrusted(phoneNumber)
        loadedResult = repo.screenAndClassify(phoneNumber, recordInHistory = false)
        isLoading = false
    }

    if (isLoading || loadedResult == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandBlue)
        }
    } else {
        CallerDetailsContent(
            result = loadedResult!!,
            isTrusted = isTrusted,
            onBack = onBack,
            onToggleTrust = {
                coroutineScope.launch {
                    val repo = viewModel.getRepository()
                    if (isTrusted) {
                        val record = repo.getTrustedCallers().firstOrNull { it.normalizedPhoneNumber == loadedResult!!.normalizedNumber }
                        if (record != null) {
                            repo.removeTrustedCaller(record.id)
                        }
                        isTrusted = false
                    } else {
                        repo.addTrustedCaller(loadedResult!!.rawNumber, loadedResult!!.callerName ?: defaultContactName)
                        isTrusted = true
                    }
                    viewModel.refreshState()
                }
            },
            onNavigateToReport = onNavigateToReport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallerDetailsContent(
    result: ClassificationResult,
    isTrusted: Boolean,
    onBack: () -> Unit,
    onToggleTrust: () -> Unit,
    onNavigateToReport: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val displayPhone = PhoneNumberNormalizer.formatForDisplay(result.normalizedNumber)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.caller_details_evidence_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Identity Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = result.callerName ?: result.businessName ?: stringResource(R.string.caller_details_unverified),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayPhone,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(result.rawNumber)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    ClassificationBadge(classification = result.classification)
                    Spacer(modifier = Modifier.height(8.dp))
                    VerificationStatusChip(status = result.verificationStatus)

                    if (result.falseSpamRecovered) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(LegitGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.subtitle_false_spam_recovered),
                                color = LegitGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Transparent Explanation Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.caller_details_analysis_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.caller_details_data_source, result.dataSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Evidence Ledger
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.caller_details_evidence_ledger),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.caller_details_evidence_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (result.evidenceList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.caller_details_no_evidence),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        result.evidenceList.forEach { evidence ->
                            EvidenceItemRow(evidence = evidence)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            // Warnings Section if any
            if (result.warnings.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SpamAmber.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SpamAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.caller_details_cautions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        result.warnings.forEach { warning ->
                            WarningItemRow(warning = warning)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Actions Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.caller_details_actions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Trust Toggle Button
                    Button(
                        onClick = onToggleTrust,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTrusted) MaterialTheme.colorScheme.surface else BrandBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isTrusted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isTrusted) LegitGreen else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTrusted) stringResource(R.string.caller_details_remove_trusted) else stringResource(R.string.caller_details_mark_trusted),
                            color = if (isTrusted) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Report Button
                    OutlinedButton(
                        onClick = { onNavigateToReport(result.rawNumber) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.caller_details_report_action))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Call back via Intent
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${result.rawNumber}")
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.caller_details_call_dialer))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EvidenceItemRow(evidence: Evidence) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(evidence.timestamp) { dateFormat.format(Date(evidence.timestamp)) }
    val verifiedLabel = if (evidence.isIndependent) {
        stringResource(R.string.caller_details_independently_verified)
    } else {
        stringResource(R.string.caller_details_user_stated)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (evidence.isIndependent) LegitGreen else BrandBlue,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = evidence.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = evidence.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.caller_details_evidence_format, evidence.source, formattedDate, verifiedLabel),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun WarningItemRow(warning: Warning) {
    val tint = when (warning.severity) {
        WarningSeverity.CRITICAL -> HighRiskRed
        WarningSeverity.HIGH -> HighRiskRed
        WarningSeverity.MEDIUM -> SpamAmber
        else -> BrandBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
                .padding(top = 6.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${warning.title} [${warning.code}]",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
            Text(
                text = warning.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(name = "Caller Details - Dark Theme", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun CallerDetailsDarkPreview() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = true) {
        CallerDetailsContent(
            result = ClassificationResult(
                rawNumber = "+919876543210",
                normalizedNumber = "+919876543210",
                callerName = "Amazon Logistics",
                businessName = "Amazon Transport Services",
                classification = CallerClassification.VERIFIED_LEGITIMATE,
                verificationStatus = VerificationStatus.ACTIVE_BOOKING_ASSOCIATED,
                falseSpamRecovered = true,
                explanation = "FALSE SPAM RECOVERY: Although flagged as spam on external apps due to high call volume, this number is independently verified as your active delivery associate.",
                evidenceList = listOf(
                    Evidence(
                        type = EvidenceType.DELIVERY_DISPATCH,
                        title = "Active Delivery Dispatch [DEV MOCK]",
                        description = "Associated with active order AMZ-84920-IND out for delivery today.",
                        source = "Simulated Amazon Logistics Partner API",
                        isIndependent = true
                    )
                )
            ),
            isTrusted = false,
            onBack = {},
            onToggleTrust = {},
            onNavigateToReport = {}
        )
    }
}

@Preview(name = "Caller Details - Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7)
@Composable
fun CallerDetailsLightPreview() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = false) {
        CallerDetailsContent(
            result = ClassificationResult(
                rawNumber = "+9118002026161",
                normalizedNumber = "+9118002026161",
                callerName = "HDFC Bank Customer Support",
                classification = CallerClassification.VERIFIED_LEGITIMATE,
                verificationStatus = VerificationStatus.VERIFIED_BUSINESS,
                explanation = "VERIFIED BUSINESS NUMBER: Belonging to registered banking entity. Does not guarantee safety of every call.",
                evidenceList = listOf(
                    Evidence(
                        type = EvidenceType.BUSINESS_REGISTRATION,
                        title = "Verified Corporate Entity",
                        description = "Corporate registration confirmed with RBI telecom registry.",
                        source = "Corporate Telecom Registry",
                        isIndependent = true
                    )
                )
            ),
            isTrusted = true,
            onBack = {},
            onToggleTrust = {},
            onNavigateToReport = {}
        )
    }
}
