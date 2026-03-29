package dev.toastlabs.toastlift

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDebugLaunchOverrides(intent)

        setContent {
            ToastLiftApp(
                viewModel = viewModel,
                selectedTabOverride = debugSelectedTab,
                themePreferenceOverride = debugThemePreference,
                completionReceiptDebugLaunch = debugReceiptLaunch,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugLaunchOverrides(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened()
    }

    private fun applyDebugLaunchOverrides(launchIntent: Intent?) {
        if (!BuildConfig.DEBUG || launchIntent == null) {
            debugSelectedTab = null
            debugThemePreference = null
            debugReceiptLaunch = null
            return
        }
        val parsedReceiptLaunch = parseReceiptDebugLaunch(
            surface = launchIntent.getStringExtra(EXTRA_DEBUG_SURFACE),
            scenario = launchIntent.getStringExtra(EXTRA_DEBUG_RECEIPT_SCENARIO),
        )
        debugReceiptLaunch = parsedReceiptLaunch
        debugSelectedTab = parseSelectedTabOverride(launchIntent.getStringExtra(EXTRA_DEBUG_TAB))
            ?: parsedReceiptLaunch?.surface?.let(::defaultTabForDebugSurface)
        debugThemePreference = parseThemePreferenceOverride(launchIntent.getStringExtra(EXTRA_DEBUG_THEME))
    }

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
            "completion_receipt", "today_receipt_recap" -> MainTab.Today
            "history_receipt_replay" -> MainTab.History
            else -> null
        }
    }

    private companion object {
        const val EXTRA_DEBUG_TAB = "dev.toastlabs.toastlift.extra.DEBUG_TAB"
        const val EXTRA_DEBUG_THEME = "dev.toastlabs.toastlift.extra.DEBUG_THEME"
        const val EXTRA_DEBUG_SURFACE = "dev.toastlabs.toastlift.extra.DEBUG_SURFACE"
        const val EXTRA_DEBUG_RECEIPT_SCENARIO = "dev.toastlabs.toastlift.extra.DEBUG_RECEIPT_SCENARIO"
    }
}
