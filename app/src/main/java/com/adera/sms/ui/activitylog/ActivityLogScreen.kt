package com.adera.sms.ui.activitylog

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.data.entity.CallLogEntry
import com.adera.sms.data.entity.CallStatus
import com.adera.sms.ui.theme.AderaShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    viewModel: ActivityLogViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Sent", "Failed", "Quiet Hours")

    // Item 11 — READ_CONTACTS permission state
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    // Item 11 — contact name lookup cache (number → name)
    val contactNameCache = remember(hasContactsPermission, entries) {
        if (!hasContactsPermission) return@remember emptyMap<String, String>()
        val map = mutableMapOf<String, String>()
        entries.forEach { entry ->
            val number = entry.callerNumber
            if (number !in map) {
                val uri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(number)
                )
                val cursor = try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                        null, null, null
                    )
                } catch (e: Exception) { null }
                cursor?.use {
                    if (it.moveToFirst()) {
                        map[number] = it.getString(0)
                    }
                }
            }
        }
        map
    }

    // Item 9 — build subscriptionId → slot-index (1-based) map from current active SIMs
    val simSlotMap = remember {
        try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sm.activeSubscriptionInfoList
                ?.sortedBy { it.simSlotIndex }
                ?.mapIndexed { idx, info -> info.subscriptionId to (idx + 1) }
                ?.toMap()
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val filteredEntries = entries.filter { entry ->
        when (selectedFilter) {
            "Sent"        -> entry.status == CallStatus.SENT
            "Failed"      -> entry.status == CallStatus.FAILED
            "Quiet Hours" -> entry.status == CallStatus.SUPPRESSED_QUIET_HOURS
            else          -> true
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Item 7 — state for Failed explanation dialog
    var failedDialogEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    if (failedDialogEntry != null) {
        AlertDialog(
            onDismissRequest = { failedDialogEntry = null },
            shape = AderaShapes.large,
            icon = {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "Message May Not Have Sent", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "The auto-reply for this missed call could not be delivered. " +
                        "This is usually caused by insufficient SMS balance, " +
                        "weak signal at the time of the call, or a temporary network issue. " +
                        "If the problem persists, check your SIM's SMS balance or signal strength.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        failedDialogEntry = null
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    var limitDialogEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    if (limitDialogEntry != null) {
        var countdownText by remember { mutableStateOf("Calculating...") }
        
        LaunchedEffect(Unit) {
            val resetTime = viewModel.getLimitResetTimeMillis()
            while (true) {
                val remaining = resetTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    countdownText = "Limit has reset."
                    break
                }
                val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(remaining)
                val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                val secs = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                countdownText = String.format("Reset in %02d:%02d:%02d", hours, mins, secs)
                kotlinx.coroutines.delay(1000)
            }
        }

        AlertDialog(
            onDismissRequest = { limitDialogEntry = null },
            shape = AderaShapes.large,
            icon = {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "Daily Limit Reached",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "To protect your carrier plan and prevent spam, Adera SMS is limited to sending 15 auto-replies every 24 hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        limitDialogEntry = null
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                CenterAlignedTopAppBar(
                    // Item 6: screen title renamed from "Activity Log" to "Recents"
                    title = { Text("Recents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    // Item 11: contacts icon button to trigger READ_CONTACTS permission
                    actions = {
                        if (!hasContactsPermission) {
                            IconButton(onClick = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }) {
                                Icon(
                                    Icons.Rounded.Contacts,
                                    contentDescription = "Show contact names",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Search by phone number") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(percent = 50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { filter ->
                        FilterChip(
                            selected  = selectedFilter == filter,
                            onClick   = { selectedFilter = filter },
                            label     = { Text(filter) },
                            shape     = RoundedCornerShape(percent = 50),
                            colors    = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "Empty",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "No activity found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Item 8 — group entries by date
            val today     = LocalDate.now()
            val yesterday = today.minusDays(1)
            val groupedEntries: List<Pair<String, List<CallLogEntry>>> = filteredEntries
                .groupBy { entry ->
                    val date = Instant.ofEpochMilli(entry.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    when (date) {
                        today     -> "Today"
                        yesterday -> "Yesterday"
                        else      -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                    }
                }
                .entries
                .map { (header, list) -> header to list }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                groupedEntries.forEach { (header, group) ->
                    // Date section header
                    item(key = "header_$header") {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(group, key = { it.id }) { entry ->
                        val contactName = contactNameCache[entry.callerNumber]
                        val simLabel = resolveSimLabel(entry.simSlot, simSlotMap)
                        LogEntryCard(
                            entry = entry,
                            contactName = contactName,
                            simLabel = simLabel,
                            onFailedClick = { failedDialogEntry = entry },
                            onLimitClick = { limitDialogEntry = entry }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Item 9 — maps a raw subscriptionId to a display label.
 * Falls back to "SIM ?" for historical entries whose SIM has since been removed.
 * Returns null (no label shown) for the single-SIM unknown sentinel (-1).
 */
private fun resolveSimLabel(subscriptionId: Int, slotMap: Map<Int, Int>): String? {
    if (subscriptionId == -1) return null          // single-SIM or unknown: show nothing
    val slot = slotMap[subscriptionId]
    return if (slot != null) "SIM $slot" else "SIM ?" // graceful fallback for removed SIMs
}

@Composable
private fun LogEntryCard(
    entry: CallLogEntry,
    contactName: String?,
    simLabel: String?,
    onFailedClick: () -> Unit,
    onLimitClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Item 7: make Failed entries tappable for explanation dialog
            .then(
                when (entry.status) {
                    CallStatus.FAILED -> Modifier.clickable(onClick = onFailedClick)
                    CallStatus.DAILY_LIMIT_REACHED -> Modifier.clickable(onClick = onLimitClick)
                    else -> Modifier
                }
            ),
        shape = AderaShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Item 11: show contact name if resolved, otherwise show raw number
                if (contactName != null) {
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.callerNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = entry.callerNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.timestamp.toRelativeTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Item 9: show resolved SIM label
                    if (simLabel != null) {
                        Text(
                            text = " · $simLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Item 7: hint text on Failed entries that they can tap for more info
                if (entry.status == CallStatus.FAILED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            StatusChip(entry.status)
        }
    }
}

@Composable
private fun StatusChip(status: CallStatus) {
    val (label, bg, contentColor) = when (status) {
        CallStatus.SENT                   -> Triple("Sent",      MaterialTheme.colorScheme.primaryContainer,   MaterialTheme.colorScheme.onPrimaryContainer)
        CallStatus.FAILED                 -> Triple("Failed",    MaterialTheme.colorScheme.errorContainer,     MaterialTheme.colorScheme.onErrorContainer)
        CallStatus.SUPPRESSED_QUIET_HOURS -> Triple("Quiet hrs", MaterialTheme.colorScheme.surface,            MaterialTheme.colorScheme.onSurfaceVariant)
        CallStatus.SUPPRESSED_COOLDOWN    -> Triple("Cooldown",  MaterialTheme.colorScheme.surface,            MaterialTheme.colorScheme.onSurfaceVariant)
        CallStatus.PENDING                -> Triple("Pending",   MaterialTheme.colorScheme.surface,            MaterialTheme.colorScheme.onSurfaceVariant)
        CallStatus.DAILY_LIMIT_REACHED    -> Triple("Limit Reached", MaterialTheme.colorScheme.surface,        MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")

private fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }
}
