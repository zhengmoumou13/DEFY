package com.workhub.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Insights
import com.workhub.app.ui.dashboard.*
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workhub.app.WorkHubApplication
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.ui.ai.AiHubScreen
import com.workhub.app.ui.ai.DailyReportScreen
import com.workhub.app.ui.ai.DietPlanScreen
import com.workhub.app.ui.ai.FitnessPlanScreen
import com.workhub.app.ui.ai.StudyPlanScreen
import com.workhub.app.ui.ai.WeeklyReportScreen
import com.workhub.app.ui.backup.BackupScreen
import com.workhub.app.ui.ball.BallTrainingScreen
import com.workhub.app.ui.ball.BallRecordScreen
import com.workhub.app.ui.ball.BallCategoryScreen
import com.workhub.app.ui.ball.BallPlanScreen
import com.workhub.app.ui.board.BoardScreen
import com.workhub.app.ui.body.BodyDataScreen
import com.workhub.app.ui.calendar.CalendarScreen
import com.workhub.app.ui.ai.CheckinReportScreen
import com.workhub.app.ui.chat.ChatScreen
import com.workhub.app.ui.files.FilesScreen
import com.workhub.app.ui.mine.MineScreen
import com.workhub.app.ui.notes.NoteEditScreen
import com.workhub.app.ui.notes.NotesScreen
import com.workhub.app.ui.pomodoro.PomodoroScreen
import com.workhub.app.ui.settings.SettingsScreen
import com.workhub.app.ui.settings.MediaSettingsScreen
import com.workhub.app.ui.media.PhotoAnalysisScreen
import com.workhub.app.ui.stats.StatsScreen
import com.workhub.app.ui.tasks.TaskEditScreen
import com.workhub.app.ui.tasks.TasksScreen
import com.workhub.app.ui.today.TodayScreen

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val inactiveIcon: ImageVector
) {
    Today("today", "今日", Icons.Filled.Home, Icons.Outlined.Home),
    Training("training", "训练", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    Nutrition("nutrition", "饮食", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
    Progress("progress", "进展", Icons.Filled.Insights, Icons.Outlined.Insights),
    Mine("mine", "我的", Icons.Filled.Person, Icons.Outlined.Person)
}

private val TOP_LEVEL_ROUTES = BottomNavItem.entries.map { it.route }.toSet()

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as WorkHubApplication
    val repo = app.repository
    val settingsRepository = remember { SettingsRepository(app) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TOP_LEVEL_ROUTES) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        BottomNavItem.entries.forEach { item ->
                            val selected = currentRoute == item.route
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.08f else 0.94f,
                                label = "bottomNavIconScale"
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.icon else item.inactiveIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp).graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                    )
                                },
                                label = {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable("today") { HomeDashboard(repo, settingsRepository, navController) }
            composable("training") {
                TrainingDashboard(repo, navController)
            }
            composable("nutrition") { NutritionDashboard(repo, settingsRepository, navController) }
            composable("progress") { ProgressDashboard(repo, navController) }
            composable("diet_reference") { com.workhub.app.ui.diet.DietReferenceScreen(repo, settingsRepository, navController) }
            composable("food_converter?meal={meal}", arguments = listOf(navArgument("meal") { type = NavType.StringType; defaultValue = "" })) { entry ->
                com.workhub.app.ui.diet.FoodConverterScreen(navController, entry.arguments?.getString("meal").orEmpty())
            }
            composable("tasks") { TasksScreen(repo, navController) }
            composable("board") { BoardScreen(repo, navController) }
            composable("notes") { NotesScreen(repo, navController) }
            composable("mine") { ProfileDashboard(repo, settingsRepository, navController) }
            composable(
                route = "task_edit?taskId={taskId}",
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                TaskEditScreen(repo, settingsRepository, navController, entry.arguments?.getLong("taskId") ?: -1L)
            }
            composable(
                route = "note_edit?noteId={noteId}",
                arguments = listOf(
                    navArgument("noteId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                NoteEditScreen(repo, navController, entry.arguments?.getLong("noteId") ?: -1L)
            }
            composable("calendar") { CalendarScreen(repo, navController) }
            composable("files") { FilesScreen(repo, navController) }
            composable("diet") { DietPlanScreen(repo, settingsRepository, navController) }
            composable("gym") { com.workhub.app.ui.gym.GymScreen(repo, navController) }
            composable("gym_edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                com.workhub.app.ui.gym.GymEditorScreen(repo, navController, entry.arguments?.getLong("id") ?: 0)
            }
            composable("gym_session/{id}") { entry ->
                com.workhub.app.ui.gym.GymSessionScreen(repo, navController, entry.arguments?.getString("id").orEmpty())
            }
            composable("fitness") { FitnessPlanScreen(repo, settingsRepository, navController) }
            composable("ball_training") { BallTrainingScreen(repo, navController) }
            composable(
                route = "ball_category?category={category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType; defaultValue = "mid_range" })
            ) { entry ->
                BallCategoryScreen(entry.arguments?.getString("category") ?: "mid_range", navController)
            }
            composable(
                route = "ball_plan?focus={focus}",
                arguments = listOf(navArgument("focus") { type = NavType.StringType; defaultValue = "balanced" })
            ) { entry ->
                BallPlanScreen(repo, settingsRepository, navController, entry.arguments?.getString("focus") ?: "balanced")
            }
            composable(
                route = "ball_record?category={category}&recordId={recordId}&movementKey={movementKey}&planId={planId}&planItemIndex={planItemIndex}",
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "mid_range" },
                    navArgument("recordId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("movementKey") { type = NavType.StringType; defaultValue = "" },
                    navArgument("planId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("planItemIndex") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                BallRecordScreen(
                    repo, navController,
                    entry.arguments?.getString("category") ?: "mid_range",
                    entry.arguments?.getLong("recordId") ?: -1L,
                    entry.arguments?.getString("movementKey").orEmpty(),
                    entry.arguments?.getLong("planId") ?: -1L,
                    entry.arguments?.getInt("planItemIndex") ?: -1
                )
            }
            composable("study") { StudyPlanScreen(repo, settingsRepository, navController) }
            composable("daily_report") { DailyReportScreen(repo, settingsRepository, navController) }
            composable("weekly_report") { WeeklyReportScreen(repo, settingsRepository, navController) }
            composable("checkin_report") { CheckinReportScreen(repo, settingsRepository, navController) }
            composable("ai_hub") { AiHubScreen(repo, settingsRepository, navController) }
            composable("chat") { ChatScreen(repo, settingsRepository, navController) }
            composable("settings") { SettingsScreen(settingsRepository, navController) }
            composable(
                route = "media_settings?mode={mode}",
                arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "vision" })
            ) { entry ->
                MediaSettingsScreen(settingsRepository, navController, entry.arguments?.getString("mode") ?: "vision")
            }
            composable(
                route = "photo_analysis?scene={scene}",
                arguments = listOf(navArgument("scene") { type = NavType.StringType; defaultValue = "training" })
            ) { entry ->
                PhotoAnalysisScreen(repo, settingsRepository, navController, entry.arguments?.getString("scene") ?: "training")
            }
            composable("stats") { StatsScreen(repo, settingsRepository, navController) }
            composable("pomodoro") { PomodoroScreen(repo, navController) }
            composable("backup") { BackupScreen(repo, settingsRepository, navController) }
            composable("body") { BodyDataScreen(repo, settingsRepository, navController) }
        }
    }
}
