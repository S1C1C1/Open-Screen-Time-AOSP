package org.openscreentime.restrictions

import android.os.Build
import android.os.UserManager
import org.openscreentime.pin.DeviceClock
import java.time.Duration

/** The four restrictions from the spec, each made of one or more Android user restrictions. */
enum class Restriction(val userRestrictions: List<String>) {
    /** App installs and uninstalls, from app stores and APK files. Also stops app updates while on. */
    Installs(
        listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
        ),
    ),

    /** Developer options, USB debugging and wireless debugging. */
    DeveloperOptions(listOf(UserManager.DISALLOW_DEBUGGING_FEATURES)),

    /** Adding or switching users, including guest mode and new profiles. */
    Users(
        buildList {
            add(UserManager.DISALLOW_ADD_USER)
            add(UserManager.DISALLOW_USER_SWITCH)
            // Work profiles can't be added on a phone with a device owner, so no
            // restriction is needed for them. There's no public restriction for
            // "app clone" profiles; GrapheneOS doesn't offer them.
            // Android 15 added a "private space" profile.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(UserManager.DISALLOW_ADD_PRIVATE_PROFILE)
            }
        },
    ),

    /** Changing the date, time or time zone. */
    DateTime(listOf(UserManager.DISALLOW_CONFIG_DATE_TIME)),
}

/**
 * A restriction lifted with the PIN for a while. Timed on the clock that
 * counts from phone start, so changing the date can't stretch it; a
 * restart ends it early, which is the safe side.
 */
data class Lift(val bootCount: Int, val endsAtSinceBootMillis: Long)

data class RestrictionsState(
    val enabled: Set<Restriction> = emptySet(),
    val lifts: Map<Restriction, Lift> = emptyMap(),
)

object RestrictionRules {
    val LIFT_DURATION: Duration = Duration.ofMinutes(15)

    /** Time left on a lift, or null if it's over (or the phone has restarted since). */
    fun liftRemaining(lift: Lift?, clock: DeviceClock): Duration? {
        if (lift == null || lift.bootCount != clock.bootCount()) return null
        val left = lift.endsAtSinceBootMillis - clock.sinceBoot().toMillis()
        return if (left > 0) Duration.ofMillis(left) else null
    }

    /** Restrictions that should be in force right now: switched on and not lifted. */
    fun active(state: RestrictionsState, clock: DeviceClock): Set<Restriction> =
        state.enabled.filter { liftRemaining(state.lifts[it], clock) == null }.toSet()

    fun newLift(clock: DeviceClock): Lift =
        Lift(clock.bootCount(), (clock.sinceBoot() + LIFT_DURATION).toMillis())

    /** The soonest a lift ends, so restrictions can be switched back on in time. */
    fun nextLiftEnd(state: RestrictionsState, clock: DeviceClock): Duration? =
        state.enabled.mapNotNull { liftRemaining(state.lifts[it], clock) }.minOrNull()
}
