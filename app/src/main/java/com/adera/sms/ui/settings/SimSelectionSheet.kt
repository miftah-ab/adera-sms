package com.adera.sms.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adera.sms.ui.theme.AderaShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSelectionSheet(onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Hardcoded dummy state for UI demonstration, since actual Multi-SIM logic 
    // involves SubscriptionManager which we'd wire up in a ViewModel.
    var selectedSim by remember { mutableStateOf("SIM 1") }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("SIM Selection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "If we can't detect which SIM received a call, we'll use your default SMS SIM.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(modifier = Modifier.selectableGroup()) {
                val simOptions = listOf("SIM 1", "SIM 2")
                simOptions.forEach { simName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .selectable(
                                selected = (simName == selectedSim),
                                onClick = {
                                    selectedSim = simName
                                    scope.launch {
                                        // Normally we'd save this to preferences here
                                        onDismissRequest()
                                    }
                                },
                                role = Role.RadioButton
                            ),
                        shape = AderaShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (simName == selectedSim) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(simName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            RadioButton(
                                selected = (simName == selectedSim),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
