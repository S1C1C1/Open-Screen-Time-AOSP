package org.openscreentime.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class GroupRulesTest {

    private fun minutes(n: Long) = Duration.ofMinutes(n)
    private val thursday = LocalDate.of(2026, 11, 5)
    private val saturday = LocalDate.of(2026, 11, 7)

    private val social = AppGroup(1, "Social", setOf("chat", "photos"), DailyLimit(minutes(60)))
    private val games = AppGroup(2, "Games", setOf("puzzle"), DailyLimit(minutes(30)))
    private val noLimit = AppGroup(3, "Reading", setOf("chat", "news"))

    @Test
    fun usageOfAllAppsInTheGroupAddsUp() {
        val used = mapOf("chat" to minutes(25), "photos" to minutes(20), "maps" to minutes(90))
        assertEquals(minutes(45), GroupRules.usedToday(social, used))
    }

    @Test
    fun sharedLimitIsReachedByTheTotal() {
        val used = mapOf("chat" to minutes(40), "photos" to minutes(20))
        val usage = GroupRules.limitedGroupsFor("photos", listOf(social), used).single()
        assertTrue(usage.isReached(thursday))
        assertEquals(Duration.ZERO, usage.timeLeft(thursday))
    }

    @Test
    fun weekendLimitIsUsedOnSaturday() {
        val limit = DailyLimit(minutes(60), weekend = minutes(120))
        assertEquals(minutes(60), limit.limitOn(thursday))
        assertEquals(minutes(120), limit.limitOn(saturday))
    }

    @Test
    fun onlyGroupsWithALimitThatContainTheAppCount() {
        val groups = listOf(social, games, noLimit)
        assertEquals(listOf(social), GroupRules.limitedGroupsFor("chat", groups, emptyMap()).map { it.group })
        assertEquals(emptyList<AppGroup>(), GroupRules.limitedGroupsFor("news", groups, emptyMap()).map { it.group })
    }

    @Test
    fun anAppCanBeInSeveralGroups() {
        val evening = AppGroup(4, "Evening", setOf("chat", "puzzle"), DailyLimit(minutes(15)))
        val result = GroupRules.limitedGroupsFor("chat", listOf(social, evening), mapOf("chat" to minutes(20)))
        assertEquals(listOf("Social", "Evening"), result.map { it.group.name })
        assertFalse(result[0].isReached(thursday))
        assertTrue(result[1].isReached(thursday))
    }

    @Test
    fun namesAreTidied() {
        assertEquals("Social media", GroupRules.cleanName("  Social   media "))
        assertNull(GroupRules.cleanName("   "))
        assertEquals(GroupRules.MAX_NAME_LENGTH, GroupRules.cleanName("x".repeat(100))?.length)
    }

    @Test
    fun limitsOutsideTheAllowedRangeAreInvalid() {
        assertTrue(DailyLimit(minutes(60)).isValid)
        assertFalse(DailyLimit(minutes(10)).isValid)
        assertFalse(DailyLimit(minutes(60), weekend = minutes(5)).isValid)
    }
}
