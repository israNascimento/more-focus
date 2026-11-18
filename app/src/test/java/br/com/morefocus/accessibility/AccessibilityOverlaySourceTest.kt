package br.com.morefocus.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityOverlaySourceTest {
    @Test
    fun overlayIsBuiltWithComposeViewInsteadOfAndroidWidgetLayout() {
        val source = File(
            "src/main/java/br/com/morefocus/accessibility/MoreFocusAccessibilityService.kt"
        ).readText()

        assertTrue(source.contains("ComposeView"))
        assertFalse(source.contains("android.widget.Button"))
        assertFalse(source.contains("android.widget.FrameLayout"))
        assertFalse(source.contains("android.widget.LinearLayout"))
        assertFalse(source.contains("android.widget.TextView"))
    }

    @Test
    fun composeOverlayGetsRequiredViewTreeOwners() {
        val source = File(
            "src/main/java/br/com/morefocus/accessibility/MoreFocusAccessibilityService.kt"
        ).readText()

        assertTrue(source.contains("SavedStateRegistryOwner"))
        assertTrue(source.contains("performAttach()"))
        assertTrue(source.contains("setViewTreeLifecycleOwner"))
        assertTrue(source.contains("setViewTreeSavedStateRegistryOwner"))
    }

    @Test
    fun unmonitoredPackageReturnsBeforeNodeScanning() {
        val source = serviceSource()
        val earlyReturn = source.indexOf("if (!monitoringScope.monitors(packageName))")
        val nodeScanning = source.indexOf("AccessibilityNodeScanner.resourceEntryNames")

        assertTrue(earlyReturn >= 0)
        assertTrue(earlyReturn < nodeScanning)
    }

    @Test
    fun scheduledLimitCheckBypassesEventThrottle() {
        val source = serviceSource()

        assertTrue(source.contains("eventEvaluationThrottle.submit"))
        assertTrue(source.contains("Runnable { evaluateCurrentScreen() }"))
    }

    @Test
    fun overlayIsReservedBeforeItIsAddedToWindowManager() {
        val source = serviceSource()
        val showOverlayStart = source.indexOf("private fun showOverlay")
        val overlayReservation = source.indexOf("overlayView = view", showOverlayStart)
        val addToWindowManager = source.indexOf("windowManager.addView", showOverlayStart)

        assertTrue(source.contains("overlayMutationInProgress"))
        assertTrue(overlayReservation > showOverlayStart)
        assertTrue(overlayReservation < addToWindowManager)
    }

    private fun serviceSource(): String =
        File(
            "src/main/java/br/com/morefocus/accessibility/MoreFocusAccessibilityService.kt"
        ).readText()
}
