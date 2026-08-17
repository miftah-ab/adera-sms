package com.adera.sms.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.adera.sms.ui.activitylog.ActivityLogScreen
import com.adera.sms.ui.home.HomeScreen
import com.adera.sms.ui.settings.SettingsScreen
import com.adera.sms.ui.templates.TemplateEditorScreen
import kotlinx.coroutines.launch

/**
 * MainPagerScreen provides full horizontal swipe/pager navigation between the
 * 4 primary destinations (Home, Templates, Recents, Settings) synchronised with
 * the Material 3 NavigationBar.
 */
@Composable
fun MainPagerScreen(
    onForceUpdate: (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()

    // When on any secondary tab, pressing Back returns smoothly to Home (tab 0)
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    val navItems = listOf(
        Triple(0, "Home", Icons.Rounded.Home),
        Triple(1, "Templates", Icons.Rounded.Message),
        Triple(2, "Recents", Icons.Rounded.List),
        Triple(3, "Settings", Icons.Rounded.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onNavigateToTemplates = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onNavigateToLog = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    },
                    onNavigateToSettings = {
                        scope.launch { pagerState.animateScrollToPage(3) }
                    }
                )
                1 -> TemplateEditorScreen(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                2 -> ActivityLogScreen(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                3 -> SettingsScreen(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    onForceUpdate = onForceUpdate
                )
            }
        }
    }
}
