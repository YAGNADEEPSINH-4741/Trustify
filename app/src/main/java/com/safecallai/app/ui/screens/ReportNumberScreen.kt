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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.domain.models.ReportCategory
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import com.safecallai.app.ui.viewmodels.SafeCallViewModel

@Composable
fun ReportNumberScreen(
    initialPhoneNumber: String? = null,
    viewModel: SafeCallViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    ReportNumberScreenContent(
        initialPhoneNumber = initialPhoneNumber,
        reportSuccessMessage = uiState.reportSuccessMessage,
        reportErrorMessage = uiState.reportErrorMessage,
        isSubmittingReport = uiState.isSubmittingReport,
        onClearMessages = { viewModel.clearReportMessages() },
        onSubmitReport = { phone, category, desc ->
            viewModel.submitReport(phone, category, desc)
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportNumberScreenContent(
    initialPhoneNumber: String? = null,
    reportSuccessMessage: String? = null,
    reportErrorMessage: String? = null,
    isSubmittingReport: Boolean = false,
    onClearMessages: () -> Unit = {},
    onSubmitReport: (phone: String, category: ReportCategory, description: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf(if (initialPhoneNumber == "{phoneNumber}" || initialPhoneNumber == null) "" else initialPhoneNumber) }
    var selectedCategory by remember { mutableStateOf(ReportCategory.INCORRECT_SPAM_CLASSIFICATION) }
    var description by remember { mutableStateOf("") }
    var isConfirmed by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
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
            Spacer(modifier = Modifier.height(4.dp))

            // Explanation Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.report_help_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Success or Error Feedback
            if (reportSuccessMessage != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LegitGreen.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LegitGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = reportSuccessMessage,
                            color = LegitGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (reportErrorMessage != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = HighRiskRed.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = HighRiskRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = reportErrorMessage,
                            color = HighRiskRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Phone Number Input
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    onClearMessages()
                },
                label = { Text(stringResource(R.string.report_phone_label)) },
                placeholder = { Text("e.g. +91 98765 43210") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = stringResource(selectedCategory.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.report_category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    ReportCategory.values().forEach { category ->
                        DropdownMenuItem(
                            text = { Text(stringResource(category.labelRes)) },
                            onClick = {
                                selectedCategory = category
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Description / Evidence Field
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    onClearMessages()
                },
                label = { Text(stringResource(R.string.report_description_label)) },
                placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Safeguard confirmation checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isConfirmed,
                    onCheckedChange = { isConfirmed = it }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.report_confirm_checkbox),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Submit Button
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank() && PhoneNumberNormalizer.isValid(phoneNumber)) {
                        onSubmitReport(phoneNumber.trim(), selectedCategory, description.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(12.dp),
                enabled = phoneNumber.isNotBlank() && isConfirmed && !isSubmittingReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSubmittingReport) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.report_submit_btn),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Report Number - Dark",
    showBackground = true,
    backgroundColor = 0xFF0B111E,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewReportNumberDark() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = true) {
        ReportNumberScreenContent(
            initialPhoneNumber = "+919876543210",
            reportSuccessMessage = null,
            reportErrorMessage = null,
            isSubmittingReport = false
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Report Number - Light",
    showBackground = true,
    backgroundColor = 0xFFF8FAFC,
    device = androidx.compose.ui.tooling.preview.Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun PreviewReportNumberLight() {
    com.safecallai.app.ui.theme.TrustifyTheme(darkTheme = false) {
        ReportNumberScreenContent(
            initialPhoneNumber = "+919876543210",
            reportSuccessMessage = "Report saved to local database successfully. Stored on-device.",
            reportErrorMessage = null,
            isSubmittingReport = false
        )
    }
}
