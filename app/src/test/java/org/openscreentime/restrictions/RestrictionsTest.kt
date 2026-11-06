package org.openscreentime.restrictions

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.ScreenTimeAdmin
import org.openscreentime.pin.FakeDeviceClock
import org.openscreentime.ui.FakeDeviceOwner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class RestrictionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeDeviceClock()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun eachRestrictionCoversWhatTheSpecLists() {
        assertEquals(
            listOf(UserManager.DISALLOW_INSTALL_APPS, UserManager.DISALLOW_UNINSTALL_APPS, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY),
            Restriction.Installs.userRestrictions,
        )
        assertEquals(listOf(UserManager.DISALLOW_DEBUGGING_FEATURES), Restriction.DeveloperOptions.userRestrictions)
        assertTrue(Restriction.Users.userRestrictions.containsAll(listOf(UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_USER_SWITCH)))
        assertEquals(listOf(UserManager.DISALLOW_CONFIG_DATE_TIME), Restriction.DateTime.userRestrictions)
    }

    @Test
    fun enabledRestrictionsAreActiveUnlessLifted() {
        val state = RestrictionsState(enabled = setOf(Restriction.Installs, Restriction.DateTime))
        assertEquals(setOf(Restriction.Installs, Restriction.DateTime), RestrictionRules.active(state, clock))

        val lifted = state.copy(lifts = mapOf(Restriction.Installs to RestrictionRules.newLift(clock)))
        assertEquals(setOf(Restriction.DateTime), RestrictionRules.active(lifted, clock))
    }

    @Test
    fun liftLastsFifteenMinutes() {
        val lift = RestrictionRules.newLift(clock)
        val state = RestrictionsState(setOf(Restriction.Installs), mapOf(Restriction.Installs to lift))
        assertEquals(Duration.ofMinutes(15), RestrictionRules.nextLiftEnd(state, clock))

        clock.advance(Duration.ofMinutes(14))
        assertEquals(Duration.ofMinutes(1), RestrictionRules.liftRemaining(lift, clock))
        clock.advance(Duration.ofMinutes(1))
        assertNull(RestrictionRules.liftRemaining(lift, clock))
        assertEquals(setOf(Restriction.Installs), RestrictionRules.active(state, clock))
    }

    @Test
    fun restartingEndsALiftEarly() {
        val lift = RestrictionRules.newLift(clock)
        clock.restartPhone()
        assertNull(RestrictionRules.liftRemaining(lift, clock))
    }

    @Test
    fun savingAndReading() = runBlocking {
        val repository = DataStoreRestrictionsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("r.preferences_pb") },
        )
        assertEquals(RestrictionsState(), repository.state.first())

        repository.setEnabled(Restriction.Users, true)
        val lift = Lift(3, 123_456)
        repository.setLift(Restriction.Users, lift)
        assertEquals(RestrictionsState(setOf(Restriction.Users), mapOf(Restriction.Users to lift)), repository.state.first())

        // Switching off clears the lift.
        repository.setEnabled(Restriction.Users, false)
        assertEquals(RestrictionsState(), repository.state.first())
    }

    @Test
    fun applyingSetsAndClearsAndroidRestrictions() {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ScreenTimeAdmin.component(context)
        shadowOf(policy).setDeviceOwner(admin)
        val userManager = context.getSystemService(UserManager::class.java)
        val applier = ModeAwareRestrictionApplier(FakeDeviceOwner(EnforcementMode.DeviceOwner), DevicePolicyRestrictionApplier(context))

        applier.apply(setOf(Restriction.DeveloperOptions, Restriction.DateTime))
        assertTrue(userManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES))
        assertTrue(userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_DATE_TIME))
        assertFalse(userManager.hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS))

        applier.apply(setOf(Restriction.DateTime))
        assertFalse(userManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES))
        assertTrue(userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_DATE_TIME))
    }

    @Test
    fun previewModeAppliesNothing() {
        val userManager = context.getSystemService(UserManager::class.java)
        ModeAwareRestrictionApplier(FakeDeviceOwner(EnforcementMode.PreviewOnly), DevicePolicyRestrictionApplier(context))
            .apply(setOf(Restriction.DateTime))
        assertFalse(userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_DATE_TIME))
    }
}
