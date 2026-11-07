package org.openscreentime.groups

import android.content.pm.ApplicationInfo
import org.openscreentime.R
import org.openscreentime.usage.InstalledApp

/** A ready-made group based on the categories apps declare about themselves. */
data class GroupSuggestion(val nameRes: Int, val packages: Set<String>)

/**
 * Suggests groups from each app's own category (set by its developer, read
 * on the phone, no internet). Only categories with at least two apps are
 * suggested; suggestions are starting points the user can edit or ignore.
 */
object GroupSuggestions {

    private val categoryNames = linkedMapOf(
        ApplicationInfo.CATEGORY_SOCIAL to R.string.group_suggest_social,
        ApplicationInfo.CATEGORY_GAME to R.string.group_suggest_games,
        ApplicationInfo.CATEGORY_VIDEO to R.string.group_suggest_video,
        ApplicationInfo.CATEGORY_AUDIO to R.string.group_suggest_audio,
        ApplicationInfo.CATEGORY_NEWS to R.string.group_suggest_news,
        ApplicationInfo.CATEGORY_IMAGE to R.string.group_suggest_photos,
        ApplicationInfo.CATEGORY_PRODUCTIVITY to R.string.group_suggest_productivity,
    )

    fun from(apps: List<InstalledApp>): List<GroupSuggestion> =
        categoryNames.mapNotNull { (category, nameRes) ->
            val packages = apps.filter { it.category == category }.map { it.packageName }.toSet()
            if (packages.size >= 2) GroupSuggestion(nameRes, packages) else null
        }
}
