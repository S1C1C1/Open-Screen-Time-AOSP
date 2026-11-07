package org.openscreentime.groups

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.R
import org.openscreentime.usage.InstalledApp

@RunWith(AndroidJUnit4::class)
class GroupSuggestionsTest {

    @Test
    fun suggestsCategoriesWithAtLeastTwoApps() {
        val apps = listOf(
            InstalledApp("chat", "Chat", ApplicationInfo.CATEGORY_SOCIAL),
            InstalledApp("photos", "Photos", ApplicationInfo.CATEGORY_SOCIAL),
            InstalledApp("puzzle", "Puzzle", ApplicationInfo.CATEGORY_GAME),
            InstalledApp("maps", "Maps", ApplicationInfo.CATEGORY_MAPS),
            InstalledApp("notes", "Notes"),
        )
        assertEquals(
            listOf(GroupSuggestion(R.string.group_suggest_social, setOf("chat", "photos"))),
            GroupSuggestions.from(apps),
        )
    }
}
