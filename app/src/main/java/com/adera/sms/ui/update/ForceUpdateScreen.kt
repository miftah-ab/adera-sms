package com.adera.sms.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adera.sms.ui.theme.*

/**
 * Full-screen blocking update screen (spec §12.6).
 *
 * Shown when installedVersion < minSupportedVersionCode.
 * There is NO dismiss button and NO back navigation — the NavGraph clears the
 * entire back stack before navigating here. The core service keeps running
 * (the user still gets auto-replies) but the UI is locked until they update.
 */
@Composable
fun ForceUpdateScreen(downloadUrl: String) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GreenBgDark, GreenSurface))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⬆️", fontSize = 72.sp)
            Spacer(Modifier.height(24.dp))
            Text("Update Required",
                style = MaterialTheme.typography.headlineMedium,
                color = OnDarkPrimary, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "This version of Adera SMS is no longer supported. " +
                "Please download the latest version to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text("Your auto-reply is still active — callers are covered.",
                style = MaterialTheme.typography.bodySmall,
                color = Green600, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary, contentColor = Black),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text("Download Update",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
