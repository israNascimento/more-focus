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
}
