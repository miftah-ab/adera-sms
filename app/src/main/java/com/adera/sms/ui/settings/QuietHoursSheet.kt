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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursSheet(
    onDismissRequest: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    
    // Parse current quiet hours
    val currentStart = settings?.quietHoursStart ?: 0
    val currentEnd = settings?.quietHoursEnd ?: 0
    val isCurrentlyEnabled = currentStart != currentEnd
    
    var isEnabled by remember { mutableStateOf(isCurrentlyEnabled) }
    
    val startTimeState = rememberTimePickerState(
        initialHour = currentStart / 60,
        initialMinute = currentStart % 60,
        is24Hour = false
    )
    val endTimeState = rememberTimePickerState(
        initialHour = currentEnd / 60,
        initialMinute = currentEnd % 60,
        is24Hour = false
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
                    Text("Auto-replies are paused during this time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.secondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isEnabled) {
                // Since M3 TimePicker takes up a lot of vertical space, 
                // typically we'd show the time text and tap to open a dialog picker.
                // Or we can show the dial inline if it's the only one. 
                // Since there's start and end, we'll use a simplified inline selector or show the text and tap to pick.
                // For this, we'll just show the start time picker inline.
                
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
                        viewModel.setQuietHours(
                            startTimeState.hour * 60 + startTimeState.minute,
                            endTimeState.hour * 60 + endTimeState.minute
                        )
                    } else {
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
