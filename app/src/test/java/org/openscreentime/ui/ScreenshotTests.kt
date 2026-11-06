package org.openscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.HistoryLength
import org.openscreentime.ui.appdetail.AppDetailScreen
import org.openscreentime.ui.dashboard.Dashboard
import org.openscreentime.ui.dashboard.buildAppDetail
import org.openscreentime.ui.home.HistoryLengthDialog
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen
import org.openscreentime.allowed.AllowedApp
import org.openscreentime.allowed.AllowedRole
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.ui.allowed.AllowedAppUi
import org.openscreentime.ui.allowed.AlwaysAllowedScreen
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.limits.AppLimit
import org.openscreentime.ui.downtime.DowntimeScreen
import org.openscreentime.ui.limits.AppPickerScreen
import org.openscreentime.ui.limits.LimitEditorScreen
import org.openscreentime.ui.limits.LimitListScreen
import org.openscreentime.ui.limits.LimitedApp
import org.openscreentime.usage.InstalledApp
import org.openscreentime.ui.pin.PinFlowActions
import org.openscreentime.ui.pin.PinFlowKind
import org.openscreentime.ui.pin.PinFlowScreen
import org.openscreentime.ui.pin.PinFlowUi
import org.openscreentime.ui.pin.PinMessage
import org.openscreentime.ui.pin.PinStep
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Screenshot tests let the UI be reviewed without a phone. The reference
 * images are in app/src/test/screenshots; see README for how to update them.
 * The dashboard is captured at full height so everything is visible.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi") // Pixel-sized phone
class ScreenshotTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            OpenScreenTimeTheme(darkTheme = dark, dynamicColor = false, content = content)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun welcome_light() = capture("welcome_light") { WelcomeScreen(onTurnOn = {}, onNotNow = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun welcome_dark() = capture("welcome_dark", dark = true) { WelcomeScreen(onTurnOn = {}, onNotNow = {}) }

    @Test
    fun usageAccess_light() = capture("usage_access_light") {
        UsageAccessScreen(onOpenSettings = {}, onBack = {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun usageAccess_dark() = capture("usage_access_dark", dark = true) {
        UsageAccessScreen(onOpenSettings = {}, onBack = {})
    }

    @Test
    fun home_trackingOff() = capture("home_tracking_off") { HomeFor(HomeStatus.TrackingOff) }

    @Test
    fun home_needsUsageAccess() = capture("home_needs_usage_access") { HomeFor(HomeStatus.NeedsUsageAccess) }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun dashboard_light() = capture("dashboard_light") { HomeFor(HomeStatus.Ready) }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-night-xxhdpi")
    fun dashboard_dark() = capture("dashboard_dark", dark = true) { HomeFor(HomeStatus.Ready) }

    @Test
    fun dashboard_earlierDaySelected() = capture("dashboard_earlier_day") {
        HomeFor(HomeStatus.Ready, SampleUsage.dashboard(selectedDay = SampleUsage.today.minusDays(1)))
    }

    @Test
    fun dashboard_noUsageYet() = capture("dashboard_no_usage") {
        HomeFor(HomeStatus.Ready, SampleUsage.dashboard(usage = emptyMap()))
    }

    @Test
    fun appDetail_light() = capture("app_detail_light") { BrowserDetail() }

    @Test
    @Config(qualifiers = "+night")
    fun appDetail_dark() = capture("app_detail_dark", dark = true) { BrowserDetail() }

    @Test
    fun pin_intro() = capture("pin_intro") { PinFor(PinFlowUi(PinFlowKind.Setup, PinStep.Intro)) }

    @Test
    fun pin_choose() = capture("pin_choose") {
        PinFor(PinFlowUi(PinFlowKind.Setup, PinStep.EnterNew, digitCount = 2))
    }

    @Test
    fun pin_confirmMismatch() = capture("pin_mismatch") {
        PinFor(PinFlowUi(PinFlowKind.Setup, PinStep.EnterNew, message = PinMessage.Mismatch))
    }

    @Test
    fun pin_verifyWrong() = capture("pin_wrong") {
        PinFor(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, digitCount = 0, message = PinMessage.WrongPin(2)))
    }

    @Test
    @Config(qualifiers = "+night")
    fun pin_verifyWaiting_dark() = capture("pin_waiting_dark", dark = true) {
        PinFor(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, waitRemaining = Duration.ofMinutes(4).plusSeconds(32)))
    }

    @Test
    fun pin_finished() = capture("pin_finished") { PinFor(PinFlowUi(PinFlowKind.Setup, PinStep.Finished)) }

    @Composable
    private fun PinFor(ui: PinFlowUi) {
        PinFlowScreen(ui, PinFlowActions({}, {}, {}, {}, {}, {}))
    }

    @Test
    fun limitEditor_new() = capture("limit_editor") {
        LimitEditorScreen("browser", "Browser", current = null, onBack = {}, onSave = {})
    }

    @Test
    @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    fun limitEditor_weekend() = capture("limit_editor_weekend") {
        LimitEditorScreen(
            "video",
            "Video",
            current = AppLimit("video", Duration.ofMinutes(90), weekend = Duration.ofHours(3)),
            onBack = {},
            onSave = {},
        )
    }

    @Test
    fun limitList_noPin() = capture("limit_list_no_pin") {
        LimitListScreen(sampleLimits, pinSet = false, onBack = {}, onAppClick = {}, onAddClick = {}, onSetPinClick = {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun limitList_dark() = capture("limit_list_dark", dark = true) {
        LimitListScreen(sampleLimits, pinSet = true, onBack = {}, onAppClick = {}, onAddClick = {}, onSetPinClick = {})
    }

    @Test
    fun appPicker() = capture("app_picker") {
        AppPickerScreen(
            apps = listOf("Calculator", "Camera", "Clock", "Maps", "Messages").map { InstalledApp(it.lowercase(), it) },
            onBack = {},
            onAppClick = {},
        )
    }

    @Test
    fun appDetail_withLimit() = capture("app_detail_limit") {
        AppDetailScreen(
            detail = buildAppDetail(
                "browser",
                SampleUsage.usageByDay,
                SampleUsage.today,
                SampleUsage.firstDayOfWeek,
                SampleUsage.labels,
                limit = AppLimit("browser", Duration.ofMinutes(90)),
            ),
            onBack = {},
        )
    }

    private val sampleLimits = listOf(
        LimitedApp(AppLimit("browser", Duration.ofMinutes(90)), "Browser"),
        LimitedApp(AppLimit("video", Duration.ofHours(1), weekend = Duration.ofHours(2)), "Video"),
    )

    @Test
    fun downtime_off() = capture("downtime_off") { DowntimeFor(DowntimeSchedule(), pinSet = false) }

    @Test
    fun downtime_everyDay() = capture("downtime_every_day") { DowntimeFor(DowntimeSchedule(enabled = true)) }

    @Test
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    fun downtime_customDays() = capture("downtime_custom_days") { DowntimeFor(customDowntime) }

    @Test
    @Config(qualifiers = "w411dp-h1300dp-night-xxhdpi")
    fun downtime_customDays_dark() = capture("downtime_custom_days_dark", dark = true) { DowntimeFor(customDowntime) }

    @Test
    fun downtime_timePicker() = capture("downtime_time_picker") {
        org.openscreentime.ui.downtime.TimePickerDialog(
            title = "Starts",
            initial = java.time.LocalTime.of(21, 0),
            use24Hour = false,
            onDismiss = {},
            onConfirm = {},
        )
    }

    private val customDowntime = DowntimeSchedule(enabled = true, sameEveryDay = false)
        .withDay(java.time.DayOfWeek.FRIDAY, DowntimeWindow(java.time.LocalTime.of(23, 0), java.time.LocalTime.of(9, 0)))
        .withDay(java.time.DayOfWeek.SATURDAY, DowntimeWindow(java.time.LocalTime.of(23, 30), java.time.LocalTime.of(9, 30)))
        .withDay(java.time.DayOfWeek.SUNDAY, null)

    @Composable
    private fun DowntimeFor(schedule: DowntimeSchedule, pinSet: Boolean = true) {
        val zone = java.time.ZoneOffset.UTC
        val noon = SampleUsage.today.atTime(12, 0).atZone(zone).toInstant()
        DowntimeScreen(
            schedule = schedule,
            status = schedule.status(noon, zone),
            today = SampleUsage.today,
            daysInOrder = (0L until 7L).map { java.time.DayOfWeek.SUNDAY.plus(it) },
            pinSet = pinSet,
            onChange = {},
            onBack = {},
            onSetPinClick = {},
        )
    }

    @Test
    fun alwaysAllowed_noPin() = capture("always_allowed_no_pin") { AllowedFor(pinSet = false) }

    @Test
    @Config(qualifiers = "+night")
    fun alwaysAllowed_dark() = capture("always_allowed_dark", dark = true) { AllowedFor(pinSet = true) }

    @Test
    fun limitEditor_alwaysAllowedNote() = capture("limit_editor_always_allowed") {
        LimitEditorScreen("maps", "Maps", current = null, onBack = {}, onSave = {}, alwaysAllowed = true)
    }

    @Composable
    private fun AllowedFor(pinSet: Boolean) {
        AlwaysAllowedScreen(
            apps = listOf(
                AllowedAppUi(AllowedApp("phone", AllowedRole.Phone), "Phone"),
                AllowedAppUi(AllowedApp("emergency", AllowedRole.Emergency), "Phone Services"),
                AllowedAppUi(AllowedApp("settings", AllowedRole.Settings), "Settings"),
                AllowedAppUi(AllowedApp("maps", AllowedRole.Added), "Maps"),
            ),
            pinSet = pinSet,
            onRemove = {},
            onAddClick = {},
            onBack = {},
            onSetPinClick = {},
        )
    }

    private fun blockUi(
        reason: org.openscreentime.blocking.BlockReason = org.openscreentime.blocking.BlockReason.LimitReached,
        mode: org.openscreentime.blocking.LimitMode = org.openscreentime.blocking.LimitMode.Strict,
        grace: Boolean = false,
        choosing: Boolean = false,
    ) = org.openscreentime.ui.block.BlockScreenUi(
        packageName = "video",
        label = "Video",
        reason = reason,
        blockedUntil = SampleUsage.today.plusDays(1).atTime(7, 0).atZone(java.time.ZoneOffset.UTC),
        mode = mode,
        graceAvailable = grace,
        choosingUnlock = choosing,
    )

    @Composable
    private fun BlockFor(ui: org.openscreentime.ui.block.BlockScreenUi) {
        org.openscreentime.ui.block.BlockScreen(ui, org.openscreentime.ui.block.BlockActions({}, {}, {}, {}, {}))
    }

    @Test
    fun block_limitStrict() = capture("block_limit_strict") { BlockFor(blockUi()) }

    @Test
    fun block_limitGrace() = capture("block_limit_grace") {
        BlockFor(blockUi(mode = org.openscreentime.blocking.LimitMode.Grace, grace = true))
    }

    @Test
    fun block_limitReminder() = capture("block_limit_reminder") {
        BlockFor(blockUi(mode = org.openscreentime.blocking.LimitMode.Reminder))
    }

    @Test
    @Config(qualifiers = "+night")
    fun block_downtimeStrict_dark() = capture("block_downtime_dark", dark = true) {
        BlockFor(blockUi(reason = org.openscreentime.blocking.BlockReason.Downtime))
    }

    @Test
    fun block_unlockChoice() = capture("block_unlock_choice") { BlockFor(blockUi(choosing = true)) }

    @Test
    fun block_unlockChoiceDowntime() = capture("block_unlock_choice_downtime") {
        BlockFor(blockUi(reason = org.openscreentime.blocking.BlockReason.Downtime, choosing = true))
    }

    @Test
    fun limitModeDialog() = capture("limit_mode_dialog") {
        org.openscreentime.ui.home.LimitModeDialog(org.openscreentime.blocking.LimitMode.Strict, {}, {})
    }

    @Test
    @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    fun appDetail_status() = capture("app_detail_status") {
        AppDetailScreen(
            detail = buildAppDetail(
                "browser",
                SampleUsage.usageByDay,
                SampleUsage.today,
                SampleUsage.firstDayOfWeek,
                SampleUsage.labels,
                limit = AppLimit("browser", Duration.ofHours(1)),
            ),
            onBack = {},
            decision = org.openscreentime.blocking.BlockDecision.Blocked(
                org.openscreentime.blocking.BlockReason.LimitReached,
                SampleUsage.today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC),
                graceAvailable = false,
            ),
        )
    }

    @Test
    fun historyDialog() = capture("history_dialog") {
        HistoryLengthDialog(selected = HistoryLength.FourWeeks, onSelect = {}, onDismiss = {})
    }

    @Composable
    private fun HomeFor(status: HomeStatus, dashboard: Dashboard = SampleUsage.dashboard()) {
        HomeScreen(
            limitCount = 2,
            downtimeSummary = "Starts today at 9:00 PM",
            alwaysAllowedCount = 3,
            status = status,
            dashboard = dashboard,
            historyLength = HistoryLength.FourWeeks,
            onTrackingChange = {},
            onAllowUsageAccess = {},
            onHistoryLengthChange = {},
        )
    }

    @Composable
    private fun BrowserDetail() {
        AppDetailScreen(
            detail = buildAppDetail(
                "browser",
                SampleUsage.usageByDay,
                SampleUsage.today,
                SampleUsage.firstDayOfWeek,
                SampleUsage.labels,
            ),
            onBack = {},
        )
    }
}
