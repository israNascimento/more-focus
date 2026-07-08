package br.com.morefocus.ui

import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.data.AccessibilityServiceRepository
import br.com.morefocus.data.MoreFocusSettings
import br.com.morefocus.data.MoreFocusSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreFocusViewModelTest {
    @Test
    fun initialStateLoadsSettingsAndAccessibilityStatus() {
        val settingsRepository = FakeMoreFocusSettingsRepository(
            settings = MoreFocusSettings(
                focusEnabled = false,
                instagramReelsEnabled = false,
                dailyReelsLimitMinutes = 30
            )
        )
        val accessibilityRepository = FakeAccessibilityServiceRepository(enabled = true)

        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = accessibilityRepository
        )

        val state = viewModel.uiState.value
        assertTrue(state.serviceEnabled)
        assertFalse(state.focusEnabled)
        assertFalse(state.instagramReelsEnabled)
        assertEquals(30, state.dailyReelsLimitMinutes)
        assertEquals(settingsRepository.dailyReelsLimitMinuteOptions, state.dailyReelsLimitMinuteOptions)
    }

    @Test
    fun toggleChangesUpdateRepositoryAndUiState() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false)
        )

        viewModel.setFocusEnabled(false)
        viewModel.setInstagramReelsEnabled(false)

        assertFalse(settingsRepository.getSettings().focusEnabled)
        assertFalse(settingsRepository.getSettings().instagramReelsEnabled)
        assertFalse(viewModel.uiState.value.focusEnabled)
        assertFalse(viewModel.uiState.value.instagramReelsEnabled)
    }

    @Test
    fun invalidDailyLimitIsSanitizedByRepository() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false)
        )

        viewModel.setDailyReelsLimitMinutes(99)

        assertEquals(DEFAULT_DAILY_REELS_LIMIT_MINUTES, settingsRepository.getSettings().dailyReelsLimitMinutes)
        assertEquals(DEFAULT_DAILY_REELS_LIMIT_MINUTES, viewModel.uiState.value.dailyReelsLimitMinutes)
    }

    @Test
    fun refreshReloadsAccessibilityStatus() {
        val accessibilityRepository = FakeAccessibilityServiceRepository(enabled = false)
        val viewModel = MoreFocusViewModel(
            settingsRepository = FakeMoreFocusSettingsRepository(),
            accessibilityServiceRepository = accessibilityRepository
        )

        assertFalse(viewModel.uiState.value.serviceEnabled)

        accessibilityRepository.enabled = true
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.serviceEnabled)
    }

    private class FakeMoreFocusSettingsRepository(
        private var settings: MoreFocusSettings = MoreFocusSettings(
            focusEnabled = true,
            instagramReelsEnabled = true,
            dailyReelsLimitMinutes = DEFAULT_DAILY_REELS_LIMIT_MINUTES
        )
    ) : MoreFocusSettingsRepository {
        override val dailyReelsLimitMinuteOptions: List<Int> = DAILY_REELS_LIMIT_MINUTE_OPTIONS

        override fun getSettings(): MoreFocusSettings = settings

        override fun getBlockSettings(nowMillis: Long): BlockSettings =
            BlockSettings(
                focusEnabled = settings.focusEnabled,
                instagramReelsEnabled = settings.instagramReelsEnabled,
                dailyLimitMinutes = settings.dailyReelsLimitMinutes
            )

        override fun setFocusEnabled(enabled: Boolean) {
            settings = settings.copy(focusEnabled = enabled)
        }

        override fun setInstagramReelsEnabled(enabled: Boolean) {
            settings = settings.copy(instagramReelsEnabled = enabled)
        }

        override fun setDailyReelsLimitMinutes(limitMinutes: Int) {
            settings = settings.copy(
                dailyReelsLimitMinutes = limitMinutes.takeIf {
                    it in dailyReelsLimitMinuteOptions
                } ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
            )
        }

        override fun pauseFor(durationMillis: Long, nowMillis: Long) = Unit

        override fun recordDailyReelsUsage(
            startedAtMillis: Long?,
            endedAtMillis: Long
        ): DailyUsageState =
            DailyUsageState(dayKey = "2026-07-08", usedMillis = 0L)
    }

    private class FakeAccessibilityServiceRepository(
        var enabled: Boolean
    ) : AccessibilityServiceRepository {
        override fun isMoreFocusAccessibilityServiceEnabled(): Boolean = enabled
    }

    private companion object {
        val DAILY_REELS_LIMIT_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_DAILY_REELS_LIMIT_MINUTES = 15
    }
}
