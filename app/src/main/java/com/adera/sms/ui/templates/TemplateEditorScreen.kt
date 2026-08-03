package com.adera.sms.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adera.sms.data.entity.MessageTemplate
import com.adera.sms.ui.theme.*

private const val SMS_MAX_CHARS = 160

@Composable
fun TemplateEditorScreen(
    viewModel: TemplateViewModel = viewModel(),
    onBack: () -> Unit
) {
    val templates    by viewModel.templates.collectAsStateWithLifecycle()
    var selectedLang by remember { mutableStateOf("en") }
    var showAddSheet by remember { mutableStateOf(false) }

    val filtered = templates.filter { it.language == selectedLang }

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
                Text("Message Templates", style = MaterialTheme.typography.titleLarge,
                    color = OnDarkPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
            }

            // ── Language selector ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("en" to "English", "am" to "አማርኛ").forEach { (code, label) ->
                    val selected = selectedLang == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Green800 else GreenSurface)
                            .border(1.dp, if (selected) GoldPrimary else GreenOutline, RoundedCornerShape(10.dp))
                            .clickable { selectedLang = code }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) GoldPrimary else OnDarkSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Template list ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { template ->
                    TemplateCard(
                        template  = template,
                        onSetDefault  = { viewModel.setDefault(template.id) },
                        onDelete  = if (template.isPreset) null
                                    else {{ viewModel.deleteCustomTemplate(template.id) }}
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }  // FAB padding
            }
        }

        // ── FAB ──────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick          = { showAddSheet = true },
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor   = GoldPrimary,
            contentColor     = Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add custom template")
        }
    }

    // ── Add-template bottom sheet ────────────────────────────────────────────
    if (showAddSheet) {
        AddTemplateSheet(
            language  = selectedLang,
            onSave    = { text ->
                viewModel.saveCustomTemplate(text, selectedLang)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

@Composable
private fun TemplateCard(
    template: MessageTemplate,
    onSetDefault: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val borderColor = if (template.isDefault) GoldPrimary else GreenOutline
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSetDefault),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (template.isDefault) GreenSurfaceVariant else GreenSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (template.isDefault) {
                    Text("✓ ACTIVE", style = MaterialTheme.typography.labelSmall,
                        color = GoldPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                }
                Text(template.text, style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkPrimary)
                Spacer(Modifier.height(4.dp))
                Text("${template.text.length} / $SMS_MAX_CHARS chars",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (template.text.length > SMS_MAX_CHARS) Ember else OnDarkDisabled)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = Ember, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTemplateSheet(
    language: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isOver = text.length > SMS_MAX_CHARS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = GreenSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("New ${if (language == "am") "Amharic" else "English"} Template",
                style = MaterialTheme.typography.titleMedium, color = OnDarkPrimary,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value           = text,
                onValueChange   = { text = it },
                placeholder     = { Text("Type your message…", color = OnDarkHint) },
                modifier        = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                maxLines        = 5,
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GoldPrimary,
                    unfocusedBorderColor = GreenOutline,
                    focusedTextColor     = OnDarkPrimary,
                    unfocusedTextColor   = OnDarkPrimary,
                    cursorColor          = GoldPrimary
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${text.length} / $SMS_MAX_CHARS",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOver) Ember else OnDarkDisabled)
                if (isOver) Text("Will be split into multiple SMS",
                    style = MaterialTheme.typography.bodySmall, color = Ember)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = { if (text.isNotBlank()) onSave(text.trim()) },
                enabled  = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary, contentColor = Black)
            ) {
                Text("Save Template", fontWeight = FontWeight.Bold)
            }
        }
    }
}
