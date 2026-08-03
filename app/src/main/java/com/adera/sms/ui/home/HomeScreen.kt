package com.adera.sms.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToTemplates: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val settings       by viewModel.settings.collectAsStateWithLifecycle()
    val template       by viewModel.defaultTemplate.collectAsStateWithLifecycle()
    val permissions    by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    // Refresh permissions whenever screen comes back into view
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissions()
        }
    }

    val isOn = settings?.autoReplyEnabled == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GreenBgDark, GreenSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── App bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Adera SMS", style = MaterialTheme.typography.headlineMedium,
                        color = OnDarkPrimary, fontWeight = FontWeight.Bold)
                    Text("Missing call auto-reply", style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary)
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings",
                        tint = OnDarkSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Permission warning banner ────────────────────────────────────
            if (!permissions.allCoreGranted) {
                PermissionWarningBanner(onNavigateToSettings)
                Spacer(Modifier.height(16.dp))
            }

            // ── Master toggle card ──────────────────────────────────────────
            val cardColor by animateColorAsState(
                targetValue = if (isOn) GreenSurfaceVariant else GreenSurface,
                animationSpec = tween(400), label = "card"
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text  = if (isOn) "Auto-Reply ON" else "Auto-Reply OFF",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isOn) GoldPrimary else OnDarkSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = if (isOn) "Callers will receive your message"
                                else "Tap the switch to enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    // Gold toggle — the single CTA element on this screen
                    Switch(
                        checked  = isOn,
                        onCheckedChange = { enabled ->
                            if (permissions.allCoreGranted) {
                                viewModel.toggleAutoReply(enabled)
                            }
                        },
                        enabled = permissions.allCoreGranted,
                        colors  = SwitchDefaults.colors(
                            checkedThumbColor      = GoldPrimary,
                            checkedTrackColor      = GoldDark.copy(alpha = 0.5f),
                            uncheckedThumbColor    = OnDarkDisabled,
                            uncheckedTrackColor    = GreenOutline
                        ),
                        modifier = Modifier.size(width = 64.dp, height = 36.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Active template card ─────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToTemplates),
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GreenSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Reply", style = MaterialTheme.typography.labelSmall,
                            color = OnDarkSecondary, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text     = template?.text ?: "No template selected",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = if (template != null) OnDarkPrimary else OnDarkDisabled,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (template != null) {
                            Spacer(Modifier.height(4.dp))
                            val lang = if (template?.language == "am") "አማርኛ" else "English"
                            Text("$lang · ${template?.text?.length ?: 0}/160 chars",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnDarkDisabled)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit template",
                        tint = GoldPrimary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Quick-action row ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    modifier  = Modifier.weight(1f),
                    icon      = Icons.Default.List,
                    label     = "Activity Log",
                    onClick   = onNavigateToLog
                )
                QuickActionCard(
                    modifier  = Modifier.weight(1f),
                    icon      = Icons.Default.Settings,
                    label     = "Settings",
                    onClick   = onNavigateToSettings
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Status strip ────────────────────────────────────────────────
            StatusStrip(permissions = permissions)
        }
    }
}

@Composable
private fun PermissionWarningBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ember.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Permissions required", style = MaterialTheme.typography.labelLarge,
                color = EmberLight, fontWeight = FontWeight.SemiBold)
            Text("Tap to fix — auto-reply won't work until resolved",
                style = MaterialTheme.typography.bodySmall, color = EmberLight.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors    = CardDefaults.cardColors(containerColor = GreenSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Green600, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnDarkSecondary,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusStrip(permissions: PermissionStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GreenSurface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatusDot("SMS", permissions.hasSendSms)
        StatusDot("Phone", permissions.hasPhoneState)
        StatusDot("Call log", permissions.hasCallLog)
        StatusDot("Battery", permissions.hasBatteryExemption)
    }
}

@Composable
private fun StatusDot(label: String, ok: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (ok) Green600 else Ember)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = OnDarkDisabled, fontSize = 10.sp)
    }
}
