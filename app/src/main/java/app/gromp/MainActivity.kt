package app.gromp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gromp.ui.components.BottomNav
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.Tab
import app.gromp.ui.screens.AppDetailScreen
import app.gromp.ui.screens.AppPickerScreen
import app.gromp.ui.screens.FocusHoursScreen
import app.gromp.ui.screens.InsightsScreen
import app.gromp.ui.screens.OnboardingScreen
import app.gromp.ui.screens.SettingsScreen
import app.gromp.ui.screens.TodayScreen
import app.gromp.ui.screens.TrendsScreen
import app.gromp.ui.theme.G
import app.gromp.ui.theme.GrompTheme
import app.gromp.util.Permissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GrompTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    GrompRoot()
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val APP = "app/{pkg}"
    const val FOCUS = "focus"
    const val PICK = "pick?focus={focus}"
    fun app(pkg: String) = "app/$pkg"
    fun pick(focus: Boolean) = "pick?focus=$focus"
}

@Composable
private fun GrompRoot() {
    val context = LocalContext.current
    val c = LocalContainer.current
    var hasAccess by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasAccess = Permissions.hasUsageAccess(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // First run with access: backfill the week Android still remembers.
    LaunchedEffect(hasAccess) { if (hasAccess) runCatching { c.repository.refresh(daysBack = 7) } }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val start = remember { if (c.prefs.onboarded && hasAccess) Tab.Today.route else Routes.ONBOARDING }

    Column(Modifier.fillMaxSize().background(G.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = start) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(hasAccess) {
                        c.prefs.onboarded = true
                        nav.navigate(Tab.Today.route) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    }
                }
                composable(Tab.Today.route) {
                    TodayScreen(
                        onOpenApp = { nav.navigate(Routes.app(it)) },
                        onSeeTrends = { nav.switchTab(Tab.Trends) },
                        onFocusHours = { nav.navigate(Routes.FOCUS) },
                    )
                }
                composable(Tab.Trends.route) { TrendsScreen() }
                composable(Tab.Insights.route) { InsightsScreen(onFocusHours = { nav.navigate(Routes.FOCUS) }) }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onOpenApp = { nav.navigate(Routes.app(it)) },
                        onAddLock = { nav.navigate(Routes.pick(false)) },
                        onFocusHours = { nav.navigate(Routes.FOCUS) },
                        onWiped = { nav.switchTab(Tab.Today) },
                    )
                }
                composable(Routes.APP, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { e ->
                    AppDetailScreen(
                        pkg = e.arguments?.getString("pkg").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onFocusHours = { nav.navigate(Routes.FOCUS) },
                    )
                }
                composable(Routes.FOCUS) {
                    FocusHoursScreen(onBack = { nav.popBackStack() }, onChooseApps = { nav.navigate(Routes.pick(true)) })
                }
                composable(Routes.PICK, arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false })) { e ->
                    AppPickerScreen(
                        forFocus = e.arguments?.getBoolean("focus") ?: false,
                        onBack = { nav.popBackStack() },
                        onOpenApp = { nav.navigate(Routes.app(it)) },
                    )
                }
            }
        }
        if (Tab.entries.any { it.route == route }) {
            BottomNav(route) { nav.switchTab(it) }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Tab.Today.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
