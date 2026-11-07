package org.openscreentime.allowed

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Keyboards and accessibility services that are switched on. These are
 * never paused: pausing the keyboard could stop someone typing their
 * password on the lock screen, and pausing a screen reader could leave
 * someone unable to use their phone at all.
 */
fun interface AssistiveApps {
    fun packages(): Set<String>
}

class AndroidAssistiveApps(private val context: Context) : AssistiveApps {

    override fun packages(): Set<String> {
        val resolver = context.contentResolver
        // Read both the settings and the system services: before the first
        // unlock, the services may only list some of them.
        val fromSettings = packagesFromComponentList(Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS)) +
            packagesFromComponentList(Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)) +
            packagesFromComponentList(Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        val keyboards = context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList.orEmpty()
            .map { it.packageName }
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
        return fromSettings + keyboards + accessibility
    }
}

/**
 * Package names from a list of components as Android stores it, like
 * "com.a/.Keyboard;123:com.b/com.b.Reader" (";…" is a keyboard layout).
 */
fun packagesFromComponentList(value: String?): Set<String> =
    value.orEmpty()
        .split(':')
        .map { it.substringBefore(';').substringBefore('/').trim() }
        .filter { it.isNotEmpty() }
        .toSet()
