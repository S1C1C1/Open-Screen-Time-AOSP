package org.openscreentime.usage

import android.content.pm.PackageManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Looks up app icons. Returns null when there is no icon to show. */
fun interface AppIcons {
    fun iconFor(packageName: String): ImageBitmap?
}

/**
 * Gives screens access to app icons without passing them everywhere. Defaults
 * to no icons, which tests and previews use, so their images don't depend on
 * the apps installed on the computer running them.
 */
val LocalAppIcons = staticCompositionLocalOf { AppIcons { null } }

class PackageManagerAppIcons(private val packageManager: PackageManager) : AppIcons {
    // Icons are reused often while scrolling; keep each one after the first load.
    private val cache = mutableMapOf<String, ImageBitmap?>()

    override fun iconFor(packageName: String): ImageBitmap? = cache.getOrPut(packageName) {
        try {
            packageManager.getApplicationIcon(packageName)
                .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                .asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val ICON_SIZE_PX = 96
    }
}
