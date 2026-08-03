package com.adera.sms.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.sp
import com.adera.sms.data.AppDatabase
import com.adera.sms.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        emoji = "📵",
        title = "Never Leave a Caller Hanging",
        body  = "Adera SMS automatically sends a reply when you miss a call — " +
                "so callers always know you'll get back to them."
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "100% Private. Stays on Your Phone.",
        body  = "Nothing is uploaded. Nothing leaves your device. " +
                "Your numbers and messages are stored locally only — always."
    ),
    OnboardingPage(
        emoji = "📋",
        title = "Two Permissions. That's All.",
        body  = "We need to read missed calls and send one SMS per call. " +
                "Nothing else. No contacts access. No internet for the core feature."
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val context   = LocalContext.current
    val pagerState = rememberPagerState { pages.size }
    val scope     = rememberCoroutineScope()
    var showPermissions by remember { mutableStateOf(false) }

    // Build the permission list based on API level
    val requiredPerms = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            // Mark onboarding complete and proceed
            scope.launch {
                val db = AppDatabase.getInstance(context)
                db.settingsDao().markOnboardingComplete()
                onOnboardingComplete()
            }
        }
        // If not all granted, user stays on screen and can try again
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GreenBgDark, GreenSurface)))
    ) {
        if (!showPermissions) {
            // ── Slides ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(60.dp))

                // Skip button
                TextButton(
                    onClick = { showPermissions = true },
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) {
                    Text("Skip", color = OnDarkSecondary,
                        style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.weight(1f)
                ) { idx ->
                    OnboardingPageContent(page = pages[idx])
                }

                // Page dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    repeat(pages.size) { idx ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == idx) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (pagerState.currentPage == idx) GoldPrimary else GreenOutline)
                        )
                    }
                }

                // Next / Get Started
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            showPermissions = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(52.dp)
                        .padding(bottom = 0.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary, contentColor = Black)
                ) {
                    Text(
                        if (pagerState.currentPage < pages.size - 1) "Next →"
                        else "Get Started",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        } else {
            // ── Permission explainer ──────────────────────────────────────────
            PermissionExplainerPage(
                onGrant = { permLauncher.launch(requiredPerms.toTypedArray()) }
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(page.emoji, fontSize = 80.sp)
        Spacer(Modifier.height(32.dp))
        Text(page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = OnDarkPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp)
    }
}

@Composable
private fun PermissionExplainerPage(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text("Quick permissions check",
            style = MaterialTheme.typography.headlineMedium,
            color = OnDarkPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("We'll ask for 3 permissions. Here's exactly why:",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        listOf(
            Triple("📞", "Phone state", "To know when a call was missed"),
            Triple("📋", "Call log",    "To get the caller's number so we can reply"),
            Triple("✉️", "Send SMS",   "To send your auto-reply message")
        ).forEach { (icon, title, reason) ->
            PermissionExplainerRow(icon, title, reason)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onGrant,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = GoldPrimary, contentColor = Black)
        ) {
            Text("Grant Permissions", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text("You can change these any time in Android settings.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkDisabled, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionExplainerRow(icon: String, title: String, reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GreenSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp, modifier = Modifier.padding(end = 14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = OnDarkPrimary, fontWeight = FontWeight.SemiBold)
            Text(reason, style = MaterialTheme.typography.bodySmall, color = OnDarkSecondary)
        }
    }
}
