package com.adera.sms.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val initStart = settings?.quietHoursStart ?: 0
    val initEnd   = settings?.quietHoursEnd   ?: 0

    // Local state for editing (not saved until user taps Save)
    var startHour by remember(initStart) { mutableIntStateOf(initStart / 60) }
    var startMin  by remember(initStart) { mutableIntStateOf(initStart % 60) }
    var endHour   by remember(initEnd)   { mutableIntStateOf(initEnd / 60) }
    var endMin    by remember(initEnd)   { mutableIntStateOf(initEnd % 60) }
    var enabled   by remember { mutableStateOf(initStart != initEnd) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GreenBgDark, GreenSurface)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 0.dp, top = 48.dp, end = 0.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = OnDarkPrimary)
                }
                Text("Quiet Hours", style = MaterialTheme.typography.titleLarge,
                    color = OnDarkPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(8.dp))

            Text("No auto-reply will be sent during this window.",
                style = MaterialTheme.typography.bodyMedium, color = OnDarkSecondary)

            Spacer(Modifier.height(24.dp))

            // ── Enable toggle ───────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = GreenSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Quiet Hours", style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkPrimary)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GoldPrimary,
                            checkedTrackColor = GoldDark.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Time pickers ─────────────────────────────────────────────────
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimePicker(
                        modifier = Modifier.weight(1f),
                        label    = "Start",
                        hour     = startHour,
                        minute   = startMin,
                        onHourChange   = { startHour = it },
                        onMinuteChange = { startMin = it }
                    )
                    TimePicker(
                        modifier = Modifier.weight(1f),
                        label    = "End",
                        hour     = endHour,
                        minute   = endMin,
                        onHourChange   = { endHour = it },
                        onMinuteChange = { endMin = it }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Visual confirmation of the range
                val startStr = "%02d:%02d".format(startHour, startMin)
                val endStr   = "%02d:%02d".format(endHour, endMin)
                val startTotal = startHour * 60 + startMin
                val endTotal   = endHour * 60 + endMin
                val overnight  = startTotal > endTotal
                Text(
                    "$startStr – $endStr${if (overnight) " (overnight)" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Save button ─────────────────────────────────────────────────
            Button(
                onClick = {
                    if (enabled) {
                        viewModel.setQuietHours(
                            startHour * 60 + startMin,
                            endHour * 60 + endMin
                        )
                    } else {
                        viewModel.setQuietHours(0, 0)  // 0==0 means disabled
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary, contentColor = Black)
            ) {
                Text("Save", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TimePicker(
    modifier: Modifier,
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = GreenSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = OnDarkSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.headlineMedium,
                color = GoldPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Hour
            Text("Hour", style = MaterialTheme.typography.bodySmall, color = OnDarkDisabled)
            Slider(
                value = hour.toFloat(),
                onValueChange = { onHourChange(it.toInt()) },
                valueRange = 0f..23f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor       = GoldPrimary,
                    activeTrackColor = GoldPrimary
                )
            )
            // Minute
            Text("Minute", style = MaterialTheme.typography.bodySmall, color = OnDarkDisabled)
            Slider(
                value = minute.toFloat(),
                onValueChange = { onMinuteChange((it / 5).toInt() * 5) }, // snap to 5 min
                valueRange = 0f..55f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor       = GoldPrimary,
                    activeTrackColor = GoldPrimary
                )
            )
        }
    }
}
