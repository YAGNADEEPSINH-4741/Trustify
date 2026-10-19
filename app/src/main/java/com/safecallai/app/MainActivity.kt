package com.safecallai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safecallai.app.ui.navigation.Screen
import com.safecallai.app.ui.screens.CallerDetailsScreen
import com.safecallai.app.ui.screens.DashboardScreen
import com.safecallai.app.ui.screens.LanguageSelectionScreen
import com.safecallai.app.ui.screens.OnboardingScreen
import com.safecallai.app.ui.screens.RecentActivityScreen
import com.safecallai.app.ui.screens.ReportNumberScreen
import com.safecallai.app.ui.screens.SettingsScreen
import com.safecallai.app.ui.screens.TrustedCallersScreen
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.TrustifyTheme
import com.safecallai.app.ui.viewmodels.SafeCallViewModel
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import com.safecallai.app.data.local.AppLanguage
import com.safecallai.app.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val viewModel: SafeCallViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as? SafeCallApplication
        val lang = app?.repository?.preferences?.appLanguage ?: AppLanguage.ENGLISH
        val localizedContext = LocaleHelper.createLocalizedContext(newBase, lang)
        super.attachBaseContext(localizedContext)
    }

    companion object {
        var hasCompletedInitialSplash = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPhoneNumber = intent?.getStringExtra("EXTRA_PHONE_NUMBER")
        val isDeepLinkOrDial = !initialPhoneNumber.isNullOrBlank() || intent?.action == android.content.Intent.ACTION_DIAL
        if (isDeepLinkOrDial) {
            hasCompletedInitialSplash = true
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showSplash by remember { mutableStateOf(!hasCompletedInitialSplash) }

            // Dynamic locale binding for Jetpack Compose string resources
            val localizedContext = remember(uiState.appLanguage) {
                LocaleHelper.createLocalizedContext(this@MainActivity, uiState.appLanguage)
            }
            val localizedConfiguration = remember(uiState.appLanguage) {
                LocaleHelper.createLocalizedConfiguration(this@MainActivity, uiState.appLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                TrustifyTheme(themeMode = uiState.themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = showSplash,
                            animationSpec = androidx.compose.animation.core.tween(400),
                            label = "splashCrossfade"
                        ) { isSplash ->
                            if (isSplash) {
                                com.safecallai.app.ui.screens.SplashScreen(
                                    onAnimationComplete = {
                                        hasCompletedInitialSplash = true
                                        showSplash = false
                                    }
                                )
                            } else {
                                TrustifyApp(
                                    viewModel = viewModel,
                                    initialPhoneNumber = initialPhoneNumber,
                                    onLanguageChanged = { lang ->
                                        viewModel.setLanguage(lang, this@MainActivity)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }
}

sealed class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    @StringRes val labelRes: Int
) {
    object Dashboard : BottomNavItem(Screen.Dashboard, Icons.Default.Shield, R.string.nav_home)
    object Activity : BottomNavItem(Screen.Activity, Icons.Default.History, R.string.nav_activity)
    object Trusted : BottomNavItem(Screen.Trusted, Icons.Default.Favorite, R.string.nav_trusted)
    object Report : BottomNavItem(Screen.Report, Icons.Default.Flag, R.string.nav_report)
    object Settings : BottomNavItem(Screen.Settings, Icons.Default.Settings, R.string.nav_settings)
}

@Composable
fun TrustifyApp(
    viewModel: SafeCallViewModel,
    initialPhoneNumber: String?,
    onLanguageChanged: (com.safecallai.app.data.local.AppLanguage) -> Unit
) {
    val navController = rememberNavController()
    val preferences = viewModel.getRepository().preferences
    val uiState by viewModel.uiState.collectAsState()

    val startDestination = when {
        !preferences.hasSelectedLanguage -> Screen.LanguageSelection.route
        !preferences.hasCompletedOnboarding -> Screen.Onboarding.route
        else -> Screen.Dashboard.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle deep link / notification tap
    LaunchedEffect(initialPhoneNumber) {
        if (!initialPhoneNumber.isNullOrBlank()) {
            navController.navigate(Screen.CallerDetails.createRoute(initialPhoneNumber))
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Activity,
        BottomNavItem.Trusted,
        BottomNavItem.Settings
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.screen.route
                            val labelText = stringResource(item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    val targetRoute = if (item.screen == Screen.Report) Screen.Report.createRoute() else item.screen.route
                                    navController.navigate(targetRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = labelText,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LanguageSelection.route) {
                LanguageSelectionScreen(
                    currentLanguage = uiState.appLanguage,
                    onLanguageSelected = { lang ->
                        onLanguageChanged(lang)
                        val nextRoute = if (preferences.hasCompletedOnboarding) {
                            Screen.Dashboard.route
                        } else {
                            Screen.Onboarding.route
                        }
                        navController.navigate(nextRoute) {
                            popUpTo(Screen.LanguageSelection.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onFinish = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToCallerDetails = { phone ->
                        navController.navigate(Screen.CallerDetails.createRoute(phone))
                    },
                    onNavigateToActivity = {
                        navController.navigate(Screen.Activity.route)
                    }
                )
            }

            composable(Screen.Activity.route) {
                RecentActivityScreen(
                    viewModel = viewModel,
                    onNavigateToCallerDetails = { phone ->
                        navController.navigate(Screen.CallerDetails.createRoute(phone))
                    }
                )
            }

            composable(Screen.Trusted.route) {
                TrustedCallersScreen(
                    viewModel = viewModel,
                    onNavigateToCallerDetails = { phone ->
                        navController.navigate(Screen.CallerDetails.createRoute(phone))
                    }
                )
            }

            composable(
                route = Screen.Report.route,
                arguments = listOf(
                    navArgument("phoneNumber") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val phone = backStackEntry.arguments?.getString("phoneNumber")
                ReportNumberScreen(
                    initialPhoneNumber = phone,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel
                )
            }

            composable(
                route = Screen.CallerDetails.route,
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
            ) { backStackEntry ->
                val phone = backStackEntry.arguments?.getString("phoneNumber") ?: ""
                CallerDetailsScreen(
                    phoneNumber = phone,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToReport = { reportPhone ->
                        navController.navigate(Screen.Report.createRoute(reportPhone))
                    }
                )
            }
        }
    }
}
