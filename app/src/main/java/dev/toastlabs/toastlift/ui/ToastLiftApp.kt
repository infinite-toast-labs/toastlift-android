package dev.toastlabs.toastlift.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt
import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.AbandonedWorkoutSummary
import dev.toastlabs.toastlift.data.CustomExerciseDraft
import dev.toastlabs.toastlift.data.DailyCoachMessage
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseHistoryDetail
import dev.toastlabs.toastlift.data.HistoryShareFormat
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.ExerciseVideoLinks
import dev.toastlabs.toastlift.data.FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_LOWER_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_SPLIT_PROGRAM_NAME
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_SPLIT_PROGRAM_NAME
import dev.toastlabs.toastlift.data.FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.HistoryDetail
import dev.toastlabs.toastlift.data.HistoryReuseMode
import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.LibraryFacets
import dev.toastlabs.toastlift.data.LibraryFilters
import dev.toastlabs.toastlift.data.LocationMode
import dev.toastlabs.toastlift.data.MAX_WEEKLY_FREQUENCY
import dev.toastlabs.toastlift.data.MAX_WORKOUT_DURATION_MINUTES
import dev.toastlabs.toastlift.data.MIN_WEEKLY_FREQUENCY
import dev.toastlabs.toastlift.data.MIN_WORKOUT_DURATION_MINUTES
import dev.toastlabs.toastlift.data.OnboardingDraft
import dev.toastlabs.toastlift.data.RecommendationBias
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.SkippedExerciseFeedbackPrompt
import dev.toastlabs.toastlift.data.TemplateSummary
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.data.TrainingSplitProgram
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WorkoutExercise
import dev.toastlabs.toastlift.data.WorkoutPlan
import dev.toastlabs.toastlift.data.CheckpointResult
import dev.toastlabs.toastlift.data.CheckpointStatus
import dev.toastlabs.toastlift.data.EvolutionSuggestion
import dev.toastlabs.toastlift.data.PlannedSession
import dev.toastlabs.toastlift.data.PlannedSessionExercise
import dev.toastlabs.toastlift.data.ProgramOverview
import dev.toastlabs.toastlift.data.ProgramSetupDraft
import dev.toastlabs.toastlift.data.ProgramStatus
import dev.toastlabs.toastlift.data.ReadinessContext
import dev.toastlabs.toastlift.data.SessionFocus
import dev.toastlabs.toastlift.data.SessionStatus
import dev.toastlabs.toastlift.data.SfrDebriefExercise
import dev.toastlabs.toastlift.data.SfrTag
import dev.toastlabs.toastlift.data.StrengthScoreSummary
import dev.toastlabs.toastlift.data.WorkoutAbFlagSnapshot
import dev.toastlabs.toastlift.data.WorkoutMovementInsight
import dev.toastlabs.toastlift.data.WorkoutMuscleInsight
import dev.toastlabs.toastlift.data.generatedWorkoutFocusDisplayName
import dev.toastlabs.toastlift.data.isValidWorkoutDurationMinutes
import dev.toastlabs.toastlift.data.intensityPrescriptionIntentForFocusKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val MainTab.icon
    get() = when (this) {
        MainTab.Today -> Icons.Rounded.Home
        MainTab.Generate -> Icons.Rounded.AutoAwesome
        MainTab.Library -> Icons.Rounded.LibraryBooks
        MainTab.History -> Icons.Rounded.QueryStats
        MainTab.Profile -> Icons.Rounded.AccountCircle
    }

private data class GlowAccent(
    val start: Color,
    val end: Color,
    val glow: Color,
    val textOnAccent: Color = Color.White,
)

private val emberAccent = GlowAccent(
    start = Color(0xFFFF5A7D),
    end = Color(0xFFFF835B),
    glow = Color(0x66FF5A7D),
)

private val surgeAccent = GlowAccent(
    start = Color(0xFF72E4CF),
    end = Color(0xFF3AB6D5),
    glow = Color(0x6672E4CF),
    textOnAccent = Color(0xFF082624),
)

private val goldAccent = GlowAccent(
    start = Color(0xFFFFD166),
    end = Color(0xFFFFA94D),
    glow = Color(0x66FFD166),
    textOnAccent = Color(0xFF382200),
)

private val ACTIVE_SESSION_HEADER_TOP_PADDING = 5.dp
private const val SESSION_SET_RENUMBER_DELAY_MS = 280L

private val amethystAccent = GlowAccent(
    start = Color(0xFFB388FF),
    end = Color(0xFF7A8CFF),
    glow = Color(0x66A388FF),
)

private fun accentForKey(key: String): GlowAccent {
    val normalized = key.lowercase()
    return when {
        listOf("history", "streak", "calendar", "rest", "recovery", "readiness").any(normalized::contains) -> surgeAccent
        listOf("goal", "template", "volume", "milestone", "plan", "builder", "pr").any(normalized::contains) -> goldAccent
        listOf("profile", "split", "generate", "adaptive", "equipment", "gym", "home").any(normalized::contains) -> amethystAccent
        else -> emberAccent
    }
}

private fun accentBrush(accent: GlowAccent): Brush = Brush.linearGradient(listOf(accent.start, accent.end))

@Composable
private fun readableTextColorFor(background: Color): Color {
    return if (background.luminance() < 0.34f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color(0xFF10131A)
    }
}

@Composable
private fun readableMutedTextColorFor(background: Color): Color {
    val primary = readableTextColorFor(background)
    val alpha = if (background.luminance() < 0.34f) 0.78f else 0.72f
    return primary.copy(alpha = alpha)
}

internal enum class TodayProgramActionConfirmation {
    SkipSession,
    PauseProgram,
    EndProgram,
}

internal data class ActionConfirmationContent(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val isDestructive: Boolean,
)

internal fun programActionConfirmationContent(
    action: TodayProgramActionConfirmation,
    nextSession: PlannedSession?,
): ActionConfirmationContent {
    return when (action) {
        TodayProgramActionConfirmation.SkipSession -> {
            val sessionLabel = nextSession?.let { "Week ${it.weekNumber} Day ${it.dayIndex + 1}" }
            ActionConfirmationContent(
                title = if (sessionLabel != null) "Skip $sessionLabel?" else "Skip this session?",
                message = "This marks the session skipped and shifts a small amount of volume onto the next planned session.",
                confirmLabel = "Skip session",
                isDestructive = true,
            )
        }

        TodayProgramActionConfirmation.PauseProgram -> ActionConfirmationContent(
            title = "Pause program?",
            message = "Your next session stays saved. When you resume, a lighter re-entry session is inserted ahead of the block.",
            confirmLabel = "Pause program",
            isDestructive = false,
        )

        TodayProgramActionConfirmation.EndProgram -> ActionConfirmationContent(
            title = "End program?",
            message = "This marks the current program complete and removes it from Today. Your workout history stays intact.",
            confirmLabel = "End program",
            isDestructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToastLiftModalBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetSystemBarAppearanceEffect()
        content()
    }
}

@Composable
private fun SheetSystemBarAppearanceEffect() {
    val view = LocalView.current
    val isDarkTheme = LocalToastLiftIsDarkTheme.current

    DisposableEffect(view, isDarkTheme) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val controller = dialogWindow?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars

        if (controller != null) {
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }

        onDispose {
            if (controller != null) {
                previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
                previousLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.Dark -> "Dark"
    ThemePreference.Light -> "Light"
    ThemePreference.System -> "Use device"
}

private val weeklyFrequencyOptionValues = (MIN_WEEKLY_FREQUENCY..MAX_WEEKLY_FREQUENCY).map(Int::toString)
private val weeklySessionCountOptions = (MIN_WEEKLY_FREQUENCY..MAX_WEEKLY_FREQUENCY).toList()

private fun compactSplitLabel(name: String): String = when (name) {
    "Push Pull Legs" -> "PPL"
    else -> name
}

internal fun snackbarDurationFor(message: String): SnackbarDuration = when (message) {
    PROFILE_SAVED_MESSAGE -> SnackbarDuration.Long
    else -> SnackbarDuration.Short
}

internal fun workoutDurationValidationMessage(input: String): String? {
    if (input.isBlank()) {
        return "Enter a duration from $MIN_WORKOUT_DURATION_MINUTES to $MAX_WORKOUT_DURATION_MINUTES minutes."
    }
    val minutes = input.toIntOrNull()
        ?: return "Enter whole minutes only."
    return if (isValidWorkoutDurationMinutes(minutes)) {
        null
    } else {
        "Enter a duration from $MIN_WORKOUT_DURATION_MINUTES to $MAX_WORKOUT_DURATION_MINUTES minutes."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToastLiftApp(viewModel: ToastLiftViewModel) {
    val state = viewModel.uiState
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latestMessage = rememberUpdatedState(state.message)
    val latestPendingExport = rememberUpdatedState(state.pendingPersonalDataExport)
    val latestPendingWorkoutShare = rememberUpdatedState(state.pendingWorkoutShare)
    var isGenerateFullscreenFlow by remember { mutableStateOf(false) }
    var isTodayFullscreenFlow by remember { mutableStateOf(false) }
    val createExportDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = latestPendingExport.value ?: return@rememberLauncherForActivityResult
        if (uri == null) {
            viewModel.cancelPendingPersonalDataExport()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open export destination.")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(export.contents)
                }
            }.onSuccess {
                viewModel.completePendingPersonalDataExport()
            }.onFailure {
                viewModel.failPendingPersonalDataExport()
            }
        }
    }

    LaunchedEffect(latestMessage.value) {
        val message = latestMessage.value ?: return@LaunchedEffect
        snackbars.currentSnackbarData?.dismiss()
        snackbars.showSnackbar(
            message = message,
            duration = snackbarDurationFor(message),
        )
        if (viewModel.uiState.message == message) {
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.pendingPersonalDataExport?.fileName) {
        state.pendingPersonalDataExport?.let { export ->
            createExportDocument.launch(export.fileName)
        }
    }

    LaunchedEffect(state.pendingWorkoutShare?.requestId) {
        val share = latestPendingWorkoutShare.value ?: return@LaunchedEffect
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = share.mimeType
                putExtra(Intent.EXTRA_SUBJECT, share.subject)
                putExtra(Intent.EXTRA_TEXT, share.contents)
            }
            context.startActivity(Intent.createChooser(intent, share.chooserTitle))
            viewModel.completePendingWorkoutShare()
        } catch (_: ActivityNotFoundException) {
            viewModel.failPendingWorkoutShare()
        }
    }

    ToastLiftTheme(themePreference = state.themePreference) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DopamineBackdrop()
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {
            when {
                state.isLoading -> LoadingScreen()
                state.profile == null -> OnboardingScreen(state = state, viewModel = viewModel)
                state.activeSession != null -> ActiveSessionScreen(
                    state = state,
                    session = requireNotNull(state.activeSession),
                    selectedExerciseIndex = state.activeSessionExerciseIndex,
                    activeSessionAddExerciseVisible = state.activeSessionAddExerciseVisible,
                    customExerciseDraft = state.customExerciseDraft,
                    onOpenExercise = viewModel::openSessionExercise,
                    onShowExerciseDetail = viewModel::showExerciseDetail,
                    onOpenExerciseHistory = viewModel::openExerciseHistory,
                    onOpenExerciseVideos = viewModel::openExerciseVideos,
                    onCloseExercise = viewModel::closeSessionExercise,
                    onPickNextExercise = viewModel::pickNextSessionExercise,
                    onOpenAddExercise = viewModel::openActiveSessionAddExercise,
                    onCloseAddExercise = viewModel::closeActiveSessionAddExercise,
                    onToggleAddExerciseSearch = viewModel::toggleLibrarySearch,
                    onAddExerciseQueryChange = viewModel::updateLibraryQuery,
                    onToggleAddExerciseFavoritesOnly = viewModel::toggleLibraryFavoritesOnly,
                    onToggleAddExerciseEquipmentFilter = viewModel::toggleLibraryEquipmentFilter,
                    onToggleAddExerciseTargetMuscleFilter = viewModel::toggleLibraryTargetMuscleFilter,
                    onToggleAddExercisePrimeMoverFilter = viewModel::toggleLibraryPrimeMoverFilter,
                    onToggleAddExerciseRecommendationBiasFilter = viewModel::toggleLibraryRecommendationBiasFilter,
                    onToggleAddExerciseLoggedHistoryFilter = viewModel::toggleLibraryLoggedHistoryFilter,
                    onClearAddExerciseFilters = viewModel::clearLibraryFilters,
                    onShowAddExerciseDetail = viewModel::showExerciseDetail,
                    onOpenCustomExercise = viewModel::openCustomExerciseFlow,
                    onCloseCustomExercise = viewModel::closeCustomExerciseFlow,
                    onCustomExerciseDraftChange = viewModel::updateCustomExerciseDraft,
                    onCustomExerciseNameChange = viewModel::updateCustomExerciseName,
                    onGenerateCustomExercise = viewModel::generateCustomExerciseDetails,
                    onAddExercises = viewModel::addExercisesToActiveSession,
                    onUseExistingExercise = viewModel::useExistingExerciseFromCustomFlow,
                    onSaveCustomExercise = viewModel::saveCustomExercise,
                    onPendingSelectionConsumed = viewModel::clearPendingAddExercisePickerSelection,
                    onValueChange = viewModel::updateSessionValue,
                    onToggleComplete = viewModel::toggleSessionSetComplete,
                    onAddSet = viewModel::addSessionSet,
                    onDeleteSet = viewModel::deleteSessionSet,
                    onDeleteExercise = viewModel::removeSessionExercise,
                    onLogSet = viewModel::logNextSessionSet,
                    onLogAllSets = viewModel::logAllSessionSets,
                    onUpdateExerciseRir = viewModel::updateSessionExerciseRepsInReserve,
                    onFinishExercise = viewModel::finishSessionExercise,
                    onCompleteSession = viewModel::completeSession,
                    onCancel = viewModel::cancelSession,
                )
                else -> {
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            if (
                                state.selectedTab != MainTab.Library &&
                                !(state.selectedTab == MainTab.Generate && isGenerateFullscreenFlow) &&
                                !(state.selectedTab == MainTab.Today && isTodayFullscreenFlow)
                            ) {
                                CenterAlignedTopAppBar(
                                    title = { Text(state.selectedTab.label, fontWeight = FontWeight.SemiBold) },
                                    actions = {
                                        var expanded by remember { mutableStateOf(false) }
                                        IconButton(onClick = { expanded = true }) {
                                            Text(
                                                "⋮",
                                                modifier = Modifier.semantics { contentDescription = "More actions" },
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Generate workout") },
                                                onClick = {
                                                    expanded = false
                                                    viewModel.generateWorkout()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Open profile") },
                                                onClick = {
                                                    expanded = false
                                                    viewModel.selectTab(MainTab.Profile)
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbars) },
                        bottomBar = {
                            if (
                                !(state.selectedTab == MainTab.Generate && isGenerateFullscreenFlow) &&
                                !(state.selectedTab == MainTab.Today && isTodayFullscreenFlow)
                            ) {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                                    MainTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = state.selectedTab == tab,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                            label = { Text(tab.label) },
                                        )
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.84f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        ),
                                    ),
                                )
                                .padding(padding),
                        ) {
                            AnimatedContent(
                                targetState = state.selectedTab,
                                transitionSpec = {
                                    slideInHorizontally(
                                        initialOffsetX = { it / 6 },
                                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    ) + fadeIn(animationSpec = tween(220)) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 8 },
                                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        ) + fadeOut(animationSpec = tween(180))
                                },
                                label = "main-tab-transition",
                            ) { tab ->
                                when (tab) {
                                    MainTab.Today -> TodayScreen(
                                        state = state,
                                        onGenerate = viewModel::generateWorkout,
                                        onOpenGenerate = { viewModel.selectTab(MainTab.Generate) },
                                        onStartTemplate = viewModel::startTemplate,
                                        onEditTemplate = viewModel::editTemplate,
                                        onRenameTemplate = viewModel::renameTemplate,
                                        onDeleteTemplate = viewModel::deleteTemplate,
                                        onTemplateNameChange = viewModel::updateTodayTemplateName,
                                        onRemoveTemplateExercise = viewModel::removeTodayTemplateExercise,
                                        onAddExercisesToTemplate = viewModel::addExercisesToTodayTemplate,
                                        onSaveTemplateEdits = viewModel::saveTodayTemplate,
                                        onStartEditedTemplate = viewModel::startTodayTemplateWorkout,
                                        onCloseTemplateEditor = viewModel::closeTodayTemplateEditor,
                                        onLibraryQueryChange = viewModel::updateLibraryQuery,
                                        onToggleLibrarySearch = viewModel::toggleLibrarySearch,
                                        onToggleLibraryFavoritesOnly = viewModel::toggleLibraryFavoritesOnly,
                                        onToggleLibraryEquipmentFilter = viewModel::toggleLibraryEquipmentFilter,
                                        onToggleLibraryTargetMuscleFilter = viewModel::toggleLibraryTargetMuscleFilter,
                                        onToggleLibraryPrimeMoverFilter = viewModel::toggleLibraryPrimeMoverFilter,
                                        onToggleLibraryRecommendationBiasFilter = viewModel::toggleLibraryRecommendationBiasFilter,
                                        onToggleLibraryLoggedHistoryFilter = viewModel::toggleLibraryLoggedHistoryFilter,
                                        onClearLibraryFilters = viewModel::clearLibraryFilters,
                                        onShowExerciseDetail = viewModel::showExerciseDetail,
                                        onOpenCustomExercise = viewModel::openCustomExerciseForTodayTemplate,
                                        onCloseCustomExercise = viewModel::closeCustomExerciseFlow,
                                        onCustomExerciseDraftChange = viewModel::updateCustomExerciseDraft,
                                        onCustomExerciseNameChange = viewModel::updateCustomExerciseName,
                                        onGenerateCustomExercise = viewModel::generateCustomExerciseDetails,
                                        onUseExistingExercise = viewModel::useExistingExerciseFromCustomFlow,
                                        onSaveCustomExercise = viewModel::saveCustomExercise,
                                        onPendingSelectionConsumed = viewModel::clearPendingAddExercisePickerSelection,
                                        onOpenExerciseHistory = viewModel::openExerciseHistory,
                                        onOpenExerciseVideos = viewModel::openExerciseVideos,
                                        onOpenHistoryWorkout = viewModel::openHistoryWorkout,
                                        onRestoreAbandonedWorkout = viewModel::restoreAbandonedWorkout,
                                        onFullscreenFlowChange = { isTodayFullscreenFlow = it },
                                        onShowProgramSetup = viewModel::showProgramSetup,
                                        onRealizeSession = { viewModel.realizeNextSession(state.programReadiness) },
                                        onSkipSession = { state.nextPlannedSession?.id?.let { viewModel.skipPlannedSession(it) } },
                                        onUnskipSession = viewModel::unskipMostRecentSession,
                                        onPauseProgram = viewModel::pauseProgram,
                                        onResumeProgram = viewModel::resumeProgram,
                                        onEndProgram = { state.activeProgram?.id?.let { viewModel.endProgram(it) } },
                                        onUpdateReadiness = viewModel::updateReadiness,
                                        onRunCheckpoint = viewModel::runPendingCheckpoint,
                                    )

                                MainTab.Generate -> GenerateScreen(
                                    state = state,
                                    onGenerate = viewModel::generateWorkout,
                                    onSwapGenerated = viewModel::swapGeneratedWorkoutToFocus,
                                    onStartGenerated = viewModel::startGeneratedWorkout,
                                    onSaveGenerated = viewModel::saveGeneratedTemplate,
                                    onShowExerciseDetail = viewModel::showExerciseDetail,
                                    onRemoveGeneratedExercise = viewModel::removeGeneratedExercise,
                                    onAddExercisesToGeneratedWorkout = viewModel::addExercisesToGeneratedWorkout,
                                    onOpenExerciseHistory = viewModel::openExerciseHistory,
                                    onOpenExerciseVideos = viewModel::openExerciseVideos,
                                    onLibraryQueryChange = viewModel::updateLibraryQuery,
                                    onToggleLibrarySearch = viewModel::toggleLibrarySearch,
                                    onToggleLibraryFavoritesOnly = viewModel::toggleLibraryFavoritesOnly,
                                    onToggleLibraryEquipmentFilter = viewModel::toggleLibraryEquipmentFilter,
                                    onToggleLibraryTargetMuscleFilter = viewModel::toggleLibraryTargetMuscleFilter,
                                    onToggleLibraryPrimeMoverFilter = viewModel::toggleLibraryPrimeMoverFilter,
                                    onToggleLibraryRecommendationBiasFilter = viewModel::toggleLibraryRecommendationBiasFilter,
                                    onToggleLibraryLoggedHistoryFilter = viewModel::toggleLibraryLoggedHistoryFilter,
                                    onClearLibraryFilters = viewModel::clearLibraryFilters,
                                    onOpenCustomExerciseForGeneratedWorkout = viewModel::openCustomExerciseForGeneratedWorkout,
                                    onOpenCustomExerciseForBuilder = viewModel::openCustomExerciseForBuilder,
                                    onCloseCustomExercise = viewModel::closeCustomExerciseFlow,
                                    onCustomExerciseDraftChange = viewModel::updateCustomExerciseDraft,
                                    onCustomExerciseNameChange = viewModel::updateCustomExerciseName,
                                    onGenerateCustomExercise = viewModel::generateCustomExerciseDetails,
                                    onUseExistingExercise = viewModel::useExistingExerciseFromCustomFlow,
                                    onSaveCustomExercise = viewModel::saveCustomExercise,
                                    onPendingSelectionConsumed = viewModel::clearPendingAddExercisePickerSelection,
                                    onManualNameChange = viewModel::updateManualWorkoutName,
                                    onRemoveManualExercise = viewModel::removeBuilderExercise,
                                    onAddExercisesToBuilder = viewModel::addExercisesToBuilder,
                                    onStartManualWorkout = viewModel::startManualWorkout,
                                    onSaveManualTemplate = viewModel::saveManualTemplate,
                                    onFullscreenFlowChange = { isGenerateFullscreenFlow = it },
                                )

                                    MainTab.Library -> LibraryScreen(
                                        state = state,
                                        onToggleSearch = viewModel::toggleLibrarySearch,
                                        onQueryChange = viewModel::updateLibraryQuery,
                                        onToggleFavoritesOnly = viewModel::toggleLibraryFavoritesOnly,
                                        onToggleEquipmentFilter = viewModel::toggleLibraryEquipmentFilter,
                                        onToggleTargetMuscleFilter = viewModel::toggleLibraryTargetMuscleFilter,
                                        onTogglePrimeMoverFilter = viewModel::toggleLibraryPrimeMoverFilter,
                                        onToggleRecommendationBiasFilter = viewModel::toggleLibraryRecommendationBiasFilter,
                                        onToggleLoggedHistoryFilter = viewModel::toggleLibraryLoggedHistoryFilter,
                                        onClearFilters = viewModel::clearLibraryFilters,
                                        onShowDetail = viewModel::showExerciseDetail,
                                        onAddToBuilder = viewModel::addExerciseToBuilder,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onOpenExerciseHistory = viewModel::openExerciseHistory,
                                        onOpenExerciseVideos = viewModel::openExerciseVideos,
                                    )

                                    MainTab.History -> HistoryScreen(
                                        profile = state.profile,
                                        history = state.history,
                                        weeklyMuscleTargets = state.weeklyMuscleTargets,
                                        topExercise = state.historyTopExercise,
                                        topEquipment = state.historyTopEquipment,
                                        strengthScore = state.historyStrengthScore,
                                        historicalMuscleInsights = state.historicalMuscleInsights,
                                        historicalMovementInsights = state.historicalMovementInsights,
                                        onOpenWorkout = viewModel::openHistoryWorkout,
                                        onShareWorkout = viewModel::prepareHistoryWorkoutShare,
                                        onDeleteWorkout = viewModel::deleteHistoryWorkout,
                                    )
                                    MainTab.Profile -> ProfileScreen(state = state, viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }

            state.selectedExerciseDetail?.let { detail ->
                ExerciseDetailSheet(
                    detail = detail,
                    onDismiss = viewModel::dismissExerciseDetail,
                    recommendationBias = state.recommendationBiasByExerciseId[detail.summary.id] ?: detail.summary.recommendationBias,
                    onSetRecommendationBias = { bias ->
                        viewModel.setRecommendationBias(
                            exerciseId = detail.summary.id,
                            selectedBias = bias,
                            currentBias = state.recommendationBiasByExerciseId[detail.summary.id] ?: detail.summary.recommendationBias,
                        )
                    },
                    onResetRecommendationPreferenceScore = { viewModel.resetRecommendationPreferenceScore(detail.summary) },
                    onToggleFavorite = { viewModel.toggleFavorite(detail.summary) },
                    onOpenExerciseHistory = { viewModel.openExerciseHistory(detail.summary.id, detail.summary.name) },
                    onSaveExerciseNote = { note -> viewModel.saveExerciseNote(detail.summary, note) },
                    onAddToBuilder = { viewModel.addExerciseToBuilder(detail.summary) },
                    onAddToMyPlan = if (state.generatedWorkout != null) {
                        { viewModel.addExerciseToGeneratedWorkout(detail.summary) }
                    } else {
                        null
                    },
                )
            }
            state.selectedExerciseHistory?.let { detail ->
                ExerciseHistorySheet(
                    detail = detail,
                    onTogglePrOnly = viewModel::setExerciseHistoryPrOnly,
                    onDismiss = viewModel::closeExerciseHistory,
                )
            }
            state.selectedExerciseVideos?.let { detail ->
                ExerciseVideosSheet(detail = detail, onDismiss = viewModel::closeExerciseVideos)
            }
            state.selectedHistoryDetail?.let { detail ->
                HistoryDetailSheet(
                    detail = detail,
                    showAbFlags = state.profile?.historyWorkoutAbFlagsVisible == true,
                    onDismiss = viewModel::closeHistoryWorkout,
                    onReuseWorkout = { mode -> viewModel.reuseHistoryWorkout(detail.id, mode) },
                    onShowExerciseDetail = viewModel::showExerciseDetail,
                    onOpenExerciseHistory = viewModel::openExerciseHistory,
                    onOpenExerciseVideos = viewModel::openExerciseVideos,
                )
            }
            if (state.activeSession == null) state.skippedExerciseFeedbackPrompt?.let { prompt ->
                SkippedExerciseFeedbackSheet(
                    prompt = prompt,
                    onDismiss = viewModel::dismissSkippedExerciseFeedbackPrompt,
                    onDislikeExercise = viewModel::markSkippedExerciseAsDisliked,
                )
            }

            // ── Program overlays ──
            if (state.showProgramSetup) {
                ProgramSetupScreen(
                    draft = state.programSetupDraft,
                    splitPrograms = state.splitPrograms,
                    onDraftChange = viewModel::updateProgramSetupDraft,
                    onStart = { viewModel.startProgram(state.programSetupDraft) },
                    onDismiss = viewModel::dismissProgramSetup,
                )
            }
            if (state.showCheckpointReview && state.checkpointResult != null) {
                CheckpointReviewSheet(
                    result = state.checkpointResult,
                    onAccept = viewModel::dismissCheckpointReview,
                    onAcceptEvolution = viewModel::acceptEvolutionSuggestion,
                    onDismiss = viewModel::dismissCheckpointReview,
                )
            }
            if (state.showSfrDebrief) {
                SfrDebriefSheet(
                    exercises = state.sfrDebriefExercises,
                    onSubmit = viewModel::submitSfrFeedback,
                    onDismiss = viewModel::dismissSfrDebrief,
                )
            }
            if (state.showProgramWrapUp) {
                ProgramWrapUpScreen(
                    onStartNext = {
                        viewModel.dismissProgramWrapUp()
                        viewModel.showProgramSetup()
                    },
                    onDismiss = viewModel::dismissProgramWrapUp,
                )
            }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val pulse = rememberInfiniteTransition(label = "loading-pulse")
        .animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-alpha",
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = pulse.value)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonLine(width = 220.dp, height = 28.dp)
                SkeletonLine(width = 180.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBlock(88.dp, 40.dp)
                    SkeletonBlock(88.dp, 40.dp)
                }
            }
        }
        repeat(3) {
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = pulse.value)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonLine(width = 140.dp, height = 22.dp)
                    SkeletonLine(width = 200.dp)
                    SkeletonLine(width = 260.dp)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DopamineBackdrop() {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val emberGlow = if (isDarkTheme) emberAccent.glow else emberAccent.glow.copy(alpha = 0.18f)
    val surgeGlow = if (isDarkTheme) surgeAccent.glow.copy(alpha = 0.55f) else surgeAccent.glow.copy(alpha = 0.18f)
    val amethystGlow = if (isDarkTheme) amethystAccent.glow.copy(alpha = 0.32f) else amethystAccent.glow.copy(alpha = 0.12f)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            emberGlow,
                            Color.Transparent,
                        ),
                        radius = 720f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .offset(x = 80.dp, y = 140.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            surgeGlow,
                            Color.Transparent,
                        ),
                        radius = 760f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .offset(x = 140.dp, y = 12.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            amethystGlow,
                            Color.Transparent,
                        ),
                        radius = 620f,
                    ),
                ),
        )
    }
}

@Composable
private fun OnboardingScreen(state: AppUiState, viewModel: ToastLiftViewModel) {
    ProfileEditor(
        title = "Build your ToastLift profile",
        subtitle = "Choose a split program, then configure Home and Gym in bottom sheets instead of one endless form.",
        draft = state.onboardingDraft,
        splitPrograms = state.splitPrograms,
        locationModes = state.locationModes,
        equipmentOptions = state.equipmentOptions,
        equipmentByLocation = state.equipmentByLocation,
        onDraftChange = viewModel::updateOnboardingDraft,
        onToggleEquipment = viewModel::toggleEquipment,
        onSave = viewModel::saveProfile,
        saveLabel = "Finish setup",
    )
}

@Composable
private fun TodayScreen(
    state: AppUiState,
    onGenerate: () -> Unit,
    onOpenGenerate: () -> Unit,
    onStartTemplate: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onRenameTemplate: (Long, String) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onTemplateNameChange: (String) -> Unit,
    onRemoveTemplateExercise: (Long) -> Unit,
    onAddExercisesToTemplate: (List<ExerciseSummary>) -> Unit,
    onSaveTemplateEdits: () -> Unit,
    onStartEditedTemplate: () -> Unit,
    onCloseTemplateEditor: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onToggleLibrarySearch: () -> Unit,
    onToggleLibraryFavoritesOnly: () -> Unit,
    onToggleLibraryEquipmentFilter: (String) -> Unit,
    onToggleLibraryTargetMuscleFilter: (String) -> Unit,
    onToggleLibraryPrimeMoverFilter: (String) -> Unit,
    onToggleLibraryRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLibraryLoggedHistoryFilter: () -> Unit,
    onClearLibraryFilters: () -> Unit,
    onShowExerciseDetail: (Long) -> Unit,
    onOpenCustomExercise: () -> Unit,
    onCloseCustomExercise: () -> Unit,
    onCustomExerciseDraftChange: (CustomExerciseDraft) -> Unit,
    onCustomExerciseNameChange: (String) -> Unit,
    onGenerateCustomExercise: () -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSaveCustomExercise: () -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
    onOpenHistoryWorkout: (Long) -> Unit,
    onRestoreAbandonedWorkout: () -> Unit,
    onFullscreenFlowChange: (Boolean) -> Unit,
    onShowProgramSetup: () -> Unit,
    onRealizeSession: () -> Unit,
    onSkipSession: () -> Unit,
    onUnskipSession: () -> Unit,
    onPauseProgram: () -> Unit,
    onResumeProgram: () -> Unit,
    onEndProgram: () -> Unit,
    onUpdateReadiness: ((dev.toastlabs.toastlift.data.ReadinessContext) -> dev.toastlabs.toastlift.data.ReadinessContext) -> Unit,
    onRunCheckpoint: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<TemplateSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<TemplateSummary?>(null) }
    var pendingProgramAction by remember { mutableStateOf<TodayProgramActionConfirmation?>(null) }
    var showTemplateAddScreen by remember { mutableStateOf(false) }
    val profile = state.profile
    val templates = state.templates
    val history = state.history
    val totalMinutes = history.sumOf { it.durationSeconds } / 60

    LaunchedEffect(showTemplateAddScreen) {
        onFullscreenFlowChange(showTemplateAddScreen)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenFlowChange(false) }
    }

    if (showTemplateAddScreen && state.todayEditingTemplateId != null) {
        AddExercisesFlowScreen(
            state = state,
            onPendingSelectionConsumed = onPendingSelectionConsumed,
            onDismiss = { showTemplateAddScreen = false },
            onConfirmAdd = { selected ->
                showTemplateAddScreen = false
                onAddExercisesToTemplate(selected)
            },
            onQueryChange = onLibraryQueryChange,
            onToggleSearch = onToggleLibrarySearch,
            onToggleFavoritesOnly = onToggleLibraryFavoritesOnly,
            onToggleEquipmentFilter = onToggleLibraryEquipmentFilter,
            onToggleTargetMuscleFilter = onToggleLibraryTargetMuscleFilter,
            onTogglePrimeMoverFilter = onToggleLibraryPrimeMoverFilter,
            onToggleRecommendationBiasFilter = onToggleLibraryRecommendationBiasFilter,
            onToggleLoggedHistoryFilter = onToggleLibraryLoggedHistoryFilter,
            onClearFilters = onClearLibraryFilters,
            onShowDetail = onShowExerciseDetail,
            onOpenCustomExercise = onOpenCustomExercise,
            onCloseCustomExercise = onCloseCustomExercise,
            onCustomExerciseDraftChange = onCustomExerciseDraftChange,
            onCustomExerciseNameChange = onCustomExerciseNameChange,
            onGenerateCustomExercise = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSaveCustomExercise = onSaveCustomExercise,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.activeProgram != null && state.programOverview != null) {
            // ── Active Program UI ──
            ProgramOverviewCard(
                overview = state.programOverview,
                status = state.activeProgram.status,
                onPauseProgram = { pendingProgramAction = TodayProgramActionConfirmation.PauseProgram },
                onResumeProgram = onResumeProgram,
                onEndProgram = { pendingProgramAction = TodayProgramActionConfirmation.EndProgram },
            )
            TodayCompletionFeedbackSection(
                variant = state.todayCompletionFeedbackVariant,
                completion = state.todayWorkoutCompletion,
            )
            state.dailyCoachMessage?.let { coach ->
                DailyCoachCard(message = coach)
            }
            state.nextPlannedSession?.coachBrief?.let { brief ->
                CoachBriefCard(brief = brief)
            }
            if (state.activeProgram.status == ProgramStatus.ACTIVE) {
                ReadinessChipRow(
                    readiness = state.programReadiness,
                    onUpdateReadiness = onUpdateReadiness,
                )
                state.nextPlannedSession?.let { session ->
                    PlannedSessionCard(
                        session = session,
                        exercises = state.nextSessionExercises,
                        onStart = onRealizeSession,
                        onSkip = { pendingProgramAction = TodayProgramActionConfirmation.SkipSession },
                        recoverableSkippedSession = state.recoverableSkippedSession,
                        onUnskip = onUnskipSession,
                    )
                }
            } else {
                FeatureCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Program paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Your next session is saved. Resume when you want the lighter re-entry workout inserted ahead of the block.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.programProgress?.let { progress ->
                ProgramProgressCard(summary = progress)
            }
            if (state.pendingCheckpoint != null && state.activeProgram.status == ProgramStatus.ACTIVE) {
                OutlinedButton(
                    onClick = onRunCheckpoint,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Review checkpoint")
                }
            }
            TextButton(onClick = onGenerate) {
                Text("Generate a one-off workout")
            }
            TodaySecondarySections(
                profile = profile,
                templates = templates,
                history = history,
                totalMinutes = totalMinutes,
                abandonedWorkout = state.abandonedWorkout,
                hasActiveProgram = true,
                onGenerate = onGenerate,
                onOpenGenerate = onOpenGenerate,
                onStartTemplate = onStartTemplate,
                onEditTemplate = onEditTemplate,
                onRenameTemplate = { renameTarget = it },
                onDeleteTemplate = { deleteTarget = it },
                onOpenHistoryWorkout = onOpenHistoryWorkout,
                onRestoreAbandonedWorkout = onRestoreAbandonedWorkout,
            )
        } else {
            // ── Standard Today UI ──
            RichHeroCard(
                eyebrow = "My Plan",
                title = profile?.goal ?: "Build your next workout",
                subtitle = profile?.let { "${it.durationMinutes} min • ${it.weeklyFrequency} days/week • ${it.experience}" }
                    ?: "Adaptive sessions tuned to your recent work.",
                accent = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text("Quick start a generated session or jump into the builder.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGenerate) { Text("Generate") }
                    OutlinedButton(onClick = onOpenGenerate) { Text("Builder") }
                }
            }
            TodayCompletionFeedbackSection(
                variant = state.todayCompletionFeedbackVariant,
                completion = state.todayWorkoutCompletion,
            )
            state.dailyCoachMessage?.let { coach ->
                DailyCoachCard(message = coach)
            }

            ProgramLaunchCard(
                profile = profile,
                onStartProgram = onShowProgramSetup,
            )
            TodaySecondarySections(
                profile = profile,
                templates = templates,
                history = history,
                totalMinutes = totalMinutes,
                abandonedWorkout = state.abandonedWorkout,
                hasActiveProgram = false,
                onGenerate = onGenerate,
                onOpenGenerate = onOpenGenerate,
                onStartTemplate = onStartTemplate,
                onEditTemplate = onEditTemplate,
                onRenameTemplate = { renameTarget = it },
                onDeleteTemplate = { deleteTarget = it },
                onOpenHistoryWorkout = onOpenHistoryWorkout,
                onRestoreAbandonedWorkout = onRestoreAbandonedWorkout,
            )
        }
    }

    renameTarget?.let { template ->
        TemplateNameDialog(
            title = "Rename template",
            initialValue = template.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRenameTemplate(template.id, name)
            },
        )
    }

    deleteTarget?.let { template ->
        ConfirmActionDialog(
            title = "Delete template?",
            message = "This removes ${template.name} from Today.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDeleteTemplate(template.id)
            },
        )
    }

    pendingProgramAction?.let { action ->
        val content = programActionConfirmationContent(action, state.nextPlannedSession)
        ConfirmActionDialog(
            title = content.title,
            message = content.message,
            confirmLabel = content.confirmLabel,
            isDestructive = content.isDestructive,
            onDismiss = { pendingProgramAction = null },
            onConfirm = {
                pendingProgramAction = null
                when (action) {
                    TodayProgramActionConfirmation.SkipSession -> onSkipSession()
                    TodayProgramActionConfirmation.PauseProgram -> onPauseProgram()
                    TodayProgramActionConfirmation.EndProgram -> onEndProgram()
                }
            },
        )
    }

    state.todayEditingTemplateId?.let {
        ManualBuilderSheet(
            title = state.todayEditingTemplateName,
            isEditingTemplate = true,
            items = state.todayEditingTemplateItems,
            recommendationBiasByExerciseId = state.recommendationBiasByExerciseId,
            onDismiss = onCloseTemplateEditor,
            onRemoveExercise = onRemoveTemplateExercise,
            onAddExercise = { showTemplateAddScreen = true },
            onOpenExerciseHistory = onOpenExerciseHistory,
            onOpenExerciseVideos = onOpenExerciseVideos,
            onSaveTemplate = onSaveTemplateEdits,
            onStartWorkout = onStartEditedTemplate,
            onTitleChange = onTemplateNameChange,
        )
    }
}

@Composable
private fun TodayCompletionFeedbackSection(
    variant: dev.toastlabs.toastlift.data.TodayCompletionFeedbackVariant,
    completion: TodayWorkoutCompletionState,
) {
    val model = buildTodayCompletionFeedbackModel(
        variant = variant,
        completion = completion,
    )
    when (variant) {
        dev.toastlabs.toastlift.data.TodayCompletionFeedbackVariant.DONE_TODAY_BADGE -> {
            AnimatedVisibility(visible = model.showDoneBadge) {
                TodayDoneBadgeCard(model = model)
            }
        }

        dev.toastlabs.toastlift.data.TodayCompletionFeedbackVariant.PROGRESS_METER -> {
            TodayCompletionMeterCard(model = model)
        }
    }
}

@Composable
private fun TodayDoneBadgeCard(model: TodayCompletionFeedbackModel) {
    CompactSectionCard(
        title = model.title,
        subtitle = model.subtitle,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✅",
                fontSize = 28.sp,
            )
            if (!model.statusLabel.equals(model.title, ignoreCase = true)) {
                MiniTag(model.statusLabel, accent = goldAccent.start.copy(alpha = 0.18f))
            }
        }
    }
}

@Composable
private fun TodayCompletionMeterCard(model: TodayCompletionFeedbackModel) {
    val accent = if (model.progressFraction >= 1f) goldAccent else surgeAccent
    val animatedProgress by animateFloatAsState(
        targetValue = model.progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "todayCompletionMeter",
    )
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiniTag(
                    text = model.statusLabel,
                    accent = accent.start.copy(alpha = 0.18f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentBrush(accent)),
                )
            }
            Text(
                text = "${(animatedProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent.start,
            )
        }
    }
}

@Composable
private fun TodaySecondarySections(
    profile: UserProfile?,
    templates: List<TemplateSummary>,
    history: List<HistorySummary>,
    totalMinutes: Int,
    abandonedWorkout: AbandonedWorkoutSummary?,
    hasActiveProgram: Boolean,
    onGenerate: () -> Unit,
    onOpenGenerate: () -> Unit,
    onStartTemplate: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onRenameTemplate: (TemplateSummary) -> Unit,
    onDeleteTemplate: (TemplateSummary) -> Unit,
    onOpenHistoryWorkout: (Long) -> Unit,
    onRestoreAbandonedWorkout: () -> Unit,
) {
    StatRail(
        items = listOf(
            Triple("Weekly target", "${profile?.weeklyFrequency ?: 0}", "days"),
            Triple("Templates", templates.size.toString(), "saved"),
            Triple("Recent work", totalMinutes.toString(), "min"),
        ),
    )

    CompactSectionCard(
        title = "Suggested next move",
        subtitle = if (hasActiveProgram) {
            "Keep your plan moving or branch into a one-off session."
        } else {
            profile?.let { "${it.goal} for ${it.durationMinutes} minutes" } ?: "Use onboarding to create a profile"
        },
        menuItems = listOf("Generate now" to onGenerate, "Open builder" to onOpenGenerate),
    ) {
        SuggestionRow(
            title = if (hasActiveProgram) "Your plan is already live" else "Train with intent",
            subtitle = if (hasActiveProgram) {
                "Your planned session stays pinned above, but one-off workouts and the builder remain available."
            } else if (history.isEmpty()) {
                "No history yet. Start with a balanced full-body day."
            } else {
                "Use your recent history to bias the next generated session."
            },
            badge = if (hasActiveProgram) "Program" else "Smart",
        )
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        SuggestionRow(
            title = "Keep density high",
            subtitle = "Save templates from workouts you repeat often and launch them from Today.",
            badge = "Tip",
        )
    }

    TodayTemplatesSection(
        templates = templates,
        onGenerate = onGenerate,
        onStartTemplate = onStartTemplate,
        onEditTemplate = onEditTemplate,
        onRenameTemplate = onRenameTemplate,
        onDeleteTemplate = onDeleteTemplate,
        subtitle = if (hasActiveProgram && templates.isEmpty()) {
            "Saved templates stay available while your program is in progress."
        } else if (templates.isEmpty()) {
            "No templates saved yet."
        } else {
            "${templates.size} saved templates"
        },
    )

    CompactSectionCard(
        title = "Recent history",
        subtitle = if (history.isEmpty()) "No sessions logged yet." else "Recent sessions and trends",
    ) {
        if (history.isEmpty()) {
            SuggestionRow(
                title = "Your recovery story starts here",
                subtitle = "Complete one workout and this area will fill with recent volume and session summaries.",
                badge = "Next",
            )
        } else {
            history.take(3).forEach { item ->
                WorkoutListRow(
                    title = item.title,
                    subtitle = item.exerciseNames.joinToString(limit = 3),
                    detail = "${formatMinutes(item.durationSeconds)} • ${formatVolume(item.totalVolume)}",
                    actionLabel = "View",
                    onAction = { onOpenHistoryWorkout(item.id) },
                )
            }
        }
    }

    CompactSectionCard(
        title = "Abandoned workouts",
        subtitle = if (abandonedWorkout == null) {
            "Only the latest abandoned workout can be restored."
        } else {
            "Resume the most recent workout you left in progress."
        },
    ) {
        if (abandonedWorkout == null) {
            SuggestionRow(
                title = "Nothing to restore",
                subtitle = "If you abandon a workout in progress, the latest one will appear here.",
                badge = "Resume",
            )
        } else {
            WorkoutListRow(
                title = abandonedWorkout.title,
                subtitle = "${abandonedWorkout.exerciseCount} exercises",
                detail = "${abandonedWorkout.completedSetCount} logged sets",
                actionLabel = "Restore",
                onAction = onRestoreAbandonedWorkout,
            )
        }
    }
}

@Composable
private fun TodayTemplatesSection(
    templates: List<TemplateSummary>,
    onGenerate: () -> Unit,
    onStartTemplate: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onRenameTemplate: (TemplateSummary) -> Unit,
    onDeleteTemplate: (TemplateSummary) -> Unit,
    subtitle: String,
) {
    CompactSectionCard(
        title = "Templates",
        subtitle = subtitle,
        menuItems = listOf("Generate instead" to onGenerate),
    ) {
        TodayTemplatesSectionContent(
            templates = templates,
            onStartTemplate = onStartTemplate,
            onEditTemplate = onEditTemplate,
            onRenameTemplate = onRenameTemplate,
            onDeleteTemplate = onDeleteTemplate,
        )
    }
}

@Composable
private fun TodayTemplatesSectionContent(
    templates: List<TemplateSummary>,
    onStartTemplate: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onRenameTemplate: (TemplateSummary) -> Unit,
    onDeleteTemplate: (TemplateSummary) -> Unit,
) {
    if (templates.isEmpty()) {
        SuggestionRow(
            title = "No templates yet",
            subtitle = "Generate or build a workout, then save it for one-tap reuse here.",
            badge = "Empty",
        )
    } else {
        templates.take(3).forEach { template ->
            TemplateListRow(
                template = template,
                onStart = { onStartTemplate(template.id) },
                onEdit = { onEditTemplate(template.id) },
                onRename = { onRenameTemplate(template) },
                onDelete = { onDeleteTemplate(template) },
            )
        }
    }
}

@Composable
private fun GenerateScreen(
    state: AppUiState,
    onGenerate: () -> Unit,
    onSwapGenerated: (String) -> Unit,
    onStartGenerated: () -> Unit,
    onSaveGenerated: (String) -> Unit,
    onShowExerciseDetail: (Long) -> Unit,
    onRemoveGeneratedExercise: (Long) -> Unit,
    onAddExercisesToGeneratedWorkout: (List<ExerciseSummary>) -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onToggleLibrarySearch: () -> Unit,
    onToggleLibraryFavoritesOnly: () -> Unit,
    onToggleLibraryEquipmentFilter: (String) -> Unit,
    onToggleLibraryTargetMuscleFilter: (String) -> Unit,
    onToggleLibraryPrimeMoverFilter: (String) -> Unit,
    onToggleLibraryRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLibraryLoggedHistoryFilter: () -> Unit,
    onClearLibraryFilters: () -> Unit,
    onOpenCustomExerciseForGeneratedWorkout: () -> Unit,
    onOpenCustomExerciseForBuilder: () -> Unit,
    onCloseCustomExercise: () -> Unit,
    onCustomExerciseDraftChange: (CustomExerciseDraft) -> Unit,
    onCustomExerciseNameChange: (String) -> Unit,
    onGenerateCustomExercise: () -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSaveCustomExercise: () -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    onManualNameChange: (String) -> Unit,
    onRemoveManualExercise: (Long) -> Unit,
    onAddExercisesToBuilder: (List<ExerciseSummary>) -> Unit,
    onStartManualWorkout: () -> Unit,
    onSaveManualTemplate: () -> Unit,
    onFullscreenFlowChange: (Boolean) -> Unit,
) {
    var showBuilderSheet by remember { mutableStateOf(false) }
    var showSwapSheet by remember { mutableStateOf(false) }
    var showBuilderAddSheet by remember { mutableStateOf(false) }
    var showGeneratedAddScreen by remember { mutableStateOf(false) }
    var showGeneratedSaveDialog by remember { mutableStateOf(false) }
    val activeProfile = state.profile
    val generated = state.generatedWorkout
    val coveredTargets = generated?.exercises?.map { it.targetMuscleGroup }?.distinct().orEmpty()
    val projectedMuscleInsights = state.projectedMuscleInsights
    val projectedMovementInsights = state.projectedMovementInsights
    val splitName = activeProfile?.let { profile ->
        state.splitPrograms.firstOrNull { it.id == profile.splitProgramId }?.name
    }.orEmpty()
    val builderCardTitle = if (state.editingTemplateId != null) "${state.manualWorkoutName} (Editing)" else state.manualWorkoutName
    val builderCardSubtitle = when {
        state.manualWorkoutItems.isEmpty() -> "Manual builder is empty."
        state.editingTemplateId != null -> "${state.manualWorkoutItems.size} exercises in saved template"
        else -> "${state.manualWorkoutItems.size} exercises queued"
    }

    LaunchedEffect(showBuilderAddSheet, showGeneratedAddScreen) {
        onFullscreenFlowChange(showBuilderAddSheet || showGeneratedAddScreen)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenFlowChange(false) }
    }

    if (showGeneratedAddScreen) {
        AddExercisesFlowScreen(
            state = state,
            onDismiss = { showGeneratedAddScreen = false },
            onConfirmAdd = { selected ->
                showGeneratedAddScreen = false
                onAddExercisesToGeneratedWorkout(selected)
            },
            onQueryChange = onLibraryQueryChange,
            onToggleSearch = onToggleLibrarySearch,
            onToggleFavoritesOnly = onToggleLibraryFavoritesOnly,
            onToggleEquipmentFilter = onToggleLibraryEquipmentFilter,
            onToggleTargetMuscleFilter = onToggleLibraryTargetMuscleFilter,
            onTogglePrimeMoverFilter = onToggleLibraryPrimeMoverFilter,
            onToggleRecommendationBiasFilter = onToggleLibraryRecommendationBiasFilter,
            onToggleLoggedHistoryFilter = onToggleLibraryLoggedHistoryFilter,
            onClearFilters = onClearLibraryFilters,
            onShowDetail = onShowExerciseDetail,
            onOpenCustomExercise = onOpenCustomExerciseForGeneratedWorkout,
            onCloseCustomExercise = onCloseCustomExercise,
            onCustomExerciseDraftChange = onCustomExerciseDraftChange,
            onCustomExerciseNameChange = onCustomExerciseNameChange,
            onGenerateCustomExercise = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSaveCustomExercise = onSaveCustomExercise,
            onPendingSelectionConsumed = onPendingSelectionConsumed,
        )
        return
    }

    if (showBuilderAddSheet) {
        AddExercisesFlowScreen(
            state = state,
            onDismiss = { showBuilderAddSheet = false },
            onConfirmAdd = { selected ->
                showBuilderAddSheet = false
                onAddExercisesToBuilder(selected)
            },
            onQueryChange = onLibraryQueryChange,
            onToggleSearch = onToggleLibrarySearch,
            onToggleFavoritesOnly = onToggleLibraryFavoritesOnly,
            onToggleEquipmentFilter = onToggleLibraryEquipmentFilter,
            onToggleTargetMuscleFilter = onToggleLibraryTargetMuscleFilter,
            onTogglePrimeMoverFilter = onToggleLibraryPrimeMoverFilter,
            onToggleRecommendationBiasFilter = onToggleLibraryRecommendationBiasFilter,
            onToggleLoggedHistoryFilter = onToggleLibraryLoggedHistoryFilter,
            onClearFilters = onClearLibraryFilters,
            onShowDetail = onShowExerciseDetail,
            onOpenCustomExercise = onOpenCustomExerciseForBuilder,
            onCloseCustomExercise = onCloseCustomExercise,
            onCustomExerciseDraftChange = onCustomExerciseDraftChange,
            onCustomExerciseNameChange = onCustomExerciseNameChange,
            onGenerateCustomExercise = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSaveCustomExercise = onSaveCustomExercise,
            onPendingSelectionConsumed = onPendingSelectionConsumed,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RichHeroCard(
            eyebrow = "Smart Generator",
            title = activeProfile?.let { state.splitPrograms.firstOrNull { split -> split.id == it.splitProgramId }?.name ?: "Adaptive" } ?: "Adaptive",
            subtitle = activeProfile?.let { "${it.goal} • ${it.durationMinutes} min • ${it.workoutStyle}" } ?: "Profile-driven workout generation",
            accent = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text("The workout detail lives in a bottom sheet so the generator screen stays compact.")
            Button(onClick = onGenerate) { Text("Generate workout") }
        }

        StatRail(
            items = listOf(
                Triple("Generated", (generated?.exercises?.size ?: 0).toString(), "exercises"),
                Triple("Targets", (if (projectedMuscleInsights.isNotEmpty()) projectedMuscleInsights.size else coveredTargets.size).toString(), "tracked"),
                Triple("Duration", (generated?.estimatedMinutes ?: activeProfile?.durationMinutes ?: 0).toString(), "min"),
            ),
        )

        if (generated == null) {
            CompactSectionCard(title = "Suggestions", subtitle = "Keep the screen useful before generation") {
                SuggestionRow(
                    title = "Use your active location mode",
                    subtitle = "Equipment availability is already constraining generation behind the scenes.",
                    badge = "Mode",
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                SuggestionRow(
                    title = "Manual and smart can coexist",
                    subtitle = "Search the library, add a few anchor lifts, then generate around them later.",
                    badge = "Mix",
                )
            }
        }

        state.generatedWorkout?.let { workout ->
            GeneratedWorkoutCard(
                workout = workout,
                recommendationBiasByExerciseId = state.recommendationBiasByExerciseId,
                onSwapGenerated = { showSwapSheet = true },
                onSaveGenerated = { showGeneratedSaveDialog = true },
                onStartGenerated = onStartGenerated,
                onShowExerciseDetail = onShowExerciseDetail,
                onAddExercise = { showGeneratedAddScreen = true },
                onRemoveExercise = onRemoveGeneratedExercise,
                onOpenExerciseHistory = onOpenExerciseHistory,
                onOpenExerciseVideos = onOpenExerciseVideos,
            )

            CompactSectionCard(
                title = "Projected Muscle Contribution",
                subtitle = "What this workout is set up to hit",
            ) {
                if (projectedMuscleInsights.isNotEmpty()) {
                    projectedMuscleInsights.take(8).forEach { insight ->
                        WorkoutListRow(
                            title = insight.muscle,
                            subtitle = "Projected share ${percentString(insight.share)} • ${insight.exerciseCount} exercise${if (insight.exerciseCount == 1) "" else "s"}",
                            detail = "Contribution ${decimalString(insight.contribution)}",
                            actionLabel = "",
                            onAction = {},
                            showAction = false,
                        )
                    }
                } else {
                    coveredTargets.forEach { target ->
                        val count = workout.exercises.count { it.targetMuscleGroup == target }
                        WorkoutListRow(
                            title = target,
                            subtitle = "$count exercise${if (count == 1) "" else "s"}",
                            detail = workout.exercises.filter { it.targetMuscleGroup == target }.joinToString(limit = 2) { it.name },
                            actionLabel = "",
                            onAction = {},
                            showAction = false,
                        )
                    }
                }
            }

            if (projectedMovementInsights.isNotEmpty()) {
                CompactSectionCard(
                    title = "Projected Movement Coverage",
                    subtitle = "Pattern and plane coverage for this session",
                ) {
                    projectedMovementInsights.take(8).forEach { insight ->
                        WorkoutListRow(
                            title = insight.label,
                            subtitle = when (insight.kind) {
                                "laterality" -> "Projected unilateral share ${percentString(insight.share)}"
                                else -> "Projected share ${percentString(insight.share)}"
                            },
                            detail = when (insight.kind) {
                                "laterality" -> "${insight.exerciseCount} tagged exercise${if (insight.exerciseCount == 1) "" else "s"}"
                                else -> "Exposure ${decimalString(insight.exposure)} • ${insight.exerciseCount} exercise${if (insight.exerciseCount == 1) "" else "s"}"
                            },
                            actionLabel = "",
                            onAction = {},
                            showAction = false,
                        )
                    }
                }
            }
        }

        CompactSectionCard(
            title = builderCardTitle,
            subtitle = builderCardSubtitle,
            menuItems = listOf(
                "Open builder" to { showBuilderSheet = true },
                "Start manual workout" to onStartManualWorkout,
                (if (state.editingTemplateId != null) "Update template" else "Save template") to onSaveManualTemplate,
            ),
        ) {
            OutlinedTextField(
                value = state.manualWorkoutName,
                onValueChange = onManualNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Workout name") },
            )
            if (state.manualWorkoutItems.isEmpty()) {
                SuggestionRow(
                    title = "Builder is empty",
                    subtitle = "Use Library > Add to send exercises here, then launch or save from the sheet.",
                    badge = "Build",
                )
            } else {
                WorkoutPreviewStack(
                    exercises = state.manualWorkoutItems,
                    footer = "${state.manualWorkoutItems.size} exercises queued",
                    recommendationBiasByExerciseId = state.recommendationBiasByExerciseId,
                    onOpenExerciseHistory = onOpenExerciseHistory,
                    onOpenExerciseVideos = onOpenExerciseVideos,
                )
            }
        }
    }

    if (showBuilderSheet) {
        ManualBuilderSheet(
            title = state.manualWorkoutName,
            isEditingTemplate = state.editingTemplateId != null,
            items = state.manualWorkoutItems,
            recommendationBiasByExerciseId = state.recommendationBiasByExerciseId,
            onDismiss = { showBuilderSheet = false },
            onTitleChange = onManualNameChange,
            onRemoveExercise = onRemoveManualExercise,
            onAddExercise = {
                showBuilderSheet = false
                showBuilderAddSheet = true
            },
            onOpenExerciseHistory = onOpenExerciseHistory,
            onOpenExerciseVideos = onOpenExerciseVideos,
            onSaveTemplate = {
                showBuilderSheet = false
                onSaveManualTemplate()
            },
            onStartWorkout = {
                showBuilderSheet = false
                onStartManualWorkout()
            },
        )
    }

    if (showSwapSheet && generated != null) {
        SwapWorkoutSheet(
            splitName = splitName,
            currentFocus = generated.focusKey,
            onDismiss = { showSwapSheet = false },
            onSelectFocus = { focus ->
                showSwapSheet = false
                onSwapGenerated(focus)
            },
        )
    }

    if (showGeneratedSaveDialog && generated != null) {
        TemplateNameDialog(
            title = "Save generated workout",
            initialValue = generated.title,
            confirmLabel = "Save",
            onDismiss = { showGeneratedSaveDialog = false },
            onConfirm = { name ->
                showGeneratedSaveDialog = false
                onSaveGenerated(name)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryScreen(
    state: AppUiState,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleEquipmentFilter: (String) -> Unit,
    onToggleTargetMuscleFilter: (String) -> Unit,
    onTogglePrimeMoverFilter: (String) -> Unit,
    onToggleRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLoggedHistoryFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onShowDetail: (Long) -> Unit,
    onAddToBuilder: (ExerciseSummary) -> Unit,
    onToggleFavorite: (ExerciseSummary) -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.librarySearchVisible) {
                    OutlinedTextField(
                        value = state.libraryQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        singleLine = true,
                        placeholder = { Text("Search exercises") },
                        trailingIcon = {
                            IconButton(onClick = onToggleSearch) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close search")
                            }
                        },
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Open filters",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (state.libraryFilters.activeCount() == 0) {
                                "Filters"
                            } else {
                                "Filters ${state.libraryFilters.activeCount()}"
                            },
                        )
                    }
                    IconButton(onClick = onToggleFavoritesOnly) {
                        Icon(
                            imageVector = if (state.libraryFilters.favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (state.libraryFilters.favoritesOnly) "Show all exercises" else "Show favorites only",
                            tint = if (state.libraryFilters.favoritesOnly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                    }
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search exercises",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(state.libraryResults, key = { it.id }) { exercise ->
                    ExerciseListCard(
                        exercise = exercise,
                        onDetails = { onShowDetail(exercise.id) },
                        onAdd = { onAddToBuilder(exercise) },
                        onToggleFavorite = { onToggleFavorite(exercise) },
                        onOpenExerciseHistory = { onOpenExerciseHistory(exercise.id, exercise.name) },
                        onOpenExerciseVideos = { onOpenExerciseVideos(exercise.id, exercise.name) },
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        LibraryFilterSheet(
            facets = state.libraryFacets,
            filters = state.libraryFilters,
            onDismiss = { showFilterSheet = false },
            onClearFilters = onClearFilters,
            onToggleEquipment = onToggleEquipmentFilter,
            onToggleTargetMuscle = onToggleTargetMuscleFilter,
            onTogglePrimeMover = onTogglePrimeMoverFilter,
            onToggleRecommendationBias = onToggleRecommendationBiasFilter,
            onToggleLoggedHistory = onToggleLoggedHistoryFilter,
        )
    }
}

private enum class HistoryMuscleFilter(val label: String) {
    All("All"),
    Upper("Upper"),
    Lower("Lower"),
    Core("Core"),
}

private enum class HistoryMovementFilter(val label: String) {
    All("All"),
    Patterns("Patterns"),
    Planes("Planes"),
    Laterality("Laterality"),
}

private fun historyMuscleFilterFor(muscle: String): HistoryMuscleFilter? = when (muscle) {
    "Chest", "Back", "Shoulders", "Trapezius", "Biceps", "Triceps", "Forearms" -> HistoryMuscleFilter.Upper
    "Quadriceps", "Glutes", "Hamstrings", "Calves", "Adductors", "Abductors" -> HistoryMuscleFilter.Lower
    "Abdominals" -> HistoryMuscleFilter.Core
    else -> null
}

@Composable
private fun HistoryScreen(
    profile: UserProfile?,
    history: List<HistorySummary>,
    weeklyMuscleTargets: WeeklyMuscleTargetSummary?,
    topExercise: String?,
    topEquipment: String?,
    strengthScore: StrengthScoreSummary?,
    historicalMuscleInsights: List<WorkoutMuscleInsight>,
    historicalMovementInsights: List<WorkoutMovementInsight>,
    onOpenWorkout: (Long) -> Unit,
    onShareWorkout: (Long, HistoryShareFormat) -> Unit,
    onDeleteWorkout: (Long) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<HistorySummary?>(null) }
    var shareTarget by remember { mutableStateOf<HistorySummary?>(null) }
    var destination by remember { mutableStateOf("dashboard") }
    var selectedMuscleFilter by remember { mutableStateOf(HistoryMuscleFilter.All) }
    var selectedMovementFilter by remember { mutableStateOf(HistoryMovementFilter.All) }
    val dashboard = remember(history, profile, topExercise, topEquipment, strengthScore) {
        buildHistoryDashboardData(
            history = history,
            weeklyGoal = profile?.weeklyFrequency ?: 4,
            topExercise = topExercise,
            topEquipment = topEquipment,
            strengthScore = strengthScore,
        )
    }
    val filteredHistoricalMuscles = remember(historicalMuscleInsights, selectedMuscleFilter) {
        historicalMuscleInsights.filter { insight ->
            selectedMuscleFilter == HistoryMuscleFilter.All || historyMuscleFilterFor(insight.muscle) == selectedMuscleFilter
        }
    }
    val filteredHistoricalMovements = remember(historicalMovementInsights, selectedMovementFilter) {
        historicalMovementInsights.filter { insight ->
            when (selectedMovementFilter) {
                HistoryMovementFilter.All -> true
                HistoryMovementFilter.Patterns -> insight.kind == "pattern"
                HistoryMovementFilter.Planes -> insight.kind == "plane"
                HistoryMovementFilter.Laterality -> insight.kind == "laterality"
            }
        }
    }

    if (destination == "workouts") {
        HistoryWorkoutStatsScreen(data = dashboard, onBack = { destination = "dashboard" })
        return
    }
    if (destination == "milestones") {
        HistoryMilestonesScreen(data = dashboard, onBack = { destination = "dashboard" })
        return
    }
    if (destination == "streak") {
        HistoryStreakScreen(data = dashboard, onBack = { destination = "dashboard" })
        return
    }
    if (destination == "weekly-muscles" && weeklyMuscleTargets != null) {
        WeeklyMuscleTargetsDetailScreen(
            summary = weeklyMuscleTargets,
            onBack = { destination = "dashboard" },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HistoryOverviewHeader(
                data = dashboard,
                onOpenWorkouts = { destination = "workouts" },
                onOpenMilestones = { destination = "milestones" },
                onOpenStreak = { destination = "streak" },
            )
        }
        weeklyMuscleTargets?.let { summary ->
            item {
                WeeklyMuscleTargetsOverviewCard(
                    summary = summary,
                    onOpen = { destination = "weekly-muscles" },
                )
            }
        }
        if (historicalMuscleInsights.isNotEmpty()) {
            item {
                CompactSectionCard(
                    title = "Muscle Index",
                    subtitle = "Historical training state with stable filters",
                ) {
                    ChoiceChipRow(
                        values = HistoryMuscleFilter.entries.map(HistoryMuscleFilter::label),
                        selected = selectedMuscleFilter.label,
                        onSelect = { label ->
                            selectedMuscleFilter = HistoryMuscleFilter.entries.firstOrNull { it.label == label } ?: HistoryMuscleFilter.All
                        },
                    )
                    if (filteredHistoricalMuscles.isEmpty()) {
                        Text(
                            text = "No completed-workout data for this filter yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredHistoricalMuscles.take(8).forEach { insight ->
                            WorkoutListRow(
                                title = insight.muscle,
                                subtitle = "Readiness ${percentString(insight.readinessScore)} • ${insight.volumeStatus}",
                                detail = "Weekly stimulus ${decimalString(insight.weeklyStimulus)} • Priority ${decimalString(insight.priorityScore)}",
                                actionLabel = "",
                                onAction = {},
                                showAction = false,
                            )
                        }
                    }
                }
            }
        }
        if (historicalMovementInsights.isNotEmpty()) {
            item {
                CompactSectionCard(
                    title = "Movement Balance",
                    subtitle = "Historical exposure with stable filters",
                ) {
                    ChoiceChipRow(
                        values = HistoryMovementFilter.entries.map(HistoryMovementFilter::label),
                        selected = selectedMovementFilter.label,
                        onSelect = { label ->
                            selectedMovementFilter = HistoryMovementFilter.entries.firstOrNull { it.label == label } ?: HistoryMovementFilter.All
                        },
                    )
                    if (filteredHistoricalMovements.isEmpty()) {
                        Text(
                            text = "No completed-workout movement data for this filter yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredHistoricalMovements.take(8).forEach { insight ->
                            WorkoutListRow(
                                title = insight.label,
                                subtitle = "Need ${percentString(insight.needScore)}",
                                detail = when (insight.kind) {
                                    "laterality" -> "Current share ${percentString(insight.currentExposure)}"
                                    else -> "Current exposure ${decimalString(insight.currentExposure)}"
                                },
                                actionLabel = "",
                                onAction = {},
                                showAction = false,
                            )
                        }
                    }
                }
            }
        }
        if (history.isEmpty()) {
            item {
                CompactSectionCard(title = "How this fills in", subtitle = "History should always explain its future value") {
                    SuggestionRow(
                        title = "Volume tracking",
                        subtitle = "Completed workouts will surface recent exercises, duration trends, and future recovery context here.",
                        badge = "Soon",
                    )
                }
            }
        } else {
            items(history, key = { it.id }) { entry ->
                HistoryEntryCard(
                    entry = entry,
                    onOpen = { onOpenWorkout(entry.id) },
                    onShare = { shareTarget = entry },
                    onDelete = { deleteTarget = entry },
                )
            }
        }
    }

    shareTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { shareTarget = null },
            title = { Text("Share workout") },
            text = { Text("Choose how ${entry.title} should be shared.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            shareTarget = null
                            onShareWorkout(entry.id, HistoryShareFormat.FormattedText)
                        },
                    ) {
                        Text(HistoryShareFormat.FormattedText.optionLabel)
                    }
                    TextButton(
                        onClick = {
                            shareTarget = null
                            onShareWorkout(entry.id, HistoryShareFormat.Json)
                        },
                    ) {
                        Text(HistoryShareFormat.Json.optionLabel)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { shareTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    deleteTarget?.let { entry ->
        ConfirmActionDialog(
            title = "Delete workout?",
            message = "This removes ${entry.title} from history.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDeleteWorkout(entry.id)
            },
        )
    }
}

private data class HistoryDashboardData(
    val totalWorkouts: Int,
    val totalMinutes: Int,
    val totalVolume: Double,
    val weeklyGoal: Int,
    val currentWeekCount: Int,
    val currentStreakWeeks: Int,
    val longestStreakWeeks: Int,
    val topExercise: String?,
    val topEquipment: String?,
    val currentWeekDays: List<HistoryCalendarDay>,
    val streakWeeks: List<HistoryWeekSnapshot>,
    val monthlyVolume: List<Pair<YearMonth, Double>>,
    val milestoneProgress: List<MilestoneProgress>,
    val strengthScore: StrengthScoreSummary?,
)

private data class HistoryCalendarDay(
    val date: LocalDate,
    val workoutCount: Int,
)

private data class HistoryWeekSnapshot(
    val weekStart: LocalDate,
    val days: List<HistoryCalendarDay>,
    val workoutCount: Int,
)

private data class MilestoneProgress(
    val title: String,
    val current: Int,
    val target: Int,
    val unit: String,
)

private data class RewardVisualSpec(
    val icon: ImageVector,
    val accent: GlowAccent,
    val token: String,
    val message: String,
)

private fun historyStatRewardIcon(title: String): ImageVector? = when (title) {
    "Milestones" -> Icons.Rounded.WorkspacePremium
    "Current Streak" -> Icons.Rounded.LocalFireDepartment
    else -> null
}

private fun milestoneRewardSpec(milestone: MilestoneProgress): RewardVisualSpec {
    val achieved = milestone.current >= milestone.target
    return when (milestone.title) {
        "Volume" -> RewardVisualSpec(
            icon = Icons.Rounded.FitnessCenter,
            accent = goldAccent,
            token = if (achieved) "Iron Medal" else "Next Medal",
            message = if (achieved) {
                "Lifetime load crossed this benchmark. Your volume work now carries real weight."
            } else {
                "Each logged pound pushes you toward the next strength medal."
            },
        )

        "Workouts" -> RewardVisualSpec(
            icon = Icons.Rounded.EmojiEvents,
            accent = emberAccent,
            token = if (achieved) "Session Trophy" else "Trophy Track",
            message = if (achieved) {
                "This reward marks repeatable execution, not a one-off burst."
            } else {
                "String together more sessions and this trophy unlocks on schedule."
            },
        )

        "Minutes" -> RewardVisualSpec(
            icon = Icons.Rounded.Schedule,
            accent = surgeAccent,
            token = if (achieved) "Endurance Ribbon" else "Clocking In",
            message = if (achieved) {
                "Your training time has stacked into a meaningful block of work."
            } else {
                "Consistent minutes build the kind of momentum that compounds."
            },
        )

        else -> RewardVisualSpec(
            icon = Icons.Rounded.WorkspacePremium,
            accent = accentForKey(milestone.title),
            token = if (achieved) "Reward Earned" else "Reward Loading",
            message = if (achieved) {
                "This benchmark is complete and now sits in your history."
            } else {
                "Keep stacking sessions to bring this reward into reach."
            },
        )
    }
}

private fun streakRewardSpec(currentStreakWeeks: Int): RewardVisualSpec = when {
    currentStreakWeeks >= 12 -> RewardVisualSpec(
        icon = Icons.Rounded.LocalFireDepartment,
        accent = goldAccent,
        token = "Gold Streak",
        message = "This run is deep enough to feel like a real training rhythm, not a hot start.",
    )

    currentStreakWeeks >= 4 -> RewardVisualSpec(
        icon = Icons.Rounded.LocalFireDepartment,
        accent = emberAccent,
        token = "Heat Building",
        message = "You are past the fragile phase. Keep the cadence and protect the streak.",
    )

    currentStreakWeeks > 0 -> RewardVisualSpec(
        icon = Icons.Rounded.LocalFireDepartment,
        accent = surgeAccent,
        token = "Spark Lit",
        message = "The streak is alive. Another solid week starts turning it into a habit.",
    )

    else -> RewardVisualSpec(
        icon = Icons.Rounded.WorkspacePremium,
        accent = amethystAccent,
        token = "Next Run",
        message = "Hit your weekly goal to light up the next streak badge.",
    )
}

@Composable
private fun HistoryOverviewHeader(
    data: HistoryDashboardData,
    onOpenWorkouts: () -> Unit,
    onOpenMilestones: () -> Unit,
    onOpenStreak: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    HistoryStatTile("Workouts", data.totalWorkouts.toString(), onClick = onOpenWorkouts, modifier = Modifier.weight(1f))
                    HistoryStatTile("Milestones", data.milestoneProgress.count { it.current >= it.target }.toString(), onClick = onOpenMilestones, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    HistoryStatTile("Weekly Goal", "${data.currentWeekCount}/${data.weeklyGoal}", modifier = Modifier.weight(1f))
                    HistoryStatTile("Current Streak", "${data.currentStreakWeeks} week${if (data.currentStreakWeeks == 1) "" else "s"}", onClick = onOpenStreak, modifier = Modifier.weight(1f))
                }
            }
        }
        data.strengthScore?.let { score ->
            StrengthScoreCard(summary = score)
        }
        FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Calendar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("MMM")),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { label ->
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.currentWeekDays.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(day.date.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .size(width = 26.dp, height = 6.dp)
                                    .background(
                                        if (day.workoutCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun strengthScoreDeltaLabel(summary: StrengthScoreSummary): String = when {
    summary.previousScore == null -> "First tracked workout"
    summary.deltaFromPrevious > 0 -> "+${summary.deltaFromPrevious} vs last"
    summary.deltaFromPrevious < 0 -> "${summary.deltaFromPrevious} vs last"
    else -> "Flat vs last"
}

private fun strengthScoreTrendIcon(summary: StrengthScoreSummary): ImageVector = when {
    summary.previousScore == null -> Icons.Rounded.QueryStats
    summary.deltaFromPrevious > 0 -> Icons.Rounded.FitnessCenter
    summary.deltaFromPrevious < 0 -> Icons.Rounded.Schedule
    else -> Icons.Rounded.QueryStats
}

private fun strengthScoreTrendAccent(summary: StrengthScoreSummary): GlowAccent = when {
    summary.previousScore == null -> amethystAccent
    summary.deltaFromPrevious > 0 -> surgeAccent
    summary.deltaFromPrevious < 0 -> emberAccent
    else -> goldAccent
}

@Composable
private fun StrengthScoreCard(summary: StrengthScoreSummary) {
    val accent = strengthScoreTrendAccent(summary)
    val recentTimeline = summary.timeline.takeLast(6)
    val maxScore = recentTimeline.maxOfOrNull { it.runningScore }?.coerceAtLeast(1) ?: 1

    FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Strength Score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        "Smoothed from recent estimated max effort and completed workload.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = strengthScoreTrendIcon(summary),
                    contentDescription = null,
                    tint = accent.start,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        formatCompactNumber(summary.currentScore.toDouble()),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                    )
                    MiniTag(
                        text = strengthScoreDeltaLabel(summary),
                        accent = accent.start.copy(alpha = 0.18f),
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Peak ${formatCompactNumber(summary.bestScore.toDouble())}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${summary.totalTrackedWorkouts} tracked workout${if (summary.totalTrackedWorkouts == 1) "" else "s"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recentTimeline.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    recentTimeline.forEach { point ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(((point.runningScore.toFloat() / maxScore) * 68f).coerceAtLeast(12f).dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                                        .background(accentBrush(accent)),
                                )
                            }
                            Text(
                                formatCompactNumber(point.runningScore.toDouble()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = accentForKey(title)
    val rewardIcon = historyStatRewardIcon(title)
    FeatureCard(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBrush(accent)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rewardIcon?.let { icon ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.start.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent.start,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HistoryWorkoutStatsScreen(data: HistoryDashboardData, onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryDetailHeader(title = "Workouts", onBack = onBack)
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatRail(
                        items = listOf(
                            Triple("Total workouts", data.totalWorkouts.toString(), ""),
                            Triple("Total time", formatMinutes(data.totalMinutes * 60), ""),
                            Triple("Longest streak", "${data.longestStreakWeeks} wk", ""),
                        ),
                    )
                    StatRail(
                        items = listOf(
                            Triple("Top exercise", data.topExercise ?: "N/A", ""),
                            Triple("Top equipment", data.topEquipment ?: "N/A", ""),
                            Triple("Volume", formatVolume(data.totalVolume), ""),
                        ),
                    )
                    Text("Past 6 months volume", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        val maxVolume = data.monthlyVolume.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
                        data.monthlyVolume.forEach { (month, volume) ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((120 * (volume / maxVolume)).coerceAtLeast(8.0).dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                )
                                Text(month.format(DateTimeFormatter.ofPattern("M")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMilestonesScreen(data: HistoryDashboardData, onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryDetailHeader(title = "Milestones", onBack = onBack)
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RewardMedallion(
                        icon = Icons.Rounded.WorkspacePremium,
                        accent = goldAccent,
                        achieved = data.milestoneProgress.any { it.current >= it.target },
                        modifier = Modifier.size(76.dp),
                        iconSize = 28.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Milestones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Track your workout consistency, training time, and total volume.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            data.milestoneProgress.forEach { milestone ->
                MilestoneRewardCard(milestone = milestone)
            }
        }
    }
}

@Composable
private fun HistoryStreakScreen(data: HistoryDashboardData, onBack: () -> Unit) {
    val streakSpec = streakRewardSpec(data.currentStreakWeeks)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryDetailHeader(title = "Streak", onBack = onBack)
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    streakSpec.accent.glow.copy(alpha = 0.24f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RewardMedallion(
                            icon = streakSpec.icon,
                            accent = streakSpec.accent,
                            achieved = data.currentStreakWeeks > 0,
                            modifier = Modifier.size(84.dp),
                            iconSize = 30.dp,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniTag(streakSpec.token, accent = streakSpec.accent.start.copy(alpha = 0.18f))
                            Text("${data.currentStreakWeeks}-Week Streak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(
                                streakSpec.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Goal: ${data.weeklyGoal} or more workouts per week",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Longest streak: ${data.longestStreakWeeks} week${if (data.longestStreakWeeks == 1) "" else "s"}",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    data.streakWeeks.forEach { week ->
                        val hitGoal = week.workoutCount >= data.weeklyGoal
                        val weekAccent = if (hitGoal) goldAccent else surgeAccent
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RewardMedallion(
                                    icon = if (hitGoal) Icons.Rounded.EmojiEvents else Icons.Rounded.WorkspacePremium,
                                    accent = weekAccent,
                                    achieved = hitGoal,
                                    showRibbons = false,
                                    modifier = Modifier.size(34.dp),
                                    iconSize = 16.dp,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Week of ${week.weekStart.format(DateTimeFormatter.ofPattern("MMM d"))}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        if (hitGoal) {
                                            "Goal met with ${week.workoutCount} workout${if (week.workoutCount == 1) "" else "s"}."
                                        } else {
                                            "${week.workoutCount} workout${if (week.workoutCount == 1) "" else "s"} logged."
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                week.days.forEach { day ->
                                    val dayFill = if (day.workoutCount > 0) Color(0xFF2A9D8F) else MaterialTheme.colorScheme.surfaceVariant
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(day.date.format(DateTimeFormatter.ofPattern("E")).take(2))
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(dayFill, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                day.date.dayOfMonth.toString(),
                                                fontWeight = FontWeight.Bold,
                                                color = readableTextColorFor(dayFill),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Text("←", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun MilestoneRewardCard(milestone: MilestoneProgress) {
    val achieved = milestone.current >= milestone.target
    val spec = milestoneRewardSpec(milestone)
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            spec.accent.glow.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RewardMedallion(
                    icon = spec.icon,
                    accent = spec.accent,
                    achieved = achieved,
                    modifier = Modifier.size(78.dp),
                    iconSize = 28.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniTag(spec.token, accent = spec.accent.start.copy(alpha = 0.18f))
                        if (achieved) {
                            MiniTag("Unlocked", accent = spec.accent.end.copy(alpha = 0.2f))
                        }
                    }
                    Text(milestone.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        spec.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProgressPill(
                        current = milestone.current,
                        target = milestone.target,
                        label = "${milestone.current} / ${milestone.target} ${milestone.unit}",
                        accent = spec.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardMedallion(
    icon: ImageVector,
    accent: GlowAccent,
    modifier: Modifier = Modifier,
    achieved: Boolean,
    showRibbons: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val badgeCenter = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * if (showRibbons) 0.42f else 0.5f)
            val outerRadius = size.minDimension * if (showRibbons) 0.28f else 0.34f
            val innerRadius = outerRadius * 0.76f
            if (showRibbons) {
                val ribbonSize = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.26f)
                rotate(degrees = -12f, pivot = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.68f)) {
                    drawRoundRect(
                        color = accent.end.copy(alpha = if (achieved) 0.86f else 0.66f),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.23f, size.height * 0.54f),
                        size = ribbonSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(ribbonSize.width * 0.42f),
                    )
                }
                rotate(degrees = 12f, pivot = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.68f)) {
                    drawRoundRect(
                        color = accent.start.copy(alpha = if (achieved) 0.92f else 0.72f),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.61f, size.height * 0.54f),
                        size = ribbonSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(ribbonSize.width * 0.42f),
                    )
                }
            }
            drawCircle(
                color = accent.glow.copy(alpha = if (achieved) 0.62f else 0.36f),
                radius = outerRadius * 1.35f,
                center = badgeCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.start.copy(alpha = 0.98f),
                        accent.end.copy(alpha = 0.94f),
                    ),
                    center = badgeCenter,
                    radius = outerRadius,
                ),
                radius = outerRadius,
                center = badgeCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = if (achieved) 0.32f else 0.18f),
                radius = innerRadius,
                center = badgeCenter,
                style = Stroke(width = outerRadius * 0.18f),
            )
            drawCircle(
                color = Color.White.copy(alpha = if (achieved) 0.18f else 0.1f),
                radius = innerRadius * 0.58f,
                center = badgeCenter.copy(y = badgeCenter.y - (outerRadius * 0.18f)),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent.textOnAccent,
            modifier = Modifier
                .size(iconSize)
                .offset(y = if (showRibbons) (-6).dp else 0.dp),
        )
        if (achieved) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = accent.start,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressPill(current: Int, target: Int, label: String, accent: GlowAccent = accentForKey(label)) {
    val progress = (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("${(progress * 100).roundToInt()}%", color = accent.start, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxSize()
                    .background(accentBrush(accent)),
            )
        }
    }
}

private fun buildHistoryDashboardData(
    history: List<HistorySummary>,
    weeklyGoal: Int,
    topExercise: String?,
    topEquipment: String?,
    strengthScore: StrengthScoreSummary?,
): HistoryDashboardData {
    val workoutDates = history.mapNotNull { runCatching { Instant.parse(it.completedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
    val workoutCountsByDate = workoutDates.groupingBy { it }.eachCount()
    val currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
    val currentWeekDays = (0L..6L).map { offset ->
        val date = currentWeekStart.plusDays(offset)
        HistoryCalendarDay(date = date, workoutCount = workoutCountsByDate[date] ?: 0)
    }
    val workoutsByWeek = workoutDates.groupingBy { it.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)) }.eachCount().toSortedMap()
    val longestStreak = calculateLongestStreak(workoutsByWeek, weeklyGoal)
    val currentStreak = calculateCurrentStreak(workoutsByWeek, weeklyGoal, currentWeekStart)
    val streakWeeks = buildList {
        val weeksToShow = maxOf(currentStreak, 1)
        repeat(weeksToShow.coerceAtMost(4)) { index ->
            val weekStart = currentWeekStart.minusWeeks((weeksToShow - index - 1).toLong())
            add(
                HistoryWeekSnapshot(
                    weekStart = weekStart,
                    workoutCount = workoutsByWeek[weekStart] ?: 0,
                    days = (0L..6L).map { offset ->
                        val date = weekStart.plusDays(offset)
                        HistoryCalendarDay(date = date, workoutCount = workoutCountsByDate[date] ?: 0)
                    },
                ),
            )
        }
    }
    val monthlyVolume = (-5L..0L).map { offset ->
        val month = YearMonth.now().plusMonths(offset)
        month to history.filter {
            runCatching { YearMonth.from(Instant.parse(it.completedAtUtc).atZone(ZoneId.systemDefault())) }.getOrNull() == month
        }.sumOf { it.totalVolume }
    }
    val totalWorkouts = history.size
    val totalMinutes = history.sumOf { it.durationSeconds } / 60
    val totalVolume = history.sumOf { it.totalVolume }
    return HistoryDashboardData(
        totalWorkouts = totalWorkouts,
        totalMinutes = totalMinutes,
        totalVolume = totalVolume,
        weeklyGoal = weeklyGoal,
        currentWeekCount = currentWeekDays.sumOf { it.workoutCount },
        currentStreakWeeks = currentStreak,
        longestStreakWeeks = longestStreak,
        topExercise = topExercise,
        topEquipment = topEquipment,
        currentWeekDays = currentWeekDays,
        streakWeeks = streakWeeks,
        monthlyVolume = monthlyVolume,
        milestoneProgress = listOf(
            MilestoneProgress("Volume", totalVolume.toInt(), nextMilestone(totalVolume.toInt(), listOf(10_000, 25_000, 50_000, 100_000)), "lb"),
            MilestoneProgress("Workouts", totalWorkouts, nextMilestone(totalWorkouts, listOf(10, 20, 50, 100)), "workouts"),
            MilestoneProgress("Minutes", totalMinutes, nextMilestone(totalMinutes, listOf(300, 600, 1_200, 2_400)), "min"),
        ),
        strengthScore = strengthScore,
    )
}

private fun calculateCurrentStreak(
    workoutsByWeek: Map<LocalDate, Int>,
    weeklyGoal: Int,
    currentWeekStart: LocalDate,
): Int {
    var cursor = currentWeekStart
    var streak = 0
    while ((workoutsByWeek[cursor] ?: 0) >= weeklyGoal) {
        streak += 1
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

private fun calculateLongestStreak(workoutsByWeek: Map<LocalDate, Int>, weeklyGoal: Int): Int {
    val qualifyingWeeks = workoutsByWeek.filterValues { it >= weeklyGoal }.keys.sorted()
    if (qualifyingWeeks.isEmpty()) return 0
    var best = 1
    var current = 1
    for (index in 1 until qualifyingWeeks.size) {
        current = if (qualifyingWeeks[index - 1].plusWeeks(1) == qualifyingWeeks[index]) current + 1 else 1
        best = maxOf(best, current)
    }
    return best
}

private fun nextMilestone(current: Int, thresholds: List<Int>): Int =
    thresholds.firstOrNull { current < it } ?: thresholds.last()

@Composable
private fun HistoryEntryCard(
    entry: HistorySummary,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FeatureCard(
        modifier = Modifier.clickable(onClick = onOpen),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.completedAtUtc.replace("T", " ").removeSuffix("Z"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniTag("${entry.exerciseCount} exercises", accent = MaterialTheme.colorScheme.primaryContainer)
                    entry.strengthScore?.let { score ->
                        MiniTag("Strength ${formatCompactNumber(score.toDouble())}", accent = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Text(
                                "⋮",
                                modifier = Modifier.semantics { contentDescription = "History entry actions" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("View") },
                                onClick = {
                                    expanded = false
                                    onOpen()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    expanded = false
                                    onShare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    expanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            StatRail(
                items = listOf(
                    Triple("Elapsed", formatMinutes(entry.durationSeconds), ""),
                    Triple("Volume", formatVolume(entry.totalVolume), ""),
                    Triple("Moves", entry.exerciseNames.distinct().size.toString(), "logged"),
                ),
            )
            Text(
                entry.exerciseNames.joinToString(limit = 4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileScreen(state: AppUiState, viewModel: ToastLiftViewModel) {
    ProfileEditor(
        title = "Profile",
        subtitle = "Settings are grouped into compact cards. Equipment configuration lives in bottom sheets.",
        themePreference = state.themePreference,
        draft = state.onboardingDraft,
        splitPrograms = state.splitPrograms,
        locationModes = state.locationModes,
        equipmentOptions = state.equipmentOptions,
        equipmentByLocation = state.equipmentByLocation,
        onThemePreferenceChange = viewModel::setThemePreference,
        onDraftChange = viewModel::updateOnboardingDraft,
        onToggleEquipment = viewModel::toggleEquipment,
        onSave = viewModel::saveProfile,
        saveLabel = "Save changes",
        profile = state.profile,
        onSetActiveLocation = viewModel::setActiveLocationMode,
        onSetGymMachineCableBiasEnabled = viewModel::setGymMachineCableBiasEnabled,
        onSetHistoryWorkoutAbFlagsVisible = viewModel::setHistoryWorkoutAbFlagsVisible,
        onSetDevPickNextExerciseEnabled = viewModel::setDevPickNextExerciseEnabled,
        onSetDevFruitExerciseIconsEnabled = viewModel::setDevFruitExerciseIconsEnabled,
        onExportPersonalData = viewModel::preparePersonalDataExport,
        onDeletePersonalData = viewModel::deleteAllPersonalData,
        showAppearanceSettings = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(
    title: String,
    subtitle: String,
    themePreference: ThemePreference = ThemePreference.Dark,
    draft: OnboardingDraft,
    splitPrograms: List<TrainingSplitProgram>,
    locationModes: List<LocationMode>,
    equipmentOptions: List<String>,
    equipmentByLocation: Map<Long, Set<String>>,
    onThemePreferenceChange: (ThemePreference) -> Unit = {},
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onToggleEquipment: (Long, String) -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    profile: UserProfile? = null,
    onSetActiveLocation: ((Long) -> Unit)? = null,
    onSetGymMachineCableBiasEnabled: ((Boolean) -> Unit)? = null,
    onSetHistoryWorkoutAbFlagsVisible: ((Boolean) -> Unit)? = null,
    onSetDevPickNextExerciseEnabled: ((Boolean) -> Unit)? = null,
    onSetDevFruitExerciseIconsEnabled: ((Boolean) -> Unit)? = null,
    onExportPersonalData: (() -> Unit)? = null,
    onDeletePersonalData: (() -> Unit)? = null,
    showAppearanceSettings: Boolean = false,
) {
    var equipmentSheetMode by remember { mutableStateOf<LocationMode?>(null) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    val splitName = splitPrograms.firstOrNull { it.id == draft.splitProgramId }?.name ?: ""
    val activeLocationMode = profile?.let { activeProfile ->
        locationModes.firstOrNull { it.id == activeProfile.activeLocationModeId }
    }
    val activeLocationLabel = activeLocationMode?.displayName.orEmpty()
    val activeLocationEquipmentCount = activeLocationMode?.let { mode ->
        equipmentByLocation[mode.id]?.size ?: 0
    } ?: 0
    val durationPresets = listOf(30, 45, 60, 75)
    var durationInput by rememberSaveable(draft.durationMinutes) {
        mutableStateOf(draft.durationMinutes.toString())
    }
    val durationValidationMessage = workoutDurationValidationMessage(durationInput)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (profile != null && onSetActiveLocation != null) {
            CompactSectionCard(
                title = "Home or Gym",
                subtitle = if (activeLocationLabel.isNotBlank()) {
                    "Active mode: $activeLocationLabel"
                } else {
                    "Choose the equipment context to use right now"
                },
            ) {
                Text("Active setup", fontWeight = FontWeight.SemiBold)
                ChoiceChipRow(
                    values = locationModes.map { it.displayName },
                    selected = activeLocationLabel,
                    onSelect = { selected ->
                        locationModes.firstOrNull { it.displayName == selected }?.let { onSetActiveLocation(it.id) }
                    },
                )
                if (activeLocationLabel.isNotBlank()) {
                    Text(
                        "$activeLocationLabel currently has $activeLocationEquipmentCount enabled tool${if (activeLocationEquipmentCount == 1) "" else "s"}. Equipment details stay available below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        RichHeroCard(
            eyebrow = "Profile",
            title = title,
            subtitle = subtitle,
            accent = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            StatRail(
                items = listOf(
                    Triple("Split", compactSplitLabel(splitName), "program"),
                    Triple("Duration", draft.durationMinutes.toString(), "min"),
                    Triple("Frequency", draft.weeklyFrequency.toString(), "days"),
                ),
            )
        }

        CompactSectionCard(title = "Training defaults", subtitle = "${draft.goal} • ${draft.experience} • ${draft.durationMinutes} min") {
            Text("Goal", fontWeight = FontWeight.SemiBold)
            ChoiceChipRow(
                values = listOf("General Fitness", "Strength", "Hypertrophy", "Conditioning", "Fat Loss"),
                selected = draft.goal,
                onSelect = { selected -> onDraftChange { it.copy(goal = selected) } },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Experience", fontWeight = FontWeight.SemiBold)
            ChoiceChipRow(
                values = listOf("Beginner", "Novice", "Intermediate", "Advanced"),
                selected = draft.experience,
                onSelect = { selected -> onDraftChange { it.copy(experience = selected) } },
            )
        }

        CompactSectionCard(title = "Schedule", subtitle = "${draft.weeklyFrequency} days per week") {
            Text("Duration", fontWeight = FontWeight.SemiBold)
            ChoiceChipRow(
                values = durationPresets.map(Int::toString),
                selected = durationPresets.firstOrNull { it == draft.durationMinutes }?.toString().orEmpty(),
                onSelect = { selected ->
                    val minutes = selected.toInt()
                    durationInput = minutes.toString()
                    onDraftChange { it.copy(durationMinutes = minutes) }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = durationInput,
                onValueChange = { updated ->
                    val sanitized = updated.filter(Char::isDigit)
                    durationInput = sanitized
                    val minutes = sanitized.toIntOrNull()
                    if (minutes != null && isValidWorkoutDurationMinutes(minutes)) {
                        onDraftChange { it.copy(durationMinutes = minutes) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true,
                suffix = { Text("min") },
                isError = durationValidationMessage != null,
                supportingText = {
                    Text(
                        durationValidationMessage
                            ?: "Choose a preset or enter any duration from $MIN_WORKOUT_DURATION_MINUTES to $MAX_WORKOUT_DURATION_MINUTES minutes.",
                    )
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Weekly frequency", fontWeight = FontWeight.SemiBold)
            ChoiceChipRow(
                values = weeklyFrequencyOptionValues,
                selected = draft.weeklyFrequency.toString(),
                onSelect = { selected -> onDraftChange { it.copy(weeklyFrequency = selected.toInt()) } },
            )
        }

        CompactSectionCard(title = "Split program", subtitle = splitPrograms.firstOrNull { it.id == draft.splitProgramId }?.name ?: "") {
            ChoiceChipRow(
                values = splitPrograms.map { it.name },
                selected = splitPrograms.firstOrNull { it.id == draft.splitProgramId }?.name ?: "",
                onSelect = { selected ->
                    splitPrograms.firstOrNull { it.name == selected }?.let { split ->
                        onDraftChange { it.copy(splitProgramId = split.id) }
                    }
                },
            )
        }

        if (showAppearanceSettings) {
            CompactSectionCard(
                title = "Appearance",
                subtitle = "Dark stays the default until you pick Light or follow the device setting.",
            ) {
                Text("Theme", fontWeight = FontWeight.SemiBold)
                ChoiceChipRow(
                    values = ThemePreference.entries.map { it.label() },
                    selected = themePreference.label(),
                    onSelect = { selected ->
                        ThemePreference.entries.firstOrNull { it.label() == selected }?.let(onThemePreferenceChange)
                    },
                )
            }
        }

        CompactSectionCard(title = "Equipment", subtitle = "Fine-tune enabled tools for Home and Gym in sheets") {
            locationModes.forEach { mode ->
                CompactRow(
                    title = mode.displayName,
                    subtitle = "${equipmentByLocation[mode.id]?.size ?: 0} enabled tools",
                    actionLabel = "Configure",
                    onAction = { equipmentSheetMode = mode },
                )
            }
        }

        if (
            profile != null && (
                onSetGymMachineCableBiasEnabled != null ||
                    onSetHistoryWorkoutAbFlagsVisible != null ||
                    onSetDevPickNextExerciseEnabled != null ||
                    onSetDevFruitExerciseIconsEnabled != null
            )
        ) {
            CompactSectionCard(
                title = "Dev",
                subtitle = "Debug generator preference behavior and history experiment visibility.",
            ) {
                onSetGymMachineCableBiasEnabled?.let { onToggle ->
                    SettingsSwitchRow(
                        label = "Bias toward machine and cable in gym mode",
                        supportingText = "Only applies when Gym is active and both Machine and Cable are enabled. The generator then targets roughly two-thirds of the workout from those categories.",
                        checked = profile.gymMachineCableBiasEnabled,
                        onCheckedChange = onToggle,
                    )
                }
                onSetHistoryWorkoutAbFlagsVisible?.let { onToggle ->
                    SettingsSwitchRow(
                        label = "Show History workout A/B flags",
                        supportingText = "Shows each completed workout's persisted Today completion feedback experiment snapshot in History detail. Export JSON always includes the snapshot.",
                        checked = profile.historyWorkoutAbFlagsVisible,
                        onCheckedChange = onToggle,
                    )
                }
                onSetDevPickNextExerciseEnabled?.let { onToggle ->
                    SettingsSwitchRow(
                        label = "Show Pick Next Exercise helper",
                        supportingText = "Adds a prominent workout button that randomly opens one untouched exercise so you can start logging without choosing manually.",
                        checked = profile.devPickNextExerciseEnabled,
                        onCheckedChange = onToggle,
                    )
                }
                onSetDevFruitExerciseIconsEnabled?.let { onToggle ->
                    SettingsSwitchRow(
                        label = "Use fruit workout badges",
                        supportingText = "Swaps the active workout exercise list from equipment initials to memorized fruit icons while the workout is in progress.",
                        checked = profile.devFruitExerciseIconsEnabled,
                        onCheckedChange = onToggle,
                    )
                }
            }
        }

        if (onExportPersonalData != null || onDeletePersonalData != null) {
            CompactSectionCard(
                title = "Privacy",
                subtitle = "Export or delete personal data stored on this device",
            ) {
                Text(
                    "Export your personal data as JSON in a structured, machine-readable format before deleting it or moving it elsewhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onExportPersonalData?.let { exportPersonalData ->
                    Button(
                        onClick = exportPersonalData,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export My Data (JSON)")
                    }
                }
                if (onDeletePersonalData != null) {
                    OutlinedButton(
                        onClick = { showDeleteSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete My Data", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = durationValidationMessage == null,
        ) {
            Text(saveLabel)
        }
    }

    equipmentSheetMode?.let { mode ->
        EquipmentSheet(
            mode = mode,
            equipmentOptions = equipmentOptions,
            selectedEquipment = equipmentByLocation[mode.id].orEmpty(),
            onDismiss = { equipmentSheetMode = null },
            onToggleEquipment = { equipment -> onToggleEquipment(mode.id, equipment) },
        )
    }

    if (showDeleteSheet && onDeletePersonalData != null) {
        DeleteDataSheet(
            onDismiss = { showDeleteSheet = false },
            onConfirm = {
                showDeleteSheet = false
                onDeletePersonalData()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChipRow(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(value) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseListCard(
    exercise: ExerciseSummary,
    onDetails: () -> Unit,
    onAdd: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenExerciseHistory: () -> Unit,
    onOpenExerciseVideos: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FeatureCard(modifier = Modifier.clickable(onClick = onDetails)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LeadingBadge(
                        label = equipmentBadgeLabel(exercise.equipment),
                        accent = accentForKey(exercise.equipment),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${exercise.bodyRegion} • ${exercise.targetMuscleGroup} • ${exercise.equipment}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniTag(exercise.difficulty)
                            MiniTag(exercise.mechanics ?: "Open chain")
                            if (exercise.favorite) MiniTag("Favorite", accent = MaterialTheme.colorScheme.primaryContainer)
                            when (exercise.recommendationBias) {
                                RecommendationBias.MoreOften ->
                                    MiniTag("Preference up", accent = MaterialTheme.colorScheme.primaryContainer)
                                RecommendationBias.LessOften ->
                                    MiniTag("Preference down", accent = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f))
                                RecommendationBias.Neutral -> Unit
                            }
                        }
                    }
                }
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RecommendationBiasIndicator(exercise.recommendationBias)
                        IconButton(onClick = { expanded = true }) {
                            Text(
                                "⋮",
                                modifier = Modifier.semantics { contentDescription = "Exercise actions" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Details") }, onClick = { expanded = false; onDetails() })
                        DropdownMenuItem(text = { Text("Add to builder") }, onClick = { expanded = false; onAdd() })
                        DropdownMenuItem(text = { Text("Exercise history") }, onClick = { expanded = false; onOpenExerciseHistory() })
                        DropdownMenuItem(text = { Text("Videos") }, onClick = { expanded = false; onOpenExerciseVideos() })
                        DropdownMenuItem(
                            text = { Text(if (exercise.favorite) "Unfavorite" else "Favorite") },
                            onClick = { expanded = false; onToggleFavorite() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSectionCard(
    title: String,
    subtitle: String,
    menuItems: List<Pair<String, () -> Unit>> = emptyList(),
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FeatureCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (menuItems.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Text(
                                "⋮",
                                modifier = Modifier.semantics { contentDescription = "Section actions" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            menuItems.forEach { (label, action) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        expanded = false
                                        action()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun CompactRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadingBadge(label = title.take(2).uppercase())
            Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = actionLabel,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAction).padding(8.dp),
        )
    }
}

@Composable
private fun GeneratedWorkoutCard(
    workout: WorkoutPlan,
    recommendationBiasByExerciseId: Map<Long, RecommendationBias>,
    onSwapGenerated: () -> Unit,
    onSaveGenerated: () -> Unit,
    onStartGenerated: () -> Unit,
    onShowExerciseDetail: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Long) -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
) {
    val accent = accentForKey("${workout.title} ${workout.subtitle}")
    val canSwapWorkout = workout.origin == "generated" && workout.focusKey != null
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.glow.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Plan", style = MaterialTheme.typography.labelLarge, color = accent.start, fontWeight = FontWeight.Bold)
                    Text(workout.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        workout.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${workout.exercises.size} exercises • ${workout.estimatedMinutes} min",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canSwapWorkout) {
                        OutlinedButton(onClick = onSwapGenerated) {
                            Text("Swap")
                        }
                    }
                    OutlinedButton(onClick = onSaveGenerated) {
                        Text("Save")
                    }
                }
            }
            workout.decisionSummary.take(3).forEach { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            workout.exercises.forEach { exercise ->
                val weightLabel = exercise.suggestedWeight?.let { " • ${decimalString(it)} suggested" }.orEmpty()
                val detail = listOfNotNull(
                    exercise.equipment,
                    exercise.overloadStrategy?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
                ).joinToString(" • ")
                WorkoutExerciseHistoryRow(
                    title = exercise.name,
                    subtitle = "${exercise.sets} sets • ${exercise.repRange}$weightLabel",
                    detail = detail,
                    equipment = exercise.equipment,
                    recommendationBias = recommendationBiasByExerciseId[exercise.exerciseId] ?: RecommendationBias.Neutral,
                    onShowDetail = { onShowExerciseDetail(exercise.exerciseId) },
                    onRemove = { onRemoveExercise(exercise.exerciseId) },
                    removeLabel = "Remove from My Plan",
                    onOpenExerciseHistory = { onOpenExerciseHistory(exercise.exerciseId, exercise.name) },
                    onOpenExerciseVideos = { onOpenExerciseVideos(exercise.exerciseId, exercise.name) },
                )
            }
            TextButtonLike(
                text = "Add Exercise",
                onClick = onAddExercise,
            )
            Button(
                onClick = onStartGenerated,
                modifier = Modifier.fillMaxWidth(),
                enabled = workout.exercises.isNotEmpty(),
            ) {
                Text("Start Workout")
            }
        }
    }
}

private fun percentString(value: Double): String = "${(value * 100).roundToInt()}%"

private fun decimalString(value: Double): String = if (value % 1.0 == 0.0) {
    value.roundToInt().toString()
} else {
    "%.1f".format(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSheet(
    title: String,
    subtitle: String,
    exercises: List<WorkoutExercise>,
    onDismiss: () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            exercises.forEach { exercise ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exercise.name, fontWeight = FontWeight.SemiBold)
                        Text("${exercise.sets} sets • ${exercise.repRange} reps • ${exercise.restSeconds}s rest")
                        Text(exercise.rationale, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                Text(primaryActionLabel)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapWorkoutSheet(
    splitName: String,
    currentFocus: String?,
    onDismiss: () -> Unit,
    onSelectFocus: (String) -> Unit,
) {
    val options = swapOptionsForSplit(splitName)
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Swap Workout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Within training split", color = MaterialTheme.colorScheme.onSurfaceVariant)
            options.forEach { (focusKey, label) ->
                val selected = focusKey == currentFocus
                FeatureCard(
                    modifier = Modifier.clickable(enabled = !selected) { onSelectFocus(focusKey) },
                    containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LeadingBadge(label = label.take(2).uppercase())
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            if (selected) "●" else "○",
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualBuilderSheet(
    title: String,
    isEditingTemplate: Boolean,
    items: List<WorkoutExercise>,
    recommendationBiasByExerciseId: Map<Long, RecommendationBias>,
    onDismiss: () -> Unit,
    onTitleChange: ((String) -> Unit)? = null,
    onRemoveExercise: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
    onSaveTemplate: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = onAddExercise) {
                    Text("Add Exercise")
                }
            }
            onTitleChange?.let { updateTitle ->
                OutlinedTextField(
                    value = title,
                    onValueChange = updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Workout name") },
                )
            }
            if (items.isEmpty()) {
                Text("No builder exercises yet.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.exerciseId }) { item ->
                        BuilderExerciseRow(
                            title = item.name,
                            subtitle = "${item.sets} sets • ${item.repRange} reps • ${item.equipment}",
                            equipment = item.equipment,
                            recommendationBias = recommendationBiasByExerciseId[item.exerciseId] ?: RecommendationBias.Neutral,
                            onRemove = { onRemoveExercise(item.exerciseId) },
                            onOpenExerciseHistory = { onOpenExerciseHistory(item.exerciseId, item.name) },
                            onOpenExerciseVideos = { onOpenExerciseVideos(item.exerciseId, item.name) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSaveTemplate, modifier = Modifier.weight(1f)) {
                    Text(if (isEditingTemplate) "Update" else "Save")
                }
                Button(onClick = onStartWorkout, modifier = Modifier.weight(1f)) {
                    Text("Start")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EquipmentSheet(
    mode: LocationMode,
    equipmentOptions: List<String>,
    selectedEquipment: Set<String>,
    onDismiss: () -> Unit,
    onToggleEquipment: (String) -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("${mode.displayName} equipment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Text("${selectedEquipment.size} enabled items", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    equipmentSelectionOptions(equipmentOptions).forEach { equipment ->
                        FilterChip(
                            selected = selectedEquipment.contains(equipment),
                            onClick = { onToggleEquipment(equipment) },
                            label = { Text(equipment) },
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteDataSheet(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Delete my data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "This permanently deletes your personal information stored in the app on this device, including workout history, templates, profile preferences, favorites, and equipment setup.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "This action cannot be undone.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("Delete")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkippedExerciseFeedbackSheet(
    prompt: SkippedExerciseFeedbackPrompt,
    onDismiss: () -> Unit,
    onDislikeExercise: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Skipped exercise", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "You skipped ${prompt.exerciseName}. If that exercise felt like friction, ToastLift can bias away from it next time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Ignore")
                }
                Button(onClick = onDislikeExercise, modifier = Modifier.weight(1f)) {
                    Text("Don't like it")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddExercisesFlowScreen(
    state: AppUiState,
    onDismiss: () -> Unit,
    onConfirmAdd: (List<ExerciseSummary>) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleEquipmentFilter: (String) -> Unit,
    onToggleTargetMuscleFilter: (String) -> Unit,
    onTogglePrimeMoverFilter: (String) -> Unit,
    onToggleRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLoggedHistoryFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onShowDetail: (Long) -> Unit,
    onOpenCustomExercise: () -> Unit,
    onCloseCustomExercise: () -> Unit,
    onCustomExerciseDraftChange: (CustomExerciseDraft) -> Unit,
    onCustomExerciseNameChange: (String) -> Unit,
    onGenerateCustomExercise: () -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSaveCustomExercise: () -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedExercises = remember { mutableStateMapOf<Long, ExerciseSummary>() }
    val listState = rememberLazyListState()
    var scrollTargetId by remember { mutableStateOf<Long?>(null) }
    val displayedExercises = remember(state.libraryResults, selectedExercises.keys.toSet(), scrollTargetId) {
        buildList {
            val libraryIds = state.libraryResults.map { it.id }.toSet()
            scrollTargetId?.let { targetId ->
                selectedExercises[targetId]
                    ?.takeIf { it.id !in libraryIds }
                    ?.let(::add)
            }
            selectedExercises.values
                .filter { it.id !in libraryIds && it.id != scrollTargetId }
                .forEach(::add)
            addAll(state.libraryResults)
        }
    }

    LaunchedEffect(state.pendingAddExercisePickerSelection?.id) {
        val pending = state.pendingAddExercisePickerSelection ?: return@LaunchedEffect
        selectedExercises[pending.id] = pending
        scrollTargetId = pending.id
        onPendingSelectionConsumed()
    }

    LaunchedEffect(scrollTargetId, displayedExercises) {
        val targetId = scrollTargetId ?: return@LaunchedEffect
        val targetIndex = displayedExercises.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
        scrollTargetId = null
    }

    if (state.customExerciseDraft != null) {
        CustomExerciseEditorScreen(
            draft = state.customExerciseDraft,
            onBack = onCloseCustomExercise,
            onDraftChange = onCustomExerciseDraftChange,
            onNameChange = onCustomExerciseNameChange,
            onGenerate = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSave = onSaveCustomExercise,
        )
        return
    }

    Box(modifier = modifier) {
        BuilderAddExercisesScreen(
            state = state,
            displayedExercises = displayedExercises,
            selectedExercises = selectedExercises,
            listState = listState,
            onDismiss = onDismiss,
            onConfirmAdd = { onConfirmAdd(selectedExercises.values.toList()) },
            onQueryChange = onQueryChange,
            onToggleSearch = onToggleSearch,
            onToggleFavoritesOnly = onToggleFavoritesOnly,
            onToggleEquipmentFilter = onToggleEquipmentFilter,
            onToggleTargetMuscleFilter = onToggleTargetMuscleFilter,
            onTogglePrimeMoverFilter = onTogglePrimeMoverFilter,
            onToggleRecommendationBiasFilter = onToggleRecommendationBiasFilter,
            onToggleLoggedHistoryFilter = onToggleLoggedHistoryFilter,
            onClearFilters = onClearFilters,
            onShowDetail = onShowDetail,
            overflowActionLabel = "Add custom exercise",
            onOverflowActionClick = onOpenCustomExercise,
        )
    }
}

@Composable
private fun BuilderAddExercisesScreen(
    state: AppUiState,
    displayedExercises: List<ExerciseSummary>,
    selectedExercises: SnapshotStateMap<Long, ExerciseSummary>,
    listState: LazyListState,
    onDismiss: () -> Unit,
    onConfirmAdd: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleEquipmentFilter: (String) -> Unit,
    onToggleTargetMuscleFilter: (String) -> Unit,
    onTogglePrimeMoverFilter: (String) -> Unit,
    onToggleRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLoggedHistoryFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onShowDetail: (Long) -> Unit,
    overflowActionLabel: String? = null,
    onOverflowActionClick: (() -> Unit)? = null,
) {
    var showFilterScreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showFilterScreen) {
        BuilderFilterScreen(
            facets = state.libraryFacets,
            filters = state.libraryFilters,
            onDismiss = { showFilterScreen = false },
            onApplyFilters = { showFilterScreen = false },
            onClearFilters = onClearFilters,
            onToggleEquipment = onToggleEquipmentFilter,
            onToggleTargetMuscle = onToggleTargetMuscleFilter,
            onTogglePrimeMover = onTogglePrimeMoverFilter,
            onToggleRecommendationBias = onToggleRecommendationBiasFilter,
            onToggleLoggedHistory = onToggleLoggedHistoryFilter,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close add exercises")
            }
            if (state.librarySearchVisible) {
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    singleLine = true,
                    placeholder = { Text("Search exercises") },
                    trailingIcon = {
                        IconButton(onClick = onToggleSearch) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                OutlinedButton(onClick = { showFilterScreen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Open filters",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.libraryFilters.activeCount() == 0) "Filters"
                        else "Filters ${state.libraryFilters.activeCount()}",
                    )
                }
                IconButton(onClick = onToggleFavoritesOnly) {
                    Icon(
                        imageVector = if (state.libraryFilters.favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = if (state.libraryFilters.favoritesOnly) "Show all exercises" else "Show favorites only",
                        tint = if (state.libraryFilters.favoritesOnly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search exercises")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Text(
                            "⋮",
                            modifier = Modifier.semantics { contentDescription = "Add exercise options" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (overflowActionLabel != null && onOverflowActionClick != null) {
                            DropdownMenuItem(
                                text = { Text(overflowActionLabel) },
                                onClick = {
                                    showMenu = false
                                    onOverflowActionClick()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Clear selected exercises") },
                            enabled = selectedExercises.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                selectedExercises.clear()
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = onConfirmAdd,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedExercises.isNotEmpty(),
        ) {
            Text("Add ${selectedExercises.size} Exercise${if (selectedExercises.size == 1) "" else "s"}")
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(displayedExercises, key = { it.id }) { exercise ->
                val selected = selectedExercises.containsKey(exercise.id)
                SelectableExerciseCard(
                    exercise = exercise,
                    selected = selected,
                    onToggleSelected = {
                        if (selected) selectedExercises.remove(exercise.id) else selectedExercises[exercise.id] = exercise
                    },
                    onShowDetail = { onShowDetail(exercise.id) },
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SelectableExerciseCard(
    exercise: ExerciseSummary,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onShowDetail: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    FeatureCard(
        modifier = Modifier.clickable(onClick = onToggleSelected),
        containerColor = containerColor,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        },
        elevation = if (selected) 6.dp else 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseDetailBadge(
                label = equipmentBadgeLabel(exercise.equipment),
                accent = accentForKey(exercise.equipment),
                onClick = onShowDetail,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercise.bodyRegion} • ${exercise.targetMuscleGroup} • ${exercise.equipment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectionIndicatorSlot(selected = selected)
        }
    }
}

@Composable
private fun SelectionIndicatorSlot(selected: Boolean) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .padding(start = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuilderFilterScreen(
    facets: LibraryFacets,
    filters: LibraryFilters,
    onDismiss: () -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleEquipment: (String) -> Unit,
    onToggleTargetMuscle: (String) -> Unit,
    onTogglePrimeMover: (String) -> Unit,
    onToggleRecommendationBias: (RecommendationBias) -> Unit,
    onToggleLoggedHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close filters")
            }
            OutlinedButton(
                onClick = onClearFilters,
                enabled = filters.activeCount() > 0,
            ) {
                Text("Reset All Filters")
            }
            Button(onClick = onApplyFilters) {
                Text("Apply Filters")
            }
        }
        FilterFacetSection(
            title = "Equipment",
            options = facets.equipment,
            selected = filters.equipment,
            onToggle = onToggleEquipment,
        )
        FilterFacetSection(
            title = "Target Muscle Group",
            options = facets.targetMuscles,
            selected = filters.targetMuscles,
            onToggle = onToggleTargetMuscle,
        )
        FilterFacetSection(
            title = "Prime Mover Muscle",
            options = facets.primeMovers,
            selected = filters.primeMovers,
            onToggle = onTogglePrimeMover,
        )
        ToggleFilterSection(
            title = "Training History",
            label = "Has logged history",
            count = facets.loggedHistoryCount,
            selected = filters.hasLoggedHistoryOnly,
            onToggle = onToggleLoggedHistory,
        )
        RecommendationBiasFacetSection(
            title = "Workout Recommendation",
            options = facets.recommendationBiases,
            selected = filters.recommendationBiases,
            onToggle = onToggleRecommendationBias,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterSheet(
    facets: LibraryFacets,
    filters: LibraryFilters,
    onDismiss: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleEquipment: (String) -> Unit,
    onToggleTargetMuscle: (String) -> Unit,
    onTogglePrimeMover: (String) -> Unit,
    onToggleRecommendationBias: (RecommendationBias) -> Unit,
    onToggleLoggedHistory: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Library filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Combine equipment, muscle, history, and recommendation filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onClearFilters,
                        enabled = filters.activeCount() > 0,
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text("Reset All Filters")
                    }
                }
            }
            item {
                FilterFacetSection(
                    title = "Equipment",
                    options = facets.equipment,
                    selected = filters.equipment,
                    onToggle = onToggleEquipment,
                )
            }
            item {
                FilterFacetSection(
                    title = "Target Muscle Group",
                    options = facets.targetMuscles,
                    selected = filters.targetMuscles,
                    onToggle = onToggleTargetMuscle,
                )
            }
            item {
                FilterFacetSection(
                    title = "Prime Mover Muscle",
                    options = facets.primeMovers,
                    selected = filters.primeMovers,
                    onToggle = onTogglePrimeMover,
                )
            }
            item {
                ToggleFilterSection(
                    title = "Training History",
                    label = "Has logged history",
                    count = facets.loggedHistoryCount,
                    selected = filters.hasLoggedHistoryOnly,
                    onToggle = onToggleLoggedHistory,
                )
            }
            item {
                RecommendationBiasFacetSection(
                    title = "Workout Recommendation",
                    options = facets.recommendationBiases,
                    selected = filters.recommendationBiases,
                    onToggle = onToggleRecommendationBias,
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToggleFilterSection(
    title: String,
    label: String,
    count: Int,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FilterChip(
            selected = selected,
            onClick = onToggle,
            enabled = selected || count > 0,
            label = { Text("$label ($count)") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterFacetSection(
    title: String,
    options: List<dev.toastlabs.toastlift.data.FilterOptionCount>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option.label in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(option.label) },
                    enabled = isSelected || option.count > 0,
                    label = { Text("${option.label} (${option.count})") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationBiasFacetSection(
    title: String,
    options: List<dev.toastlabs.toastlift.data.RecommendationBiasFilterOptionCount>,
    selected: Set<RecommendationBias>,
    onToggle: (RecommendationBias) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option.bias in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(option.bias) },
                    enabled = isSelected || option.count > 0,
                    label = { Text("${option.bias.filterLabel} (${option.count})") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionAddExerciseScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleEquipmentFilter: (String) -> Unit,
    onToggleTargetMuscleFilter: (String) -> Unit,
    onTogglePrimeMoverFilter: (String) -> Unit,
    onToggleRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleLoggedHistoryFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onConfirmAdd: (List<ExerciseSummary>) -> Unit,
    onAddCustomExercise: () -> Unit,
    onCloseCustomExercise: () -> Unit,
    onCustomExerciseDraftChange: (CustomExerciseDraft) -> Unit,
    onCustomExerciseNameChange: (String) -> Unit,
    onGenerateCustomExercise: () -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSaveCustomExercise: () -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    onShowDetail: (Long) -> Unit,
) {
    AddExercisesFlowScreen(
        state = state,
        onDismiss = onBack,
        onConfirmAdd = onConfirmAdd,
        onQueryChange = onQueryChange,
        onToggleSearch = onToggleSearch,
        onToggleFavoritesOnly = onToggleFavoritesOnly,
        onToggleEquipmentFilter = onToggleEquipmentFilter,
        onToggleTargetMuscleFilter = onToggleTargetMuscleFilter,
        onTogglePrimeMoverFilter = onTogglePrimeMoverFilter,
        onToggleRecommendationBiasFilter = onToggleRecommendationBiasFilter,
        onToggleLoggedHistoryFilter = onToggleLoggedHistoryFilter,
        onClearFilters = onClearFilters,
        onShowDetail = onShowDetail,
        onOpenCustomExercise = onAddCustomExercise,
        onCloseCustomExercise = onCloseCustomExercise,
        onCustomExerciseDraftChange = onCustomExerciseDraftChange,
        onCustomExerciseNameChange = onCustomExerciseNameChange,
        onGenerateCustomExercise = onGenerateCustomExercise,
        onUseExistingExercise = onUseExistingExercise,
        onSaveCustomExercise = onSaveCustomExercise,
        onPendingSelectionConsumed = onPendingSelectionConsumed,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSessionScreen(
    state: AppUiState,
    session: ActiveSession,
    selectedExerciseIndex: Int?,
    activeSessionAddExerciseVisible: Boolean,
    customExerciseDraft: CustomExerciseDraft?,
    onOpenExercise: (Int) -> Unit,
    onShowExerciseDetail: (Long) -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
    onCloseExercise: () -> Unit,
    onPickNextExercise: () -> Unit,
    onOpenAddExercise: () -> Unit,
    onCloseAddExercise: () -> Unit,
    onToggleAddExerciseSearch: () -> Unit,
    onAddExerciseQueryChange: (String) -> Unit,
    onToggleAddExerciseFavoritesOnly: () -> Unit,
    onToggleAddExerciseEquipmentFilter: (String) -> Unit,
    onToggleAddExerciseTargetMuscleFilter: (String) -> Unit,
    onToggleAddExercisePrimeMoverFilter: (String) -> Unit,
    onToggleAddExerciseRecommendationBiasFilter: (RecommendationBias) -> Unit,
    onToggleAddExerciseLoggedHistoryFilter: () -> Unit,
    onClearAddExerciseFilters: () -> Unit,
    onShowAddExerciseDetail: (Long) -> Unit,
    onOpenCustomExercise: () -> Unit,
    onCloseCustomExercise: () -> Unit,
    onCustomExerciseDraftChange: (CustomExerciseDraft) -> Unit,
    onCustomExerciseNameChange: (String) -> Unit,
    onGenerateCustomExercise: () -> Unit,
    onAddExercises: (List<ExerciseSummary>) -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSaveCustomExercise: () -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    onValueChange: (Int, Int, String, Boolean) -> Unit,
    onToggleComplete: (Int, Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onDeleteSet: (Int, Int) -> Unit,
    onDeleteExercise: (Int) -> Unit,
    onLogSet: (Int) -> Unit,
    onLogAllSets: (Int) -> Unit,
    onUpdateExerciseRir: (Int, Int) -> Unit,
    onFinishExercise: (Int) -> Unit,
    onCompleteSession: () -> Unit,
    onCancel: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showWorkoutDetailsSheet by remember { mutableStateOf(false) }
    var pendingExerciseDeletionIndex by remember { mutableStateOf<Int?>(null) }
    if (customExerciseDraft != null && !activeSessionAddExerciseVisible) {
        CustomExerciseEditorScreen(
            draft = customExerciseDraft,
            onBack = onCloseCustomExercise,
            onDraftChange = onCustomExerciseDraftChange,
            onNameChange = onCustomExerciseNameChange,
            onGenerate = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSave = onSaveCustomExercise,
        )
        return
    }
    if (activeSessionAddExerciseVisible) {
        ActiveSessionAddExerciseScreen(
            state = state,
            onBack = onCloseAddExercise,
            onToggleSearch = onToggleAddExerciseSearch,
            onQueryChange = onAddExerciseQueryChange,
            onToggleFavoritesOnly = onToggleAddExerciseFavoritesOnly,
            onToggleEquipmentFilter = onToggleAddExerciseEquipmentFilter,
            onToggleTargetMuscleFilter = onToggleAddExerciseTargetMuscleFilter,
            onTogglePrimeMoverFilter = onToggleAddExercisePrimeMoverFilter,
            onToggleRecommendationBiasFilter = onToggleAddExerciseRecommendationBiasFilter,
            onToggleLoggedHistoryFilter = onToggleAddExerciseLoggedHistoryFilter,
            onClearFilters = onClearAddExerciseFilters,
            onConfirmAdd = onAddExercises,
            onAddCustomExercise = onOpenCustomExercise,
            onCloseCustomExercise = onCloseCustomExercise,
            onCustomExerciseDraftChange = onCustomExerciseDraftChange,
            onCustomExerciseNameChange = onCustomExerciseNameChange,
            onGenerateCustomExercise = onGenerateCustomExercise,
            onUseExistingExercise = onUseExistingExercise,
            onSaveCustomExercise = onSaveCustomExercise,
            onPendingSelectionConsumed = onPendingSelectionConsumed,
            onShowDetail = onShowAddExerciseDetail,
        )
        return
    }
    val selectedExercise = selectedExerciseIndex?.let(session.exercises::getOrNull)
    if (selectedExercise != null) {
        SessionExerciseDetailScreen(
            exercise = selectedExercise,
            exerciseIndex = requireNotNull(selectedExerciseIndex),
            onBack = onCloseExercise,
            onShowExerciseDetail = { onShowExerciseDetail(selectedExercise.exerciseId) },
            onValueChange = onValueChange,
            onToggleComplete = onToggleComplete,
            onAddSet = onAddSet,
            onDeleteSet = onDeleteSet,
            onLogSet = onLogSet,
            onLogAllSets = onLogAllSets,
            onUpdateExerciseRir = onUpdateExerciseRir,
            onFinishExercise = onFinishExercise,
        )
        return
    }

    val elapsed by produceState(initialValue = formatElapsedTime(session.startedAtUtc), session.startedAtUtc) {
        while (true) {
            value = formatElapsedTime(session.startedAtUtc)
            delay(1000)
        }
    }
    val completedExercises = session.exercises.count { exercise -> exercise.sets.isNotEmpty() && exercise.sets.all(SessionSet::completed) }
    val totalSets = session.exercises.sumOf { it.sets.size }
    val completedSets = session.exercises.sumOf { exercise -> exercise.sets.count(SessionSet::completed) }
    val completionFraction = if (totalSets == 0) 0f else completedSets / totalSets.toFloat()
    val orderedExercises = orderedSessionExercises(session)
    val shouldShowPickNextExercise = state.profile?.devPickNextExerciseEnabled == true
    val shouldShowFruitWorkoutBadges = state.profile?.devFruitExerciseIconsEnabled == true
    val untouchedExerciseCount = session.exercises.count { exercise -> exercise.sets.none(SessionSet::completed) }
    val splitName = state.profile?.let { profile ->
        state.splitPrograms.firstOrNull { it.id == profile.splitProgramId }?.name
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Button(
                onClick = onCompleteSession,
                enabled = canFinishActiveSession(session.exercises.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Finish Workout")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionMomentumHeader(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = ACTIVE_SESSION_HEADER_TOP_PADDING),
                elapsed = elapsed,
                completedExercises = completedExercises,
                totalExercises = session.exercises.size,
                completedSets = completedSets,
                totalSets = totalSets,
                completionFraction = completionFraction,
                onExitWorkout = { showDiscardDialog = true },
                onOpenWorkoutDetails = { showWorkoutDetailsSheet = true },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = orderedExercises,
                    key = { orderedExercise -> "${orderedExercise.index}:${orderedExercise.value.exerciseId}" },
                ) { orderedExercise ->
                    val exerciseIndex = orderedExercise.index
                    val exercise = orderedExercise.value
                    SessionExerciseRow(
                        exercise = exercise,
                        fruitIconsEnabled = shouldShowFruitWorkoutBadges,
                        recommendationBias = state.recommendationBiasByExerciseId[exercise.exerciseId] ?: RecommendationBias.Neutral,
                        onOpen = { onOpenExercise(exerciseIndex) },
                        onShowDetail = { onShowExerciseDetail(exercise.exerciseId) },
                        onOpenExerciseHistory = { onOpenExerciseHistory(exercise.exerciseId, exercise.name) },
                        onOpenExerciseVideos = { onOpenExerciseVideos(exercise.exerciseId, exercise.name) },
                        onDelete = { pendingExerciseDeletionIndex = exerciseIndex },
                    )
                }
                item {
                    AddExerciseCallToAction(onAddExercise = onOpenAddExercise)
                }
                if (shouldShowPickNextExercise) {
                    item {
                        PickNextExerciseCallToAction(
                            untouchedExerciseCount = untouchedExerciseCount,
                            onPickNextExercise = onPickNextExercise,
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmActionDialog(
            title = "Abandon workout?",
            message = "Leaving now can lose progress. The latest abandoned workout will be saved under Today so you can restore it later.",
            confirmLabel = "Abandon workout",
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                onCancel()
            },
        )
    }

    if (showWorkoutDetailsSheet) {
        ActiveWorkoutDetailsSheet(
            session = session,
            splitName = splitName,
            elapsed = elapsed,
            onDismiss = { showWorkoutDetailsSheet = false },
        )
    }

    pendingExerciseDeletionIndex?.let { exerciseIndex ->
        session.exercises.getOrNull(exerciseIndex)?.let { exercise ->
            val exerciseCompletedSets = exercise.sets.count { it.completed }
            val deleteMessage = if (exerciseCompletedSets > 0) {
                "This removes ${exercise.name} and its $exerciseCompletedSets logged set${if (exerciseCompletedSets == 1) "" else "s"} from the workout."
            } else {
                "This removes ${exercise.name} from the workout."
            }
            ConfirmActionDialog(
                title = "Delete exercise?",
                message = deleteMessage,
                confirmLabel = "Delete exercise",
                onDismiss = { pendingExerciseDeletionIndex = null },
                onConfirm = {
                    pendingExerciseDeletionIndex = null
                    onDeleteExercise(exerciseIndex)
                },
            )
        }
    }
}

@Composable
private fun PickNextExerciseCallToAction(
    untouchedExerciseCount: Int,
    onPickNextExercise: () -> Unit,
) {
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Need a starting point?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (untouchedExerciseCount > 0) {
                    "$untouchedExerciseCount untouched exercise${if (untouchedExerciseCount == 1) "" else "s"} left. Pick one at random and jump straight into logging."
                } else {
                    "Every exercise has already been started. Pick one from the list to continue logging."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPickNextExercise,
                enabled = untouchedExerciseCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick Next Exercise")
            }
        }
    }
}

@Composable
private fun AddExerciseCallToAction(onAddExercise: () -> Unit) {
    FeatureCard(
        modifier = Modifier.clickable(onClick = onAddExercise),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("+ Add Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Browse the existing library first, then create a custom exercise only if you need one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomExerciseEditorScreen(
    draft: CustomExerciseDraft,
    onBack: () -> Unit,
    onDraftChange: (CustomExerciseDraft) -> Unit,
    onNameChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onUseExistingExercise: (ExerciseSummary) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Back to workout")
                }
                Text("Custom Exercise", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onGenerate,
                    enabled = draft.name.isNotBlank() && !draft.isGenerating && !draft.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (draft.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (draft.generatedWithAi) "Regenerate" else "Generate")
                }
                Button(
                    onClick = onSave,
                    enabled = !draft.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (draft.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Exercise name") },
                        placeholder = { Text("Hammerstrength Incline Chest Press") },
                    )
                    Text(
                        "Start with the exercise name, then use AI to draft the rest. You can still edit everything before saving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (draft.errorMessage != null) {
                FeatureCard(containerColor = emberAccent.glow.copy(alpha = 0.22f)) {
                    Text(
                        draft.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (draft.existingMatches.isNotEmpty()) {
                FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Close Matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Use an existing exercise if one already describes what you’re about to add.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        draft.existingMatches.forEach { exercise ->
                            ExistingExerciseMatchCard(
                                exercise = exercise,
                                onUseExisting = { onUseExistingExercise(exercise) },
                            )
                        }
                    }
                }
            }

            CustomExerciseSection(title = "Basics") {
                DraftField(
                    label = "Difficulty",
                    value = draft.difficultyLevel,
                    onValueChange = { onDraftChange(draft.copy(difficultyLevel = it)) },
                    supporting = optionHint("Options", draft.taxonomy.difficultyLevels),
                )
                DraftField(
                    label = "Body Region",
                    value = draft.bodyRegion,
                    onValueChange = { onDraftChange(draft.copy(bodyRegion = it)) },
                    supporting = optionHint("Options", draft.taxonomy.bodyRegions),
                )
                DraftField(
                    label = "Target Muscle Group",
                    value = draft.targetMuscleGroup,
                    onValueChange = { onDraftChange(draft.copy(targetMuscleGroup = it)) },
                    supporting = optionHint("Options", draft.taxonomy.targetMuscles),
                )
                DraftField(
                    label = "Prime Mover Muscle",
                    value = draft.primeMoverMuscle,
                    onValueChange = { onDraftChange(draft.copy(primeMoverMuscle = it)) },
                    supporting = optionHint("Existing", draft.taxonomy.primeMovers),
                )
                DraftField(label = "Secondary Muscle", value = draft.secondaryMuscle, onValueChange = { onDraftChange(draft.copy(secondaryMuscle = it)) })
                DraftField(label = "Tertiary Muscle", value = draft.tertiaryMuscle, onValueChange = { onDraftChange(draft.copy(tertiaryMuscle = it)) })
            }

            CustomExerciseSection(title = "Equipment") {
                DraftField(
                    label = "Primary Equipment",
                    value = draft.primaryEquipment,
                    onValueChange = { onDraftChange(draft.copy(primaryEquipment = it)) },
                    supporting = optionHint("Options", draft.taxonomy.equipmentOptions),
                )
                DraftField(
                    label = "Primary Item Count",
                    value = draft.primaryItemCount,
                    onValueChange = { onDraftChange(draft.copy(primaryItemCount = it)) },
                    keyboardType = KeyboardType.Number,
                )
                DraftField(
                    label = "Secondary Equipment",
                    value = draft.secondaryEquipment,
                    onValueChange = { onDraftChange(draft.copy(secondaryEquipment = it)) },
                    supporting = optionHint("Optional", draft.taxonomy.equipmentOptions),
                )
                DraftField(
                    label = "Secondary Item Count",
                    value = draft.secondaryItemCount,
                    onValueChange = { onDraftChange(draft.copy(secondaryItemCount = it)) },
                    keyboardType = KeyboardType.Number,
                )
            }

            CustomExerciseSection(title = "Movement") {
                DraftField(label = "Mechanics", value = draft.mechanics, onValueChange = { onDraftChange(draft.copy(mechanics = it)) }, supporting = optionHint("Options", draft.taxonomy.mechanicsOptions))
                DraftField(label = "Laterality", value = draft.laterality, onValueChange = { onDraftChange(draft.copy(laterality = it)) }, supporting = optionHint("Options", draft.taxonomy.lateralityOptions))
                DraftField(label = "Posture", value = draft.posture, onValueChange = { onDraftChange(draft.copy(posture = it)) }, supporting = optionHint("Examples", draft.taxonomy.postures))
                DraftField(label = "Force Type", value = draft.forceType, onValueChange = { onDraftChange(draft.copy(forceType = it)) }, supporting = optionHint("Options", draft.taxonomy.forceTypeOptions))
                DraftField(label = "Classification", value = draft.classification, onValueChange = { onDraftChange(draft.copy(classification = it)) }, supporting = optionHint("Options", draft.taxonomy.classificationOptions))
                DraftField(label = "Movement Patterns", value = draft.movementPatternsInput, onValueChange = { onDraftChange(draft.copy(movementPatternsInput = it)) }, supporting = optionHint("Comma separated", draft.taxonomy.movementPatternOptions))
                DraftField(label = "Planes Of Motion", value = draft.planesOfMotionInput, onValueChange = { onDraftChange(draft.copy(planesOfMotionInput = it)) }, supporting = optionHint("Comma separated", draft.taxonomy.planeOfMotionOptions))
            }

            CustomExerciseSection(title = "Technical") {
                DraftField(label = "Arm Usage", value = draft.armUsage, onValueChange = { onDraftChange(draft.copy(armUsage = it)) }, supporting = optionHint("Options", draft.taxonomy.armUsageOptions))
                DraftField(label = "Arm Pattern", value = draft.armPattern, onValueChange = { onDraftChange(draft.copy(armPattern = it)) }, supporting = optionHint("Options", draft.taxonomy.armPatternOptions))
                DraftField(label = "Grip", value = draft.grip, onValueChange = { onDraftChange(draft.copy(grip = it)) }, supporting = optionHint("Examples", draft.taxonomy.gripOptions))
                DraftField(label = "Load Position Ending", value = draft.loadPositionEnding, onValueChange = { onDraftChange(draft.copy(loadPositionEnding = it)) }, supporting = optionHint("Examples", draft.taxonomy.loadPositionOptions))
                DraftField(label = "Leg Pattern", value = draft.legPattern, onValueChange = { onDraftChange(draft.copy(legPattern = it)) }, supporting = optionHint("Options", draft.taxonomy.legPatternOptions))
                DraftField(label = "Foot Elevation", value = draft.footElevation, onValueChange = { onDraftChange(draft.copy(footElevation = it)) }, supporting = optionHint("Examples", draft.taxonomy.footElevationOptions))
                DraftField(label = "Combination Type", value = draft.combinationType, onValueChange = { onDraftChange(draft.copy(combinationType = it)) }, supporting = optionHint("Options", draft.taxonomy.combinationTypeOptions))
            }

            CustomExerciseSection(title = "Links And Aliases") {
                DraftField(label = "Short Demo Label", value = draft.shortDemoLabel, onValueChange = { onDraftChange(draft.copy(shortDemoLabel = it)) })
                DraftField(label = "Short Demo URL", value = draft.shortDemoUrl, onValueChange = { onDraftChange(draft.copy(shortDemoUrl = it)) }, keyboardType = KeyboardType.Uri)
                DraftField(label = "In-Depth Label", value = draft.inDepthLabel, onValueChange = { onDraftChange(draft.copy(inDepthLabel = it)) })
                DraftField(label = "In-Depth URL", value = draft.inDepthUrl, onValueChange = { onDraftChange(draft.copy(inDepthUrl = it)) }, keyboardType = KeyboardType.Uri)
                DraftField(label = "Synonyms", value = draft.synonymsInput, onValueChange = { onDraftChange(draft.copy(synonymsInput = it)) }, supporting = "Comma separated aliases")
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ExistingExerciseMatchCard(
    exercise: ExerciseSummary,
    onUseExisting: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, fontWeight = FontWeight.Bold)
                Text(
                    "${exercise.targetMuscleGroup} • ${exercise.equipment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onUseExisting) {
                Text("Use")
            }
        }
    }
}

@Composable
private fun CustomExerciseSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            },
        )
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        supportingText = supporting?.let { { Text(it) } },
        singleLine = keyboardType != KeyboardType.Text,
    )
}

private fun optionHint(label: String, options: List<String>, limit: Int = 6): String {
    if (options.isEmpty()) return ""
    val preview = options.take(limit).joinToString(", ")
    val suffix = if (options.size > limit) ", …" else ""
    return "$label: $preview$suffix"
}

@Composable
private fun SessionMomentumHeader(
    modifier: Modifier = Modifier,
    elapsed: String,
    completedExercises: Int,
    totalExercises: Int,
    completedSets: Int,
    totalSets: Int,
    completionFraction: Float,
    onExitWorkout: () -> Unit,
    onOpenWorkoutDetails: () -> Unit,
) {
    val accent = if (completionFraction >= 0.66f) goldAccent else if (completionFraction >= 0.3f) surgeAccent else emberAccent
    FeatureCard(
        modifier = modifier
            .semantics {
                contentDescription = "Running workout timer"
                role = Role.Button
            }
            .clickable(onClick = onOpenWorkoutDetails),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.glow.copy(alpha = 0.32f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            IconButton(
                onClick = onExitWorkout,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            ) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Exit workout")
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniTag("$completedExercises/$totalExercises exercises", accent = accent.start.copy(alpha = 0.2f))
                    MiniTag("$completedSets/$totalSets sets", accent = accent.end.copy(alpha = 0.2f))
                }
                ProgressPill(
                    current = completedSets.coerceAtMost(totalSets),
                    target = totalSets.coerceAtLeast(1),
                    label = if (completionFraction >= 0.66f) "Momentum" else "Workout flow",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActiveWorkoutDetailsSheet(
    session: ActiveSession,
    splitName: String?,
    elapsed: String,
    onDismiss: () -> Unit,
) {
    val expectedVolume = activeSessionExpectedLoadVolume(session)
    val targetMuscles = session.exercises
        .map(SessionExercise::targetMuscleGroup)
        .filter { it.isNotBlank() }
        .distinct()
    val bodyRegions = session.exercises
        .map(SessionExercise::bodyRegion)
        .filter { it.isNotBlank() }
        .distinct()
    val focusLabel = session.focusKey?.let { generatedWorkoutFocusDisplayName(it) }
    val splitDay = focusLabel?.let { label ->
        listOfNotNull(splitName, label).joinToString(" • ")
    }
    val startedLabel = runCatching {
        Instant.parse(session.startedAtUtc)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }.getOrElse {
        session.startedAtUtc.replace("T", " ").removeSuffix("Z")
    }
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Workout details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(session.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (session.subtitle.isNotBlank()) {
                Text(session.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            StatRail(
                items = listOf(
                    Triple("Elapsed", elapsed, "live"),
                    Triple("Plan", (session.estimatedMinutes ?: activeSessionFallbackEstimatedMinutes(session)).toString(), "target min"),
                    Triple("Exercises", session.exercises.size.toString(), "in session"),
                ),
            )

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Plan snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ActiveWorkoutDetailRow(label = "Workout name", value = session.title)
                    splitDay?.let { ActiveWorkoutDetailRow(label = "Split day", value = it) }
                    ActiveWorkoutDetailRow(label = "Expected reps", value = activeSessionExpectedRepSummary(session))
                    ActiveWorkoutDetailRow(
                        label = "Expected volume",
                        value = expectedVolume?.let(::formatVolume) ?: "Not enough weight targets yet",
                    )
                    ActiveWorkoutDetailRow(label = "Expected intensity", value = activeSessionIntensityLabel(session))
                    session.sessionFormat?.takeIf { it.isNotBlank() }?.let {
                        ActiveWorkoutDetailRow(label = "Session format", value = it)
                    }
                    ActiveWorkoutDetailRow(label = "Started", value = startedLabel)
                    ActiveWorkoutDetailRow(
                        label = "Workout source",
                        value = session.origin.replaceFirstChar { it.uppercase() },
                    )
                }
            }

            if (targetMuscles.isNotEmpty() || bodyRegions.isNotEmpty()) {
                FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (targetMuscles.isNotEmpty()) {
                            Text(
                                "Primary targets",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                targetMuscles.take(8).forEach { muscle -> MiniTag(muscle) }
                            }
                        }
                        if (bodyRegions.isNotEmpty()) {
                            Text(
                                "Body regions",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                bodyRegions.forEach { region -> MiniTag(region) }
                            }
                        }
                    }
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActiveWorkoutDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

internal fun activeSessionExpectedRepSummary(session: ActiveSession): String {
    val totals = session.exercises
        .flatMap(SessionExercise::sets)
        .mapNotNull { set -> parseSessionRepRange(set.targetReps) }
        .fold(0 to 0) { acc, range -> (acc.first + range.first) to (acc.second + range.second) }
    if (totals.first > 0 && totals.second > 0) {
        return if (totals.first == totals.second) {
            "${totals.first} total reps"
        } else {
            "${totals.first}-${totals.second} total reps"
        }
    }

    val distinctRanges = session.exercises
        .flatMap(SessionExercise::sets)
        .map(SessionSet::targetReps)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    return distinctRanges.joinToString(", ").ifBlank { "Targets not set" }
}

internal fun activeSessionExpectedLoadVolume(session: ActiveSession): Double? {
    val volume = session.exercises
        .flatMap(SessionExercise::sets)
        .sumOf { set ->
            val weight = set.recommendedWeight.trim().toDoubleOrNull() ?: return@sumOf 0.0
            val reps = set.recommendedReps ?: parseSessionRepRange(set.targetReps)?.let { (min, max) ->
                if (min == max) min else (min + max) / 2
            } ?: return@sumOf 0.0
            weight * reps
        }
    return volume.takeIf { it > 0.0 }
}

internal fun activeSessionIntensityLabel(session: ActiveSession): String {
    return when (intensityPrescriptionIntentForFocusKey(session.focusKey)) {
        dev.toastlabs.toastlift.data.IntensityPrescriptionIntent.HEAVY -> "Heavy"
        dev.toastlabs.toastlift.data.IntensityPrescriptionIntent.HIGH_REPS -> "High reps"
        dev.toastlabs.toastlift.data.IntensityPrescriptionIntent.STANDARD -> {
            val repMids = session.exercises
                .flatMap(SessionExercise::sets)
                .mapNotNull { set -> parseSessionRepRange(set.targetReps) }
                .map { (min, max) -> (min + max) / 2.0 }
            val averageMidpoint = if (repMids.isEmpty()) null else repMids.average()
            when {
                averageMidpoint == null -> "Standard"
                averageMidpoint <= 6.5 -> "Heavy"
                averageMidpoint >= 12.0 -> "High reps"
                else -> "Standard"
            }
        }
    }
}

internal fun activeSessionFallbackEstimatedMinutes(session: ActiveSession): Int {
    return (session.exercises.sumOf { it.sets.size } * 2).coerceAtLeast(5)
}

private fun parseSessionRepRange(targetReps: String): Pair<Int, Int>? {
    val digits = targetReps
        .split("-", "to", "–")
        .mapNotNull { token -> token.trim().toIntOrNull() }
    return when (digits.size) {
        0 -> null
        1 -> digits.first() to digits.first()
        else -> digits.first() to digits.last()
    }
}

@Composable
private fun SessionExerciseRow(
    exercise: SessionExercise,
    fruitIconsEnabled: Boolean,
    recommendationBias: RecommendationBias,
    onOpen: () -> Unit,
    onShowDetail: () -> Unit,
    onOpenExerciseHistory: () -> Unit,
    onOpenExerciseVideos: () -> Unit,
    onDelete: () -> Unit,
) {
    val completedSets = exercise.sets.count { it.completed }
    val totalSets = exercise.sets.size
    val progressFraction = if (totalSets > 0) completedSets / totalSets.toFloat() else 0f
    val badgeLabel = sessionExerciseBadgeLabel(
        exercise = exercise,
        fruitIconsEnabled = fruitIconsEnabled,
    )
    val badgeAccentKey = sessionExerciseBadgeAccentKey(
        exercise = exercise,
        fruitIconsEnabled = fruitIconsEnabled,
    )
    val summaryLine = if (completedSets > 0) {
        buildString {
            append("$completedSets/$totalSets Sets Logged")
            if (completedSets == totalSets) {
                exercise.lastSetRepsInReserve?.let {
                    append(" • RIR ")
                    append(formatRirLabel(it))
                }
            }
        }
    } else {
        "$totalSets Sets • ${exercise.sets.firstOrNull()?.targetReps ?: "--"} Reps" +
            exercise.sets.firstOrNull()?.displayedWeight()?.takeIf { it.isNotBlank() }?.let { " • $it lb" }.orEmpty()
    }
    val summaryColor = when {
        progressFraction >= 1f -> goldAccent.start
        completedSets > 0 -> surgeAccent.start
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    FeatureCard(
        modifier = Modifier.clickable(onClick = onOpen),
        containerColor = if (progressFraction > 0f) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionExerciseProgressBadge(
                label = badgeLabel,
                progressFraction = progressFraction,
                accent = accentForKey(badgeAccentKey),
                onClick = onShowDetail,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    summaryLine,
                    color = summaryColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (completedSets > 0) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
            ExerciseHistoryOverflow(
                recommendationBias = recommendationBias,
                onShowDetail = onShowDetail,
                onRemove = onDelete,
                removeLabel = "Delete exercise",
                onOpenExerciseHistory = onOpenExerciseHistory,
                onOpenExerciseVideos = onOpenExerciseVideos,
            )
        }
    }
}

@Composable
private fun SessionExerciseProgressBadge(
    label: String,
    progressFraction: Float,
    accent: GlowAccent,
    onClick: () -> Unit,
) {
    val clampedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sessionExerciseProgress",
    )
    val dottedRingColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    Box(
        modifier = Modifier
            .size(50.dp)
            .semantics { contentDescription = "Show exercise details" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LeadingBadge(
            label = label,
            accent = accent,
            textColor = if (clampedProgress > 0f) accent.textOnAccent else MaterialTheme.colorScheme.onSurface,
        )
        if (clampedProgress > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 4.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = size.minDimension - strokeWidth
                if (clampedProgress < 1f) {
                    drawArc(
                        color = dottedRingColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(strokeWidth * 0.8f, strokeWidth * 1.6f),
                            ),
                        ),
                    )
                }
                drawArc(
                    color = accent.start,
                    startAngle = -90f,
                    sweepAngle = 360f * clampedProgress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailBadge(
    label: String,
    accent: GlowAccent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .semantics { contentDescription = "Show exercise details" }
            .clickable(onClick = onClick),
    ) {
        LeadingBadge(label = label, accent = accent)
    }
}

@Composable
private fun ExerciseEffortPromptCard(
    selectedRepsInReserve: Int?,
    onSelect: (Int) -> Unit,
) {
    val choices = listOf(4, 3, 2, 1, 0)
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Rate Your Last Set", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "How many more reps could you do?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { value ->
                    val selected = selectedRepsInReserve == value
                    val containerColor = when (value) {
                        4, 3 -> Color(0xFFFFF1A6)
                        2 -> Color(0xFFFF4A6A)
                        1 -> Color(0xFFE61E53)
                        else -> Color(0xFFB70F38)
                    }
                    Surface(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) containerColor.copy(alpha = 0.95f) else containerColor.copy(alpha = 0.82f),
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                formatRirLabel(value),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = if (value >= 3) Color(0xFF29221A) else Color.White,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Too Easy", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text("Max Effort", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionExerciseDetailScreen(
    exercise: SessionExercise,
    exerciseIndex: Int,
    onBack: () -> Unit,
    onShowExerciseDetail: () -> Unit,
    onValueChange: (Int, Int, String, Boolean) -> Unit,
    onToggleComplete: (Int, Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onDeleteSet: (Int, Int) -> Unit,
    onLogSet: (Int) -> Unit,
    onLogAllSets: (Int) -> Unit,
    onUpdateExerciseRir: (Int, Int) -> Unit,
    onFinishExercise: (Int) -> Unit,
) {
    val allSetsCompleted = exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
    val currentSetNumbers = exercise.sets.associate { it.id to it.setNumber }
    val currentSetNumberSnapshot = exercise.sets.map { it.id to it.setNumber }
    var animatedSetNumbers by remember(exercise.exerciseId) { mutableStateOf(currentSetNumbers) }
    var committedSetNumbers by remember(exercise.exerciseId) { mutableStateOf(currentSetNumbers) }
    LaunchedEffect(exercise.exerciseId, currentSetNumberSnapshot) {
        val sameSetIds = committedSetNumbers.keys == currentSetNumbers.keys
        val numbersChanged = committedSetNumbers != currentSetNumbers
        if (sameSetIds && numbersChanged) {
            animatedSetNumbers = committedSetNumbers
            delay(SESSION_SET_RENUMBER_DELAY_MS)
        }
        animatedSetNumbers = currentSetNumbers
        committedSetNumbers = currentSetNumbers
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (allSetsCompleted) {
                Button(
                    onClick = { onFinishExercise(exerciseIndex) },
                    enabled = exercise.lastSetRepsInReserve != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Done")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { onLogAllSets(exerciseIndex) }, modifier = Modifier.weight(1f)) {
                        Text("Log All Sets")
                    }
                    Button(onClick = { onLogSet(exerciseIndex) }, modifier = Modifier.weight(1f)) {
                        Text("Log Set")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            ) {
                IconButton(
                    onClick = {
                        if (allSetsCompleted) {
                            onFinishExercise(exerciseIndex)
                        } else {
                            onBack()
                        }
                    },
                    enabled = !allSetsCompleted || exercise.lastSetRepsInReserve != null,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Back to workout")
                }
                OutlinedButton(
                    onClick = onShowExerciseDetail,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Details")
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniTag("${exercise.restSeconds}s rest")
                        MiniTag(exercise.targetMuscleGroup)
                        MiniTag(exercise.equipment)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = exercise.sets,
                    key = { _, set -> set.id },
                ) { setIndex: Int, set: SessionSet ->
                    DismissibleSessionSetRow(
                        modifier = Modifier.animateItemPlacement(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                        set = set,
                        displaySetNumber = animatedSetNumbers[set.id] ?: set.setNumber,
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        onValueChange = onValueChange,
                        onToggleComplete = onToggleComplete,
                        onDeleteSet = onDeleteSet,
                    )
                }
                item {
                    Text(
                        text = "Add Set",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onAddSet(exerciseIndex) }.padding(top = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (allSetsCompleted) {
                    item {
                        ExerciseEffortPromptCard(
                            selectedRepsInReserve = exercise.lastSetRepsInReserve,
                            onSelect = { onUpdateExerciseRir(exerciseIndex, it) },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}

@Composable
private fun DismissibleSessionSetRow(
    modifier: Modifier = Modifier,
    set: SessionSet,
    displaySetNumber: Int,
    exerciseIndex: Int,
    setIndex: Int,
    onValueChange: (Int, Int, String, Boolean) -> Unit,
    onToggleComplete: (Int, Int) -> Unit,
    onDeleteSet: (Int, Int) -> Unit,
) {
    val actionWidth = 64.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val focusManager = LocalFocusManager.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rowAccent = if (set.completed) surgeAccent else emberAccent
    val completedRowColor = MaterialTheme.colorScheme.secondaryContainer
    val pendingRowColor = MaterialTheme.colorScheme.surfaceVariant
    val completedTextColor = if (set.completed) {
        if (isDarkTheme) surgeAccent.start else MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    val rowScale = remember(set.id) { Animatable(1f) }
    val completionBurst = remember(set.id) { Animatable(0f) }
    var previousCompleted by remember(set.id) { mutableStateOf(set.completed) }
    val rowColor by animateColorAsState(
        targetValue = if (set.completed) {
            completedRowColor
        } else {
            pendingRowColor
        },
        label = "setRowColor",
    )
    LaunchedEffect(set.id) {
        horizontalOffset = 0f
    }
    LaunchedEffect(set.completed) {
        if (set.completed && !previousCompleted) {
            coroutineScope {
                launch {
                    completionBurst.snapTo(0f)
                    completionBurst.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    rowScale.snapTo(0.97f)
                    rowScale.animateTo(
                        targetValue = 1.02f,
                        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                    )
                    rowScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        } else if (!set.completed) {
            completionBurst.snapTo(0f)
            rowScale.snapTo(1f)
        }
        previousCompleted = set.completed
    }
    val settledOffset by animateFloatAsState(
        targetValue = horizontalOffset,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "setRowSwipeOffset",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                onClick = { onDeleteSet(exerciseIndex, setIndex) },
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete set",
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(settledOffset.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = rowScale.value
                    scaleY = rowScale.value
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        horizontalOffset = (horizontalOffset + delta).coerceIn(-actionWidthPx, 0f)
                    },
                    onDragStopped = {
                        horizontalOffset = if (horizontalOffset <= -actionWidthPx * 0.45f) {
                            -actionWidthPx
                        } else {
                            0f
                        }
                    },
                ),
            shape = RoundedCornerShape(18.dp),
            color = rowColor,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val highlightAlpha = ((1f - completionBurst.value) * 0.34f).coerceIn(0f, 0.34f)
                if (highlightAlpha > 0f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val sweepCenter = size.width * (0.14f + (completionBurst.value * 1.05f))
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    rowAccent.glow.copy(alpha = highlightAlpha),
                                    Color.Transparent,
                                ),
                                startX = sweepCenter - (size.width * 0.22f),
                                endX = sweepCenter + (size.width * 0.22f),
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeadingBadge(
                        label = displaySetNumber.toString(),
                        accent = rowAccent,
                        textColor = if (set.completed) rowAccent.textOnAccent else Color.Unspecified,
                    )
                    PersistentLabelOutlinedTextField(
                        value = set.displayedReps(),
                        onValueChange = { value -> onValueChange(exerciseIndex, setIndex, value, false) },
                        modifier = Modifier.weight(1f),
                        label = "Reps",
                        textColor = completedTextColor,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    PersistentLabelOutlinedTextField(
                        value = set.displayedWeight(),
                        onValueChange = { value -> onValueChange(exerciseIndex, setIndex, value, true) },
                        modifier = Modifier.weight(1f),
                        label = "Weight (lb)",
                        textColor = completedTextColor,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    SessionSetCompletionAction(
                        completed = set.completed,
                        accent = rowAccent,
                        burstProgress = completionBurst.value,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            onToggleComplete(exerciseIndex, setIndex)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSetCompletionAction(
    completed: Boolean,
    accent: GlowAccent,
    burstProgress: Float,
    onClick: () -> Unit,
) {
    val isDarkTheme = LocalToastLiftIsDarkTheme.current
    val completionProgress by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sessionSetCompletionProgress",
    )
    val idleStart = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.94f else 0.98f)
    val idleEnd = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.74f else 0.9f)
    val activeStart = accent.start.copy(alpha = if (isDarkTheme) 0.34f else 0.22f)
    val activeEnd = accent.end.copy(alpha = if (isDarkTheme) 0.24f else 0.16f)
    val borderColor = lerp(
        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        accent.start.copy(alpha = 0.55f),
        completionProgress,
    )
    val iconBackground = lerp(
        MaterialTheme.colorScheme.surfaceVariant,
        accent.start,
        completionProgress,
    )
    val iconTint = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        accent.textOnAccent,
        completionProgress,
    )
    val labelColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        if (isDarkTheme) accent.start else MaterialTheme.colorScheme.onSecondaryContainer,
        completionProgress,
    )
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .width(96.dp)
            .height(54.dp)
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        lerp(idleStart, activeStart, completionProgress),
                        lerp(idleEnd, activeEnd, completionProgress),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (completed) "Undo completed set" else "Mark set done"
                stateDescription = if (completed) "Completed" else "Pending"
                role = Role.Button
            },
    ) {
        val shimmerAlpha = ((1f - burstProgress) * 0.42f).coerceIn(0f, 0.42f)
        if (shimmerAlpha > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val sweepCenter = size.width * (0.18f + (burstProgress * 0.96f))
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = shimmerAlpha),
                            Color.Transparent,
                        ),
                        startX = sweepCenter - (size.width * 0.28f),
                        endX = sweepCenter + (size.width * 0.12f),
                    ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val pulse = (1f - burstProgress).coerceIn(0f, 1f)
                        scaleX = 0.92f + (completionProgress * 0.08f) + (pulse * 0.12f)
                        scaleY = 0.92f + (completionProgress * 0.08f) + (pulse * 0.12f)
                    }
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val ringAlpha = ((1f - burstProgress) * 0.5f).coerceIn(0f, 0.5f)
                    if (ringAlpha > 0f) {
                        drawCircle(
                            color = accent.end.copy(alpha = ringAlpha),
                            radius = size.minDimension * (0.42f + (burstProgress * 0.38f)),
                            style = Stroke(width = size.minDimension * 0.1f),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = labelColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersistentLabelOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    var draftValue by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    val fieldValue = if (isFocused) draftValue else value

    LaunchedEffect(value, isFocused) {
        if (!isFocused && draftValue != value) {
            draftValue = value
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = {
            draftValue = it
            onValueChange(it)
        },
        modifier = modifier.onFocusChanged { focusState ->
            val nowFocused = focusState.isFocused
            if (nowFocused && !isFocused) {
                draftValue = value
            }
            if (!nowFocused && isFocused) {
                draftValue = value
            }
            isFocused = nowFocused
        },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
        ),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = fieldValue.ifEmpty { " " },
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = false,
                label = { Text(label, color = textColor) },
                placeholder = null,
                leadingIcon = null,
                trailingIcon = null,
                prefix = null,
                suffix = null,
                supportingText = null,
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                    )
                },
            )
        },
    )
}

private fun formatElapsedTime(startedAtUtc: String): String {
    val elapsed = runCatching {
        Duration.between(Instant.parse(startedAtUtc), Instant.now()).seconds
    }.getOrDefault(0L).coerceAtLeast(0L)
    val hours = elapsed / 3600
    val minutes = (elapsed % 3600) / 60
    val seconds = elapsed % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d:%02d", 0, minutes, seconds)
    }
}

private fun formatMinutes(durationSeconds: Int): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatVolume(volume: Double): String {
    val whole = volume.toLong()
    return if (whole >= 1000L) "${formatCompactNumber(volume)} lb" else "$whole lb"
}

private fun formatCompactNumber(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fk", value / 1_000.0)
        else -> value.toLong().toString()
    }
}

private fun formatEntryDate(completedAtUtc: String): String =
    runCatching {
        Instant.parse(completedAtUtc)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrElse { completedAtUtc.replace("T", " ").removeSuffix("Z") }

private fun formatRirLabel(repsInReserve: Int): String = if (repsInReserve >= 4) "4+" else repsInReserve.toString()

private fun formatRpeLabel(rpe: Double): String {
    val rounded = kotlin.math.round(rpe * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun swapOptionsForSplit(splitName: String): List<Pair<String, String>> {
    return when (splitName) {
        "Upper/Lower" -> listOf(
            "upper_body" to "Upper Body Day 1",
            "lower_body" to "Lower Body Day 1",
            "upper_body_2" to "Upper Body Day 2",
            "lower_body_2" to "Lower Body Day 2",
            "full_body" to "Full Body Day",
        )
        "Push Pull Legs" -> listOf(
            "push_day" to "Push Day",
            "pull_day" to "Pull Day",
            "legs_day" to "Leg Day",
        )
        "Body Part Split" -> listOf(
            "chest_day" to "Chest Day",
            "back_day" to "Back Day",
            "shoulders_arms_day" to "Shoulders + Arms Day",
            "lower_body" to "Lower Body Day",
        )
        FORMULA_A_SPLIT_PROGRAM_NAME -> listOf(
            FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY to "UPPER-PUSH-S (chest/delts/tri HEAVY)",
            FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY to "LOWER-H (legs/abs HIGH REPS)",
            FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY to "UPPER-PULL-S (back/biceps HEAVY)",
            FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY to "UPPER-PUSH-H (chest/delts/tri HIGH REPS)",
            FORMULA_A_LOWER_STRENGTH_FOCUS_KEY to "LOWER-S (legs/abs HEAVY)",
            FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY to "UPPER-PULL-H (back/biceps HIGH REPS)",
        )
        FORMULA_B_SPLIT_PROGRAM_NAME -> listOf(
            FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY to "GLUTES+HAMSTRINGS-S (HEAVY)",
            FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY to "UPPER CHEST-H (HIGH REPS)",
            FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY to "REAR DELTS + SIDE DELTS-S (HEAVY)",
            FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY to "GLUTES+HAMSTRINGS-H (HIGH REPS)",
            FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY to "UPPER CHEST-S (HEAVY)",
            FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY to "REAR DELTS + SIDE DELTS-H (HIGH REPS)",
        )
        else -> listOf("full_body" to "Full Body Day")
    }
}

private fun openPreferredExternalLink(
    context: android.content.Context,
    appUri: String,
    appPackage: String,
    webUrl: String,
) {
    val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri))
        .setPackage(appPackage)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(nativeIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(webIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailSheet(
    detail: ExerciseDetail,
    onDismiss: () -> Unit,
    recommendationBias: RecommendationBias,
    onSetRecommendationBias: (RecommendationBias) -> Unit,
    onResetRecommendationPreferenceScore: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenExerciseHistory: () -> Unit,
    onSaveExerciseNote: (String) -> Unit,
    onAddToBuilder: () -> Unit,
    onAddToMyPlan: (() -> Unit)?,
) {
    val context = LocalContext.current
    var noteDraft by rememberSaveable(detail.summary.id, detail.notes) { mutableStateOf(detail.notes.orEmpty()) }
    val persistedNote = detail.notes.orEmpty()
    val hasUnsavedNoteChanges = noteDraft != persistedNote
    val encodedQuery = remember(detail.summary.name) {
        java.net.URLEncoder.encode("${detail.summary.name.trim()} exercise tutorial", Charsets.UTF_8.name())
    }
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LeadingBadge(
                        label = equipmentBadgeLabel(detail.summary.equipment),
                        accent = accentForKey(detail.summary.equipment),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(detail.summary.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${detail.summary.bodyRegion} • ${detail.summary.targetMuscleGroup} • ${detail.summary.equipment}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (detail.summary.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (detail.summary.favorite) "Remove favorite" else "Add favorite",
                            tint = if (detail.summary.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenExerciseHistory) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Open exercise history",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTag(detail.summary.difficulty)
                MiniTag(detail.classification)
                MiniTag(detail.summary.mechanics ?: "Open chain")
                MiniTag(detail.posture)
                MiniTag(detail.laterality)
                detail.primeMover?.let { MiniTag("Prime: $it", accent = MaterialTheme.colorScheme.primaryContainer) }
                when (detail.summary.recommendationBias) {
                    RecommendationBias.MoreOften ->
                        MiniTag("Preference up", accent = MaterialTheme.colorScheme.primaryContainer)
                    RecommendationBias.LessOften ->
                        MiniTag("Preference down", accent = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f))
                    RecommendationBias.Neutral -> Unit
                }
            }

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompactRow(
                        title = "YouTube",
                        subtitle = "Search ${detail.summary.name} exercise tutorial",
                        actionLabel = "Open",
                        onAction = {
                            openPreferredExternalLink(
                                context = context,
                                appUri = "vnd.youtube://results?search_query=$encodedQuery",
                                appPackage = "com.google.android.youtube",
                                webUrl = "https://www.youtube.com/results?search_query=$encodedQuery",
                            )
                        },
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    CompactRow(
                        title = "TikTok",
                        subtitle = "Search ${detail.summary.name} exercise tutorial",
                        actionLabel = "Open",
                        onAction = {
                            openPreferredExternalLink(
                                context = context,
                                appUri = "snssdk1233://search/result?keyword=$encodedQuery",
                                appPackage = "com.zhiliaoapp.musically",
                                webUrl = "https://www.tiktok.com/search?q=$encodedQuery",
                            )
                        },
                    )
                }
            }

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append("Equipment: ")
                            append(detail.summary.equipment)
                            detail.summary.secondaryEquipment?.takeIf { it.isNotBlank() }?.let {
                                append(" + ")
                                append(it)
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (detail.movementPatterns.isNotEmpty()) {
                        Text("Patterns: ${detail.movementPatterns.take(3).joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.planesOfMotion.isNotEmpty()) {
                        Text("Planes: ${detail.planesOfMotion.take(3).joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.synonyms.isNotEmpty()) {
                        Text("Also called: ${detail.synonyms.take(4).joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Personal note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Private note for this exercise. It stays on-device and is included in personal data export.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6,
                        label = { Text("Notes") },
                        placeholder = { Text("Technique cues, pain triggers, setup reminders...") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = { noteDraft = persistedNote },
                            enabled = hasUnsavedNoteChanges,
                        ) {
                            Text("Reset")
                        }
                        Button(
                            onClick = { onSaveExerciseNote(noteDraft) },
                            enabled = hasUnsavedNoteChanges,
                        ) {
                            Text(if (persistedNote.isBlank() && noteDraft.isBlank()) "Save" else "Save note")
                        }
                    }
                }
            }

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Learned preference", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Read-only score from workout feedback signals. Adding an exercise pushes it up, removing it pushes it down.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Status: ${preferenceScoreStatus(detail.summary.preferenceScoreDelta)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "RO score: ${formatPreferenceScore(detail.summary.preferenceScoreDelta)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onResetRecommendationPreferenceScore,
                        enabled = detail.summary.preferenceScoreDelta != 0.0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to default")
                    }
                }
            }

            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Workout recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Biases workout generation without hiding the exercise.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RecommendationBiasToggleRow(
                        selectedBias = recommendationBias,
                        onSelect = onSetRecommendationBias,
                    )
                }
            }

            OutlinedButton(onClick = onAddToBuilder, modifier = Modifier.fillMaxWidth()) {
                Text("Add to Builder")
            }
            onAddToMyPlan?.let { addToMyPlan ->
                OutlinedButton(onClick = addToMyPlan, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to My Plan")
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
    detail: HistoryDetail,
    showAbFlags: Boolean,
    onDismiss: () -> Unit,
    onReuseWorkout: (HistoryReuseMode) -> Unit,
    onShowExerciseDetail: (Long) -> Unit,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
) {
    var infoFlag by remember { mutableStateOf<WorkoutAbFlagSnapshot?>(null) }
    var showReuseDialog by remember { mutableStateOf(false) }
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(detail.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${detail.completedAtUtc.replace("T", " ").removeSuffix("Z")} • ${detail.origin.replaceFirstChar { it.uppercase() }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatRail(
                items = listOf(
                    Triple("Elapsed", formatMinutes(detail.durationSeconds), ""),
                    Triple("Volume", formatVolume(detail.totalVolume), ""),
                    Triple("Exercises", detail.exerciseCount.toString(), "logged"),
                ),
            )
            OutlinedButton(
                onClick = { showReuseDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reuse in My Plan")
            }
            if (showAbFlags) {
                detail.abFlags?.completionFeedbackFlag?.let { flag ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Applied A/B flags", fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(flag.flagName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MiniTag(flag.variantName)
                                        MiniTag(flag.enabledStatus.replaceFirstChar { it.uppercase() })
                                    }
                                    Text(
                                        "${flag.variantKey} • ${flag.experimentKey}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { infoFlag = flag }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "A/B flag description",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            detail.exercises.forEach { exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                ExerciseDetailBadge(
                                    label = exercise.name.trim().take(2).uppercase(),
                                    accent = accentForKey(exercise.name),
                                    onClick = { onShowExerciseDetail(exercise.exerciseId) },
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MiniTag("${exercise.loggedSets}/${exercise.totalSets} sets")
                                        if (exercise.targetReps.isNotBlank()) MiniTag("${exercise.targetReps} reps")
                                    }
                                }
                            }
                            ExerciseHistoryOverflow(
                                onOpenExerciseHistory = { onOpenExerciseHistory(exercise.exerciseId, exercise.name) },
                                onOpenExerciseVideos = { onOpenExerciseVideos(exercise.exerciseId, exercise.name) },
                            )
                        }
                        Text(
                            "Volume ${formatVolume(exercise.totalVolume)} • Best ${exercise.bestReps} reps @ ${exercise.bestWeight.toLong()} lb",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    infoFlag?.let { flag ->
        AlertDialog(
            onDismissRequest = { infoFlag = null },
            title = { Text(flag.variantName) },
            text = { Text(flag.flagDescription) },
            confirmButton = {
                TextButton(onClick = { infoFlag = null }) {
                    Text("Close")
                }
            },
        )
    }
    if (showReuseDialog) {
        HistoryReuseModeDialog(
            workoutTitle = detail.title,
            onDismiss = { showReuseDialog = false },
            onSelectMode = { mode ->
                showReuseDialog = false
                onReuseWorkout(mode)
            },
        )
    }
}

internal data class ExerciseHistoryEmptyStateContent(
    val title: String,
    val subtitle: String,
)

internal fun exerciseHistorySummary(detail: ExerciseHistoryDetail): String {
    if (detail.totalEntries == 0) return "No sessions logged yet"

    val sessionLabel = if (detail.totalEntries == 1) "session" else "sessions"
    val prLabel = if (detail.prEntryCount == 1) "PR session" else "PR sessions"

    return when {
        detail.isPrOnlyFilterEnabled && detail.prEntryCount == 0 -> "No PR sessions yet"
        detail.isPrOnlyFilterEnabled -> "Showing ${detail.entries.size} $prLabel"
        detail.prEntryCount > 0 -> "${detail.totalEntries} $sessionLabel • ${detail.prEntryCount} $prLabel"
        else -> "${detail.totalEntries} $sessionLabel"
    }
}

internal fun exerciseHistoryEmptyState(detail: ExerciseHistoryDetail): ExerciseHistoryEmptyStateContent {
    return when {
        detail.totalEntries == 0 -> ExerciseHistoryEmptyStateContent(
            title = "No exercise history yet",
            subtitle = "Complete a workout with this exercise and the performed sets will appear here.",
        )

        detail.isPrOnlyFilterEnabled -> ExerciseHistoryEmptyStateContent(
            title = "No PR entries yet",
            subtitle = "Turn off PRs only to view every logged session for this exercise.",
        )

        else -> ExerciseHistoryEmptyStateContent(
            title = "No exercise history yet",
            subtitle = "Complete a workout with this exercise and the performed sets will appear here.",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseHistorySheet(
    detail: ExerciseHistoryDetail,
    onTogglePrOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val emptyState = if (detail.entries.isEmpty()) exerciseHistoryEmptyState(detail) else null
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(detail.exerciseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Results", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        exerciseHistorySummary(detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilterChip(
                    selected = detail.isPrOnlyFilterEnabled,
                    onClick = { onTogglePrOnly(!detail.isPrOnlyFilterEnabled) },
                    enabled = detail.totalEntries > 0,
                    label = { Text("PRs only") },
                )
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            if (emptyState != null) {
                SuggestionRow(
                    title = emptyState.title,
                    subtitle = emptyState.subtitle,
                    badge = if (detail.totalEntries == 0) "New" else "Filter",
                )
            } else {
                detail.entries.forEach { entry ->
                    ExerciseHistoryEntryCard(entry = entry)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseVideosSheet(detail: ExerciseVideoLinks, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(detail.exerciseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Open native YouTube or TikTok search if installed. Otherwise fall back to the browser.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompactRow(
                        title = "YouTube",
                        subtitle = "Search ${detail.exerciseName} exercise tutorial",
                        actionLabel = "Open",
                        onAction = {
                            openPreferredExternalLink(
                                context = context,
                                appUri = detail.youtubeAppUri,
                                appPackage = "com.google.android.youtube",
                                webUrl = detail.youtubeWebUrl,
                            )
                        },
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    CompactRow(
                        title = "TikTok",
                        subtitle = "Search ${detail.exerciseName} exercise tutorial",
                        actionLabel = "Open",
                        onAction = {
                            openPreferredExternalLink(
                                context = context,
                                appUri = detail.tiktokAppUri,
                                appPackage = "com.zhiliaoapp.musically",
                                webUrl = detail.tiktokWebUrl,
                            )
                        },
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExerciseHistoryEntryCard(entry: dev.toastlabs.toastlift.data.ExerciseHistoryEntry) {
    FeatureCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatEntryDate(entry.completedAtUtc), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            val prCount = entry.workingSets.sumOf { set ->
                listOf(set.isRepPr, set.isWeightPr, set.isVolumePr).count { it }
            }
            if (prCount > 0 || entry.lastSetRepsInReserve != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prCount > 0) {
                        MiniTag("$prCount personal record${if (prCount == 1) "" else "s"}", accent = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                    entry.lastSetRepsInReserve?.let {
                        MiniTag("RIR ${formatRirLabel(it)}", accent = MaterialTheme.colorScheme.primaryContainer)
                    }
                    entry.lastSetRpe?.let {
                        MiniTag("RPE ${formatRpeLabel(it)}", accent = MaterialTheme.colorScheme.secondaryContainer)
                    }
                }
            }
            Text("Working sets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            entry.workingSets.forEach { set ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LeadingBadge(label = set.setNumber.toString())
                    Text(
                        buildString {
                            append(set.reps?.toString() ?: "--")
                            append(" reps")
                            set.weight?.takeIf { it > 0 }?.let {
                                append(" x ")
                                append(it.toLong())
                                append(" lb")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (set.isRepPr) MiniTag("Rep PR", accent = MaterialTheme.colorScheme.primaryContainer)
                        if (set.isWeightPr) MiniTag("Weight PR", accent = MaterialTheme.colorScheme.primaryContainer)
                        if (set.isVolumePr) MiniTag("Volume PR", accent = MaterialTheme.colorScheme.primaryContainer)
                    }
                }
            }
            if (entry.estimatedOneRepMax != null) {
                Text("Est. 1RM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.estimatedOneRepMax.toLong()} lb",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatRail(
                items = listOf(
                    Triple("Weight", "${entry.bestWeight.toLong()}", "lb"),
                    Triple("Volume", formatCompactNumber(entry.totalVolume), "lb"),
                    Triple("Sets", entry.workingSets.size.toString(), "logged"),
                ),
            )
        }
    }
}

@Composable
private fun RichHeroCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accentSet = accentForKey("$eyebrow $title")
    val heroContainer = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val heroPrimary = readableTextColorFor(heroContainer)
    val heroSecondary = readableMutedTextColorFor(heroContainer)
    FeatureCard(containerColor = heroContainer, contentColor = heroPrimary) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            accentSet.glow.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides heroPrimary) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = {
                    Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, color = accentSet.start, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = heroPrimary)
                    Text(subtitle, color = heroSecondary)
                    Divider(color = accentSet.start.copy(alpha = 0.18f))
                    content()
                })
            }
        }
    }
}

@Composable
private fun StatRail(items: List<Triple<String, String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, value, suffix) ->
            val accent = accentForKey(label)
            val cardContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            val cardPrimary = readableTextColorFor(cardContainer)
            val cardSecondary = readableMutedTextColorFor(cardContainer)
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = cardContainer,
                    contentColor = cardPrimary,
                ),
                border = BorderStroke(1.dp, accent.start.copy(alpha = 0.18f)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accentBrush(accent)),
                    )
                    Text(label, style = MaterialTheme.typography.labelMedium, color = cardSecondary)
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, color = cardPrimary)
                    Text(suffix, style = MaterialTheme.typography.bodySmall, color = cardSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, subtitle: String, badge: String) {
    val accent = accentForKey("$title $badge")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        LeadingBadge(label = badge.take(2).uppercase(), accent = accent)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutPreviewStack(
    exercises: List<WorkoutExercise>,
    footer: String,
    recommendationBiasByExerciseId: Map<Long, RecommendationBias>,
    onOpenExerciseHistory: (Long, String) -> Unit,
    onOpenExerciseVideos: (Long, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        exercises.take(3).forEach { exercise ->
            WorkoutExerciseHistoryRow(
                title = exercise.name,
                subtitle = "${exercise.sets} sets • ${exercise.repRange} reps",
                detail = exercise.targetMuscleGroup.ifBlank { exercise.equipment },
                equipment = exercise.equipment,
                recommendationBias = recommendationBiasByExerciseId[exercise.exerciseId] ?: RecommendationBias.Neutral,
                onOpenExerciseHistory = { onOpenExerciseHistory(exercise.exerciseId, exercise.name) },
                onOpenExerciseVideos = { onOpenExerciseVideos(exercise.exerciseId, exercise.name) },
            )
        }
        Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TemplateListRow(
    template: TemplateSummary,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = accentForKey(template.name)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingBadge(label = template.name.take(2).uppercase(), accent = accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${template.exerciseCount} exercises", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Reusable template", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(text = "Start", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onStart).padding(8.dp))
        Box {
            IconButton(onClick = { expanded = true }) {
                Text(
                    "⋮",
                    modifier = Modifier.semantics { contentDescription = "Template actions" },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Start") }, onClick = { expanded = false; onStart() })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { expanded = false; onEdit() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onDelete() })
            }
        }
    }
}

@Composable
private fun WorkoutListRow(
    title: String,
    subtitle: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean = true,
) {
    val accent = accentForKey("$title $detail")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LeadingBadge(label = title.take(2).uppercase(), accent = accent)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showAction) {
            Text(text = actionLabel, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
private fun TemplateNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun HistoryReuseModeDialog(
    workoutTitle: String,
    onDismiss: () -> Unit,
    onSelectMode: (HistoryReuseMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reuse in My Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose how $workoutTitle should land in My Plan before you start it.")
                OutlinedButton(
                    onClick = { onSelectMode(HistoryReuseMode.ExactCopy) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Exact copy", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Keep the same exercises and prefill the logged sets, reps, and weights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = { onSelectMode(HistoryReuseMode.RefreshPrescription) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Refresh through engine", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Keep the exercise list but recompute the current prescription before editing.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun WorkoutExerciseHistoryRow(
    title: String,
    subtitle: String,
    detail: String,
    equipment: String,
    recommendationBias: RecommendationBias = RecommendationBias.Neutral,
    onShowDetail: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove",
    onOpenExerciseHistory: () -> Unit,
    onOpenExerciseVideos: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onShowDetail != null) Modifier.clickable(onClick = onShowDetail) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingBadge(label = equipmentBadgeLabel(equipment), accent = accentForKey(equipment))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ExerciseHistoryOverflow(
            recommendationBias = recommendationBias,
            onShowDetail = onShowDetail,
            onRemove = onRemove,
            removeLabel = removeLabel,
            onOpenExerciseHistory = onOpenExerciseHistory,
            onOpenExerciseVideos = onOpenExerciseVideos,
        )
    }
}

@Composable
private fun BuilderExerciseRow(
    title: String,
    subtitle: String,
    equipment: String,
    recommendationBias: RecommendationBias,
    onRemove: () -> Unit,
    onOpenExerciseHistory: () -> Unit,
    onOpenExerciseVideos: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingBadge(label = equipmentBadgeLabel(equipment), accent = accentForKey(equipment))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "Remove",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
        )
        ExerciseHistoryOverflow(
            recommendationBias = recommendationBias,
            onOpenExerciseHistory = onOpenExerciseHistory,
            onOpenExerciseVideos = onOpenExerciseVideos,
        )
    }
}

@Composable
private fun ExerciseHistoryOverflow(
    recommendationBias: RecommendationBias = RecommendationBias.Neutral,
    onShowDetail: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove",
    onOpenExerciseHistory: () -> Unit,
    onOpenExerciseVideos: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecommendationBiasIndicator(recommendationBias)
            IconButton(onClick = { expanded = true }) {
                Text(
                    "⋮",
                    modifier = Modifier.semantics { contentDescription = "Exercise row actions" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onShowDetail?.let { showDetail ->
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = {
                        expanded = false
                        showDetail()
                    },
                )
            }
            onRemove?.let { remove ->
                DropdownMenuItem(
                    text = { Text(removeLabel) },
                    onClick = {
                        expanded = false
                        remove()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Exercise history") },
                onClick = {
                    expanded = false
                    onOpenExerciseHistory()
                },
            )
            DropdownMenuItem(
                text = { Text("Videos") },
                onClick = {
                    expanded = false
                    onOpenExerciseVideos()
                },
            )
        }
    }
}

private fun preferenceScoreStatus(score: Double): String = when {
    score > 0.0 -> "Upgraded in preference"
    score < 0.0 -> "Downgraded in preference"
    else -> "Default"
}

private fun formatPreferenceScore(score: Double): String =
    String.format(java.util.Locale.US, "%+.1f", score)

@Composable
private fun RecommendationBiasToggleRow(
    selectedBias: RecommendationBias,
    onSelect: (RecommendationBias) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RecommendationBiasSwitchRow(
            label = "Recommend more often",
            checked = selectedBias == RecommendationBias.MoreOften,
            onCheckedChange = { checked ->
                onSelect(if (checked) RecommendationBias.MoreOften else RecommendationBias.Neutral)
            },
        )
        RecommendationBiasSwitchRow(
            label = "Recommend less often",
            checked = selectedBias == RecommendationBias.LessOften,
            onCheckedChange = { checked ->
                onSelect(if (checked) RecommendationBias.LessOften else RecommendationBias.Neutral)
            },
        )
    }
}

@Composable
private fun RecommendationBiasSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
    Surface(
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (supportingText == null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun RecommendationBiasIndicator(bias: RecommendationBias) {
    if (bias == RecommendationBias.Neutral) return
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = when (bias) {
        RecommendationBias.MoreOften -> if (isDark) Color(0xFF86E3B4) else Color(0xFF126B43)
        RecommendationBias.LessOften -> if (isDark) Color(0xFFFFB38A) else Color(0xFF8A4B12)
        RecommendationBias.Neutral -> Color.Unspecified
    }
    Text(
        text = if (bias == RecommendationBias.MoreOften) "^" else "v",
        modifier = Modifier.width(16.dp),
        color = tint,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun LeadingBadge(
    label: String,
    accent: GlowAccent = emberAccent,
    textColor: Color = Color.Unspecified,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accent.start.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(accentBrush(accent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = when {
                    label.length >= 4 -> 12.sp
                    label.length == 3 -> 13.sp
                    else -> 15.sp
                },
                fontWeight = FontWeight.Black,
                color = if (textColor == Color.Unspecified) accent.textOnAccent else textColor,
            )
        }
    }
}

@Composable
private fun MiniTag(text: String, accent: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = accent,
            contentColor = readableTextColorFor(accent),
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = readableTextColorFor(accent),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SkeletonLine(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp = 14.dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small),
    )
}

@Composable
private fun SkeletonBlock(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium),
    )
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = Color.Unspecified,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    elevation: androidx.compose.ui.unit.Dp = 10.dp,
    fullWidth: Boolean = true,
    content: @Composable () -> Unit,
) {
    val resolvedContentColor = if (contentColor == Color.Unspecified) readableTextColorFor(containerColor) else contentColor
    Card(
        modifier = if (fullWidth) modifier.fillMaxWidth() else modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = resolvedContentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = border,
    ) {
        content()
    }
}

// ── Adaptive Program Engine Composables ──

@Composable
private fun ProgramLaunchCard(
    profile: UserProfile?,
    onStartProgram: () -> Unit,
) {
    val accent = accentForKey("guided program")
    FeatureCard(
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.start.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniTag(
                text = "Adaptive Program",
                accent = accent.start.copy(alpha = 0.18f),
            )
            Text(
                "Build a guided training block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                profile?.let {
                    "Get a 4-6 week plan with scheduled sessions, checkpoint reviews, and day-to-day readiness adjustments."
                } ?: "Create a guided multi-week block with scheduled sessions, checkpoint reviews, and readiness-aware adjustments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SuggestionRow(
                    title = "Progress over randomization",
                    subtitle = "Hold onto your core lifts across the block instead of generating each workout from scratch.",
                    badge = "Plan",
                )
                SuggestionRow(
                    title = "Adjust when life gets messy",
                    subtitle = "Low energy, short time, and weekly checkpoints can steer the block without restarting it.",
                    badge = "Adaptive",
                )
            }
            Button(
                onClick = onStartProgram,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start a Program")
            }
        }
    }
}

@Composable
private fun ProgramOverviewCard(
    overview: ProgramOverview,
    status: ProgramStatus,
    onPauseProgram: () -> Unit,
    onResumeProgram: () -> Unit,
    onEndProgram: () -> Unit,
) {
    val accent = accentForKey("program ${overview.title}")
    val progress = if (overview.totalWeeks > 0) overview.weekNumber.toFloat() / overview.totalWeeks else 0f
    val confidenceColor = when (overview.confidenceLabel) {
        "Stable" -> surgeAccent.start
        "Adjusting" -> goldAccent.start
        else -> emberAccent.start
    }
    val adherenceColor = when {
        (overview.adherenceSnapshot?.balance ?: 0) > 0 -> surgeAccent.start
        (overview.adherenceSnapshot?.balance ?: 0) < 0 -> emberAccent.start
        else -> goldAccent.start
    }
    FeatureCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        overview.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Week ${overview.weekNumber} of ${overview.totalWeeks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MiniTag(text = overview.confidenceLabel, accent = confidenceColor.copy(alpha = 0.2f))
                    overview.adherenceSnapshot?.let { snapshot ->
                        MiniTag(
                            text = "Adherence ${snapshot.displayValue}",
                            accent = adherenceColor.copy(alpha = 0.2f),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(listOf(accent.start, accent.end)),
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
            overview.nextSessionFocus?.let { focus ->
                Text(
                    "Next: $focus",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            overview.adherenceSnapshot?.let { snapshot ->
                Text(
                    "${snapshot.statusLabel} adherence. ${snapshot.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status == ProgramStatus.ACTIVE) {
                    OutlinedButton(onClick = onPauseProgram) {
                        Text("Pause")
                    }
                } else if (status == ProgramStatus.PAUSED) {
                    Button(onClick = onResumeProgram) {
                        Text("Resume")
                    }
                }
                TextButton(onClick = onEndProgram) {
                    Text("End program", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CoachBriefCard(brief: String) {
    FeatureCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Coach Brief", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(brief, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DailyCoachCard(message: DailyCoachMessage) {
    CompactSectionCard(
        title = "✨ Daily Coach",
        subtitle = "",
    ) {
        Text(
            message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadinessChipRow(
    readiness: ReadinessContext,
    onUpdateReadiness: ((ReadinessContext) -> ReadinessContext) -> Unit,
) {
    FeatureCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Readiness",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadinessChip(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    label = "Low Energy",
                    selected = readiness.energyLevel <= 2,
                    onClick = {
                        onUpdateReadiness { it.copy(energyLevel = if (it.energyLevel <= 2) 3 else 1) }
                    },
                )
                ReadinessChip(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    label = "Short on Time",
                    selected = readiness.timeBudgetMinutes != null && readiness.timeBudgetMinutes < 40,
                    onClick = {
                        onUpdateReadiness {
                            it.copy(timeBudgetMinutes = if (it.timeBudgetMinutes != null && it.timeBudgetMinutes < 40) null else 30)
                        }
                    },
                )
                ReadinessChip(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    label = "Sore",
                    selected = readiness.sorenessLevel >= 4,
                    onClick = {
                        onUpdateReadiness { it.copy(sorenessLevel = if (it.sorenessLevel >= 4) 2 else 4) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadinessChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun PlannedSessionCard(
    session: PlannedSession,
    exercises: List<PlannedSessionExercise>,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    recoverableSkippedSession: PlannedSession?,
    onUnskip: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusLabel = programFocusLabel(session.focusKey)
    FeatureCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Up Next: $focusLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (recoverableSkippedSession != null) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Text(
                                "⋮",
                                modifier = Modifier.semantics { contentDescription = "Planned session actions" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(unskipSessionMenuLabel(recoverableSkippedSession)) },
                                onClick = {
                                    expanded = false
                                    onUnskip()
                                },
                            )
                        }
                    }
                }
            }
            Text(
                "Week ${session.weekNumber} • Day ${session.dayIndex + 1} • ${session.plannedSets} sets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.timeBudgetMinutes?.let { mins ->
                Text(
                    "$mins min session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exercises.isNotEmpty()) {
                Text(
                    "${exercises.size} planned exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart) { Text("Start Session") }
                OutlinedButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklyMuscleTargetsOverviewCard(
    summary: WeeklyMuscleTargetSummary,
    onOpen: () -> Unit,
) {
    FeatureCard(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open weekly muscle targets"
                role = Role.Button
            },
        border = BorderStroke(1.dp, goldAccent.start.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeeklyMuscleTargetHexagon(
                summary = summary,
                modifier = Modifier.size(124.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniTag(
                    text = "This Week",
                    accent = goldAccent.start.copy(alpha = 0.18f),
                )
                Text(
                    "Weekly Muscle Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    weeklyMuscleTargetRangeLabel(summary.range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.groupSummaries.forEach { group ->
                    val progress = if (group.targetSets > 0.0) {
                        (group.completedSets / group.targetSets).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.label.removeSuffix(" Muscles"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            percentString(progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = weeklyMuscleAccent(group.key).start,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "All completed workouts count toward this week. Tap for the full breakdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyMuscleTargetHexagon(
    summary: WeeklyMuscleTargetSummary,
    modifier: Modifier = Modifier,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    val outlineStrong = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val outlineSoft = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val pushRatio = summary.groupSummaries.firstOrNull { it.key == "push" }
        ?.let { if (it.targetSets > 0.0) (it.completedSets / it.targetSets).coerceIn(0.0, 1.0) else 0.0 }
        ?: 0.0
    val pullRatio = summary.groupSummaries.firstOrNull { it.key == "pull" }
        ?.let { if (it.targetSets > 0.0) (it.completedSets / it.targetSets).coerceIn(0.0, 1.0) else 0.0 }
        ?: 0.0
    val legsRatio = summary.groupSummaries.firstOrNull { it.key == "legs" }
        ?.let { if (it.targetSets > 0.0) (it.completedSets / it.targetSets).coerceIn(0.0, 1.0) else 0.0 }
        ?: 0.0
    val segmentRatios = listOf(pushRatio, pushRatio, pullRatio, pullRatio, legsRatio, legsRatio)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.42f

            fun point(index: Int, scale: Double): androidx.compose.ui.geometry.Offset {
                val angle = Math.toRadians((-90.0) + (index * 60.0))
                return androidx.compose.ui.geometry.Offset(
                    x = center.x + (kotlin.math.cos(angle) * radius * scale).toFloat(),
                    y = center.y + (kotlin.math.sin(angle) * radius * scale).toFloat(),
                )
            }

            fun hexPath(scale: Double): Path = Path().apply {
                moveTo(point(0, scale).x, point(0, scale).y)
                for (index in 1 until 6) {
                    val target = point(index, scale)
                    lineTo(target.x, target.y)
                }
                close()
            }

            drawPath(
                path = hexPath(1.0),
                color = surfaceVariant,
            )
            drawPath(
                path = hexPath(1.0),
                color = outlineStrong,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawPath(
                path = hexPath(0.68),
                color = outlineSoft,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawPath(
                path = hexPath(0.36),
                color = outlineSoft,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            val filledPath = Path().apply {
                moveTo(point(0, segmentRatios[0]).x, point(0, segmentRatios[0]).y)
                for (index in 1 until 6) {
                    val target = point(index, segmentRatios[index])
                    lineTo(target.x, target.y)
                }
                close()
            }
            drawPath(
                path = filledPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        goldAccent.start.copy(alpha = 0.72f),
                        surgeAccent.start.copy(alpha = 0.58f),
                        emberAccent.start.copy(alpha = 0.68f),
                    ),
                ),
            )
            drawPath(
                path = filledPath,
                color = Color.White.copy(alpha = 0.16f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                percentString(summary.overallCompletionRatio.coerceIn(0.0, 1.0)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "cleared",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyMuscleTargetsDetailScreen(
    summary: WeeklyMuscleTargetSummary,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HistoryDetailHeader(
                title = "Weekly Muscle Targets",
                onBack = onBack,
            )
        }
        item {
            Text(
                "Every completed workout, whether guided, generated, or manual, feeds these weekly targets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            WeeklyMuscleTargetsCard(summary = summary)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WeeklyMuscleTargetsCard(summary: WeeklyMuscleTargetSummary) {
    val expandedByGroup = remember(summary.weekNumber) {
        mutableStateMapOf<String, Boolean>().apply {
            summary.groupSummaries.forEach { put(it.key, false) }
        }
    }
    val progress = summary.overallCompletionRatio.coerceIn(0.0, 1.0).toFloat()

    FeatureCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Weekly Muscle Targets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "This Week • ${weeklyMuscleTargetRangeLabel(summary.range)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiniTag(
                    text = percentString(summary.overallCompletionRatio.coerceIn(0.0, 1.0)),
                    accent = goldAccent.start.copy(alpha = 0.2f),
                )
            }
            Text(
                "${decimalString(summary.completedSets)} / ${decimalString(summary.targetSets)} weighted sets cleared this week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(listOf(goldAccent.start, surgeAccent.end)),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                summary.groupSummaries.forEach { group ->
                    WeeklyMuscleTargetGroupCard(
                        summary = group,
                        expanded = expandedByGroup[group.key] == true,
                        onToggle = { expandedByGroup[group.key] = expandedByGroup[group.key] != true },
                    )
                }
            }
            if (summary.history.isNotEmpty()) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Recent Weeks",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        summary.history.forEach { history ->
                            WeeklyMuscleTargetHistoryChip(
                                summary = history,
                                isCurrentWeek = history.weekNumber == summary.weekNumber,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMuscleTargetGroupCard(
    summary: WeeklyMuscleTargetGroupSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = weeklyMuscleAccent(summary.key)
    val progress = if (summary.targetSets > 0.0) {
        (summary.completedSets / summary.targetSets).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val remainingSets = (summary.targetSets - summary.completedSets).coerceAtLeast(0.0)

    FeatureCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, accent.start.copy(alpha = 0.18f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeadingBadge(
                    label = when (summary.key) {
                        "push" -> "PU"
                        "pull" -> "PL"
                        "legs" -> "LG"
                        else -> summary.label.take(2).uppercase()
                    },
                    accent = accent,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        summary.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${decimalString(summary.completedSets)} / ${decimalString(summary.targetSets)} sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "${decimalString(remainingSets)} to go",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse ${summary.label}" else "Expand ${summary.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(listOf(accent.start, accent.end)),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }
            if (expanded) {
                Divider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    summary.muscleSummaries.forEach { muscle ->
                        val muscleRemaining = (muscle.targetSets - muscle.completedSets).coerceAtLeast(0.0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    muscle.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${decimalString(muscle.completedSets)} / ${decimalString(muscle.targetSets)} sets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${decimalString(muscleRemaining)} to go",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(
                        "Primary muscles = 1 set. Secondary muscles = 0.5 sets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun WeeklyMuscleTargetHistoryChip(
    summary: WeeklyMuscleTargetHistorySummary,
    isCurrentWeek: Boolean,
) {
    val accent = if (isCurrentWeek) goldAccent else surgeAccent
    FeatureCard(
        fullWidth = false,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            if (isCurrentWeek) accent.start.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                percentString(summary.completionRatio.coerceIn(0.0, 1.0)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                weeklyMuscleTargetRangeLabel(summary.range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun weeklyMuscleAccent(key: String): GlowAccent = when (key) {
    "push" -> goldAccent
    "pull" -> emberAccent
    "legs" -> surgeAccent
    else -> amethystAccent
}

@Composable
private fun ProgramProgressCard(summary: ProgramProgressSummary) {
    val clearedSessions = summary.totalSessions - summary.remainingSessions
    val progress = if (summary.totalSessions > 0) clearedSessions.toFloat() / summary.totalSessions else 0f
    val reviewValue = if (summary.totalCheckpoints > 0) {
        "${summary.completedCheckpoints}/${summary.totalCheckpoints}"
    } else {
        "None"
    }

    FeatureCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Program Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$clearedSessions of ${summary.totalSessions} sessions cleared across ${summary.totalWeeks} weeks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(listOf(surgeAccent.start, goldAccent.end)),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProgramProgressMetric(
                    label = "Cleared",
                    value = "$clearedSessions/${summary.totalSessions}",
                    accent = surgeAccent,
                    modifier = Modifier.weight(1f),
                )
                ProgramProgressMetric(
                    label = "Weeks",
                    value = "${summary.completedWeeks}/${summary.totalWeeks}",
                    accent = goldAccent,
                    modifier = Modifier.weight(1f),
                )
                ProgramProgressMetric(
                    label = "Reviews",
                    value = reviewValue,
                    accent = amethystAccent,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                summary.weekSummaries.forEach { week ->
                    ProgramProgressWeekRow(week = week)
                }
            }
        }
    }
}

@Composable
private fun ProgramProgressMetric(
    label: String,
    value: String,
    accent: GlowAccent,
    modifier: Modifier = Modifier,
) {
    FeatureCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBrush(accent)),
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun ProgramProgressWeekRow(week: ProgramWeekProgressSummary) {
    val closedSessions = week.sessionStatuses.count(::isClosedProgramSession)
    val statusAccent = when (week.statusLabel) {
        "Done", "Closed" -> surgeAccent.start.copy(alpha = 0.18f)
        "Current" -> goldAccent.start.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    }
    val checkpointLabel = when (week.checkpointStatus) {
        CheckpointStatus.COMPLETED -> "Review done"
        CheckpointStatus.PENDING -> "Review ahead"
        CheckpointStatus.SKIPPED -> "Review skipped"
        null -> null
    }
    val checkpointAccent = when (week.checkpointStatus) {
        CheckpointStatus.COMPLETED -> amethystAccent.start.copy(alpha = 0.18f)
        CheckpointStatus.PENDING -> goldAccent.start.copy(alpha = 0.18f)
        CheckpointStatus.SKIPPED -> emberAccent.start.copy(alpha = 0.18f)
        null -> Color.Transparent
    }
    val detailLine = buildList {
        week.weekTypeLabel?.let { add(it) }
        if (week.totalSessions > 0) {
            add("$closedSessions/${week.totalSessions} cleared")
        }
        if (week.skippedSessions > 0) {
            add("${week.skippedSessions} skipped")
        }
    }.ifEmpty { listOf("Waiting for sessions") }.joinToString(" • ")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Week ${week.weekNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                checkpointLabel?.let {
                    MiniTag(text = it, accent = checkpointAccent)
                }
                MiniTag(text = week.statusLabel, accent = statusAccent)
            }
        }
        ProgramProgressSessionStrip(week = week)
    }
}

@Composable
private fun ProgramProgressSessionStrip(week: ProgramWeekProgressSummary) {
    if (week.sessionStatuses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        week.sessionStatuses.forEach { status ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when (status) {
                            SessionStatus.COMPLETED -> surgeAccent.start
                            SessionStatus.SKIPPED -> emberAccent.start
                            SessionStatus.MIGRATED -> amethystAccent.start.copy(alpha = 0.72f)
                            SessionStatus.IN_PROGRESS -> goldAccent.end
                            SessionStatus.UPCOMING -> if (week.isCurrentWeek) {
                                goldAccent.start.copy(alpha = 0.82f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
                            }
                        },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProgramSetupScreen(
    draft: ProgramSetupDraft,
    splitPrograms: List<TrainingSplitProgram>,
    onDraftChange: ((ProgramSetupDraft) -> ProgramSetupDraft) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Build Your Program", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Goal
            Text("Goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Strength", "Hypertrophy", "Conditioning", "Fat Loss", "General Fitness").forEach { goal ->
                    FilterChip(
                        selected = draft.goal == goal,
                        onClick = { onDraftChange { it.copy(goal = goal) } },
                        label = { Text(goal) },
                    )
                }
            }

            // Duration
            Text("Duration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4 to "4 weeks", 6 to "6 weeks", 0 to "Auto").forEach { (weeks, label) ->
                    FilterChip(
                        selected = draft.durationWeeks == weeks,
                        onClick = { onDraftChange { it.copy(durationWeeks = weeks) } },
                        label = { Text(label) },
                    )
                }
            }

            // Sessions per week
            Text("Sessions per week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weeklySessionCountOptions.forEach { count ->
                    FilterChip(
                        selected = draft.sessionsPerWeek == count,
                        onClick = { onDraftChange { it.copy(sessionsPerWeek = count) } },
                        label = { Text("$count") },
                    )
                }
            }

            // Split
            Text("Split", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                splitPrograms.forEach { split ->
                    FilterChip(
                        selected = draft.splitProgramId == split.id,
                        onClick = { onDraftChange { it.copy(splitProgramId = split.id) } },
                        label = { Text(compactSplitLabel(split.name)) },
                    )
                }
            }

            // Session time
            Text("Session time: ${draft.sessionTimeMinutes} min", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60, 75, 90).forEach { mins ->
                    FilterChip(
                        selected = draft.sessionTimeMinutes == mins,
                        onClick = { onDraftChange { it.copy(sessionTimeMinutes = mins) } },
                        label = { Text("${mins}m") },
                    )
                }
            }

            // Equipment stability
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Same gym/equipment each session?", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.equipmentStability,
                        onClick = { onDraftChange { it.copy(equipmentStability = true) } },
                        label = { Text("Yes") },
                    )
                    FilterChip(
                        selected = !draft.equipmentStability,
                        onClick = { onDraftChange { it.copy(equipmentStability = false) } },
                        label = { Text("No") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Build My Program")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SfrDebriefSheet(
    exercises: List<SfrDebriefExercise>,
    onSubmit: (Long, SfrTag) -> Unit,
    onDismiss: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How did each exercise feel?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Quick feedback helps the program learn which exercises work best for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exercises.forEach { exercise ->
                SfrExerciseRow(
                    exerciseName = exercise.exerciseName,
                    onTag = { tag -> onSubmit(exercise.exerciseId, tag) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SfrExerciseRow(
    exerciseName: String,
    onTag: (SfrTag) -> Unit,
) {
    var selected by remember { mutableStateOf<SfrTag?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SfrTag.entries.forEach { tag ->
                val label = when (tag) {
                    SfrTag.GREAT_STIMULUS -> "Great stimulus"
                    SfrTag.JOINT_DISCOMFORT -> "Joint discomfort"
                    SfrTag.NO_OPINION -> "No opinion"
                }
                FilterChip(
                    selected = selected == tag,
                    onClick = {
                        selected = tag
                        onTag(tag)
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckpointReviewSheet(
    result: CheckpointResult,
    onAccept: () -> Unit,
    onAcceptEvolution: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ToastLiftModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Checkpoint Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(result.summaryText, style = MaterialTheme.typography.bodyMedium)
            val actionLabel = when (result.action) {
                dev.toastlabs.toastlift.data.CheckpointAction.CONTINUE -> "Continue as planned"
                dev.toastlabs.toastlift.data.CheckpointAction.INTENSIFY -> "Increase intensity"
                dev.toastlabs.toastlift.data.CheckpointAction.EXTEND_BLOCK -> "Extend this block"
                dev.toastlabs.toastlift.data.CheckpointAction.TRIGGER_DELOAD -> "Deload recommended"
                dev.toastlabs.toastlift.data.CheckpointAction.PIVOT_EXERCISES -> "Exercise changes recommended"
                dev.toastlabs.toastlift.data.CheckpointAction.REDUCE_TO_MAINTAIN -> "Reduce to maintenance"
            }
            FeatureCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Recommendation", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(actionLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
            val confidenceLabel = when {
                result.newConfidenceScore >= 0.7 -> "Stable"
                result.newConfidenceScore >= 0.4 -> "Adjusting"
                else -> "Needs Review"
            }
            Text("Confidence: $confidenceLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.exerciseEvolutionSuggestions.isNotEmpty()) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text("Exercise Progressions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                result.exerciseEvolutionSuggestions.forEach { suggestion ->
                    EvolutionSuggestionRow(
                        suggestion = suggestion,
                        onAccept = { onAcceptEvolution(suggestion.slotId, suggestion.suggestedExerciseId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Got it")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EvolutionSuggestionRow(
    suggestion: EvolutionSuggestion,
    onAccept: () -> Unit,
) {
    FeatureCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${suggestion.currentExerciseName} → ${suggestion.suggestedExerciseName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(suggestion.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept) { Text("Accept") }
                OutlinedButton(onClick = {}) { Text("Defer") }
            }
        }
    }
}

@Composable
private fun ProgramWrapUpScreen(
    onStartNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("Program Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Great work finishing your training block.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Next Block")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to Today")
            }
        }
    }
}
