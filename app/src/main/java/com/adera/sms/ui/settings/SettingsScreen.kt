package com.adera.sms.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.BuildConfig
import com.adera.sms.update.UpdateStatus
import com.adera.sms.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToQuietHours: () -> Unit,
    onBack: () -> Unit,
    onForceUpdate: (String) -> Unit
) {
    val context        = LocalContext.current
    val settings       by viewModel.settings.collectAsStateWithLifecycle()
    val batteryIgnored by viewModel.batteryIgnored.collectAsStateWithLifecycle()
    val updateStatus   by viewModel.updateStatus.collectAsStateWithLifecycle()
    val isChecking     by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshBatteryStatus()
        }
    }

    // Handle forced update navigation
    LaunchedEffect(updateStatus) {
        if (updateStatus is UpdateStatus.ForceUpdate) {
            onForceUpdate((updateStatus as UpdateStatus.ForceUpdate).info.downloadUrl)
        }
    }

    // Battery optimization launcher
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshBatteryStatus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GreenBgDark, GreenSurface)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 48.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = OnDarkPrimary)
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge,
                    color = OnDarkPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }

            // ── Section: Permissions ─────────────────────────────────────────
            SectionHeader("Permissions")
            SettingsCard {
                PermissionRow("Send SMS",      hasSmsPermission(context))
                PermissionRow("Phone state",   hasPermission(context, Manifest.permission.READ_PHONE_STATE))
                PermissionRow("Call log",      hasPermission(context, Manifest.permission.READ_CALL_LOG))
                HorizontalDivider(color = GreenOutline, modifier = Modifier.padding(vertical = 4.dp))
                PermissionRow("Battery optimization exempt", batteryIgnored,
                    actionLabel = if (!batteryIgnored) "Fix →" else null,
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .apply { data = Uri.parse("package:${context.packageName}") }
                        batteryLauncher.launch(intent)
                    }
                )
            }

            // OEM-specific battery guide
            if (!batteryIgnored) {
                OemBatteryGuide()
            }

            // ── Section: Quiet Hours ─────────────────────────────────────────
            SectionHeader("Quiet Hours")
            SettingsCard {
                val qs = settings?.quietHoursStart ?: 0
                val qe = settings?.quietHoursEnd ?: 0
                val enabled = qs != qe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToQuietHours)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Quiet Hours", style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkPrimary)
                        Text(
                            if (enabled) "${qs.toTimeString()} – ${qe.toTimeString()}"
                            else "Not configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) GoldPrimary else OnDarkDisabled
                        )
                    }
                    Text("Configure →", style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary)
                }
            }

            // ── Section: Updates ─────────────────────────────────────────────
            SectionHeader("Updates")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall, color = OnDarkSecondary)
                    when (val s = updateStatus) {
                        is UpdateStatus.UpdateAvailable ->
                            Text("⬆ Update available: ${s.info.releaseNotes}",
                                style = MaterialTheme.typography.bodySmall, color = GoldPrimary)
                        is UpdateStatus.UpToDate ->
                            Text("✓ You're on the latest version",
                                style = MaterialTheme.typography.bodySmall, color = Green600)
                        is UpdateStatus.Error ->
                            Text("Check failed: ${s.message}",
                                style = MaterialTheme.typography.bodySmall, color = Ember)
                        else -> Unit
                    }
                    Button(
                        onClick  = { viewModel.checkForUpdate() },
                        enabled  = !isChecking,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary, contentColor = Black)
                    ) {
                        if (isChecking) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), color = Black, strokeWidth = 2.dp)
                        else Text("Check for Update", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Section: Privacy ─────────────────────────────────────────────
            SectionHeader("Privacy")
            SettingsCard {
                Text(
                    "Adera SMS is fully offline. No calls, no numbers, no messages leave your device. " +
                    "The activity log is stored locally and never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Anonymous analytics (optional)",
                        style = MaterialTheme.typography.bodySmall, color = OnDarkPrimary)
                    Switch(
                        checked = settings?.analyticsOptIn == true,
                        onCheckedChange = { viewModel.setAnalyticsOptIn(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = GoldPrimary,
                            checkedTrackColor  = GoldDark.copy(alpha = 0.4f)
                        )
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = OnDarkSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = GreenSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Green600 else Ember,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (granted) OnDarkPrimary else OnDarkSecondary)
        }
        if (!granted && actionLabel != null && onAction != null) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = GoldPrimary,
                modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
private fun OemBatteryGuide() {
    val brand = android.os.Build.MANUFACTURER.lowercase()
    val steps = when {
        brand.contains("tecno") || brand.contains("infinix") || brand.contains("itel") ->
            "Settings → Battery → App Power Management → find Adera SMS → select 'Don't restrict'"
        brand.contains("samsung") ->
            "Settings → Apps → Adera SMS → Battery → select 'Unrestricted'"
        brand.contains("huawei") || brand.contains("honor") ->
            "Settings → Apps → Adera SMS → Battery → Enable auto-launch and allow background activity"
        brand.contains("xiaomi") || brand.contains("poco") || brand.contains("redmi") ->
            "Settings → Apps → Manage apps → Adera SMS → Battery saver → select 'No restrictions'"
        brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
            "Settings → Battery → Battery optimization → All apps → Adera SMS → Don't optimize"
        else ->
            "Settings → Battery → Battery Optimization → All apps → Adera SMS → Don't optimize"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Ember.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("For ${android.os.Build.MANUFACTURER}:",
                style = MaterialTheme.typography.labelMedium,
                color = EmberLight, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(steps, style = MaterialTheme.typography.bodySmall, color = OnDarkSecondary)
        }
    }
}

private fun hasPermission(ctx: android.content.Context, perm: String) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun hasSmsPermission(ctx: android.content.Context) =
    hasPermission(ctx, Manifest.permission.SEND_SMS)

private fun Int.toTimeString(): String {
    val h = this / 60
    val m = this % 60
    return String.format("%02d:%02d", h, m)
}
