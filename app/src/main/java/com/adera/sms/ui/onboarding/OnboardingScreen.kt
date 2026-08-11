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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var agreed by remember { mutableStateOf(false) }

    var showPrivacySheet by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }

    // Intercept back button so they cannot dismiss the consent gate (Fix 6)
    BackHandler(enabled = true) {}

    if (showPrivacySheet) {
        ModalBottomSheet(onDismissRequest = { showPrivacySheet = false }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Effective date: January 1, 2025",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Text("1. Data We Collect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Adera SMS operates entirely on your device. The app processes phone call state events and sends SMS messages on your behalf. No call logs, phone numbers, message content, or personal data are transmitted to any external server, cloud service, or third party.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("2. Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The app uses Firebase Analytics to collect anonymous, aggregated usage statistics such as app opens and feature interactions. No personally identifiable information is included. Analytics data helps improve the app and is subject to Google's privacy policy.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("3. SMS Sending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Adera SMS sends text messages from your SIM card using your device's standard SMS capability. You are responsible for any carrier charges that apply. The app sends messages only in response to missed calls, and only when auto-reply is enabled by you.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("4. Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The app requires the following permissions: READ_PHONE_STATE and READ_CALL_LOG to detect missed calls, SEND_SMS to send auto-replies, POST_NOTIFICATIONS to display the background service notification, and RECEIVE_BOOT_COMPLETED to restart after reboot. No permission is used for any purpose beyond what is described above.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("5. Data Retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Activity logs (timestamps and masked caller numbers) are stored locally in the app's private database and are never shared. You can delete all data at any time from Settings → Clear All Data.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("6. Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "For privacy questions or concerns, contact us on Telegram: @Adera_SMS",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showPrivacySheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Close") }
                }
            }
        }
    }

    if (showTermsSheet) {
        ModalBottomSheet(onDismissRequest = { showTermsSheet = false }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Terms of Service",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Effective date: January 1, 2025",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Text("1. Acceptance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "By using Adera SMS, you agree to these Terms of Service. If you do not agree, do not use the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("2. Intended Use", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Adera SMS is designed to send a single automatic SMS reply to callers when you miss a phone call. You are solely responsible for the content of your auto-reply messages and for ensuring your use complies with applicable laws and your mobile carrier's terms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("3. Carrier Charges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Standard SMS rates from your mobile carrier may apply for each auto-reply message sent. Adera SMS does not charge any fee for using the app, but you are responsible for any costs your carrier imposes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("4. No Warranty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Adera SMS is provided \"as is\" without warranty of any kind. We do not guarantee that the app will detect every missed call or that every SMS will be delivered. Factors outside our control — such as network conditions, device battery optimization, or carrier restrictions — may affect performance.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("5. Limitation of Liability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "To the maximum extent permitted by law, the developers of Adera SMS shall not be liable for any direct, indirect, incidental, or consequential damages arising from your use of the app, including but not limited to missed communications, carrier charges, or device compatibility issues.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text("6. Updates to Terms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "We may update these Terms from time to time. Continued use of the app after any update constitutes acceptance of the revised Terms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showTermsSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Close") }
                }
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            // Hero Area
            Text(
                text = "Adera SMS",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(48.dp))

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

            Spacer(modifier = Modifier.weight(0.7f))

            // Consent Checkbox Row
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset).firstOrNull()?.let {
                            showPrivacySheet = true
                        }
                        annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset).firstOrNull()?.let {
                            showTermsSheet = true
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
                    .scale(scale), // Apply spring physics scale
                interactionSource = interactionSource,
                enabled = agreed,
                shape = RoundedCornerShape(percent = 50) // Fully rounded per M3 Expressive
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
