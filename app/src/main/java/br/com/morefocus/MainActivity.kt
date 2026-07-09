package br.com.morefocus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.data.SelectableApp
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
        onAddAppLimit = viewModel::addAppLimit,
        onAppLimitMinutesChange = viewModel::setAppLimitMinutes,
        onAppLimitEnabledChange = viewModel::setAppLimitEnabled,
        onRemoveAppLimit = viewModel::removeAppLimit,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings
    )
}

private enum class MoreFocusDestination(
    val route: String,
    @StringRes val labelResId: Int
) {
    Usage("usage", R.string.usage_tab_title),
    Preferences("preferences", R.string.preferences_tab_title)
}

@Composable
private fun MoreFocusScreen(
    uiState: MoreFocusUiState,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onDailyReelsLimitMinutesChange: (Int) -> Unit,
    onAddAppLimit: (String, Int) -> Unit,
    onAppLimitMinutesChange: (String, Int) -> Unit,
    onAppLimitEnabledChange: (String, Boolean) -> Unit,
    onRemoveAppLimit: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val navController = rememberNavController()
    val destinations = remember { MoreFocusDestination.entries.toList() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MoreFocusDestination.Usage.route
    val selectedTabIndex = destinations
        .indexOfFirst { destination -> destination.route == currentRoute }
        .coerceAtLeast(0)

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
            ) {
                Header(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = 24.dp,
                        end = 20.dp,
                        bottom = 16.dp
                    )
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    destinations.forEach { destination ->
                        Tab(
                            selected = destination.route == currentRoute,
                            onClick = {
                                if (destination.route != currentRoute) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            text = { Text(text = stringResource(destination.labelResId)) }
                        )
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = MoreFocusDestination.Usage.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(MoreFocusDestination.Usage.route) {
                        UsageScreen(uiState = uiState)
                    }
                    composable(MoreFocusDestination.Preferences.route) {
                        PreferencesScreen(
                            uiState = uiState,
                            onFocusEnabledChange = onFocusEnabledChange,
                            onInstagramReelsEnabledChange = onInstagramReelsEnabledChange,
                            onDailyReelsLimitMinutesChange = onDailyReelsLimitMinutesChange,
                            onAddAppLimit = onAddAppLimit,
                            onAppLimitMinutesChange = onAppLimitMinutesChange,
                            onAppLimitEnabledChange = onAppLimitEnabledChange,
                            onRemoveAppLimit = onRemoveAppLimit,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesScreen(
    uiState: MoreFocusUiState,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onDailyReelsLimitMinutesChange: (Int) -> Unit,
    onAddAppLimit: (String, Int) -> Unit,
    onAppLimitMinutesChange: (String, Int) -> Unit,
    onAppLimitEnabledChange: (String, Boolean) -> Unit,
    onRemoveAppLimit: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val scrollable = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollable)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            onDailyReelsLimitMinutesChange = onDailyReelsLimitMinutesChange,
            appLimitRules = uiState.appLimitRules,
            selectableApps = uiState.selectableApps,
            appLimitMinuteOptions = uiState.appLimitMinuteOptions,
            onAddAppLimit = onAddAppLimit,
            onAppLimitMinutesChange = onAppLimitMinutesChange,
            onAppLimitEnabledChange = onAppLimitEnabledChange,
            onRemoveAppLimit = onRemoveAppLimit
        )
    }
}

@Composable
private fun UsageScreen(uiState: MoreFocusUiState) {
    val scrollable = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollable)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.usage_today_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.usage_today_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UsageSummaryCard(
            title = stringResource(R.string.usage_reels_title),
            usedMillis = uiState.dailyReelsUsageMillis,
            limitMillis = uiState.dailyReelsLimitMinutes.toLimitMillis(),
            enabled = uiState.instagramReelsEnabled
        )

        Text(
            text = stringResource(R.string.usage_apps_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (uiState.appLimitRules.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_apps_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.appLimitRules.forEach { rule ->
                    UsageSummaryCard(
                        title = rule.appName,
                        usedMillis = rule.usageMillis,
                        limitMillis = rule.limitMillis,
                        enabled = rule.enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(
    title: String,
    usedMillis: Long,
    limitMillis: Long,
    enabled: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UsageCircularProgress(
                usedMillis = usedMillis,
                limitMillis = limitMillis
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.usage_time,
                        usedMillis.toWholeMinutes(),
                        limitMillis.toWholeMinutes()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!enabled) {
                    Text(
                        text = stringResource(R.string.usage_rule_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (usedMillis >= limitMillis) {
                    Text(
                        text = stringResource(R.string.usage_limit_reached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageCircularProgress(
    usedMillis: Long,
    limitMillis: Long
) {
    val safeLimitMillis = limitMillis.coerceAtLeast(1L)
    val progress = (usedMillis.toFloat() / safeLimitMillis.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = stringResource(R.string.usage_progress_minutes, usedMillis.toWholeMinutes()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
    onDailyReelsLimitMinutesChange: (Int) -> Unit,
    appLimitRules: List<AppLimitRule>,
    selectableApps: List<SelectableApp>,
    appLimitMinuteOptions: List<Int>,
    onAddAppLimit: (String, Int) -> Unit,
    onAppLimitMinutesChange: (String, Int) -> Unit,
    onAppLimitEnabledChange: (String, Boolean) -> Unit,
    onRemoveAppLimit: (String) -> Unit
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
            AppLimitSection(
                appLimitRules = appLimitRules,
                selectableApps = selectableApps,
                limitOptions = appLimitMinuteOptions,
                onAddAppLimit = onAddAppLimit,
                onLimitSelected = onAppLimitMinutesChange,
                onEnabledChange = onAppLimitEnabledChange,
                onRemove = onRemoveAppLimit
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
private fun AppLimitSection(
    appLimitRules: List<AppLimitRule>,
    selectableApps: List<SelectableApp>,
    limitOptions: List<Int>,
    onAddAppLimit: (String, Int) -> Unit,
    onLimitSelected: (String, Int) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val configuredPackages = appLimitRules.map { it.packageName }.toSet()
    val availableApps = selectableApps.filterNot { it.packageName in configuredPackages }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.app_limits_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.app_limits_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (appLimitRules.isEmpty()) {
            Text(
                text = stringResource(R.string.app_limits_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                appLimitRules.forEach { rule ->
                    AppLimitRuleRow(
                        rule = rule,
                        limitOptions = limitOptions,
                        onLimitSelected = onLimitSelected,
                        onEnabledChange = onEnabledChange,
                        onRemove = onRemove
                    )
                }
            }
        }

        Button(
            onClick = { showAddDialog = true },
            enabled = availableApps.isNotEmpty()
        ) {
            Text(text = stringResource(R.string.app_limits_add))
        }

        if (availableApps.isEmpty() && selectableApps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.app_limits_no_available_apps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAddDialog) {
        AddAppLimitDialog(
            availableApps = availableApps,
            limitOptions = limitOptions,
            onDismiss = { showAddDialog = false },
            onAddAppLimit = { packageName, limitMinutes ->
                onAddAppLimit(packageName, limitMinutes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AppLimitRuleRow(
    rule: AppLimitRule,
    limitOptions: List<Int>,
    onLimitSelected: (String, Int) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = rule.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.app_limits_usage,
                        rule.usageMillis.toWholeMinutes(),
                        rule.limitMinutes
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { enabled -> onEnabledChange(rule.packageName, enabled) }
            )
        }

        LimitOptionChips(
            selectedLimitMinutes = rule.limitMinutes,
            limitOptions = limitOptions,
            enabled = rule.enabled,
            onLimitSelected = { limitMinutes ->
                onLimitSelected(rule.packageName, limitMinutes)
            }
        )

        TextButton(onClick = { onRemove(rule.packageName) }) {
            Text(text = stringResource(R.string.app_limits_remove))
        }
    }
}

@Composable
private fun AddAppLimitDialog(
    availableApps: List<SelectableApp>,
    limitOptions: List<Int>,
    onDismiss: () -> Unit,
    onAddAppLimit: (String, Int) -> Unit
) {
    var appMenuExpanded by remember { mutableStateOf(false) }
    var selectedPackageName by remember(availableApps) {
        mutableStateOf(availableApps.firstOrNull()?.packageName.orEmpty())
    }
    var selectedLimitMinutes by remember(limitOptions) {
        mutableStateOf(limitOptions.firstOrNull() ?: 15)
    }
    val selectedApp = availableApps.firstOrNull { it.packageName == selectedPackageName }
        ?: availableApps.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.app_limits_add_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.app_limits_choose_app),
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { appMenuExpanded = true },
                        enabled = availableApps.isNotEmpty()
                    ) {
                        Text(
                            text = selectedApp?.displayName
                                ?: stringResource(R.string.app_limits_no_apps)
                        )
                    }
                    DropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false }
                    ) {
                        availableApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(text = app.displayName) },
                                onClick = {
                                    selectedPackageName = app.packageName
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                LimitOptionChips(
                    selectedLimitMinutes = selectedLimitMinutes,
                    limitOptions = limitOptions,
                    enabled = selectedApp != null,
                    onLimitSelected = { selectedLimitMinutes = it }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedApp != null,
                onClick = {
                    selectedApp?.let { app ->
                        onAddAppLimit(app.packageName, selectedLimitMinutes)
                    }
                }
            ) {
                Text(text = stringResource(R.string.app_limits_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun LimitOptionChips(
    selectedLimitMinutes: Int,
    limitOptions: List<Int>,
    enabled: Boolean = true,
    onLimitSelected: (Int) -> Unit
) {
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
                        enabled = enabled,
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

private const val MILLIS_PER_MINUTE = 60 * 1000L

private fun Int.toLimitMillis(): Long =
    coerceAtLeast(1).toLong() * MILLIS_PER_MINUTE

private fun Long.toWholeMinutes(): Int =
    (this / MILLIS_PER_MINUTE).toInt()

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
                dailyReelsUsageMillis = 7 * 60 * 1000L,
                dailyReelsLimitMinuteOptions = listOf(5, 10, 15, 30, 60),
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.instagram.android",
                        appName = "Instagram",
                        limitMinutes = 30,
                        usageMillis = 12 * 60 * 1000L
                    ),
                    AppLimitRule(
                        packageName = "com.youtube.android",
                        appName = "YouTube",
                        limitMinutes = 15,
                        usageMillis = 18 * 60 * 1000L
                    )
                ),
                selectableApps = listOf(
                    SelectableApp("com.tiktok", "TikTok"),
                    SelectableApp("com.youtube", "YouTube")
                ),
                appLimitMinuteOptions = listOf(5, 10, 15, 30, 60)
            ),
            onFocusEnabledChange = {},
            onInstagramReelsEnabledChange = {},
            onDailyReelsLimitMinutesChange = {},
            onAddAppLimit = { _, _ -> },
            onAppLimitMinutesChange = { _, _ -> },
            onAppLimitEnabledChange = { _, _ -> },
            onRemoveAppLimit = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
