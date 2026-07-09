package br.com.morefocus.accessibility

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.com.morefocus.ui.theme.MoreFocusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pauseButtonCountsDownBeforeBecomingEnabled() {
        var clicks = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoreFocusTheme {
                FocusOverlay(
                    title = "Limite atingido",
                    description = "Descrição",
                    initialPauseLockSeconds = 3,
                    onPauseClick = { clicks++ },
                    onExitClick = {}
                )
            }
        }

        composeRule.onNodeWithText("+1 minuto (3s)").assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.onNodeWithText("+1 minuto (2s)").assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(2_100L)
        composeRule.onNodeWithText("+1 minuto").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun pauseButtonStartsEnabledWithoutLock() {
        var clicks = 0
        composeRule.setContent {
            MoreFocusTheme {
                FocusOverlay(
                    title = "Limite atingido",
                    description = "Descrição",
                    initialPauseLockSeconds = 0,
                    onPauseClick = { clicks++ },
                    onExitClick = {}
                )
            }
        }

        composeRule.onNodeWithText("+1 minuto").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }
}
