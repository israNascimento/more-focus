package br.com.morefocus.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.edit
import androidx.test.espresso.Espresso.pressBack
import br.com.morefocus.MainActivity
import br.com.morefocus.ONBOARDING_COMPLETED
import br.com.morefocus.data.AndroidAccessibilityServiceRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InstalledLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun showOnboarding() {
        compose.activity.getPreferences(Context.MODE_PRIVATE).edit { remove(ONBOARDING_COMPLETED) }
        compose.activityRule.scenario.recreate()
    }

    @Test
    fun onboardingSupportsBackAndKeepsProgressAcrossRecreation() {
        compose.onNodeWithText("Escolha seu limite de vídeos").assertIsDisplayed()
        compose.onNodeWithText("Voltar").assertDoesNotExist()
        compose.onNodeWithText("Próximo").performClick()
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithText("Escolha seu limite de vídeos").assertIsDisplayed()
        compose.onNodeWithText("Próximo").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Reserve meia hora de foco").assertIsDisplayed()
        compose.onNodeWithText("Próximo").performClick()
        pressBack()
        compose.onNodeWithText("Reserve meia hora de foco").assertIsDisplayed()
        compose.onNodeWithText("Próximo").performClick()
        compose.onNodeWithText("Acompanhe seu tempo").assertIsDisplayed()
        compose.onNodeWithText("Começar").performClick()
        compose.onNodeWithText("Uso de hoje").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Uso de hoje").assertIsDisplayed()
    }

    @Test
    fun bothTabsAndAppDetailsShowTheCurrentSettings() {
        compose.onNodeWithText("Pular").performClick()
        compose.onNodeWithText("Uso de hoje").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
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
