package com.adera.sms.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.adera.sms.BuildConfig
import com.adera.sms.ui.theme.AderaShapes
import com.adera.sms.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onForceUpdate: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showQuietHoursSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var privacyText by remember { mutableStateOf("Loading...") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            privacyText = try {
                context.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) { "Error loading Privacy Policy." }
        }
    }

    LaunchedEffect(updateStatus) {
        val s = updateStatus
        if (s is UpdateStatus.ForceUpdate) {
            onForceUpdate(s.info.downloadUrl)
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Data?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("This will permanently delete all your templates, activity logs, and settings. This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacySheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showPrivacySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    "Privacy Policy",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        Text(
                            text = privacyText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showPrivacySheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Close") }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                CenterAlignedTopAppBar(
                    title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {

            // APP SETTINGS
            SectionTitle("App Settings")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.NightsStay,
                    title = "Quiet Hours",
                    subtitle = "Pause replies at night",
                    onClick = { showQuietHoursSheet = true }
                )
            }

            // SHARE APP
            SectionTitle("Share")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Share,
                    title = "Share App",
                    subtitle = "Send the APK to another device via Xender, SHAREit or Bluetooth",
                    onClick = {
                        scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    val src = java.io.File(context.applicationInfo.sourceDir)
                                    val dst = java.io.File(context.cacheDir, "AderaSMS.apk")
                                    src.copyTo(dst, overwrite = true)
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        dst
                                    )
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Adera SMS"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }

            // PRIVACY & DATA
            SectionTitle("Privacy and Data")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "Read how your data is handled locally",
                    onClick = { showPrivacySheet = true }
                )
                Divider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all templates and logs",
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showClearDataDialog = true }
                )
            }

            // ABOUT
            SectionTitle("About")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
                Divider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Check for Updates row — shows real network result
                val updateSubtitle = when (val s = updateStatus) {
                    is UpdateStatus.UpdateAvailable -> "Update available: ${s.info.releaseNotes}"
                    is UpdateStatus.UpToDate -> "You are using the latest version."
                    is UpdateStatus.Error -> "Check failed. Try again when you have internet."
                    is UpdateStatus.ForceUpdate -> "Critical update required"
                    null -> "Tap to check for updates"
                }
                val updateColor = when (updateStatus) {
                    is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.secondary
                    is UpdateStatus.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isChecking) viewModel.checkForUpdate() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for Update", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(updateSubtitle, style = MaterialTheme.typography.bodySmall, color = updateColor)
                    }
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else if (updateStatus is UpdateStatus.UpdateAvailable) {
                        val downloadUrl = (updateStatus as UpdateStatus.UpdateAvailable).info.downloadUrl
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                        }) {
                            Text("Download", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Support contact row — Telegram only
                SettingsRow(
                    icon = Icons.Rounded.SupportAgent,
                    title = "Contact Support",
                    subtitle = "Reach us on Telegram",
                    onClick = {
                        try {
                            val telegramUrl = "https://t.me/Adera_SMS"
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(telegramUrl))
                            )
                        } catch (e: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(
                                context,
                                "Telegram is not installed. Handle: @Adera_SMS",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                
                Divider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Diagnostic row — Heartbeat
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "Service Diagnostic",
                    subtitle = "Heartbeat: " + (settings?.lastServiceHeartbeat?.let {
                        if (it == 0L) "Never" else {
                            val diff = (System.currentTimeMillis() - it) / 60000
                            if (diff == 0L) "Just now" else "$diff min ago"
                        }
                    } ?: "Unknown")
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showQuietHoursSheet) {
        QuietHoursSheet(onDismissRequest = { showQuietHoursSheet = false })
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 40.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = AderaShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
