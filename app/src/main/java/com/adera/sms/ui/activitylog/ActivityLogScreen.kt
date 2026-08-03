package com.adera.sms.ui.activitylog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.data.entity.CallLogEntry
import com.adera.sms.data.entity.CallStatus
import com.adera.sms.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityLogScreen(
    viewModel: ActivityLogViewModel = viewModel(),
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GreenBgDark, GreenSurface)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

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
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Activity Log", style = MaterialTheme.typography.titleLarge,
                        color = OnDarkPrimary, fontWeight = FontWeight.Bold)
                    Text("${entries.size} events", style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary)
                }
            }

            if (entries.isEmpty()) {
                // ── Empty state ───────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("No activity yet", style = MaterialTheme.typography.titleMedium,
                            color = OnDarkSecondary)
                        Text("Missed calls will appear here",
                            style = MaterialTheme.typography.bodySmall, color = OnDarkDisabled)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LogEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: CallLogEntry) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = GreenSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(entry.status.color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.callerNumberMasked,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(entry.timestamp.toRelativeTime(),
                    style = MaterialTheme.typography.bodySmall, color = OnDarkSecondary)
                if (entry.simSlot != -1) {
                    Text("SIM ${entry.simSlot}",
                        style = MaterialTheme.typography.bodySmall, color = OnDarkDisabled)
                }
            }
            // Status chip
            StatusChip(entry.status)
        }
    }
}

@Composable
private fun StatusChip(status: CallStatus) {
    val (label, bg) = when (status) {
        CallStatus.SENT                     -> "Sent"       to Green700.copy(alpha = 0.25f)
        CallStatus.FAILED                   -> "Failed"     to Ember.copy(alpha = 0.2f)
        CallStatus.SUPPRESSED_QUIET_HOURS   -> "Quiet hrs"  to GoldDark.copy(alpha = 0.2f)
        CallStatus.SUPPRESSED_COOLDOWN      -> "Cooldown"   to GreenOutline
        CallStatus.PENDING                  -> "Pending"    to GreenSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = status.color,
            fontWeight = FontWeight.SemiBold)
    }
}

private val CallStatus.color get() = when (this) {
    CallStatus.SENT                   -> Green600
    CallStatus.FAILED                 -> Ember
    CallStatus.SUPPRESSED_QUIET_HOURS -> GoldPrimary
    CallStatus.SUPPRESSED_COOLDOWN    -> OnDarkSecondary
    CallStatus.PENDING                -> OnDarkDisabled
}

private val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
private fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000       -> "Just now"
        diff < 3_600_000    -> "${diff / 60_000}m ago"
        diff < 86_400_000   -> "${diff / 3_600_000}h ago"
        else                -> timeFormat.format(Date(this))
    }
}
