package com.safecallai.app.ui.call

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.safecallai.app.ui.theme.ElectricMint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecallai.app.R
import com.safecallai.app.domain.engine.ContactLookupHelper
import com.safecallai.app.domain.engine.DeviceContact
import com.safecallai.app.domain.engine.PhoneNumberNormalizer
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.HighRiskRed
import com.safecallai.app.ui.theme.LegitGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full Add Call Screen providing both saved Contacts search and a complete in-app manual Dial Pad.
 *
 * Requirements:
 * - Option A: Contacts list with real-time search, avatars/initials, and multi-number chooser.
 * - Option B: Full Dial Pad (0-9, *, #, +, delete/clear) allowing dialing unsaved numbers directly.
 * - Input validation with friendly error states.
 * - Seamless integration without terminating the existing active call.
 */
@Composable
fun AddCallScreen(
    onDismiss: () -> Unit,
    onCallNumber: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Dial Pad

    var allContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingContacts by remember { mutableStateOf(true) }

    // Multi-number picker dialog state
    var contactForNumberPicker by remember { mutableStateOf<DeviceContact?>(null) }

    // Dial pad state
    var dialpadNumber by remember { mutableStateOf("") }
    var dialpadError by remember { mutableStateOf<String?>(null) }

    // Load device contacts asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val contacts = ContactLookupHelper.getAllContacts(context)
            withContext(Dispatchers.Main) {
                allContacts = contacts
                isLoadingContacts = false
            }
        }
    }

    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            val query = searchQuery.trim().lowercase()
            allContacts.filter { contact ->
                contact.name.lowercase().contains(query) ||
                        contact.phoneNumbers.any { it.contains(query) }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.call_add_call),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.call_add_call_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Row: Contacts vs Dial Pad
            val tabAccent = MaterialTheme.colorScheme.primary
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = tabAccent,
                indicator = { tabPositions ->
                    androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = tabAccent,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                tint = if (selectedTab == 0) tabAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_call_tab_contacts),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selectedTab == 0) tabAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Dialpad,
                                contentDescription = null,
                                tint = if (selectedTab == 1) tabAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_call_tab_dialpad),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selectedTab == 1) tabAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 1: CONTACTS
                    ContactsTabView(
                        contacts = filteredContacts,
                        isLoading = isLoadingContacts,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSelectContact = { contact ->
                            if (contact.phoneNumbers.size == 1) {
                                onCallNumber(contact.phoneNumbers.first())
                            } else if (contact.phoneNumbers.size > 1) {
                                contactForNumberPicker = contact
                            }
                        }
                    )
                }
                1 -> {
                    // TAB 2: DIAL PAD
                    com.safecallai.app.ui.components.TrustifyDialPad(
                        number = dialpadNumber,
                        error = dialpadError,
                        onNumberChange = {
                            dialpadNumber = it
                            dialpadError = null
                        },
                        onCallClick = {
                            val cleanNumber = dialpadNumber.trim()
                            val digitsOnly = cleanNumber.filter { it.isDigit() }
                            if (digitsOnly.length < 3) {
                                dialpadError = context.getString(R.string.add_call_invalid_number)
                            } else {
                                dialpadError = null
                                onCallNumber(cleanNumber)
                            }
                        }
                    )
                }
            }
        }
    }

    // Number Selection Dialog for contacts with multiple phone numbers
    contactForNumberPicker?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactForNumberPicker = null },
            title = {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.add_call_select_number),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    contact.phoneNumbers.forEach { num ->
                        Card(
                            onClick = {
                                contactForNumberPicker = null
                                onCallNumber(num)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = num,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contactForNumberPicker = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun ContactsTabView(
    contacts: List<DeviceContact>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectContact: (DeviceContact) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.add_call_search_contacts)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading contacts...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.add_call_no_contacts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.id.ifEmpty { it.name + it.phoneNumbers.firstOrNull() } }) { contact ->
                    ContactRowItem(
                        contact = contact,
                        onClick = { onSelectContact(contact) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRowItem(
    contact: DeviceContact,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Trustify Avatar with brand accent
        com.safecallai.app.ui.components.TrustifyAvatar(
            name = contact.name,
            size = 44.dp,
            accentColor = MaterialTheme.colorScheme.primary,
            isActiveCall = false
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val primaryNumber = contact.phoneNumbers.firstOrNull() ?: ""
            Text(
                text = primaryNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contact.phoneNumbers.size > 1) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${contact.phoneNumbers.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialPadTabView(
    number: String,
    error: String?,
    onNumberChange: (String) -> Unit,
    onCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Number display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.ifEmpty { stringResource(R.string.add_call_enter_number) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighRiskRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 12-key Dialpad Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: 1, 2, 3
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "1", letters = "") { onNumberChange(number + "1") }
                DialpadKey(digit = "2", letters = "ABC") { onNumberChange(number + "2") }
                DialpadKey(digit = "3", letters = "DEF") { onNumberChange(number + "3") }
            }

            // Row 2: 4, 5, 6
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "4", letters = "GHI") { onNumberChange(number + "4") }
                DialpadKey(digit = "5", letters = "JKL") { onNumberChange(number + "5") }
                DialpadKey(digit = "6", letters = "MNO") { onNumberChange(number + "6") }
            }

            // Row 3: 7, 8, 9
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "7", letters = "PQRS") { onNumberChange(number + "7") }
                DialpadKey(digit = "8", letters = "TUV") { onNumberChange(number + "8") }
                DialpadKey(digit = "9", letters = "WXYZ") { onNumberChange(number + "9") }
            }

            // Row 4: *, 0 (+), #
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialpadKey(digit = "*", letters = "") { onNumberChange(number + "*") }
                DialpadKey(
                    digit = "0",
                    letters = "+",
                    onLongClick = { onNumberChange(number + "+") },
                    onClick = { onNumberChange(number + "0") }
                )
                DialpadKey(digit = "#", letters = "") { onNumberChange(number + "#") }
            }
        }

        // Actions Row: (Spacer), Call Button, Backspace Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty placeholder for balance
            Box(modifier = Modifier.size(68.dp))

            // Green Call Button
            FilledIconButton(
                onClick = onCall,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LegitGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(68.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = stringResource(R.string.add_call_dialpad_call),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Backspace / Clear button
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    if (number.isNotEmpty()) {
                                        onNumberChange(number.dropLast(1))
                                    }
                                },
                                onLongClick = {
                                    onNumberChange("")
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = stringResource(R.string.add_call_dialpad_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(
    digit: String,
    letters: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
