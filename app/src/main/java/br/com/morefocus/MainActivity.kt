package br.com.morefocus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.morefocus.ui.MoreFocusUiState
import br.com.morefocus.ui.MoreFocusViewModel
import br.com.morefocus.ui.theme.MoreFocusTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MoreFocusViewModel by lazy {
        ViewModelProvider(
            this,
            (application as MoreFocusApplication).appContainer.moreFocusViewModelFactory
        )[MoreFocusViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoreFocusTheme {
                MoreFocusApp(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun MoreFocusApp(
    viewModel: MoreFocusViewModel,
    onOpenAccessibilitySettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MoreFocusScreen(
        uiState = uiState,
        onFocusEnabledChange = viewModel::setFocusEnabled,
        onInstagramReelsEnabledChange = viewModel::setInstagramReelsEnabled,
        onDailyReelsLimitMinutesChange = viewModel::setDailyReelsLimitMinutes,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings
    )
}

@Composable
private fun MoreFocusScreen(
    uiState: MoreFocusUiState,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onDailyReelsLimitMinutesChange: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val scrollable = rememberScrollState()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollable)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()

                ServiceStatusCard(
                    serviceEnabled = uiState.serviceEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )

                DisclosureCard()

                PreferenceCard(
                    focusEnabled = uiState.focusEnabled,
                    instagramReelsEnabled = uiState.instagramReelsEnabled,
                    dailyReelsLimitMinutes = uiState.dailyReelsLimitMinutes,
                    dailyReelsLimitMinuteOptions = uiState.dailyReelsLimitMinuteOptions,
                    onFocusEnabledChange = onFocusEnabledChange,
                    onInstagramReelsEnabledChange = onInstagramReelsEnabledChange,
                    onDailyReelsLimitMinutesChange = onDailyReelsLimitMinutesChange
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.header_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServiceStatusCard(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (serviceEnabled) {
                    stringResource(R.string.accessibility_status_active)
                } else {
                    stringResource(R.string.accessibility_status_inactive)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (serviceEnabled) {
                    stringResource(R.string.accessibility_status_active_description)
                } else {
                    stringResource(R.string.accessibility_status_inactive_description)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenAccessibilitySettings) {
                Text(text = stringResource(R.string.open_accessibility_settings))
            }
        }
    }
}

@Composable
private fun DisclosureCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.accessibility_disclosure_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.accessibility_disclosure_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    focusEnabled: Boolean,
    instagramReelsEnabled: Boolean,
    dailyReelsLimitMinutes: Int,
    dailyReelsLimitMinuteOptions: List<Int>,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onDailyReelsLimitMinutesChange: (Int) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.preferences_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.preference_focus_enabled_title),
                description = stringResource(R.string.preference_focus_enabled_description),
                checked = focusEnabled,
                onCheckedChange = onFocusEnabledChange
            )
            ToggleRow(
                title = stringResource(R.string.preference_instagram_reels_enabled_title),
                description = stringResource(R.string.preference_instagram_reels_enabled_description),
                checked = instagramReelsEnabled,
                onCheckedChange = onInstagramReelsEnabledChange
            )
            DailyLimitSelector(
                selectedLimitMinutes = dailyReelsLimitMinutes,
                limitOptions = dailyReelsLimitMinuteOptions,
                onLimitSelected = onDailyReelsLimitMinutesChange
            )
        }
    }
}

@Composable
private fun DailyLimitSelector(
    selectedLimitMinutes: Int,
    limitOptions: List<Int>,
    onLimitSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.daily_reels_limit_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.daily_reels_limit_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            limitOptions.chunked(3).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { limitMinutes ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedLimitMinutes == limitMinutes,
                            onClick = { onLimitSelected(limitMinutes) },
                            label = {
                                Text(
                                    text = stringResource(
                                        R.string.daily_reels_limit_option_minutes,
                                        limitMinutes
                                    )
                                )
                            }
                        )
                    }
                    repeat(3 - rowOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun Context.openAccessibilitySettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreFocusScreenPreview() {
    MoreFocusTheme {
        MoreFocusScreen(
            uiState = MoreFocusUiState(
                serviceEnabled = false,
                focusEnabled = true,
                instagramReelsEnabled = true,
                dailyReelsLimitMinutes = 15,
                dailyReelsLimitMinuteOptions = listOf(5, 10, 15, 30, 60)
            ),
            onFocusEnabledChange = {},
            onInstagramReelsEnabledChange = {},
            onDailyReelsLimitMinutesChange = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
