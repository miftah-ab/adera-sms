package com.adera.sms.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

/**
 * Quiet Hours bottom sheet.
 *
 * Item 1 fix: The time picker states are initialized from current DB values but the
 * isEnabled toggle was using a stale `remember` snapshot. Fixed by deriving isEnabled
 * directly from DB state and using the TimePickerState values at save time, which always
 * reflects the current picker selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursSheet(
    onDismissRequest: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Parse current quiet hours from DB — these are the "saved" values
    val savedStart = settings?.quietHoursStart ?: 0
    val savedEnd   = settings?.quietHoursEnd   ?: 0

    // isEnabled is driven by DB state: enabled when start != end
    var isEnabled by remember(savedStart, savedEnd) {
        mutableStateOf(savedStart != savedEnd)
    }

    // Picker states initialized from DB values. rememberTimePickerState keys on the
    // saved values so that if the sheet is dismissed and reopened the picker correctly
    // reflects the last persisted selection.
    val startTimeState = rememberTimePickerState(
        initialHour   = if (savedStart != 0) savedStart / 60 else 22,
        initialMinute = savedStart % 60,
        is24Hour      = false
    )
    val endTimeState = rememberTimePickerState(
        initialHour   = if (savedEnd != 0) savedEnd / 60 else 7,
        initialMinute = savedEnd % 60,
        is24Hour      = false
    )

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Quiet Hours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Auto replies are paused during this time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor  = MaterialTheme.colorScheme.secondary,
                        checkedTrackColor  = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isEnabled) {
                Text("Start Time", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TimePicker(state = startTimeState)

                Spacer(modifier = Modifier.height(24.dp))

                Text("End Time", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TimePicker(state = endTimeState)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isEnabled) {
                        // Read from picker state at click time — this is the source of truth
                        val startMinutes = startTimeState.hour * 60 + startTimeState.minute
                        val endMinutes   = endTimeState.hour * 60 + endTimeState.minute
                        // Guard: if start == end after rounding, nudge end by 1 min to keep enabled
                        val finalEnd = if (startMinutes == endMinutes) endMinutes + 1 else endMinutes
                        viewModel.setQuietHours(startMinutes, finalEnd)
                    } else {
                        // Disabled: store 0,0 which the service treats as "no quiet hours"
                        viewModel.setQuietHours(0, 0)
                    }
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
