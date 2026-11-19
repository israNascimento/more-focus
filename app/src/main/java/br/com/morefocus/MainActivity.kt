package br.com.morefocus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
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
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            (application as MoreFocusApplication).appContainer.dailyUsageNotification.refresh()
        }
    }

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
            MoreFocusTheme(dynamicColor = false) {
                MoreFocusApp(
                    viewModel = viewModel,
                    onShowUsageNotificationChange = { show ->
                        viewModel.setShowUsageNotification(show)
                        if (show) requestNotificationPermission(userInitiated = true)
                    },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        val state = viewModel.uiState.value
        if (state.serviceEnabled && state.showUsageNotification) requestNotificationPermission()
    }

    private fun requestNotificationPermission(userInitiated: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return

        val preferences = getPreferences(MODE_PRIVATE)
        if (!preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false) ||
            (userInitiated && shouldShowRequestPermissionRationale(permission))
        ) {
            preferences.edit { putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true) }
            notificationPermission.launch(permission)
        } else if (userInitiated) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}

@Composable
private fun MoreFocusApp(
    viewModel: MoreFocusViewModel,
    onShowUsageNotificationChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MoreFocusScreen(
        uiState = uiState,
        onFocusEnabledChange = viewModel::setFocusEnabled,
        onShowUsageNotificationChange = onShowUsageNotificationChange,
        onInstagramReelsEnabledChange = viewModel::setInstagramReelsEnabled,
        onHideFeedReelsChange = viewModel::setHideFeedReels,
        onHardBlockReelsChange = viewModel::setHardBlockReels,
        onYouTubeShortsEnabledChange = viewModel::setYouTubeShortsEnabled,
        onExponentialPauseLockEnabledChange = viewModel::setExponentialPauseLockEnabled,
        onDailyVerticalVideosLimitSecondsChange =
            viewModel::setDailyVerticalVideosLimitSeconds,
        onAddAppLimit = viewModel::addAppLimit,
        onAppLimitSecondsChange = viewModel::setAppLimitSeconds,
        onAppLimitEnabledChange = viewModel::setAppLimitEnabled,
        onRemoveAppLimit = viewModel::removeAppLimit,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings
    )
}

private enum class MoreFocusDestination(
    val route: String,
    @StringRes val labelResId: Int,
    @DrawableRes val icon: Int
) {
    Usage("usage", R.string.usage_tab_title, R.drawable.ic_usage),
    Preferences("preferences", R.string.preferences_tab_title, R.drawable.ic_preferences)
}

@Composable
internal fun MoreFocusScreen(
    uiState: MoreFocusUiState,
    onFocusEnabledChange: (Boolean) -> Unit,
    onShowUsageNotificationChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onHideFeedReelsChange: (Boolean) -> Unit,
    onHardBlockReelsChange: (Boolean) -> Unit,
    onYouTubeShortsEnabledChange: (Boolean) -> Unit,
    onExponentialPauseLockEnabledChange: (Boolean) -> Unit,
    onDailyVerticalVideosLimitSecondsChange: (Int) -> Unit,
    onAddAppLimit: (String, Int) -> Unit,
    onAppLimitSecondsChange: (String, Int) -> Unit,
    onAppLimitEnabledChange: (String, Boolean) -> Unit,
    onRemoveAppLimit: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MoreFocusDestination.Usage.route
    Scaffold(
        bottomBar = {
            if (MoreFocusDestination.entries.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MoreFocusDestination.entries.forEach { destination ->
                        NavigationBarItem(
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
                            icon = { Icon(painterResource(destination.icon), null) },
                            label = { Text(stringResource(destination.labelResId)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MoreFocusDestination.Usage.route,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(MoreFocusDestination.Usage.route) {
                UsageScreen(uiState, onOpenAccessibilitySettings)
            }
            composable(MoreFocusDestination.Preferences.route) {
                PreferencesScreen(
                    uiState = uiState,
                    onFocusEnabledChange = onFocusEnabledChange,
                    onShowUsageNotificationChange = onShowUsageNotificationChange,
                    onExponentialPauseLockEnabledChange = onExponentialPauseLockEnabledChange,
                    onLimitSelected = onDailyVerticalVideosLimitSecondsChange,
                    onOpenApp = { navController.navigate(it.route) },
                    onOpenOtherApps = { navController.navigate("other-apps") },
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
            FeaturedApp.entries.forEach { app ->
                composable(app.route) {
                    AppPreferencesScreen(
                        app = app,
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onAddAppLimit = onAddAppLimit,
                        onLimitSelected = onAppLimitSecondsChange,
                        onEnabledChange = onAppLimitEnabledChange,
                        onRemove = onRemoveAppLimit
                    ) {
                        when (app) {
                            FeaturedApp.Instagram -> InstagramPreferences(
                                uiState, onInstagramReelsEnabledChange,
                                onHideFeedReelsChange, onHardBlockReelsChange
                            )
                            FeaturedApp.YouTube -> ToggleRow(
                                stringResource(R.string.preference_youtube_shorts_enabled_title),
                                uiState.youtubeShortsEnabled,
                                onYouTubeShortsEnabledChange,
                                stringResource(R.string.shared_limit_note)
                            )
                        }
                    }
                }
            }
            composable("other-apps") {
                PreferencesDetailLayout(
                    onBack = { navController.popBackStack() },
                    serviceEnabled = uiState.serviceEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                ) {
                    AppLimitSection(
                        appLimitRules = uiState.appLimitRules.filter { featuredApp(it.packageName) == null },
                        selectableApps = uiState.selectableApps.filter { featuredApp(it.packageName) == null },
                        limitOptions = uiState.appLimitSecondOptions,
                        onAddAppLimit = onAddAppLimit,
                        onLimitSelected = onAppLimitSecondsChange,
                        onEnabledChange = onAppLimitEnabledChange,
                        onRemove = onRemoveAppLimit
                    )
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
            onAddAppLimit = { packageName, limitSeconds ->
                onAddAppLimit(packageName, limitSeconds)
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
                        durationLabel(rule.usageMillis),
                        durationLabel(rule.limitMillis)
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
            selectedLimitSeconds = rule.limitSeconds,
            limitOptions = limitOptions,
            enabled = rule.enabled,
            onLimitSelected = { limitSeconds ->
                onLimitSelected(rule.packageName, limitSeconds)
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
    var selectedLimitSeconds by remember(limitOptions) {
        mutableStateOf(limitOptions.firstOrNull() ?: 15 * 60)
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
                    selectedLimitSeconds = selectedLimitSeconds,
                    limitOptions = limitOptions,
                    enabled = selectedApp != null,
                    onLimitSelected = { selectedLimitSeconds = it }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedApp != null,
                onClick = {
                    selectedApp?.let { app ->
                        onAddAppLimit(app.packageName, selectedLimitSeconds)
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
internal fun LimitOptionChips(
    selectedLimitSeconds: Int?,
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
                rowOptions.forEach { limitSeconds ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedLimitSeconds == limitSeconds,
                        enabled = enabled,
                        onClick = { onLimitSelected(limitSeconds) },
                        label = {
                            Text(text = limitOptionLabel(limitSeconds))
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

private fun Context.openAccessibilitySettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun durationLabel(durationMillis: Long): String {
    val durationSeconds = (durationMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()
    return if (durationSeconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.duration_seconds, durationSeconds)
    } else {
        stringResource(R.string.duration_minutes, durationSeconds / SECONDS_PER_MINUTE)
    }
}

@Composable
internal fun limitOptionLabel(limitSeconds: Int): String =
    if (limitSeconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.duration_seconds, limitSeconds)
    } else {
        stringResource(R.string.duration_minutes, limitSeconds / SECONDS_PER_MINUTE)
    }

@Preview(showBackground = true)
@Composable
private fun MoreFocusScreenPreview() {
    MoreFocusTheme(dynamicColor = false) {
        MoreFocusScreen(
            uiState = MoreFocusUiState(
                serviceEnabled = false,
                focusEnabled = true,
                instagramReelsEnabled = true,
                youtubeShortsEnabled = true,
                exponentialPauseLockEnabled = true,
                dailyVerticalVideosLimitSeconds = 15 * 60,
                dailyVerticalVideosUsageMillis = 7 * 60 * 1000L,
                dailyVerticalVideosLimitSecondOptions = listOf(
                    30,
                    5 * 60,
                    10 * 60,
                    15 * 60,
                    30 * 60,
                    60 * 60
                ),
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = FeaturedApp.Instagram.packageName,
                        appName = "Instagram",
                        limitSeconds = 30 * 60,
                        usageMillis = 12 * 60 * 1000L
                    ),
                    AppLimitRule(
                        packageName = FeaturedApp.YouTube.packageName,
                        appName = "YouTube",
                        limitSeconds = 15 * 60,
                        usageMillis = 18 * 60 * 1000L
                    )
                ),
                selectableApps = listOf(
                    SelectableApp("com.tiktok", "TikTok"),
                    SelectableApp(FeaturedApp.YouTube.packageName, "YouTube")
                ),
                appLimitSecondOptions = listOf(
                    30,
                    5 * 60,
                    10 * 60,
                    15 * 60,
                    30 * 60,
                    60 * 60
                )
            ),
            onFocusEnabledChange = {},
            onShowUsageNotificationChange = {},
            onInstagramReelsEnabledChange = {},
            onHideFeedReelsChange = {},
            onHardBlockReelsChange = {},
            onYouTubeShortsEnabledChange = {},
            onExponentialPauseLockEnabledChange = {},
            onDailyVerticalVideosLimitSecondsChange = {},
            onAddAppLimit = { _, _ -> },
            onAppLimitSecondsChange = { _, _ -> },
            onAppLimitEnabledChange = { _, _ -> },
            onRemoveAppLimit = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
