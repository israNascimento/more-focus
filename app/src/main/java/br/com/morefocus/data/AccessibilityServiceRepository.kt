package br.com.morefocus.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import br.com.morefocus.accessibility.MoreFocusAccessibilityService

interface AccessibilityServiceRepository {
    fun isMoreFocusAccessibilityServiceEnabled(): Boolean
}

class AndroidAccessibilityServiceRepository(
    context: Context
) : AccessibilityServiceRepository {
    private val applicationContext = context.applicationContext

    override fun isMoreFocusAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(
            applicationContext,
            MoreFocusAccessibilityService::class.java
        )
        val expectedNames = setOf(
            expectedComponent.flattenToString(),
            expectedComponent.flattenToShortString()
        )
        val enabledServices = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .any { enabledService ->
                expectedNames.any { expectedName ->
                    enabledService.equals(expectedName, ignoreCase = true)
                }
            }
    }
}
