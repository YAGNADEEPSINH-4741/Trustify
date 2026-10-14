package com.safecallai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safecallai.app.ui.navigation.Screen
import com.safecallai.app.ui.screens.CallerDetailsScreen
import com.safecallai.app.ui.screens.DashboardScreen
import com.safecallai.app.ui.screens.OnboardingScreen
import com.safecallai.app.ui.screens.RecentActivityScreen
import com.safecallai.app.ui.screens.ReportNumberScreen
import com.safecallai.app.ui.screens.SettingsScreen
import com.safecallai.app.ui.screens.TrustedCallersScreen
import com.safecallai.app.ui.theme.BrandBlue
import com.safecallai.app.ui.theme.SafeCallAITheme
import com.safecallai.app.ui.viewmodels.SafeCallViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SafeCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPhoneNumber = intent?.getStringExtra("EXTRA_PHONE_NUMBER")

        setContent {
            SafeCallAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafeCallApp(
                        viewModel = viewModel,
                        initialPhoneNumber = initialPhoneNumber
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }
}

sealed class BottomNavItem(val screen: Screen, val icon: ImageVector, val label: String) {
    object Dashboard : BottomNavItem(Screen.Dashboard, Icons.Default.Shield, "Home")
    object Activity : BottomNavItem(Screen.Activity, Icons.Default.History, "Activity")
    object Trusted : BottomNavItem(Screen.Trusted, Icons.Default.Favorite, "Trusted")
    object Report : BottomNavItem(Screen.Report, Icons.Default.Flag, "Report")
    object Settings : BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
}

@Composable
fun SafeCallApp(
    viewModel: SafeCallViewModel,
    initialPhoneNumber: String?
) {
    val navController = rememberNavController()
    val preferences = viewModel.getRepository().preferences
    val startDestination = if (preferences.hasCompletedOnboarding) Screen.Dashboard.route else Screen.Onboarding.route

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
        BottomNavItem.Report,
        BottomNavItem.Settings
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
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
                                    contentDescription = item.label,
                                    tint = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BrandBlue.copy(alpha = 0.15f)
                            )
                        )
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

            composable(Screen.Report.route) {
                ReportNumberScreen(
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
                        navController.navigate(Screen.Report.route)
                    }
                )
            }
        }
    }
}
