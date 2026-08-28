package dev.toastlabs.toastlift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.ui.CompletionReceiptDebugLaunch
import dev.toastlabs.toastlift.ui.MainTab
import dev.toastlabs.toastlift.ui.ToastLiftApp
import dev.toastlabs.toastlift.ui.ToastLiftViewModel
import dev.toastlabs.toastlift.ui.ToastLiftViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: ToastLiftViewModel by viewModels {
        ToastLiftViewModelFactory((application as ToastLiftApplication).container)
    }
    private var debugSelectedTab: MainTab? by mutableStateOf(null)
    private var debugThemePreference: ThemePreference? by mutableStateOf(null)
    private var debugReceiptLaunch: CompletionReceiptDebugLaunch? by mutableStateOf(null)
    private var debugSurfaceOverride: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDebugLaunchOverrides(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            ToastLiftApp(
                viewModel = viewModel,
                selectedTabOverride = debugSelectedTab,
                themePreferenceOverride = debugThemePreference,
                completionReceiptDebugLaunch = debugReceiptLaunch,
                debugSurfaceOverride = debugSurfaceOverride,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugLaunchOverrides(intent)
        if (BuildConfig.INTERNAL_TOOLS_ENABLED && intent.hasDebugLaunchOverride()) {
            viewModel.refreshAll()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened()
    }

    private fun applyDebugLaunchOverrides(launchIntent: Intent?) {
        if (!BuildConfig.INTERNAL_TOOLS_ENABLED || launchIntent == null) {
            debugSelectedTab = null
            debugThemePreference = null
            debugReceiptLaunch = null
            debugSurfaceOverride = null
            return
        }
        debugSurfaceOverride = launchIntent.getStringExtra(EXTRA_DEBUG_SURFACE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val parsedReceiptLaunch = parseReceiptDebugLaunch(
            surface = launchIntent.getStringExtra(EXTRA_DEBUG_SURFACE),
            scenario = launchIntent.getStringExtra(EXTRA_DEBUG_RECEIPT_SCENARIO),
        )
        debugReceiptLaunch = parsedReceiptLaunch
        debugSelectedTab = parseSelectedTabOverride(launchIntent.getStringExtra(EXTRA_DEBUG_TAB))
            ?: debugSurfaceOverride?.let(::defaultTabForDebugSurface)
        debugThemePreference = parseThemePreferenceOverride(launchIntent.getStringExtra(EXTRA_DEBUG_THEME))
    }

    private fun Intent.hasDebugLaunchOverride(): Boolean =
        hasExtra(EXTRA_DEBUG_TAB) ||
            hasExtra(EXTRA_DEBUG_THEME) ||
            hasExtra(EXTRA_DEBUG_SURFACE) ||
            hasExtra(EXTRA_DEBUG_RECEIPT_SCENARIO)

    private fun parseSelectedTabOverride(rawValue: String?): MainTab? {
        return rawValue?.let { requestedTab ->
            MainTab.entries.firstOrNull { tab ->
                tab.name.equals(requestedTab, ignoreCase = true) ||
                    tab.label.equals(requestedTab, ignoreCase = true)
            }
        }
    }

    private fun parseThemePreferenceOverride(rawValue: String?): ThemePreference? {
        return when {
            rawValue.equals(ThemePreference.Light.name, ignoreCase = true) ||
                rawValue.equals(ThemePreference.Light.storageValue, ignoreCase = true) -> ThemePreference.Light
            rawValue.equals(ThemePreference.Dark.name, ignoreCase = true) ||
                rawValue.equals(ThemePreference.Dark.storageValue, ignoreCase = true) -> ThemePreference.Dark
            rawValue.equals(ThemePreference.System.name, ignoreCase = true) ||
                rawValue.equals(ThemePreference.System.storageValue, ignoreCase = true) -> ThemePreference.System
            else -> null
        }
    }

    private fun parseReceiptDebugLaunch(
        surface: String?,
        scenario: String?,
    ): CompletionReceiptDebugLaunch? {
        val normalizedSurface = surface?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalizedScenario = scenario?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return CompletionReceiptDebugLaunch(
            surface = normalizedSurface,
            scenario = normalizedScenario,
        )
    }

    private fun defaultTabForDebugSurface(surface: String): MainTab? {
        return when (surface.lowercase()) {
            "completion_receipt", "today_receipt_recap" -> MainTab.Home
            "history_receipt_replay",
            "history",
            "history.dashboard",
            "history.workouts",
            "history.stats",
            "history.milestones",
            "history.streak",
            "history.calendar",
            "history.weekly-muscles",
            "history.token-balance",
            "history.bounty-cards",
            "sheet.library_filters",
            "sheet.exercise_detail",
            "sheet.exercise_description",
            "sheet.exercise_family",
            "sheet.exercise_history",
            "sheet.exercise_videos",
            "sheet.history_detail",
            -> MainTab.Explore
            "sheet.generated_workout_swap",
            "sheet.manual_builder",
            -> MainTab.Generate
            "profile",
            "profile.main",
            "sheet.profile_equipment_home",
            "sheet.profile_equipment_gym",
            "sheet.profile_delete_data",
            "sheet.active_workout_details",
            "sheet.active_session_filters",
            "sheet.skipped_exercise_feedback",
            "sheet.sfr_debrief",
            "sheet.checkpoint_review",
            -> MainTab.Home
            else -> null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 5101
        const val EXTRA_DEBUG_TAB = "dev.toastlabs.toastlift.extra.DEBUG_TAB"
        const val EXTRA_DEBUG_THEME = "dev.toastlabs.toastlift.extra.DEBUG_THEME"
        const val EXTRA_DEBUG_SURFACE = "dev.toastlabs.toastlift.extra.DEBUG_SURFACE"
        const val EXTRA_DEBUG_RECEIPT_SCENARIO = "dev.toastlabs.toastlift.extra.DEBUG_RECEIPT_SCENARIO"
    }
}
