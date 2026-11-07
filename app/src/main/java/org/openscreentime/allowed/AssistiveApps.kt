package org.openscreentime.allowed

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Keyboards and accessibility services that must keep working, so no one
 * is locked out of typing their password or using their phone.
 */
interface AssistiveApps {
    /**
     * Never paused by any rule: the keyboard in use, and switched-on
     * accessibility services that Android marks as accessibility tools
     * (screen readers, switch access and the like). Ordinary apps that
     * merely switch on an accessibility service aren't included, so that
     * can't be used to get out of a limit.
     */
    fun alwaysProtected(): Set<String>

    /**
     * Not paused while the phone starts, when the lock screen may need
     * them: every switched-on keyboard and accessibility service.
     */
    fun protectedAtStartup(): Set<String>

    companion object {
        val None = object : AssistiveApps {
            override fun alwaysProtected() = emptySet<String>()
            override fun protectedAtStartup() = emptySet<String>()
        }
    }
}

class AndroidAssistiveApps(private val context: Context) : AssistiveApps {

    override fun alwaysProtected(): Set<String> {
        val keyboard = packagesFromComponentList(secureSetting(Settings.Secure.DEFAULT_INPUT_METHOD))
        val tools = enabledAccessibilityServices()
            .filter { it.isAccessibilityTool }
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
        return keyboard + tools
    }

    override fun protectedAtStartup(): Set<String> {
        // Read both the settings and the system services: before the first
        // unlock, the services may only list some of them.
        val fromSettings = packagesFromComponentList(secureSetting(Settings.Secure.ENABLED_INPUT_METHODS)) +
            packagesFromComponentList(secureSetting(Settings.Secure.DEFAULT_INPUT_METHOD)) +
            packagesFromComponentList(secureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        val keyboards = context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList.orEmpty()
            .map { it.packageName }
        val accessibility = enabledAccessibilityServices().mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
        return fromSettings + keyboards + accessibility
    }

    private fun secureSetting(name: String): String? = Settings.Secure.getString(context.contentResolver, name)

    private fun enabledAccessibilityServices(): List<AccessibilityServiceInfo> =
        context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
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
