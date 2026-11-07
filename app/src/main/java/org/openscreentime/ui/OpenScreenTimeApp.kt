package org.openscreentime.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.blocking.BlockDecision
import org.openscreentime.ui.admin.BlockingStatusScreen
import org.openscreentime.ui.restrictions.RestrictionsScreen
import org.openscreentime.blocking.BlockReason
import org.openscreentime.ui.allowed.AlwaysAllowedScreen
import org.openscreentime.ui.block.BlockActions
import org.openscreentime.ui.block.BlockScreen
import org.openscreentime.ui.appdetail.AppDetailScreen
import kotlinx.coroutines.flow.map
import org.openscreentime.limits.AppLimit
import org.openscreentime.ui.downtime.DowntimeScreen
import org.openscreentime.ui.format.formatActiveSchedules
import org.openscreentime.ui.format.formatDowntimeStatus
import org.openscreentime.ui.groups.GroupAppPickerScreen
import org.openscreentime.ui.groups.GroupEditorScreen
import org.openscreentime.ui.groups.GroupListScreen
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.schedules.ScheduleEditorScreen
import org.openscreentime.ui.schedules.ScheduleListScreen
import org.openscreentime.ui.schedules.ScheduleTargetPickerScreen
import org.openscreentime.ui.limits.AppPickerScreen
import org.openscreentime.ui.limits.LimitEditorScreen
import org.openscreentime.ui.limits.LimitListScreen
import org.openscreentime.ui.pin.PinFlowActions
import org.openscreentime.ui.pin.PinFlowScreen
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen

/**
 * Shows the right screen for the current state: welcome and usage access
 * setup first, then the dashboard and the screens reached from it. PIN
 * screens appear on top of whatever is showing.
 */
@Composable
fun OpenScreenTimeApp(
    viewModel: MainViewModel,
    onOpenUsageAccessSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinUi by viewModel.pinFlow.ui.collectAsStateWithLifecycle()
    val blockUi by viewModel.blockScreenUi.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (state.screen) {
            // Settings load in a moment; show a plain background meanwhile.
            AppScreen.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            AppScreen.Welcome -> WelcomeScreen(
                onTurnOn = viewModel::onWelcomeTurnOn,
                onNotNow = viewModel::onWelcomeNotNow,
            )
            AppScreen.UsageAccessGuide -> {
                BackHandler(onBack = viewModel::closeUsageAccessGuide)
                UsageAccessScreen(
                    onOpenSettings = onOpenUsageAccessSettings,
                    onBack = viewModel::closeUsageAccessGuide,
                )
            }
            AppScreen.Home -> MainNavigation(viewModel, state)
        }

        blockUi?.let { ui ->
            BackHandler(onBack = viewModel::closeBlockScreen)
            BlockScreen(
                ui = ui,
                actions = remember(viewModel) {
                    BlockActions(
                        onClose = viewModel::closeBlockScreen,
                        onOneMoreMinute = viewModel::blockOneMoreMinute,
                        onIgnore = viewModel::blockIgnore,
                        onEnterPin = viewModel::blockEnterPin,
                        onUnlock = viewModel::blockUnlock,
                    )
                },
            )
        }

        pinUi?.let { ui ->
            val flow = viewModel.pinFlow
            // Declared last, so Back closes the PIN screen before anything underneath.
            BackHandler(onBack = flow::cancel)
            PinFlowScreen(
                ui = ui,
                actions = remember(flow) {
                    PinFlowActions(
                        onDigit = flow::addDigit,
                        onDelete = flow::deleteDigit,
                        onSubmit = viewModel::submitPin,
                        onContinueIntro = flow::continueFromIntro,
                        onWaitFinished = flow::waitFinished,
                        onClose = flow::cancel,
                    )
                },
            )
        }
    }
}

/** The dashboard and the screens reached from it. Back returns to the previous one. */
@Composable
private fun MainNavigation(viewModel: MainViewModel, state: AppUiState) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            val downtime by viewModel.downtime.collectAsStateWithLifecycle()
            val enforcement by viewModel.enforcementMode.collectAsStateWithLifecycle()
            val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()
            val schedules by viewModel.schedules.collectAsStateWithLifecycle()
            val activeSchedules by viewModel.activeSchedules.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
            val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed = notificationsAllowed(context)
            }
            val resources = LocalResources.current
            val locale = LocalConfiguration.current.locales[0]
            val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
            HomeScreen(
                downtimeSummary = formatDowntimeStatus(downtime.status, downtime.today, resources, locale, use24Hour),
                onDowntimeClick = { navController.navigate(Routes.DOWNTIME) },
                alwaysAllowedCount = state.alwaysAllowedCount,
                limitMode = state.limitMode,
                blockingActive = enforcement == EnforcementMode.DeviceOwner,
                restrictionsOnCount = restrictions.enabled.size,
                onRestrictionsClick = { navController.navigate(Routes.RESTRICTIONS) },
                // Warnings only matter once there's a limit or downtime to warn about.
                showNotificationPrompt = !notificationsAllowed &&
                    (state.limitCount > 0 || state.groupLimitCount > 0 || state.scheduleOnCount > 0 || downtime.schedule.enabled),
                onAllowNotifications = { askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onBlockingClick = { navController.navigate(Routes.BLOCKING) },
                onLimitModeChange = viewModel::setLimitMode,
                onAlwaysAllowedClick = { navController.navigate(Routes.ALLOWED) },
                status = state.homeStatus,
                dashboard = state.dashboard,
                historyLength = state.historyLength,
                pinSet = state.pinSet,
                onTrackingChange = viewModel::setTrackingEnabled,
                onAllowUsageAccess = viewModel::openUsageAccessGuide,
                onHistoryLengthChange = viewModel::setHistoryLength,
                onSelectDay = viewModel::selectDay,
                onAppClick = { packageName -> navController.navigate(Routes.appDetail(packageName)) },
                onPinClick = viewModel::onPinSettingClick,
                limitCount = state.limitCount,
                onLimitsClick = { navController.navigate(Routes.LIMITS) },
                groupCount = state.groupCount,
                onGroupsClick = { navController.navigate(Routes.GROUPS) },
                scheduleCount = schedules.size,
                scheduleOnCount = state.scheduleOnCount,
                activeScheduleSummary = formatActiveSchedules(activeSchedules, resources, locale, use24Hour),
                onSchedulesClick = { navController.navigate(Routes.SCHEDULES) },
            )
        }
        composable(Routes.SCHEDULES) {
            val schedules by viewModel.schedules.collectAsStateWithLifecycle()
            ScheduleListScreen(
                schedules = schedules,
                daysInOrder = viewModel.daysInOrder,
                pinSet = state.pinSet,
                onBack = { navController.popBackStack() },
                onScheduleClick = { id ->
                    viewModel.editSchedule(id)
                    navController.navigate(Routes.SCHEDULE_EDITOR)
                },
                onNewSchedule = {
                    viewModel.startNewSchedule()
                    navController.navigate(Routes.SCHEDULE_EDITOR)
                },
                onPresetClick = { preset, name ->
                    viewModel.startNewSchedule(preset, name)
                    navController.navigate(Routes.SCHEDULE_EDITOR)
                },
                onSetPinClick = viewModel::startPinSetup,
            )
        }
        composable(Routes.SCHEDULE_EDITOR) {
            val draft by viewModel.scheduleDraft.collectAsStateWithLifecycle()
            val groups by viewModel.groups.collectAsStateWithLifecycle()
            draft?.let { current ->
                ScheduleEditorScreen(
                    draft = current,
                    daysInOrder = viewModel.daysInOrder,
                    groups = groups,
                    labelFor = viewModel::labelFor,
                    onChange = viewModel::updateScheduleDraft,
                    onChooseTargets = { navController.navigate(Routes.SCHEDULE_TARGETS) },
                    onBack = { navController.popBackStack() },
                    onSave = { viewModel.saveSchedule { navController.popBackStack() } },
                    onDelete = { viewModel.deleteSchedule { navController.popBackStack() } },
                )
            }
        }
        composable(Routes.SCHEDULE_TARGETS) {
            val draft by viewModel.scheduleDraft.collectAsStateWithLifecycle()
            val groups by viewModel.groups.collectAsStateWithLifecycle()
            val apps by remember { viewModel.appsForGroups() }.collectAsStateWithLifecycle(initialValue = emptyList())
            ScheduleTargetPickerScreen(
                groups = groups,
                apps = apps,
                selectedGroups = draft?.groupIds.orEmpty(),
                selectedApps = draft?.packages.orEmpty(),
                onToggleGroup = { id -> viewModel.updateScheduleDraft { it.toggleGroup(id) } },
                onToggleApp = { pkg -> viewModel.updateScheduleDraft { it.toggleApp(pkg) } },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.GROUPS) {
            val groups by viewModel.groups.collectAsStateWithLifecycle()
            val suggestions by remember { viewModel.groupSuggestions() }.collectAsStateWithLifecycle(initialValue = emptyList())
            GroupListScreen(
                groups = groups,
                suggestions = suggestions,
                pinSet = state.pinSet,
                onBack = { navController.popBackStack() },
                onGroupClick = { id ->
                    viewModel.editGroup(id)
                    navController.navigate(Routes.GROUP_EDITOR)
                },
                onNewGroup = {
                    viewModel.startNewGroup()
                    navController.navigate(Routes.GROUP_EDITOR)
                },
                onSuggestionClick = { suggestion ->
                    viewModel.startNewGroup(suggestion.name, suggestion.packages)
                    navController.navigate(Routes.GROUP_EDITOR)
                },
                onSetPinClick = viewModel::startPinSetup,
            )
        }
        composable(Routes.GROUP_EDITOR) {
            val draft by viewModel.groupDraft.collectAsStateWithLifecycle()
            draft?.let { current ->
                GroupEditorScreen(
                    draft = current,
                    labelFor = viewModel::labelFor,
                    onChange = viewModel::updateGroupDraft,
                    onChooseApps = { navController.navigate(Routes.GROUP_APPS) },
                    onBack = { navController.popBackStack() },
                    onSave = { viewModel.saveGroup { navController.popBackStack() } },
                    onDelete = { viewModel.deleteGroup { navController.popBackStack() } },
                )
            }
        }
        composable(Routes.GROUP_APPS) {
            val draft by viewModel.groupDraft.collectAsStateWithLifecycle()
            val apps by remember { viewModel.appsForGroups() }.collectAsStateWithLifecycle(initialValue = emptyList())
            GroupAppPickerScreen(
                apps = apps,
                selected = draft?.packages.orEmpty(),
                onToggle = { pkg -> viewModel.updateGroupDraft { it.toggle(pkg) } },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.LIMITS) {
            val limited by viewModel.limitedApps.collectAsStateWithLifecycle()
            LimitListScreen(
                apps = limited,
                pinSet = state.pinSet,
                onBack = { navController.popBackStack() },
                onAppClick = { navController.navigate(Routes.limitEditor(it)) },
                onAddClick = { navController.navigate(Routes.ADD_LIMIT) },
                onSetPinClick = viewModel::startPinSetup,
            )
        }
        composable(Routes.RESTRICTIONS) {
            val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()
            val enforcement by viewModel.enforcementMode.collectAsStateWithLifecycle()
            RestrictionsScreen(
                ui = restrictions,
                blockingActive = enforcement == EnforcementMode.DeviceOwner,
                onToggle = viewModel::setRestriction,
                onAllowFor15Minutes = viewModel::allowRestrictionFor15Minutes,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BLOCKING) {
            val enforcement by viewModel.enforcementMode.collectAsStateWithLifecycle()
            BlockingStatusScreen(
                pinSet = state.pinSet,
                mode = enforcement,
                onBack = { navController.popBackStack() },
                onRemoveDeviceOwner = viewModel::removeDeviceOwner,
            )
        }
        composable(Routes.ALLOWED) {
            val allowed by viewModel.alwaysAllowed.collectAsStateWithLifecycle()
            AlwaysAllowedScreen(
                apps = allowed,
                pinSet = state.pinSet,
                onRemove = viewModel::removeAlwaysAllowed,
                onAddClick = { navController.navigate(Routes.ADD_ALLOWED) },
                onBack = { navController.popBackStack() },
                onSetPinClick = viewModel::startPinSetup,
            )
        }
        composable(Routes.ADD_ALLOWED) {
            val apps by remember { viewModel.appsNotAlwaysAllowed() }.collectAsStateWithLifecycle(initialValue = emptyList())
            AppPickerScreen(
                apps = apps,
                onBack = { navController.popBackStack() },
                onAppClick = { packageName ->
                    viewModel.addAlwaysAllowed(packageName) { navController.popBackStack() }
                },
            )
        }
        composable(Routes.DOWNTIME) {
            val downtime by viewModel.downtime.collectAsStateWithLifecycle()
            DowntimeScreen(
                schedule = downtime.schedule,
                status = downtime.status,
                today = downtime.today,
                daysInOrder = viewModel.daysInOrder,
                pinSet = state.pinSet,
                onChange = viewModel::saveDowntime,
                onBack = { navController.popBackStack() },
                onSetPinClick = viewModel::startPinSetup,
                onPreviewBlock = {
                    viewModel.previewBlockScreen(state.dashboard?.apps?.firstOrNull()?.packageName, BlockReason.Downtime)
                },
            )
        }
        composable(Routes.ADD_LIMIT) {
            val apps by remember { viewModel.appsWithoutLimit() }.collectAsStateWithLifecycle(initialValue = emptyList())
            AppPickerScreen(
                apps = apps,
                onBack = { navController.popBackStack() },
                onAppClick = { packageName ->
                    // Replace the picker, so Back from the editor returns to the list.
                    navController.navigate(Routes.limitEditor(packageName)) {
                        popUpTo(Routes.ADD_LIMIT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.LIMIT_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_PACKAGE) { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString(Routes.ARG_PACKAGE).orEmpty()
            // Wait for the saved limit before showing the editor, so it starts from the right values.
            val current by remember(packageName) { viewModel.limitFor(packageName).map { LoadedLimit(it) } }
                .collectAsStateWithLifecycle(initialValue = null)
            current?.let { loaded ->
                val alwaysAllowed by remember(packageName) { viewModel.isAlwaysAllowed(packageName) }
                    .collectAsStateWithLifecycle(initialValue = false)
                LimitEditorScreen(
                    alwaysAllowed = alwaysAllowed,
                    packageName = packageName,
                    label = remember(packageName) { viewModel.labelFor(packageName) },
                    current = loaded.limit,
                    onBack = { navController.popBackStack() },
                    onSave = { limit ->
                        viewModel.saveLimit(packageName, limit) { navController.popBackStack() }
                    },
                )
            }
        }
        composable(
            route = Routes.APP_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_PACKAGE) { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString(Routes.ARG_PACKAGE).orEmpty()
            val detail by remember(packageName) { viewModel.appDetail(packageName) }
                .collectAsStateWithLifecycle(initialValue = null)
            val decision by remember(packageName) { viewModel.blockDecision(packageName) }
                .collectAsStateWithLifecycle(initialValue = null)
            val enforcement by viewModel.enforcementMode.collectAsStateWithLifecycle()
            AppDetailScreen(
                blockingActive = enforcement == EnforcementMode.DeviceOwner,
                onGetMoreTime = {
                    val blocked = decision as? BlockDecision.Blocked
                    viewModel.openBlockScreen(packageName, blocked?.reason ?: BlockReason.LimitReached, real = true, blocked?.ruleName)
                },
                decision = decision,
                onPreviewBlock = {
                    val blocked = decision as? BlockDecision.Blocked
                    viewModel.previewBlockScreen(packageName, blocked?.reason ?: BlockReason.LimitReached, blocked?.ruleName)
                },
                detail = detail,
                onBack = { navController.popBackStack() },
                onLimitClick = { navController.navigate(Routes.limitEditor(packageName)) },
                onGroupClick = { id ->
                    viewModel.editGroup(id)
                    navController.navigate(Routes.GROUP_EDITOR)
                },
                onScheduleClick = { id ->
                    viewModel.editSchedule(id)
                    navController.navigate(Routes.SCHEDULE_EDITOR)
                },
            )
        }
    }
}

private fun notificationsAllowed(context: Context) =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ARG_PACKAGE = "packageName"
    const val APP_DETAIL = "app/{$ARG_PACKAGE}"

    const val LIMITS = "limits"
    const val DOWNTIME = "downtime"
    const val ALLOWED = "allowed"
    const val BLOCKING = "blocking"
    const val RESTRICTIONS = "restrictions"
    const val GROUPS = "groups"
    const val GROUP_EDITOR = "groups/edit"
    const val GROUP_APPS = "groups/edit/apps"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedules/edit"
    const val SCHEDULE_TARGETS = "schedules/edit/targets"
    const val ADD_ALLOWED = "allowed/add"
    const val ADD_LIMIT = "limits/add"
    const val LIMIT_EDITOR = "limits/edit/{$ARG_PACKAGE}"

    fun appDetail(packageName: String) = "app/${Uri.encode(packageName)}"
    fun limitEditor(packageName: String) = "limits/edit/${Uri.encode(packageName)}"
}

/** Wraps a limit that may be null, to tell "loaded, no limit" apart from "still loading". */
private class LoadedLimit(val limit: AppLimit?)
