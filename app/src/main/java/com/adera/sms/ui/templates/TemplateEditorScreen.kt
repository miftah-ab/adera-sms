package com.adera.sms.ui.templates

import android.telephony.SmsMessage
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.data.entity.MessageTemplate
import com.adera.sms.service.SmsSenderWorker
import com.adera.sms.ui.theme.AderaShapes
import kotlinx.coroutines.launch

/**
 * Calculates SMS segment info accounting for encoding.
 * SmsMessage.calculateLength correctly handles GSM7 (160 chars/seg) vs Unicode (70 chars/seg).
 * Returns Pair(segmentCount, charsUsedInFinalSegment).
 *
 * Item 14: The [signature] is included in the calculation so the counter reflects
 * the true final message length including the mandatory "\n\nBy Adera SMS" suffix.
 */
private fun smsSegmentInfo(userText: String, signature: String): Pair<Int, Int> {
    val fullText = userText + signature
    if (fullText.isEmpty()) return Pair(1, 0)
    // calculateLength returns [codeUnits, codeUnitsRemaining, bytesPerChar, codeUnitsPerPage, codeUnitCount]
    val lengths = SmsMessage.calculateLength(fullText, false)
    val segments  = lengths[0]
    val charsInLastSeg = lengths[4] // total chars across segments used
    return Pair(segments, charsInLastSeg)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    onBack: () -> Unit,
    viewModel: TemplateViewModel = viewModel()
) {
    val haptic = LocalHapticFeedback.current
    val templates by viewModel.templates.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedLang by remember { mutableStateOf("en") }

    val filtered = templates.filter { it.language == selectedLang }

    var showEditSheet by remember { mutableStateOf(false) }
    var templateToEdit by remember { mutableStateOf<MessageTemplate?>(null) }

    // Item 16: "Coming soon" dialog state for New Template
    var showComingSoonDialog by remember { mutableStateOf(false) }

    if (showComingSoonDialog) {
        AlertDialog(
            onDismissRequest = { showComingSoonDialog = false },
            shape = AderaShapes.large,
            icon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "Coming Soon",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text  = { 
                Text(
                    "Creating custom templates is coming soon. You can edit your existing templates in the meantime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showComingSoonDialog = false 
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                Text("Templates", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Segmented control with 48dp min touch target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("en" to "English", "am" to "Amharic").forEach { (code, label) ->
                        val selected = selectedLang == code
                        val containerColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        val contentColor   by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(containerColor)
                                .clickable { 
                                    if (selectedLang != code) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedLang = code 
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        // Item 16: Always show "Coming soon" — do not open edit sheet for new templates
                        showComingSoonDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add new template")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(filtered, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onSetDefault = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setDefault(template.id) 
                    },
                    onEdit = {
                        templateToEdit = template
                        showEditSheet = true
                    }
                )
            }
        }
    }

    if (showEditSheet) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        EditTemplateSheet(
            template = templateToEdit,
            language = selectedLang,
            onSave = { text ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val current = templateToEdit
                if (current == null) {
                    // saveCustomTemplate is suspend — launch from scope.
                    // If the limit is hit it returns false and the IAM prompt fires;
                    // keep the sheet open so the user's text is preserved.
                    scope.launch {
                        val saved = viewModel.saveCustomTemplate(text, selectedLang)
                        if (saved) {
                            showEditSheet = false
                            templateToEdit = null
                        }
                        // If not saved (limit hit), the In-App Messaging prompt fires
                        // automatically; the sheet remains open with the user's text.
                    }
                } else {
                    // Fix #5: update the existing row — do NOT insert a duplicate
                    viewModel.updateCustomTemplate(current.copy(text = text.trim()))
                    showEditSheet = false
                    templateToEdit = null
                }
            },
            onDismiss = {
                showEditSheet = false
                templateToEdit = null
            }
        )
    }
}

@Composable
private fun TemplateCard(
    template: MessageTemplate,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSetDefault),
        shape = AderaShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (template.isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (template.isDefault) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (template.language == "am") "Amharic" else "English",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = template.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = onEdit) {
                Text("Edit", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTemplateSheet(
    template: MessageTemplate?,
    language: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(template?.text ?: "") }

    // Item 10 & 14: Include the signature in segment calculation, handle Unicode correctly
    val (segmentCount, _) = remember(text) { smsSegmentInfo(text, SmsSenderWorker.SIGNATURE) }
    val fullLength = text.length + SmsSenderWorker.SIGNATURE.length

    // WCAG 2.2 compliant contrast: high-contrast error for > 1 segment warnings, onSurfaceVariant for standard
    val counterColor by animateColorAsState(
        targetValue = when {
            segmentCount > 1 -> MaterialTheme.colorScheme.error
            else             -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (template == null) "New Message" else "Edit Message",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Type your message...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = AderaShapes.small
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Signature preview
            Text(
                text = "+\n\nBy Adera SMS (appended automatically)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (language == "am") "Amharic" else "English",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Item 10 + 14: Show total chars including signature and correct SMS segment count
                Text(
                    text = "$fullLength chars • $segmentCount SMS",
                    style = MaterialTheme.typography.bodySmall,
                    color = counterColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(buttonScale),
                interactionSource = interactionSource,
                shape = RoundedCornerShape(percent = 50)
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
