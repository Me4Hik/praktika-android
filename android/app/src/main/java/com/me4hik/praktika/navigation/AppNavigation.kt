// 04.08.2026 Reminder App cursor by Me4Hik START - NavHost и переходы между экранами
// 05.08.2026 Main Screen cursor by Me4Hik START - state-driven navigation
// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - детерминированный onboarding → Home
// 05.08.2026 Question And Skip cursor by Me4Hik START - route конкретного occurrence и skip
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - lifecycle-aware root state collection
// 05.08.2026 Answer Save cursor by Me4Hik START - AnswerViewModel, save navigation и Snackbar
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.me4hik.praktika.R
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.NavigationRouteTracker
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.diagnostics.DiagnosticReportCoordinator
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ContentResolverExportDocumentWriter
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime
import androidx.activity.ComponentActivity
import com.me4hik.praktika.ui.components.PracticeSnackbarHost
import com.me4hik.praktika.ui.components.showPracticeActionSnackbar
import com.me4hik.praktika.ui.components.showPracticeInfoSnackbar
import com.me4hik.praktika.ui.archive.ArchiveDatesScreen
import com.me4hik.praktika.ui.archive.ArchiveDatesViewModel
import com.me4hik.praktika.ui.archive.ArchiveDayScreen
import com.me4hik.praktika.ui.archive.ArchiveDayUiState
import com.me4hik.praktika.ui.archive.ArchiveDayViewModel
import com.me4hik.praktika.ui.archive.ArchiveDeleteUiState
import com.me4hik.praktika.ui.archive.ArchiveExportCoordinator
import com.me4hik.praktika.ui.archive.ArchiveExportEffects
import com.me4hik.praktika.ui.archive.ArchiveExportFormatDialog
import com.me4hik.praktika.ui.archive.ArchiveEntryShareDialog
import com.me4hik.praktika.ui.archive.ArchivePeriodExportDialog
import com.me4hik.praktika.ui.archive.ArchiveShareCoordinator
import com.me4hik.praktika.ui.archive.ArchiveShareEffects
import com.me4hik.praktika.ui.archive.EntryShareRequest
import com.me4hik.praktika.export.share.EntryShareMode
import com.me4hik.praktika.share.ShareTempFileStore
import com.me4hik.praktika.ui.archive.ArchiveQuestionHistoryScreen
import com.me4hik.praktika.ui.archive.ArchiveQuestionHistoryUiState
import com.me4hik.praktika.ui.archive.ArchiveQuestionHistoryViewModel
import com.me4hik.praktika.ui.archive.ArchiveQuestionsScreen
import com.me4hik.praktika.ui.archive.ArchiveQuestionsViewModel
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.archive.ArchiveViewModelFactory
import com.me4hik.praktika.ui.analytics.AnalyticsInsightsScreen
import com.me4hik.praktika.ui.analytics.AnalyticsInsightsViewModel
import com.me4hik.praktika.ui.analytics.AnalyticsInsightsViewModelFactory
import com.me4hik.praktika.ui.analytics.MissedOccurrencesDetailScreen
import com.me4hik.praktika.ui.analytics.MissedOccurrencesDetailViewModel
import com.me4hik.praktika.ui.analytics.MissedOccurrencesDetailViewModelFactory
import com.me4hik.praktika.data.read.analytics.MissedDetailFilter
import com.me4hik.praktika.ui.AnswerScreen
import com.me4hik.praktika.ui.HomeScreen
import com.me4hik.praktika.ui.OnboardingScreen
import com.me4hik.praktika.ui.QuestionHistoryScreen
import com.me4hik.praktika.ui.QuestionScreen
import com.me4hik.praktika.ui.SettingsScreen
import com.me4hik.praktika.ui.settings.SettingsBackupEffects
import com.me4hik.praktika.ui.settings.SettingsNavigationEvent
import com.me4hik.praktika.ui.settings.SettingsSnackbarEvent
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.settings.BugReportUiState
import com.me4hik.praktika.ui.settings.SettingsViewModel
import com.me4hik.praktika.ui.settings.SettingsViewModelFactory
import com.me4hik.praktika.ui.settings.SoundLibraryScreen
import com.me4hik.praktika.ui.settings.SoundLibraryViewModel
import com.me4hik.praktika.ui.settings.SoundLibraryViewModelFactory
import com.me4hik.praktika.ui.practice.AnswerBlockedReason
import com.me4hik.praktika.ui.practice.AnswerNavigationEvent
import com.me4hik.praktika.ui.practice.AnswerUiState
import com.me4hik.praktika.ui.practice.AnswerViewModel
import com.me4hik.praktika.ui.practice.AnswerViewModelFactory
import com.me4hik.praktika.ui.practice.PendingSaveConfirmation
import com.me4hik.praktika.ui.practice.PracticeFatalErrorScreen
import com.me4hik.praktika.ui.practice.PracticeLoadingScreen
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.PracticeUiState
import com.me4hik.praktika.ui.practice.QuestionBlockedReason
import com.me4hik.praktika.ui.practice.QuestionCommandState
import com.me4hik.praktika.ui.practice.QuestionNavigationEvent
import com.me4hik.praktika.ui.practice.QuestionUiState
import com.me4hik.praktika.ui.practice.QuestionViewModel
import com.me4hik.praktika.ui.practice.QuestionViewModelFactory
import com.me4hik.praktika.ui.restore.ProductionRestoreEffects
import com.me4hik.praktika.ui.restore.ProductionRestoreSessionHost
import com.me4hik.praktika.ui.restore.ProductionRestoreViewModel
import com.me4hik.praktika.ui.restore.ProductionRestoreViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun AppNavigation(
    viewModel: PracticeRootViewModel,
    runtime: PraktikaRuntime,
    testPeriodExportStartEpochDay: Long? = null,
    testPeriodExportEndEpochDay: Long? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPracticeStarted = uiState is PracticeUiState.Started
    var navHostStartDestination by remember { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        PracticeUiState.Loading -> {
            PracticeLoadingScreen()
            return
        }
        is PracticeUiState.FatalError -> {
            PracticeFatalErrorScreen(
                error = state.error,
                onRetry = viewModel::retryRead,
            )
            return
        }
        is PracticeUiState.NotStarted -> {
            if (navHostStartDestination == null) {
                navHostStartDestination = Routes.ONBOARDING
            }
        }
        is PracticeUiState.Started -> {
            if (navHostStartDestination == null) {
                navHostStartDestination = Routes.HOME
            }
        }
    }

    val startDestination = navHostStartDestination ?: when (uiState) {
        is PracticeUiState.NotStarted -> Routes.ONBOARDING
        is PracticeUiState.Started -> Routes.HOME
        else -> null
    }

    if (startDestination == null) {
        PracticeLoadingScreen()
        return
    }
    val navController = rememberNavController()
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - centralized screen_open diagnostics
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: "unknown"
            val previousRoute = NavigationRouteTracker.currentRoute
            NavigationRouteTracker.onRouteChanged(route)
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.APP,
                    name = "screen_open",
                    metadata = TargetedBugDiagnostics.screenOpenMetadata(
                        route = route,
                        previousRoute = previousRoute,
                    ),
                )
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSaveConfirmation by remember { mutableStateOf<PendingSaveConfirmation?>(null) }
    var pendingSettingsSnackbarMessage by remember { mutableStateOf<String?>(null) }
    var pendingDeferSnackbarMessage by remember { mutableStateOf<String?>(null) }
    // 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - export orchestration wiring
    // 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - format dialog before export
    val context = LocalContext.current
    val archiveZoneIdProvider = remember(runtime) {
        com.me4hik.praktika.ui.archive.TimeProviderArchiveZoneIdProvider(runtime.timeProvider)
    }
    val diagnosticReportSubmitter = remember(runtime) {
        DiagnosticReportCoordinator(
            context = context.applicationContext,
            runtimeProvider = { runtime },
        )
    }
    val archiveExportCoordinator = remember(runtime, context, coroutineScope) {
        ArchiveExportCoordinator(
            exportUseCase = ArchiveExportUseCase(
                archiveReadRepository = runtime.archiveReadRepository,
                zoneIdProvider = archiveZoneIdProvider,
            ),
            documentWriter = ContentResolverExportDocumentWriter(context.contentResolver),
            scope = coroutineScope,
        )
    }
    var showPeriodExportDialog by remember { mutableStateOf(false) }
    var pendingExportSelection by remember { mutableStateOf<ExportSelection?>(null) }
    val archiveExportNoAnswersMessage = stringResource(R.string.archive_export_no_answers)
    val archiveExportSavedMessage = stringResource(R.string.archive_export_saved)
    val archiveExportWriteErrorMessage = stringResource(R.string.archive_export_write_error)
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.2 single restore ViewModel owner
    val activity = context as ComponentActivity
    val restoreFactory = remember(activity, runtime) {
        ProductionRestoreViewModelFactory(
            owner = activity,
            applicationContext = activity.applicationContext,
            runtime = runtime,
        )
    }
    val restoreViewModel: ProductionRestoreViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = restoreFactory,
    )
    ProductionRestoreEffects(
        viewModel = restoreViewModel,
        onRestoreCommitted = { practiceStarted ->
            if (!practiceStarted) {
                viewModel.discardOnboardingDraftFromPersistedSchedule()
            }
        },
    )
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    ArchiveExportEffects(
        coordinator = archiveExportCoordinator,
        snackbarHostState = snackbarHostState,
        noAnswersMessage = archiveExportNoAnswersMessage,
        successMessage = archiveExportSavedMessage,
        writeErrorMessage = archiveExportWriteErrorMessage,
    )
    if (showPeriodExportDialog) {
        ArchivePeriodExportDialog(
            onDismiss = { showPeriodExportDialog = false },
            onConfirm = { startEpochDay, endEpochDayInclusive ->
                showPeriodExportDialog = false
                pendingExportSelection = ExportSelection.Range(
                    startEpochDay = startEpochDay,
                    endEpochDayInclusive = endEpochDayInclusive,
                )
            },
            initialDisplayedMonthMillis = runtime.timeProvider.nowEpochMillis(),
            initialSelectedStartEpochDay = testPeriodExportStartEpochDay,
            initialSelectedEndEpochDay = testPeriodExportEndEpochDay,
        )
    }
    pendingExportSelection?.let { selection ->
        ArchiveExportFormatDialog(
            onDismiss = { pendingExportSelection = null },
            onFormatSelected = { format ->
                pendingExportSelection = null
                archiveExportCoordinator.requestExport(selection, format)
            },
        )
    }
    val requestArchiveExport: (ExportSelection) -> Unit = { selection ->
        pendingExportSelection = selection
    }
    // 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
    // 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
    // 07.08.2026 Stage 21 Share cursor by Me4Hik START - share orchestration wiring
    val archiveShareCoordinator = remember(runtime, context, coroutineScope) {
        ArchiveShareCoordinator(
            exportUseCase = ArchiveExportUseCase(
                archiveReadRepository = runtime.archiveReadRepository,
                zoneIdProvider = archiveZoneIdProvider,
            ),
            tempFileStore = ShareTempFileStore.fromContext(context.applicationContext),
            scope = coroutineScope,
        )
    }
    var showPeriodShareDialog by remember { mutableStateOf(false) }
    var pendingShareSelection by remember { mutableStateOf<ExportSelection?>(null) }
    var pendingEntryShare by remember { mutableStateOf<EntryShareRequest?>(null) }
    val archiveShareNoAnswersMessage = stringResource(R.string.archive_share_no_answers)
    val archiveSharePrepareErrorMessage = stringResource(R.string.archive_share_prepare_error)
    val archiveShareChooserErrorMessage = stringResource(R.string.archive_share_chooser_error)
    val archiveShareTextChooserTitle = stringResource(R.string.archive_share_text_chooser_title)
    val archiveShareFileChooserTitle = stringResource(R.string.archive_share_file_chooser_title)
    ArchiveShareEffects(
        coordinator = archiveShareCoordinator,
        snackbarHostState = snackbarHostState,
        context = context,
        noAnswersMessage = archiveShareNoAnswersMessage,
        prepareFailedMessage = archiveSharePrepareErrorMessage,
        chooserFailedMessage = archiveShareChooserErrorMessage,
        textChooserTitle = archiveShareTextChooserTitle,
        fileChooserTitle = archiveShareFileChooserTitle,
    )
    if (showPeriodShareDialog) {
        ArchivePeriodExportDialog(
            onDismiss = { showPeriodShareDialog = false },
            onConfirm = { startEpochDay, endEpochDayInclusive ->
                showPeriodShareDialog = false
                pendingShareSelection = ExportSelection.Range(
                    startEpochDay = startEpochDay,
                    endEpochDayInclusive = endEpochDayInclusive,
                )
            },
            initialDisplayedMonthMillis = runtime.timeProvider.nowEpochMillis(),
            initialSelectedStartEpochDay = testPeriodExportStartEpochDay,
            initialSelectedEndEpochDay = testPeriodExportEndEpochDay,
            titleRes = R.string.archive_share_period_dialog_title,
            confirmRes = R.string.archive_share_period_confirm,
            dialogTestTag = ArchiveTestTags.SHARE_PERIOD_DIALOG,
            confirmTestTag = ArchiveTestTags.SHARE_PERIOD_CONFIRM,
        )
    }
    pendingShareSelection?.let { selection ->
        ArchiveExportFormatDialog(
            onDismiss = { pendingShareSelection = null },
            onFormatSelected = { format ->
                pendingShareSelection = null
                archiveShareCoordinator.requestFileShare(selection, format)
            },
            titleRes = R.string.archive_share_format_dialog_title,
            dialogTestTag = ArchiveTestTags.SHARE_FORMAT_DIALOG,
        )
    }
    pendingEntryShare?.let { request ->
        ArchiveEntryShareDialog(
            onDismiss = { pendingEntryShare = null },
            onQuestionOnly = {
                pendingEntryShare = null
                archiveShareCoordinator.shareEntry(
                    mode = EntryShareMode.QUESTION_ONLY,
                    questionText = request.questionText,
                    answerText = request.answerText,
                )
            },
            onQuestionAndAnswer = {
                pendingEntryShare = null
                archiveShareCoordinator.shareEntry(
                    mode = EntryShareMode.QUESTION_AND_ANSWER,
                    questionText = request.questionText,
                    answerText = request.answerText,
                )
            },
        )
    }
    val requestArchiveShare: (ExportSelection) -> Unit = { selection ->
        pendingShareSelection = selection
    }
    val requestEntryShare: (String, String) -> Unit = { questionText, answerText ->
        pendingEntryShare = EntryShareRequest(
            questionText = questionText,
            answerText = answerText,
        )
    }
    // 07.08.2026 Stage 21 Share cursor by Me4Hik END
    val saveConfirmationMessage = stringResource(R.string.answer_saved_confirmation)
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - snackbar action label
    val viewHistoryActionLabel = stringResource(R.string.answer_view_history_action)
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
    val settingsPracticePausedMessage = stringResource(R.string.settings_practice_paused)
    val settingsPracticeResumedMessage = stringResource(R.string.settings_practice_resumed)
    val settingsScheduleSaveFailedMessage = stringResource(R.string.settings_schedule_save_error)
    val settingsSoundChangeFailedMessage = stringResource(R.string.settings_sound_error)
    val settingsDeferChangeFailedMessage = stringResource(R.string.settings_defer_error)
    val settingsPauseChangeFailedMessage = stringResource(R.string.settings_pause_error)

    LaunchedEffect(pendingSaveConfirmation) {
        when (val pending = pendingSaveConfirmation) {
            null -> return@LaunchedEffect
            PendingSaveConfirmation.ConfirmationOnly -> {
                snackbarHostState.showPracticeInfoSnackbar(saveConfirmationMessage)
                pendingSaveConfirmation = null
            }
            // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - history offer snackbar action
            is PendingSaveConfirmation.WithHistoryOffer -> {
                val result = snackbarHostState.showPracticeActionSnackbar(
                    message = saveConfirmationMessage,
                    actionLabel = viewHistoryActionLabel,
                )
                pendingSaveConfirmation = null
                if (result == SnackbarResult.ActionPerformed) {
                    navController.navigate(Routes.archiveQuestion(pending.questionId)) {
                        launchSingleTop = true
                    }
                }
            }
            // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
        }
    }

    LaunchedEffect(pendingSettingsSnackbarMessage) {
        val message = pendingSettingsSnackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showPracticeInfoSnackbar(message)
        pendingSettingsSnackbarMessage = null
    }

    LaunchedEffect(pendingDeferSnackbarMessage) {
        val message = pendingDeferSnackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showPracticeInfoSnackbar(message)
        pendingDeferSnackbarMessage = null
    }

    LaunchedEffect(runtime.notificationOpenRequestStore, navController) {
        runtime.notificationOpenRequestStore.pendingOpen.collectLatest { request ->
            val openRequest = request ?: return@collectLatest
            // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - main-thread navigation from IO tap publish
            withContext(Dispatchers.Main.immediate) {
                navController.navigate(Routes.question(openRequest.occurrenceId)) {
                    launchSingleTop = true
                }
                runtime.notificationOpenRequestStore.consume()
            }
            // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
        }
    }

    LaunchedEffect(isPracticeStarted, navController) {
        if (!isPracticeStarted) {
            return@LaunchedEffect
        }
        val entry = navController.currentBackStackEntryFlow
            .first { it.destination.route != null }
        if (RootNavigationPolicy.shouldNavigateOnboardingToHome(entry.destination.route, isPracticeStarted)) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        // 09.08.2026 Post-release fixes cursor by Me4Hik START - dark Snackbar host
        snackbarHost = {
            Box(modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SNACKBAR)) {
                PracticeSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.testTag(PracticeTestTags.ANSWER_SAVED_SNACKBAR),
                )
            }
        },
        // 09.08.2026 Post-release fixes cursor by Me4Hik END
    ) { innerPadding ->
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.1 restore overlay host
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
            composable(Routes.ONBOARDING) {
                when (val notStarted = uiState) {
                    is PracticeUiState.NotStarted -> {
                        OnboardingScreen(
                            state = notStarted,
                            onSlotTimeChange = viewModel::onSlotTimeChanged,
                            onStartPractice = viewModel::onStartPracticeClicked,
                            onRestoreBackup = restoreViewModel::onOpenRestore,
                        )
                    }
                    is PracticeUiState.Started -> {
                        PracticeLoadingScreen()
                    }
                    else -> {
                        PracticeLoadingScreen()
                    }
                }
            }
            composable(Routes.HOME) {
                val started = uiState as? PracticeUiState.Started
                if (started != null) {
                    HomeScreen(
                        content = started.content,
                        notificationCard = started.notificationCard,
                        exactAlarmCard = started.exactAlarmCard,
                        onNotificationCardAction = viewModel::onNotificationCardActionClicked,
                        onExactAlarmCardAction = viewModel::onExactAlarmCardActionClicked,
                        onOpenQuestion = { occurrenceId ->
                            // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - navigate before IO mutation sync
                            navController.navigate(Routes.question(occurrenceId))
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    runtime.cycleRepository.markOccurrenceOpened(occurrenceId)
                                    runtime.notificationSyncRequester.requestSync(
                                        NotificationSyncReason.MUTATION,
                                    )
                                }
                            }
                            // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
                        },
                        onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                } else {
                    PracticeLoadingScreen()
                }
            }
            composable(
                route = Routes.QUESTION_PATTERN,
                arguments = listOf(
                    navArgument(Routes.QUESTION_ARGUMENT) { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val occurrenceId = backStackEntry.arguments?.getLong(Routes.QUESTION_ARGUMENT)
                if (occurrenceId == null || occurrenceId <= 0L) {
                    QuestionScreen(
                        uiState = QuestionUiState.Blocked(null, QuestionBlockedReason.NOT_FOUND),
                        commandState = QuestionCommandState(),
                        onAnswer = {},
                        onDefer = {},
                        onSkip = {},
                        onBackHome = {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onRetry = {},
                    )
                } else {
                    val factory = remember(occurrenceId, runtime) {
                        QuestionViewModelFactory(runtime, occurrenceId)
                    }
                    val questionViewModel: QuestionViewModel = viewModel(factory = factory)
                    val questionUiState by questionViewModel.uiState.collectAsStateWithLifecycle()
                    val commandState by questionViewModel.commandUiState.collectAsStateWithLifecycle()

                    LaunchedEffect(questionViewModel) {
                        questionViewModel.navigation.collect { event ->
                            when (event) {
                                is QuestionNavigationEvent.OpenAnswer -> {
                                    navController.navigate(Routes.answer(event.occurrenceId))
                                }
                                QuestionNavigationEvent.ReturnHomeAfterSkip -> {
                                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                is QuestionNavigationEvent.ReturnHomeAfterDefer -> {
                                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    pendingDeferSnackbarMessage = context.getString(
                                        R.string.question_deferred_for_minutes,
                                        event.durationMinutes,
                                    )
                                }
                            }
                        }
                    }

                    QuestionScreen(
                        uiState = questionUiState,
                        commandState = commandState,
                        onAnswer = questionViewModel::onAnswerClicked,
                        onDefer = questionViewModel::onDeferClicked,
                        onSkip = questionViewModel::onSkipClicked,
                        onBackHome = {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onRetry = questionViewModel::retryRead,
                    )
                }
            }
            composable(
                route = Routes.ANSWER_PATTERN,
                arguments = listOf(
                    navArgument(Routes.QUESTION_ARGUMENT) { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val occurrenceId = backStackEntry.arguments?.getLong(Routes.QUESTION_ARGUMENT)
                if (occurrenceId == null || occurrenceId <= 0L) {
                    AnswerScreen(
                        uiState = AnswerUiState.Blocked(
                            questionText = null,
                            reason = AnswerBlockedReason.NOT_FOUND,
                        ),
                        onDraftChanged = {},
                        onSave = {},
                        onBack = { navController.popBackStack() },
                        onBackHome = {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onRetry = {},
                    )
                } else {
                    val factory = remember(occurrenceId, runtime) {
                        AnswerViewModelFactory(
                            owner = backStackEntry,
                            runtime = runtime,
                            occurrenceId = occurrenceId,
                        )
                    }
                    val answerViewModel: AnswerViewModel = viewModel(factory = factory)
                    val answerUiState by answerViewModel.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(answerViewModel) {
                        answerViewModel.navigation.collect { event ->
                            when (event) {
                                is AnswerNavigationEvent.ReturnHomeAfterSave -> {
                                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Routes.HOME) {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = false
                                            }
                                        }
                                    }
                                    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - post-save snackbar state
                                    pendingSaveConfirmation = event.historyOfferQuestionId?.let { questionId ->
                                        PendingSaveConfirmation.WithHistoryOffer(questionId)
                                    } ?: PendingSaveConfirmation.ConfirmationOnly
                                    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
                                }
                                AnswerNavigationEvent.ReturnHomeAfterStale -> {
                                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Routes.HOME) {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnswerScreen(
                        uiState = answerUiState,
                        onDraftChanged = answerViewModel::onDraftChanged,
                        onSave = answerViewModel::saveAnswer,
                        onBack = { navController.popBackStack() },
                        onBackHome = {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onRetry = answerViewModel::retryRead,
                    )
                }
            }
            composable(Routes.ARCHIVE) { backStackEntry ->
                val archiveDatesViewModel: ArchiveDatesViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = ArchiveViewModelFactory.dates(backStackEntry, runtime),
                )
                val archiveDatesUiState by archiveDatesViewModel.uiState.collectAsStateWithLifecycle()
                ArchiveDatesScreen(
                    uiState = archiveDatesUiState,
                    onDateSelected = { epochDay ->
                        navController.navigate(Routes.archiveDay(epochDay)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenQuestions = {
                        navController.navigate(Routes.ARCHIVE_QUESTIONS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenInsights = {
                        navController.navigate(Routes.ARCHIVE_INSIGHTS) {
                            launchSingleTop = true
                        }
                    },
                    onExportAll = {
                        requestArchiveExport(ExportSelection.All)
                    },
                    onExportPeriod = {
                        showPeriodExportDialog = true
                    },
                    onShareAll = {
                        requestArchiveShare(ExportSelection.All)
                    },
                    onSharePeriod = {
                        showPeriodShareDialog = true
                    },
                    onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
            // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - archive questions routes
            composable(Routes.ARCHIVE_QUESTIONS) { backStackEntry ->
                val archiveQuestionsViewModel: ArchiveQuestionsViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = ArchiveViewModelFactory.questions(backStackEntry, runtime),
                )
                val archiveQuestionsUiState by archiveQuestionsViewModel.uiState.collectAsStateWithLifecycle()
                ArchiveQuestionsScreen(
                    uiState = archiveQuestionsUiState,
                    onQuestionSelected = { questionId ->
                        navController.navigate(Routes.archiveQuestion(questionId)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ARCHIVE_INSIGHTS) { backStackEntry ->
                val analyticsInsightsViewModel: AnalyticsInsightsViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = AnalyticsInsightsViewModelFactory(runtime),
                )
                val analyticsInsightsUiState by analyticsInsightsViewModel.uiState
                    .collectAsStateWithLifecycle()
                AnalyticsInsightsScreen(
                    uiState = analyticsInsightsUiState,
                    onRetry = analyticsInsightsViewModel::refresh,
                    onBack = { navController.popBackStack() },
                    onWeekdayMissedClick = { dayOfWeek ->
                        navController.navigate(Routes.insightsMissedWeekday(dayOfWeek)) {
                            launchSingleTop = true
                        }
                    },
                    onTopMissedQuestionClick = { questionId ->
                        navController.navigate(Routes.insightsMissedQuestion(questionId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Routes.ARCHIVE_INSIGHTS_MISSED_WEEKDAY_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARCHIVE_INSIGHTS_MISSED_WEEKDAY_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val dayName = backStackEntry.arguments
                    ?.getString(Routes.ARCHIVE_INSIGHTS_MISSED_WEEKDAY_ARGUMENT)
                val dayOfWeek = dayName?.let(Routes::parseInsightsMissedWeekday)
                if (dayOfWeek == null) {
                    LaunchedEffect(dayName) {
                        navController.popBackStack()
                    }
                } else {
                    val filter = MissedDetailFilter.ByWeekday(dayOfWeek)
                    val detailViewModel: MissedOccurrencesDetailViewModel = viewModel(
                        viewModelStoreOwner = backStackEntry,
                        factory = MissedOccurrencesDetailViewModelFactory(runtime, filter),
                    )
                    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
                    MissedOccurrencesDetailScreen(
                        uiState = detailUiState,
                        onRetry = detailViewModel::refresh,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = Routes.ARCHIVE_INSIGHTS_MISSED_QUESTION_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARCHIVE_INSIGHTS_MISSED_QUESTION_ARGUMENT) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val questionId = backStackEntry.arguments
                    ?.getInt(Routes.ARCHIVE_INSIGHTS_MISSED_QUESTION_ARGUMENT)
                    ?: return@composable
                val filter = MissedDetailFilter.ByQuestion(questionId)
                val detailViewModel: MissedOccurrencesDetailViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = MissedOccurrencesDetailViewModelFactory(runtime, filter),
                )
                val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
                MissedOccurrencesDetailScreen(
                    uiState = detailUiState,
                    onRetry = detailViewModel::refresh,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ARCHIVE_QUESTION_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARCHIVE_QUESTION_ARGUMENT) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val questionId = backStackEntry.arguments?.getInt(Routes.ARCHIVE_QUESTION_ARGUMENT)
                if (questionId == null || questionId <= 0) {
                    ArchiveQuestionHistoryScreen(
                        uiState = ArchiveQuestionHistoryUiState.Error(
                            message = "Invalid archive question route",
                        ),
                        deleteUiState = ArchiveDeleteUiState(),
                        onExportHistory = {},
                        onShareHistory = {},
                        onBack = { navController.popBackStack() },
                        onRequestDelete = {},
                        onRequestShare = { _, _ -> },
                        onCancelDelete = {},
                        onConfirmDelete = {},
                    )
                } else {
                    val archiveQuestionHistoryViewModel: ArchiveQuestionHistoryViewModel = viewModel(
                        viewModelStoreOwner = backStackEntry,
                        factory = ArchiveViewModelFactory.questionHistory(
                            backStackEntry,
                            runtime,
                            questionId,
                        ),
                    )
                    val archiveQuestionHistoryUiState by archiveQuestionHistoryViewModel.uiState
                        .collectAsStateWithLifecycle()
                    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - delete wiring question history
                    val archiveDeleteUiState by archiveQuestionHistoryViewModel.deleteUiState
                        .collectAsStateWithLifecycle()
                    ArchiveQuestionHistoryScreen(
                        uiState = archiveQuestionHistoryUiState,
                        deleteUiState = archiveDeleteUiState,
                        onExportHistory = {
                            requestArchiveExport(ExportSelection.Question(questionId))
                        },
                        onShareHistory = {
                            requestArchiveShare(ExportSelection.Question(questionId))
                        },
                        onBack = { navController.popBackStack() },
                        onRequestDelete = archiveQuestionHistoryViewModel::requestDelete,
                        onRequestShare = requestEntryShare,
                        onCancelDelete = archiveQuestionHistoryViewModel::cancelDelete,
                        onConfirmDelete = archiveQuestionHistoryViewModel::confirmDelete,
                    )
                    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
                }
            }
            // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
            composable(
                route = Routes.ARCHIVE_DAY_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARCHIVE_DAY_ARGUMENT) {
                        type = NavType.LongType
                    },
                ),
            ) { backStackEntry ->
                val epochDay = backStackEntry.arguments?.getLong(Routes.ARCHIVE_DAY_ARGUMENT)
                if (epochDay == null) {
                    ArchiveDayScreen(
                        uiState = ArchiveDayUiState.Error(message = "Invalid archive day route"),
                        deleteUiState = ArchiveDeleteUiState(),
                        onExportDay = {},
                        onShareDay = {},
                        onBack = { navController.popBackStack() },
                        onRequestDelete = {},
                        onRequestShare = { _, _ -> },
                        onCancelDelete = {},
                        onConfirmDelete = {},
                    )
                } else {
                    val archiveDayViewModel: ArchiveDayViewModel = viewModel(
                        viewModelStoreOwner = backStackEntry,
                        factory = ArchiveViewModelFactory.day(backStackEntry, runtime, epochDay),
                    )
                    val archiveDayUiState by archiveDayViewModel.uiState.collectAsStateWithLifecycle()
                    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - delete wiring day archive
                    val archiveDeleteUiState by archiveDayViewModel.deleteUiState.collectAsStateWithLifecycle()
                    ArchiveDayScreen(
                        uiState = archiveDayUiState,
                        deleteUiState = archiveDeleteUiState,
                        onExportDay = {
                            requestArchiveExport(ExportSelection.Day(epochDay))
                        },
                        onShareDay = {
                            requestArchiveShare(ExportSelection.Day(epochDay))
                        },
                        onBack = { navController.popBackStack() },
                        onRequestDelete = archiveDayViewModel::requestDelete,
                        onRequestShare = requestEntryShare,
                        onCancelDelete = archiveDayViewModel::cancelDelete,
                        onConfirmDelete = archiveDayViewModel::confirmDelete,
                    )
                    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
                }
            }
            composable(Routes.QUESTION_HISTORY) {
                QuestionHistoryScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) { backStackEntry ->
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = SettingsViewModelFactory(
                        owner = backStackEntry,
                        runtime = runtime,
                        diagnosticReportSubmitter = diagnosticReportSubmitter,
                    ),
                )
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val bugReportState by settingsViewModel.bugReportState.collectAsStateWithLifecycle()
                var showDirtyDialog by remember { mutableStateOf(false) }
                var showBugReportDialog by remember { mutableStateOf(false) }

                LaunchedEffect(settingsViewModel) {
                    settingsViewModel.snackbar.collect { event ->
                        pendingSettingsSnackbarMessage = when (event) {
                            SettingsSnackbarEvent.PracticePaused -> settingsPracticePausedMessage
                            SettingsSnackbarEvent.PracticeResumed -> settingsPracticeResumedMessage
                            SettingsSnackbarEvent.ScheduleSaveFailed -> settingsScheduleSaveFailedMessage
                            SettingsSnackbarEvent.SoundChangeFailed -> settingsSoundChangeFailedMessage
                            SettingsSnackbarEvent.DeferDurationChangeFailed -> settingsDeferChangeFailedMessage
                            SettingsSnackbarEvent.PauseStateChangeFailed -> settingsPauseChangeFailedMessage
                            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
                            is SettingsSnackbarEvent.BackupMessage ->
                                context.getString(event.messageResId)
                            // 10.08.2026 Post-release fixes cursor by Me4Hik END
                        }
                    }
                }

                LaunchedEffect(settingsViewModel) {
                    settingsViewModel.navigation.collect { event ->
                        when (event) {
                            SettingsNavigationEvent.ConfirmDiscardChanges -> {
                                showDirtyDialog = true
                            }
                            SettingsNavigationEvent.NavigateBackClean -> {
                                showDirtyDialog = false
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                            is SettingsNavigationEvent.OpenNotificationSettings -> {
                                context.startActivity(event.intent)
                            }
                        }
                    }
                }

                // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
                SettingsBackupEffects(viewModel = settingsViewModel)
                // 10.08.2026 Post-release fixes cursor by Me4Hik END

                SettingsScreen(
                    uiState = settingsUiState,
                    onSlotTimeChange = settingsViewModel::onSlotTimeChanged,
                    onSoundEnabledChanged = settingsViewModel::onSoundEnabledChanged,
                    onDeferDurationMinutesChanged = settingsViewModel::onDeferDurationMinutesChanged,
                    onOpenNotificationSettings = settingsViewModel::onNotificationSettingsClicked,
                    onOpenSoundLibrary = { navController.navigate(Routes.SOUND_LIBRARY) },
                    onTogglePauseState = settingsViewModel::togglePauseState,
                    onBack = settingsViewModel::onBackRequested,
                    onStayOnDirtyBack = { showDirtyDialog = false },
                    onDiscardChanges = {
                        showDirtyDialog = false
                        settingsViewModel.confirmDiscardChanges()
                    },
                    showDirtyDialog = showDirtyDialog,
                    onOpenBugReport = { showBugReportDialog = true },
                    showBugReportDialog = showBugReportDialog,
                    bugReportState = bugReportState,
                    onDismissBugReport = {
                        showBugReportDialog = false
                        settingsViewModel.resetBugReportState()
                    },
                    onSubmitBugReport = settingsViewModel::submitBugReport,
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
                    onBackupSetup = settingsViewModel::onBackupSetupRequested,
                    onBackupNow = settingsViewModel::onBackupNowRequested,
                    onBackupChangeFolder = settingsViewModel::onBackupReplacementRequested,
                    onBackupReconnect = settingsViewModel::onBackupReconnectRequested,
                    onBackupDisable = settingsViewModel::onBackupDisableRequested,
                    onBackupDisclosureConfirm = settingsViewModel::onBackupDisclosureConfirmed,
                    onBackupDisclosureCancel = settingsViewModel::onBackupDisclosureCancelled,
                    onBackupCandidateConfirm = settingsViewModel::onBackupCandidateConfirmed,
                    onBackupCandidateCancel = settingsViewModel::onBackupCandidateCancelled,
                    onBackupChooseAnother = settingsViewModel::onBackupChooseAnotherFolder,
                    onBackupCommitRetry = settingsViewModel::onBackupCommitRetry,
                    onBackupDisableConfirm = settingsViewModel::onBackupDisableConfirmed,
                    onBackupDisableCancel = settingsViewModel::onBackupDisableCancelled,
                    onBackupReconnectDifferentUseAsNew = settingsViewModel::onBackupReconnectDifferentUseAsNew,
                    onBackupReconnectDifferentCancel = settingsViewModel::onBackupReconnectDifferentCancelled,
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END
                )
            }
            composable(Routes.SOUND_LIBRARY) { backStackEntry ->
                val appContext = LocalContext.current.applicationContext as android.app.Application
                val soundLibraryViewModel: SoundLibraryViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = SoundLibraryViewModelFactory(
                        application = appContext,
                        runtime = runtime,
                    ),
                )
                val soundLibraryUiState by soundLibraryViewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val previewUnavailable = stringResource(R.string.sound_library_preview_unavailable)
                val genericError = stringResource(R.string.sound_library_error_generic)
                LaunchedEffect(soundLibraryUiState.messageResId) {
                    val resId = soundLibraryUiState.messageResId ?: return@LaunchedEffect
                    val message = when (resId) {
                        R.string.sound_library_preview_unavailable -> previewUnavailable
                        else -> genericError
                    }
                    snackbarHostState.showSnackbar(message)
                    soundLibraryViewModel.consumeMessage()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    SoundLibraryScreen(
                        uiState = soundLibraryUiState,
                        onSelect = soundLibraryViewModel::select,
                        onTogglePreview = soundLibraryViewModel::togglePreview,
                        onHide = soundLibraryViewModel::hide,
                        onRestoreAllHidden = soundLibraryViewModel::restoreAllHidden,
                        onStopPreview = soundLibraryViewModel::stopPreview,
                        onBack = { navController.popBackStack() },
                    )
                    PracticeSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        ProductionRestoreSessionHost(
            viewModel = restoreViewModel,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik END
// 05.08.2026 Question And Skip cursor by Me4Hik END
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
