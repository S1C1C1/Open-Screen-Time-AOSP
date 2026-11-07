package org.openscreentime.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.SettingsRepository
import org.openscreentime.admin.DeviceOwner
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.SelfProtection
import org.openscreentime.allowed.AllowedApp
import org.openscreentime.allowed.AlwaysAllowedPolicy
import org.openscreentime.allowed.AlwaysAllowedRepository
import org.openscreentime.blocking.AppBlocker
import org.openscreentime.blocking.AppOverride
import org.openscreentime.blocking.PreviewOnlyAppBlocker
import org.openscreentime.monitor.MonitorService
import org.openscreentime.pin.DeviceClock
import org.openscreentime.restrictions.Restriction
import org.openscreentime.restrictions.RestrictionApplier
import org.openscreentime.restrictions.RestrictionRules
import org.openscreentime.restrictions.RestrictionsRepository
import org.openscreentime.restrictions.RestrictionsState
import org.openscreentime.ui.restrictions.RestrictionsUi
import org.openscreentime.blocking.BlockDecision
import org.openscreentime.blocking.BlockReason
import org.openscreentime.blocking.BlockRules
import org.openscreentime.blocking.LimitMode
import org.openscreentime.blocking.OverrideRepository
import org.openscreentime.blocking.UnlockDuration
import org.openscreentime.downtime.DowntimeRepository
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.ui.block.BlockScreenUi
import org.openscreentime.ui.allowed.AllowedAppUi
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.GroupRepository
import org.openscreentime.groups.GroupRules
import org.openscreentime.groups.GroupSuggestion
import org.openscreentime.groups.GroupSuggestions
import org.openscreentime.limits.AppLimit
import org.openscreentime.ui.groups.GroupDraft
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.SchedulePreset
import org.openscreentime.schedules.ScheduleRepository
import org.openscreentime.schedules.ScheduleRules
import org.openscreentime.ui.schedules.ScheduleDraft
import org.openscreentime.limits.LimitRepository
import org.openscreentime.pin.PinManager
import org.openscreentime.ui.limits.LimitedApp
import org.openscreentime.usage.InstalledApp
import org.openscreentime.usage.InstalledApps
import org.openscreentime.ui.pin.PinFlowController
import org.openscreentime.ui.pin.PinFlowKind
import org.openscreentime.ui.dashboard.AppDetail
import org.openscreentime.ui.dashboard.buildAppDetail
import org.openscreentime.ui.dashboard.buildDashboard
import org.openscreentime.ui.dashboard.weekContaining
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.UsageAccessChecker
import org.openscreentime.usage.UsageRepository
import java.time.Clock
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Holds the app's state and handles what the user taps. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val usageAccessChecker: UsageAccessChecker,
    private val usageRepository: UsageRepository,
    private val appLabels: AppLabels,
    private val pinManager: PinManager,
    private val limitRepository: LimitRepository,
    private val installedApps: InstalledApps,
    private val downtimeRepository: DowntimeRepository,
    private val alwaysAllowedRepository: AlwaysAllowedRepository,
    private val alwaysAllowedPolicy: AlwaysAllowedPolicy,
    private val overrideRepository: OverrideRepository,
    private val deviceOwner: DeviceOwner,
    private val groupRepository: GroupRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appBlocker: AppBlocker = PreviewOnlyAppBlocker(),
    /** Asks the background service to re-check blocking now, e.g. after extra time is given. */
    private val requestEnforcementCheck: () -> Unit = {},
    private val restrictionsRepository: RestrictionsRepository? = null,
    private val restrictionApplier: RestrictionApplier = RestrictionApplier {},
    private val deviceClock: DeviceClock? = null,
    private val selfProtection: SelfProtection? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** First day of the week, from the phone's region (Sunday in the US, Monday in most of Europe). */
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    /** For slower lookups like the installed app list. Tests pass one that runs straight away. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** The PIN screens. */
    val pinFlow = PinFlowController(pinManager)

    /**
     * True after the PIN has been entered, until the app is left. While true,
     * protected settings can be changed without asking again.
     */
    private val settingsUnlocked = MutableStateFlow(false)

    private val hasUsageAccess = MutableStateFlow(usageAccessChecker.hasUsageAccess())
    private val usageAccessGuideOpen = MutableStateFlow(false)
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The moment the downtime status is worked out for; refreshed when the app is opened. */
    private val now = MutableStateFlow(clock.instant())

    /** Days of the week in the order the phone's region uses. */
    val daysInOrder: List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

    /** The downtime schedule and whether it's on right now. */
    val downtime: StateFlow<DowntimeUi> = combine(downtimeRepository.schedule, now) { schedule, instant ->
        DowntimeUi(schedule, schedule.status(instant, clock.zone), instant.atZone(clock.zone).toLocalDate())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DowntimeUi())

    /** The day picked in the week chart; null means today. */
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Saved usage for every day of the current week. */
    private val weekUsage = today.flatMapLatest { day ->
        val week = weekContaining(day, firstDayOfWeek)
        usageRepository.usageForDays(week.first(), week.last())
    }

    /** Saved app groups, sorted by name. */
    val groups: StateFlow<List<AppGroup>> = groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saved schedules, sorted by name. */
    val schedules: StateFlow<List<AppSchedule>> = scheduleRepository.schedules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dashboard = combine(weekUsage, today, selectedDay, limitRepository.limits, groups) { usage, day, selected, limits, groups ->
        // Apps limited by a group get the same hourglass as apps with their own limit.
        val limited = limits.map { it.packageName }.toSet() +
            groups.filter { it.limit != null }.flatMap { it.packages }
        buildDashboard(usage, day, selected, firstDayOfWeek, appLabels, limited)
    }

    /** The always allowed list, with names. */
    val alwaysAllowed: StateFlow<List<AllowedAppUi>> = alwaysAllowedRepository.config
        .map { config ->
            withContext(ioDispatcher) { alwaysAllowedPolicy.listed(config) }
                .map { AllowedAppUi(it, appLabels.labelFor(it.packageName)) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps with limits, with their names, sorted by name. */
    val limitedApps: StateFlow<List<LimitedApp>> = limitRepository.limits
        .map { limits ->
            limits.map { LimitedApp(it, appLabels.labelFor(it.packageName)) }.sortedBy { it.label.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<AppUiState> = combine(
        combine(settingsRepository.settings, hasUsageAccess, usageAccessGuideOpen, ::Triple),
        dashboard,
        pinManager.isPinSet,
        combine(limitedApps, alwaysAllowed, groups, schedules) { limited, allowed, groups, schedules ->
            listOf(limited.size, allowed.size, groups.size, groups.count { it.limit != null }, schedules.count { it.enabled })
        },
    ) { (settings, hasAccess, guideOpen), dashboard, pinSet, counts ->
        AppUiState(
            settings, hasAccess, guideOpen, dashboard, pinSet,
            limitCount = counts[0],
            alwaysAllowedCount = counts[1],
            groupCount = counts[2],
            groupLimitCount = counts[3],
            scheduleOnCount = counts[4],
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /**
     * Called whenever the app comes to the front: re-checks usage access
     * (the user changes it in Settings, outside this app) and saves the
     * latest usage.
     */
    fun onAppVisible() {
        // Coming back to the app starts again from today.
        selectedDay.value = null
        now.value = clock.instant()
        // Device-owner status can change from a computer while the app is in the background.
        enforcement.value = deviceOwner.mode()
        viewModelScope.launch { applyRestrictions() }
        refreshUsageAccess()
        syncUsage()
    }

    fun refreshUsageAccess() {
        val granted = usageAccessChecker.hasUsageAccess()
        hasUsageAccess.value = granted
        if (granted) usageAccessGuideOpen.value = false
    }

    fun onWelcomeTurnOn() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = true) }
        openGuideIfNeeded()
        syncUsage()
    }

    fun onWelcomeNotNow() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = false) }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        if (enabled) {
            // Turning screen time on only adds protection, so it never needs the PIN.
            viewModelScope.launch { settingsRepository.setTrackingEnabled(true) }
            openGuideIfNeeded()
            syncUsage()
        } else {
            withPin { settingsRepository.setTrackingEnabled(false) }
        }
    }

    fun setHistoryLength(length: HistoryLength) = withPin {
        settingsRepository.setHistoryLength(length)
        // Apply a shorter setting right away rather than at the next background run.
        usageRepository.deleteOldHistory(length)
    }

    /** Opens PIN setup, or PIN change if a PIN exists (which asks for the current PIN). */
    fun onPinSettingClick() {
        viewModelScope.launch {
            val kind = if (pinManager.isPinSet.first()) PinFlowKind.Change else PinFlowKind.Setup
            pinFlow.start(kind)
        }
    }

    fun submitPin() {
        viewModelScope.launch { pinFlow.submit() }
    }

    /**
     * Called when the app goes to the background: settings lock again, and
     * any PIN screen closes and forgets what was typed.
     */
    fun onAppHidden() {
        settingsUnlocked.value = false
        pinFlow.cancel()
        blockScreen.value = null
    }

    /**
     * Runs [change] now if no PIN is set or settings are unlocked; otherwise
     * asks for the PIN first and runs it only after the correct PIN.
     */
    private fun withPin(change: suspend () -> Unit) {
        viewModelScope.launch {
            if (settingsUnlocked.value || !pinManager.isPinSet.first()) {
                change()
            } else {
                pinFlow.start(PinFlowKind.Verify) {
                    settingsUnlocked.value = true
                    viewModelScope.launch { change() }
                }
            }
        }
    }

    /** Shows [day] in the dashboard. Days after today are ignored. */
    fun selectDay(day: LocalDate) {
        if (!day.isAfter(today.value)) selectedDay.value = day
    }

    /** One app's usage this week, for its detail screen. */
    fun appDetail(packageName: String): Flow<AppDetail> =
        combine(weekUsage, today, limitRepository.limitFor(packageName)) { usage, day, limit ->
            buildAppDetail(packageName, usage, day, firstDayOfWeek, appLabels, limit)
        }

    /** One app's saved limit, for the limit editor. */
    fun limitFor(packageName: String): Flow<AppLimit?> = limitRepository.limitFor(packageName)

    fun labelFor(packageName: String): String = appLabels.labelFor(packageName)

    /** Apps a new limit can be added for: launchable apps that don't have one yet. */
    fun appsWithoutLimit(): Flow<List<InstalledApp>> = limitRepository.limits.map { limits ->
        val limited = limits.map { it.packageName }.toSet()
        withContext(ioDispatcher) { installedApps.launchableApps() }.filter { it.packageName !in limited }
    }

    /**
     * Saves [limit] for [packageName], or removes the limit if it's null.
     * Asks for the PIN first when one is set. [onDone] runs after saving.
     */
    fun saveLimit(packageName: String, limit: AppLimit?, onDone: () -> Unit) = withPin {
        if (limit == null) limitRepository.remove(packageName) else limitRepository.save(limit)
        onDone()
    }

    /** Removes an app from the always allowed list, asking for the PIN first when one is set. */
    fun removeAlwaysAllowed(app: AllowedApp) = withPin {
        val config = alwaysAllowedRepository.config.first()
        alwaysAllowedRepository.save(alwaysAllowedPolicy.remove(app, config))
    }

    /** Adds an app to the always allowed list, asking for the PIN first when one is set. */
    fun addAlwaysAllowed(packageName: String, onDone: () -> Unit) = withPin {
        val config = alwaysAllowedRepository.config.first()
        alwaysAllowedRepository.save(alwaysAllowedPolicy.add(packageName, config))
        onDone()
    }

    /** Apps that can be added: launchable apps not already always allowed. */
    fun appsNotAlwaysAllowed(): Flow<List<InstalledApp>> = alwaysAllowed.map { allowed ->
        val listed = allowed.map { it.app.packageName }.toSet()
        withContext(ioDispatcher) { installedApps.launchableApps() }.filter { it.packageName !in listed }
    }

    /** Whether [packageName] is never blocked; shown as a note in the limit editor. */
    fun isAlwaysAllowed(packageName: String): Flow<Boolean> =
        alwaysAllowedRepository.config.map { config ->
            withContext(ioDispatcher) { alwaysAllowedPolicy.isAlwaysAllowed(packageName, config) }
        }

    // --- App groups ---

    private val draft = MutableStateFlow<GroupDraft?>(null)

    /** The group being created or edited, or null when none is open. */
    val groupDraft: StateFlow<GroupDraft?> = draft

    /** Ready-made groups from app categories. */
    fun groupSuggestions(): Flow<List<GroupSuggestion>> = flow {
        emit(GroupSuggestions.from(withContext(ioDispatcher) { installedApps.launchableApps() }))
    }

    /** Apps that can be put in a group: every launchable app. */
    fun appsForGroups(): Flow<List<InstalledApp>> = flow {
        emit(withContext(ioDispatcher) { installedApps.launchableApps() })
    }

    /** Starts a new group, empty or filled in from a suggestion. */
    fun startNewGroup(name: String = "", packages: Set<String> = emptySet()) {
        draft.value = GroupDraft(name = name, packages = packages)
    }

    /** Opens a saved group for editing. */
    fun editGroup(groupId: Long) {
        draft.value = groups.value.firstOrNull { it.id == groupId }?.let(GroupDraft::from)
    }

    fun updateGroupDraft(change: (GroupDraft) -> GroupDraft) {
        draft.update { it?.let(change) }
    }

    /** Saves the open group, asking for the PIN first when one is set. */
    fun saveGroup(onDone: () -> Unit) {
        val current = draft.value ?: return
        if (!current.canSave) return
        withPin {
            groupRepository.save(current.toGroup())
            draft.value = null
            now.value = clock.instant()
            requestEnforcementCheck()
            onDone()
        }
    }

    /** Deletes the open group, asking for the PIN first when one is set. */
    fun deleteGroup(onDone: () -> Unit) {
        val current = draft.value ?: return
        withPin {
            if (!current.isNew) groupRepository.delete(current.id)
            draft.value = null
            now.value = clock.instant()
            requestEnforcementCheck()
            onDone()
        }
    }

    // --- Schedules ---

    private val scheduleDraftState = MutableStateFlow<ScheduleDraft?>(null)

    /** The schedule being created or edited, or null when none is open. */
    val scheduleDraft: StateFlow<ScheduleDraft?> = scheduleDraftState

    /** Starts a new schedule, empty or from a preset with its (translated) name. */
    fun startNewSchedule(preset: SchedulePreset? = null, name: String = "") {
        scheduleDraftState.value = if (preset == null) ScheduleDraft(name = name) else ScheduleDraft.from(preset, name)
    }

    /** Opens a saved schedule for editing. */
    fun editSchedule(scheduleId: Long) {
        scheduleDraftState.value = schedules.value.firstOrNull { it.id == scheduleId }?.let(ScheduleDraft::from)
    }

    fun updateScheduleDraft(change: (ScheduleDraft) -> ScheduleDraft) {
        scheduleDraftState.update { it?.let(change) }
    }

    /** Saves the open schedule, asking for the PIN first when one is set. */
    fun saveSchedule(onDone: () -> Unit) {
        val current = scheduleDraftState.value ?: return
        if (!current.canSave) return
        withPin {
            scheduleRepository.save(current.toSchedule())
            scheduleDraftState.value = null
            now.value = clock.instant()
            requestEnforcementCheck()
            onDone()
        }
    }

    /** Deletes the open schedule, asking for the PIN first when one is set. */
    fun deleteSchedule(onDone: () -> Unit) {
        val current = scheduleDraftState.value ?: return
        withPin {
            if (!current.isNew) scheduleRepository.delete(current.id)
            scheduleDraftState.value = null
            now.value = clock.instant()
            requestEnforcementCheck()
            onDone()
        }
    }

    // --- Blocking ---

    private val blockScreen = MutableStateFlow<BlockScreenUi?>(null)

    /** The block screen preview, or null when it's closed. */
    val blockScreenUi: StateFlow<BlockScreenUi?> = blockScreen

    private val enforcement = MutableStateFlow(deviceOwner.mode())

    init {
        // Self-protection follows the PIN: on as soon as one is set (device-owner mode).
        viewModelScope.launch {
            pinManager.isPinSet.collect { pinSet -> selfProtection?.update(pinSet) }
        }
    }

    /** Whether apps can really be blocked (device owner) or only previewed. */
    val enforcementMode: StateFlow<EnforcementMode> = enforcement

    /**
     * Gives up device-owner control so the app can be uninstalled. Needs the
     * PIN when one is set, since it switches off all blocking.
     */
    fun removeDeviceOwner() = withPin {
        // Undo everything this app set before giving up control.
        appBlocker.setBlockedApps(emptySet())
        restrictionApplier.apply(emptySet())
        selfProtection?.removeAll()
        deviceOwner.remove()
        enforcement.value = deviceOwner.mode()
    }

    /** Whether [packageName] would be blocked right now, and why. */
    fun blockDecision(packageName: String): Flow<BlockDecision> = combine(
        combine(today, now) { day, instant -> day to instant }.flatMapLatest { (day, instant) ->
            usageRepository.usageForDay(day).map { apps -> instant to apps.associate { it.packageName to it.time } }
        },
        combine(limitRepository.limitFor(packageName), groupRepository.groups, scheduleRepository.schedules, ::Triple),
        downtimeRepository.schedule,
        combine(alwaysAllowedRepository.config, overrideRepository.overrideFor(packageName), ::Pair),
        settingsRepository.settings,
    ) { (instant, usage), (limit, groups, schedules), schedule, (allowedConfig, override), settings ->
        BlockRules.decide(
            now = instant,
            zone = clock.zone,
            usedToday = usage[packageName] ?: Duration.ZERO,
            limit = limit,
            downtime = schedule,
            alwaysAllowed = withContext(ioDispatcher) { alwaysAllowedPolicy.isAlwaysAllowed(packageName, allowedConfig) },
            override = override,
            mode = settings.limitMode,
            groups = GroupRules.limitedGroupsFor(packageName, groups, usage),
            schedules = ScheduleRules.activeFor(packageName, schedules, groups, instant, clock.zone),
        )
    }

    /**
     * Opens the block screen as it would look for [packageName] (null: no
     * particular app) blocked for [reason], using the current mode and
     * what's been used today.
     */
    fun previewBlockScreen(packageName: String?, reason: BlockReason, ruleName: String? = null) =
        openBlockScreen(packageName, reason, real = false, ruleName = ruleName)

    /**
     * Opens the block screen for [packageName] blocked for [reason]. A real
     * one (device-owner mode) has no preview banner; its extra time unpauses
     * the app straight away.
     */
    fun openBlockScreen(packageName: String?, reason: BlockReason, real: Boolean, ruleName: String? = null) {
        viewModelScope.launch {
            val instant = clock.instant()
            now.value = instant
            val zone = clock.zone
            val day = instant.atZone(zone).toLocalDate()
            val pkg = packageName.orEmpty()
            val override = if (packageName == null) AppOverride() else overrideRepository.overrideFor(pkg).first()
            val mode = settingsRepository.settings.first().limitMode
            val until = when (reason) {
                BlockReason.LimitReached -> day.plusDays(1).atStartOfDay(zone)
                BlockReason.Downtime -> downtimeEnd(instant)
                BlockReason.Scheduled -> scheduleEnd(ruleName, instant) ?: day.plusDays(1).atStartOfDay(zone)
            }
            blockScreen.value = BlockScreenUi(
                packageName = pkg,
                label = packageName?.let(appLabels::labelFor),
                reason = reason,
                blockedUntil = until,
                mode = mode,
                graceAvailable = mode == LimitMode.Grace && override.graceUsedOn != day,
                isPreview = !real,
                ruleName = ruleName.takeIf { reason != BlockReason.Downtime },
            )
        }
    }

    /** When the schedule called [name] ends: now if it's on, otherwise at the end of its next run (for previews). */
    private fun scheduleEnd(name: String?, instant: java.time.Instant): java.time.ZonedDateTime? {
        val schedule = schedules.value.firstOrNull { it.name == name } ?: return null
        val zone = clock.zone
        return when (val status = schedule.copy(enabled = true).status(instant, zone)) {
            is DowntimeStatus.ActiveUntil -> status.end
            is DowntimeStatus.StartsAt -> schedule.window.on(status.start.toLocalDate(), zone).end
            DowntimeStatus.Off -> null
        }
    }

    /** When downtime ends: now if it's on, otherwise the end of the next downtime. */
    private suspend fun downtimeEnd(instant: java.time.Instant): java.time.ZonedDateTime {
        val schedule = downtimeRepository.schedule.first()
        val zone = clock.zone
        return when (val status = schedule.copy(enabled = true).status(instant, zone)) {
            is DowntimeStatus.ActiveUntil -> status.end
            is DowntimeStatus.StartsAt -> {
                val start = status.start
                val window = schedule.windowFor(start.dayOfWeek) ?: DowntimeWindow.DEFAULT
                window.on(start.toLocalDate(), zone).end
            }
            DowntimeStatus.Off -> DowntimeWindow.DEFAULT.on(instant.atZone(zone).toLocalDate(), zone).end
        }
    }

    fun closeBlockScreen() {
        blockScreen.value = null
    }

    /** Grace mode: one more minute, once per app per day. */
    fun blockOneMoreMinute() = blockAction { ui, instant ->
        overrideRepository.markGraceUsed(ui.packageName, instant.atZone(clock.zone).toLocalDate())
        overrideRepository.allowUntil(ui.packageName, instant + BlockRules.ONE_MORE_MINUTE)
    }

    /** Reminder mode: ignore for 15 minutes. */
    fun blockIgnore() = blockAction { ui, instant ->
        overrideRepository.allowUntil(ui.packageName, instant + BlockRules.REMINDER_SNOOZE)
    }

    /** Asks for the PIN, then lets the user choose how long to unlock for. Without a PIN, goes straight to the choice. */
    fun blockEnterPin() {
        viewModelScope.launch {
            val showChoice = { blockScreen.update { it?.copy(choosingUnlock = true) } }
            if (pinManager.isPinSet.first()) pinFlow.start(PinFlowKind.Verify) { showChoice() } else showChoice()
        }
    }

    fun blockUnlock(duration: UnlockDuration) = blockAction { ui, instant ->
        val blocked = BlockDecision.Blocked(ui.reason, ui.blockedUntil, ui.graceAvailable)
        overrideRepository.allowUntil(ui.packageName, BlockRules.unlockEnd(instant, duration, blocked))
    }

    /** Runs [action] for the open block screen, then closes it and refreshes "right now" statuses. */
    private fun blockAction(action: suspend (BlockScreenUi, java.time.Instant) -> Unit) {
        val ui = blockScreen.value ?: return
        viewModelScope.launch {
            val instant = clock.instant()
            // A preview without a particular app has nothing to save.
            if (ui.packageName.isNotEmpty()) action(ui, instant)
            blockScreen.value = null
            now.value = clock.instant()
            requestEnforcementCheck()
        }
    }

    fun setLimitMode(mode: LimitMode) = withPin { settingsRepository.setLimitMode(mode) }

    // --- Restrictions ---

    /** Restrictions and their lifts, for the Restrictions screen. */
    val restrictions: StateFlow<RestrictionsUi> =
        combine(restrictionsRepository?.state ?: flowOf(RestrictionsState()), now) { state, instant ->
            val clock = deviceClock
            RestrictionsUi(
                enabled = state.enabled,
                liftedUntil = if (clock == null) {
                    emptyMap()
                } else {
                    state.enabled.mapNotNull { r ->
                        RestrictionRules.liftRemaining(state.lifts[r], clock)?.let { r to instant + it }
                    }.toMap()
                },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RestrictionsUi())

    /** Turning a restriction on is free; turning it off needs the PIN when one is set. */
    fun setRestriction(restriction: Restriction, enabled: Boolean) {
        val repository = restrictionsRepository ?: return
        val change: suspend () -> Unit = {
            repository.setEnabled(restriction, enabled)
            applyRestrictions()
        }
        if (enabled) viewModelScope.launch { change() } else withPin(change)
    }

    /** Lifts a restriction for 15 minutes with the PIN. */
    fun allowRestrictionFor15Minutes(restriction: Restriction) {
        val repository = restrictionsRepository ?: return
        val clock = deviceClock ?: return
        withPin {
            repository.setLift(restriction, RestrictionRules.newLift(clock))
            applyRestrictions()
            // The background service switches it back on when the 15 minutes are up.
            requestEnforcementCheck()
        }
    }

    private suspend fun applyRestrictions() {
        val repository = restrictionsRepository ?: return
        val clock = deviceClock ?: return
        restrictionApplier.apply(RestrictionRules.active(repository.state.first(), clock))
        now.value = this.clock.instant()
    }

    /** Saves a changed downtime schedule, asking for the PIN first when one is set. */
    fun saveDowntime(schedule: DowntimeSchedule) = withPin {
        downtimeRepository.save(schedule)
        now.value = clock.instant()
    }

    /** Opens PIN setup; used by the reminders on the App limits and Downtime screens. */
    fun startPinSetup() {
        viewModelScope.launch { pinFlow.start(PinFlowKind.Setup) }
    }

    fun openUsageAccessGuide() {
        usageAccessGuideOpen.value = true
    }

    fun closeUsageAccessGuide() {
        usageAccessGuideOpen.value = false
    }

    private fun openGuideIfNeeded() {
        if (!hasUsageAccess.value) usageAccessGuideOpen.value = true
    }

    /** Saves the latest usage, if tracking is on and allowed. */
    private fun syncUsage() {
        // The date may have changed since the app was last open.
        today.value = LocalDate.now(clock)
        viewModelScope.launch {
            if (!hasUsageAccess.value) return@launch
            // Read settings after any pending save above has finished.
            if (!settingsRepository.settings.first().trackingEnabled) return@launch
            try {
                usageRepository.sync()
            } catch (e: Exception) {
                // Not fatal: the background job will try again. Never log usage data.
                Log.w(TAG, "Usage sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val container = (appContext as OpenScreenTimeApplication).container
                MainViewModel(
                    container.settingsRepository,
                    container.usageAccessChecker,
                    container.usageRepository,
                    container.appLabels,
                    container.pinManager,
                    container.limitRepository,
                    container.installedApps,
                    container.downtimeRepository,
                    container.alwaysAllowedRepository,
                    container.alwaysAllowedPolicy,
                    container.overrideRepository,
                    container.deviceOwner,
                    container.groupRepository,
                    container.scheduleRepository,
                    container.appBlocker,
                    { MonitorService.checkNow(appContext) },
                    container.restrictionsRepository,
                    container.restrictionApplier,
                    container.deviceClock,
                    container.selfProtection,
                )
            }
        }
    }
}

/** What the Downtime screen and its Settings row show. */
data class DowntimeUi(
    val schedule: DowntimeSchedule = DowntimeSchedule(),
    val status: DowntimeStatus = DowntimeStatus.Off,
    val today: LocalDate = LocalDate.MIN,
)
