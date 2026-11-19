package br.com.morefocus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.com.morefocus.MoreFocusScreen
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import br.com.morefocus.blocking.YOUTUBE_PACKAGE_NAME
import br.com.morefocus.data.SelectableApp
import br.com.morefocus.ui.theme.MoreFocusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreFocusLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        MoreFocusUiState(
            serviceEnabled = true,
            dailyVerticalVideosLimitSeconds = 900,
            dailyVerticalVideosUsageMillis = 420_000L,
            dailyVerticalVideosLimitSecondOptions = listOf(30, 300, 600, 900, 1800, 3600),
            appLimitSecondOptions = listOf(30, 300, 600, 900, 1800, 3600),
            appLimitRules = listOf(AppLimitRule(INSTAGRAM_PACKAGE_NAME, "Instagram", 1800, 720_000L)),
            selectableApps = listOf(SelectableApp(YOUTUBE_PACKAGE_NAME, "YouTube"))
        )
    )
    private var settingsOpened = 0
    private var addedLimit: Pair<String, Int>? = null
    private var editedLimit: Pair<String, Int>? = null

    @Test
    fun appDetailsRouteVideoTogglesAndWholeAppLimitsSeparately() {
        launch()
        tap("Preferências")
        val hardBlockTitle = "Bloqueio rígido de vídeos verticais"
        compose.onNodeWithContentDescription(hardBlockTitle).performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Instagram").performScrollTo().performClick()
        compose.onNodeWithText("Limitar Shorts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Limitar Reels").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Ocultar Reels no feed").assertExists()
        compose.onNodeWithContentDescription(hardBlockTitle).assertDoesNotExist()
        tap("Limite diário")
        tap("10 min")
        assertEquals(INSTAGRAM_PACKAGE_NAME to 600, editedLimit)
        backToPreferences()
        tap("YouTube")
        compose.onNodeWithContentDescription(hardBlockTitle).assertDoesNotExist()
        compose.onNodeWithText("Limitar Reels").assertDoesNotExist()
        compose.onNodeWithContentDescription("Limitar Shorts").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("Ocultar Shorts no feed").assertIsOff().performClick().assertIsOn()
        tap("Definir")
        tap("5 min")
        assertEquals(YOUTUBE_PACKAGE_NAME to 300, addedLimit)
        backToPreferences()
        tap("Limite compartilhado")
        tap("10 min")
        tap("Uso")
        compose.onNodeWithText("3 min").assertIsDisplayed()
        compose.onNodeWithText("Regra desativada").assertIsDisplayed()
        compose.onNodeWithText("12 min de 30 min usados").assertExists()
    }

    @Test
    fun bothTabsShowTheServiceWarningAndOpenTheDisclosureUntilServiceIsActive() {
        state = state.copy(serviceEnabled = false)
        launch()
        for (tab in listOf("Uso", "Preferências")) {
            tap(tab)
            compose.onNodeWithText("Acessibilidade desativada").assertIsDisplayed()
            tap("Abrir configurações")
            compose.onNodeWithText("Uso da acessibilidade").assertIsDisplayed()
            compose.onNode(hasText("Abrir configurações") and hasAnyAncestor(isDialog())).performClick()
        }
        assertEquals(2, settingsOpened)
        compose.runOnIdle { state = state.copy(serviceEnabled = true) }
        compose.onNodeWithText("Acessibilidade desativada").assertDoesNotExist()
        tap("Uso")
        compose.onNodeWithText("Acessibilidade desativada").assertDoesNotExist()
        compose.onNodeWithText("8 min").assertIsDisplayed()
        compose.onNodeWithText("7 min de 15 min usados").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(dailyVerticalVideosUsageMillis = 920_000, appLimitRules = emptyList()) }
        compose.onNodeWithText("Limite atingido").assertIsDisplayed()
        compose.onNodeWithText("Nenhum limite por aplicativo configurado.").assertIsDisplayed()
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun backToPreferences() =
        compose.onNodeWithContentDescription("Voltar para Preferências").performClick()

    private fun launch() {
        compose.setContent {
            MoreFocusTheme(dynamicColor = false) {
                MoreFocusScreen(
                    uiState = state,
                    onFocusEnabledChange = {},
                    onShowUsageNotificationChange = {},
                    onInstagramReelsEnabledChange = { state = state.copy(instagramReelsEnabled = it) },
                    onHideFeedReelsChange = {},
                    onHideFeedShortsChange = { state = state.copy(hideFeedShorts = it) },
                    onHardBlockVerticalVideosChange = { state = state.copy(hardBlockVerticalVideos = it) },
                    onYouTubeShortsEnabledChange = { state = state.copy(youtubeShortsEnabled = it) },
                    onExponentialPauseLockEnabledChange = {},
                    onDailyVerticalVideosLimitSecondsChange = { state = state.copy(dailyVerticalVideosLimitSeconds = it) },
                    onAddAppLimit = { pkg, seconds -> addedLimit = pkg to seconds },
                    onAppLimitSecondsChange = { pkg, seconds -> editedLimit = pkg to seconds },
                    onAppLimitEnabledChange = { _, _ -> },
                    onRemoveAppLimit = {},
                    onOpenAccessibilitySettings = { settingsOpened++ }
                )
            }
        }
    }
}
