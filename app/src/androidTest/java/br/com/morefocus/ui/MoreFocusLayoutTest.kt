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
    fun scheduleCanBeEnabledEditedAndDisabled() {
        launch()
        tap("Preferências")
        fun openSchedule() = compose.onNodeWithText("Horário de foco").performScrollTo().performClick()
        openSchedule()
        compose.onNodeWithContentDescription("Ativar agendamento").performClick()
        val friday = java.text.DateFormatSymbols.getInstance().weekdays[java.util.Calendar.FRIDAY]
        compose.onNodeWithContentDescription(friday).performClick()
        tap("Salvar")
        assertEquals(true, state.focusSchedule.enabled)
        assertEquals(false, state.focusSchedule.includes(java.util.Calendar.FRIDAY))
        openSchedule()
        compose.onNodeWithContentDescription("Ativar agendamento").assertIsOn().performClick()
        tap("Salvar")
        assertEquals(false, state.focusSchedule.enabled)
    }

    @Test
    fun usageOffersOneSessionAndShowsItsEndWithOnlyKwaiEnabled() {
        state = state.copy(instagramReelsEnabled = false, youtubeShortsEnabled = false, facebookReelsEnabled = false,
            xVideosEnabled = false, tikTokForYouEnabled = false)
        launch()
        tap("Foco por 30 minutos")
        compose.onNodeWithText("Foco até ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Foco por 30 minutos").assertDoesNotExist()
        tap("Preferências")
        tap("Uso")
        compose.onNodeWithText("Foco até ", substring = true).assertIsDisplayed()
    }

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
        tap("Uso")
        compose.onNodeWithText("restantes hoje").assertIsDisplayed()
        tap("Preferências")
        compose.onNodeWithText("X").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Limitar vídeos em tela cheia").assertIsOn().performClick().assertIsOff()
        backToPreferences()
        compose.onNodeWithText("TikTok").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Limitar Para Você").assertIsOn().performClick().assertIsOff()
        backToPreferences()
        compose.onNodeWithText("Facebook").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Limitar Reels").assertIsOn().performClick().assertIsOff()
        backToPreferences()
        compose.onNodeWithText("Kwai").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Limitar feed de vídeos").assertIsOn().performClick().assertIsOff()
        backToPreferences()
        compose.onNodeWithText("Limite compartilhado").performScrollTo().performClick()
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
                    onSaveFocusSchedule = { state = state.copy(focusSchedule = it) },
                    onStartFocusSession = { state = state.copy(focusSessionEndsAtMillis = System.currentTimeMillis() + 1_800_000L) },
                    onFocusEnabledChange = {},
                    onShowUsageNotificationChange = {},
                    onInstagramReelsEnabledChange = { state = state.copy(instagramReelsEnabled = it) },
                    onHideFeedReelsChange = {},
                    onHideFeedShortsChange = { state = state.copy(hideFeedShorts = it) },
                    onHardBlockVerticalVideosChange = { state = state.copy(hardBlockVerticalVideos = it) },
                    onYouTubeShortsEnabledChange = { state = state.copy(youtubeShortsEnabled = it) },
                    onXVideosEnabledChange = { state = state.copy(xVideosEnabled = it) },
                    onFacebookReelsEnabledChange = { state = state.copy(facebookReelsEnabled = it) },
                    onKwaiFeedEnabledChange = { state = state.copy(kwaiFeedEnabled = it) },
                    onTikTokForYouEnabledChange = { state = state.copy(tikTokForYouEnabled = it) },
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
