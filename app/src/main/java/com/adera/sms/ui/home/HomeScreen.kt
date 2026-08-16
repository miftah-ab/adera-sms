package com.adera.sms.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.ui.settings.QuietHoursSheet
import com.adera.sms.ui.theme.AderaShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTemplates: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val template by viewModel.defaultTemplate.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()

    val isOn = settings?.autoReplyEnabled == true

    var showQuietHoursSheet by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer) // Primary-dark surface
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                val appName = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onPrimaryContainer)) {
                        append("Adera ")
                    }
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                        append("SMS")
                    }
                }
                Text(text = appName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isOn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOn) "Active, auto reply protection enabled" else "Inactive, auto replies paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            if (!permissions.allCoreGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = AderaShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permissions missing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Auto-reply cannot function", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        TextButton(onClick = {
                            val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.SEND_SMS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permLauncher.launch(perms.toTypedArray())
                        }) {
                            Text("Fix now", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (!permissions.hasBatteryExemption) {
                 Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = AderaShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.BatteryAlert, contentDescription = "Battery Warning", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery restricted", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Service may be killed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Fix now", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Master Toggle Card
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.98f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                shape = AderaShapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Auto-reply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOn) "Turned On" else "Turned Off",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = isOn,
                        onCheckedChange = { isChecked -> 
                            if (isChecked && !permissions.allCoreGranted) {
                                // Request permissions directly from the toggle
                                val perms = mutableListOf(
                                    android.Manifest.permission.READ_PHONE_STATE, 
                                    android.Manifest.permission.READ_CALL_LOG, 
                                    android.Manifest.permission.SEND_SMS
                                )
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permLauncher.launch(perms.toTypedArray())
                            } else {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.toggleAutoReply(isChecked) 
                            }
                        },
                        enabled = true,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        interactionSource = interactionSource
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Active Message Section
            Text("ACTIVE MESSAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Card(
                shape = AderaShapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (template?.language == "am") "Amharic" else "English",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        TextButton(onClick = onNavigateToTemplates) {
                            Text("Edit", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = template?.text ?: "No template selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            /* ── Support Adera entry point (Ye Buna) — temporarily hidden ──────
             * Uncomment this entire block when ready to re-enable.
            val yebunaUrl = "https://ye-buna.com/PrimeWisdom"
            var cardVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { cardVisible = true }

            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 600)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 3 }
                        )
            ) {
                // ... full Ye Buna card UI ...
            } */

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Activity Preview
            if (recentLogs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RECENT ACTIVITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "View all", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToLog() }
                    )
                }
                
                recentLogs.take(2).forEach { entry ->
                    // Resolve caller number to a contact name (READ_CONTACTS optional)
                    val contactName = remember(entry.callerNumber) {
                        runCatching {
                            val uri = android.net.Uri.withAppendedPath(
                                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                android.net.Uri.encode(entry.callerNumber)
                            )
                            context.contentResolver.query(
                                uri,
                                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                                null, null, null
                            )?.use { c ->
                                if (c.moveToFirst()) c.getString(0) else null
                            }
                        }.getOrNull()
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = AderaShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Show contact name prominently if available, number as subtitle
                                if (contactName != null) {
                                    Text(
                                        contactName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        entry.callerNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        entry.callerNumber,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = android.text.format.DateUtils.getRelativeTimeSpanString(
                                        entry.timestamp,
                                        System.currentTimeMillis(),
                                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                                    ).toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            val (label, bg, contentColor) = when (entry.status) {
                                com.adera.sms.data.entity.CallStatus.SENT -> Triple("Sent", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                com.adera.sms.data.entity.CallStatus.FAILED -> Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                com.adera.sms.data.entity.CallStatus.SUPPRESSED_QUIET_HOURS -> Triple("Quiet hrs", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> Triple(entry.status.name.lowercase().replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Surface(shape = RoundedCornerShape(percent = 50), color = bg) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Settings Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showQuietHoursSheet = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.NightsStay, contentDescription = "Quiet Hours", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet Hours", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Pause replies at night", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Old Ye Buna location removed
        }
    }

    if (showQuietHoursSheet) {
        QuietHoursSheet(onDismissRequest = { showQuietHoursSheet = false })
    }
}
