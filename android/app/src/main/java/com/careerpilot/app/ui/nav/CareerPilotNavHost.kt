package com.careerpilot.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.careerpilot.app.data.repo.Session
import com.careerpilot.app.ui.careerplan.CareerPlanScreen
import com.careerpilot.app.ui.coverletter.CoverLetterScreen
import com.careerpilot.app.ui.dashboard.DashboardScreen
import com.careerpilot.app.ui.interview.InterviewScreen
import com.careerpilot.app.ui.jd.JdDetailScreen
import com.careerpilot.app.ui.jd.JdListScreen
import com.careerpilot.app.ui.match.MatchDetailScreen
import com.careerpilot.app.ui.match.MatchScreen
import com.careerpilot.app.ui.optimize.OptimizeScreen
import com.careerpilot.app.ui.profile.ProfileScreen
import com.careerpilot.app.ui.auth.LoginScreen
import com.careerpilot.app.ui.auth.RegisterScreen
import com.careerpilot.app.ui.resume.ResumeDetailScreen
import com.careerpilot.app.ui.resume.ResumeListScreen
import com.careerpilot.app.ui.settings.ServerSettingsScreen

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(Routes.DASHBOARD, "首页", Icons.Default.Home),
    TabItem(Routes.RESUME, "简历", Icons.AutoMirrored.Filled.Article),
    TabItem(Routes.JD, "职位", Icons.Default.Work),
    TabItem(Routes.MATCH, "匹配", Icons.Default.TrackChanges),
    TabItem(Routes.PROFILE, "我的", Icons.Default.Person),
)

@Composable
fun CareerPilotNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val start = if (Session.authenticated) Routes.DASHBOARD else Routes.LOGIN

        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onGoRegister = { navController.navigate(Routes.REGISTER) },
                    onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                )
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onRegistered = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onGoLogin = { navController.popBackStack() },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigate = { route -> navController.navigate(route) },
                )
            }

            composable(Routes.RESUME) {
                ResumeListScreen(
                    onOpenResume = { id -> navController.navigate(Routes.resumeDetail(id)) },
                )
            }

            composable(Routes.RESUME_DETAIL) { entry ->
                val id = entry.arguments?.getLong("resumeId") ?: 0L
                ResumeDetailScreen(
                    resumeId = id,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.JD) {
                JdListScreen(
                    onOpenJd = { id -> navController.navigate(Routes.jdDetail(id)) },
                )
            }

            composable(Routes.JD_DETAIL) { entry ->
                val id = entry.arguments?.getLong("jdId") ?: 0L
                JdDetailScreen(
                    jdId = id,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.MATCH) {
                MatchScreen(
                    onOpenRecord = { recordId -> navController.navigate(Routes.matchDetail(recordId)) },
                )
            }

            composable(Routes.MATCH_DETAIL) { entry ->
                val recordId = entry.arguments?.getString("recordId").orEmpty()
                MatchDetailScreen(
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.OPTIMIZE) {
                OptimizeScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.INTERVIEW) {
                InterviewScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.COVER_LETTER) {
                CoverLetterScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.CAREER_PLAN) {
                CareerPlanScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.SERVER_SETTINGS) {
                ServerSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
