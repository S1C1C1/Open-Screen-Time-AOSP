package org.openscreentime.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class DurationFormatTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private fun format(d: Duration) = formatDuration(d, resources)

    @Test
    fun formats() {
        assertEquals("0 min", format(Duration.ZERO))
        assertEquals("Less than 1 min", format(Duration.ofSeconds(59)))
        assertEquals("1 min", format(Duration.ofSeconds(60)))
        assertEquals("45 min", format(Duration.ofMinutes(45)))
        assertEquals("1 hr", format(Duration.ofHours(1)))
        assertEquals("1 hr 5 min", format(Duration.ofMinutes(65)))
        assertEquals("23 hr 45 min", format(Duration.ofMinutes(23 * 60 + 45)))
    }

    @Test
    fun roundsDownToTheMinute() {
        assertEquals("1 hr 5 min", format(Duration.ofMinutes(65).plusSeconds(59)))
    }
}
