package org.openscreentime.usage

/**
 * The app on screen right now, from recent usage events: of the apps
 * brought to the front and not yet gone, the most recent one. Turning the
 * screen off closes everything. Null when no app is open (for example on
 * the home screen).
 */
fun foregroundApp(events: List<UsageEvent>): String? {
    // Kept in the order apps came to the front, most recent last.
    val open = LinkedHashMap<String, MutableSet<String>>()
    for (event in events.sortedBy { it.time }) {
        when (event) {
            is UsageEvent.AppResumed -> {
                val screens = open.remove(event.packageName) ?: mutableSetOf()
                screens += event.className
                open[event.packageName] = screens
            }
            is UsageEvent.AppPaused -> open[event.packageName]?.let { screens ->
                screens -= event.className
                if (screens.isEmpty()) open.remove(event.packageName)
            }
            is UsageEvent.ScreenOff -> open.clear()
        }
    }
    return open.keys.lastOrNull()
}
