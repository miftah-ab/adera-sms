package com.adera.sms.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.adera.sms.data.AppDatabase
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var agreed by remember { mutableStateOf(false) }

    var showPrivacySheet by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }

    var privacyText by remember { mutableStateOf("Loading...") }
    var termsText by remember { mutableStateOf("Loading...") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            privacyText = try {
                context.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) { "Error loading Privacy Policy." }
            termsText = try {
                context.assets.open("terms_of_service.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) { "Error loading Terms of Service." }
        }
    }

    // Intercept back button so they cannot dismiss the consent gate (Fix 6)
    BackHandler(enabled = true) {}

    if (showPrivacySheet) {
        ModalBottomSheet(onDismissRequest = { showPrivacySheet = false }) {
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

    if (showTermsSheet) {
        ModalBottomSheet(onDismissRequest = { showTermsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    "Terms of Service",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        Text(
                            text = termsText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showTermsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Close") }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Hero Area
            Text(
                text = "Adera SMS",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Adera SMS replies to missed calls automatically, entirely on your phone.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Nothing is uploaded. Call and message data never leave this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Consent Checkbox Row with expanded tappable area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        agreed = !agreed
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                val annotatedString = buildAnnotatedString {
                    append("I agree to the ")
                    pushStringAnnotation(tag = "privacy", annotation = "privacy")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append("Privacy Policy")
                    }
                    pop()
                    append(" and ")
                    pushStringAnnotation(tag = "terms", annotation = "terms")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append("Terms of Service")
                    }
                    pop()
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        val privacyAnnotation = annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset).firstOrNull()
                        val termsAnnotation = annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset).firstOrNull()
                        
                        if (privacyAnnotation != null) {
                            showPrivacySheet = true
                        } else if (termsAnnotation != null) {
                            showTermsSheet = true
                        } else {
                            // Tapping plain text toggles the checkbox
                            agreed = !agreed
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Button
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            
            // Spring scale animation
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
            )

            Button(
                onClick = {
                    if (agreed) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val db = AppDatabase.getInstance(context)
                            db.settingsDao().markConsentGiven(System.currentTimeMillis())
                            com.adera.sms.analytics.AnalyticsManager.onboardingComplete(context)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onOnboardingComplete()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scale),
                interactionSource = interactionSource,
                enabled = agreed,
                shape = RoundedCornerShape(percent = 50)
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
