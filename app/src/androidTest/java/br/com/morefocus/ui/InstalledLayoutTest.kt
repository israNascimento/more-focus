package br.com.morefocus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.com.morefocus.MainActivity
import br.com.morefocus.data.AndroidAccessibilityServiceRepository
import org.junit.Rule
import org.junit.Test

class InstalledLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun bothTabsAndAppDetailsShowTheCurrentSettings() {
        val service = AndroidAccessibilityServiceRepository(compose.activity)
        val active = service.isMoreFocusAccessibilityServiceEnabled()
        compose.onNodeWithText("Uso de hoje").assertIsDisplayed()
        checkServiceWarning(active)
        compose.onNodeWithText("Preferências").performClick()
        compose.onNodeWithText("Limite compartilhado").assertIsDisplayed()
        checkServiceWarning(active)
        for (app in listOf("Instagram", "YouTube")) {
            compose.onNodeWithText(app).performScrollTo().performClick()
            val toggle = if (app == "Instagram") "Limitar Reels" else "Limitar Shorts"
            compose.onNodeWithText(toggle).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar para Preferências").performClick()
        }
    }

    private fun checkServiceWarning(active: Boolean) {
        val warning = compose.onNodeWithText("Acessibilidade desativada")
        if (active) warning.assertDoesNotExist() else warning.assertIsDisplayed()
    }
}
