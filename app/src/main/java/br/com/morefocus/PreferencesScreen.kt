package br.com.morefocus

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.morefocus.blocking.FocusSchedule
import br.com.morefocus.blocking.FACEBOOK_PACKAGE_NAME
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import br.com.morefocus.blocking.KWAI_PACKAGE_NAME
import br.com.morefocus.blocking.TIKTOK_PACKAGE_NAME
import br.com.morefocus.blocking.X_PACKAGE_NAME
import br.com.morefocus.blocking.YOUTUBE_PACKAGE_NAME
import br.com.morefocus.ui.MoreFocusUiState

internal enum class FeaturedApp(
    val route: String,
    val packageName: String,
    @StringRes val nameRes: Int,
    @StringRes val summary: Int,
    @StringRes val videoType: Int,
    @DrawableRes val logo: Int
) {
    Instagram(
        "instagram", INSTAGRAM_PACKAGE_NAME, R.string.instagram_name,
        R.string.instagram_summary, R.string.reels_title, R.drawable.ic_instagram
    ),
    YouTube(
        "youtube", YOUTUBE_PACKAGE_NAME, R.string.youtube_name,
        R.string.shorts_title, R.string.shorts_title, R.drawable.ic_youtube
    ),
    X(
        "x", X_PACKAGE_NAME, R.string.x_name,
        R.string.x_videos_title, R.string.x_videos_title, R.drawable.ic_x
    ),
    TikTok(
        "tiktok", TIKTOK_PACKAGE_NAME, R.string.tiktok_name,
        R.string.tiktok_for_you_title, R.string.tiktok_for_you_title, R.drawable.ic_tiktok
    ),
    Facebook(
        "facebook", FACEBOOK_PACKAGE_NAME, R.string.facebook_name,
        R.string.reels_title, R.string.reels_title, R.drawable.ic_facebook
    ),
    Kwai(
        "kwai", KWAI_PACKAGE_NAME, R.string.kwai_name,
        R.string.kwai_feed_title, R.string.kwai_feed_title, R.drawable.ic_kwai
    )
}

internal fun featuredApp(packageName: String) = FeaturedApp.entries.find { it.packageName == packageName }

@Composable
internal fun ScreenHeader(@StringRes title: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.wordmark), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(title), modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
internal fun AccessibilityStatus(serviceEnabled: Boolean, onOpenSettings: () -> Unit) {
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    if (serviceEnabled) {
        TextButton(onClick = { showDisclosure = true }) { Text(stringResource(R.string.accessibility_status_active)) }
    } else {
        Surface(
            onClick = { showDisclosure = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Info, null, Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.accessibility_status_inactive), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.accessibility_status_inactive_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.open_accessibility_settings), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_disclosure_description), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onOpenSettings()
                }) { Text(stringResource(R.string.open_accessibility_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@Composable
internal fun PreferencesScreen(
    uiState: MoreFocusUiState,
    onSaveFocusSchedule: (FocusSchedule) -> Unit,
    onFocusEnabledChange: (Boolean) -> Unit,
    onShowUsageNotificationChange: (Boolean) -> Unit,
    onHardBlockVerticalVideosChange: (Boolean) -> Unit,
    onExponentialPauseLockEnabledChange: (Boolean) -> Unit,
    onLimitSelected: (Int) -> Unit,
    onOpenApp: (FeaturedApp) -> Unit,
    onOpenOtherApps: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var editLimit by rememberSaveable { mutableStateOf(false) }
    var editSchedule by rememberSaveable { mutableStateOf(false) }
    if (editSchedule) {
        FocusScheduleDialog(
            initial = uiState.focusSchedule,
            onSave = {
                onSaveFocusSchedule(it)
                editSchedule = false
            },
            onDismiss = { editSchedule = false }
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenHeader(R.string.preferences_title)
        if (!uiState.serviceEnabled) AccessibilityStatus(false, onOpenAccessibilitySettings)
        Card(
            onClick = { editLimit = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                val accent = MaterialTheme.colorScheme.primary
                Canvas(Modifier.matchParentSize()) {
                    val corner = Offset(size.width, size.height)
                    val radius = minOf(size.width * .30f, size.height)
                    drawCircle(accent.copy(alpha = .14f), radius, corner)
                    drawCircle(accent.copy(alpha = .24f), radius * .73f, corner)
                    drawCircle(accent.copy(alpha = .55f), radius * .46f, corner)
                }
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.shared_limit_title), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(limitOptionLabel(uiState.dailyVerticalVideosLimitSeconds),
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp), tint = accent)
                    }
                    Text(stringResource(R.string.shared_limit_caption), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column {
            SectionTitle(R.string.usage_apps_title)
            FeaturedApp.entries.forEach { app ->
                PreferenceLink(stringResource(app.nameRes), { onOpenApp(app) }, stringResource(app.summary),
                    leading = { Image(painterResource(app.logo), null, Modifier.size(28.dp)) })
                HorizontalDivider()
            }
            PreferenceLink(stringResource(R.string.other_apps_title), onOpenOtherApps)
        }
        Column {
            SectionTitle(R.string.general_title)
            PreferenceLink(stringResource(R.string.focus_schedule), { editSchedule = true }, scheduleSummary(uiState.focusSchedule))
            HorizontalDivider()
            ToggleRow(
                title = stringResource(R.string.preference_focus_enabled_title),
                checked = uiState.focusEnabled,
                onChange = onFocusEnabledChange
            )
            HorizontalDivider()
            ToggleRow(
                title = stringResource(R.string.preference_usage_notification_title),
                checked = uiState.showUsageNotification,
                onChange = onShowUsageNotificationChange
            )
            HorizontalDivider()
            ToggleRow(
                title = stringResource(R.string.preference_hard_block_vertical_videos_title),
                checked = uiState.hardBlockVerticalVideos,
                onChange = onHardBlockVerticalVideosChange,
                description = stringResource(R.string.preference_hard_block_vertical_videos_description)
            )
            HorizontalDivider()
            ToggleRow(
                title = stringResource(R.string.preference_exponential_pause_lock_title),
                checked = uiState.exponentialPauseLockEnabled,
                onChange = onExponentialPauseLockEnabledChange,
                description = stringResource(R.string.preference_exponential_pause_lock_description)
            )
        }
        if (uiState.serviceEnabled) AccessibilityStatus(true, onOpenAccessibilitySettings)
    }
    if (editLimit) {
        LimitDialog(R.string.daily_vertical_videos_limit_title, uiState.dailyVerticalVideosLimitSeconds,
            uiState.dailyVerticalVideosLimitSecondOptions, { editLimit = false }, onLimitSelected,
            stringResource(R.string.daily_vertical_videos_limit_description))
    }
}

@Composable
internal fun PreferencesDetailLayout(
    onBack: () -> Unit,
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_preferences))
            }
            Text(stringResource(R.string.preferences_title), style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!serviceEnabled) AccessibilityStatus(false, onOpenAccessibilitySettings)
            content()
        }
    }
}

@Composable
internal fun AppPreferencesScreen(
    app: FeaturedApp,
    uiState: MoreFocusUiState,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAddAppLimit: (String, Int) -> Unit,
    onLimitSelected: (String, Int) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    videoOptions: @Composable () -> Unit
) {
    var editLimit by rememberSaveable { mutableStateOf(false) }
    val rule = uiState.appLimitRules.find { it.packageName == app.packageName }
    val installed = uiState.selectableApps.any { it.packageName == app.packageName }
    PreferencesDetailLayout(onBack, uiState.serviceEnabled, onOpenAccessibilitySettings) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(app.logo), null, Modifier.size(36.dp))
            Text(stringResource(app.nameRes), modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Column {
            SectionTitle(app.videoType)
            videoOptions()
        }
        Column {
            SectionTitle(R.string.whole_app_title)
            PreferenceLink(
                title = stringResource(R.string.app_daily_limit),
                onClick = { editLimit = true },
                subtitle = if (rule == null && !installed) stringResource(R.string.app_not_installed) else null,
                value = rule?.let { limitOptionLabel(it.limitSeconds) } ?: stringResource(R.string.set_limit),
                enabled = rule?.enabled ?: installed,
                leading = { Icon(painterResource(R.drawable.ic_usage_notification), null) }
            )
            if (rule != null) {
                HorizontalDivider()
                ToggleRow(stringResource(R.string.rule_enabled), rule.enabled, { onEnabledChange(app.packageName, it) },
                    stringResource(R.string.app_limits_usage, durationLabel(rule.usageMillis), durationLabel(rule.limitMillis)))
                TextButton(onClick = { onRemove(app.packageName) }) { Text(stringResource(R.string.app_limits_remove)) }
            }
        }
    }
    if (editLimit) {
        LimitDialog(R.string.app_daily_limit, rule?.limitSeconds, uiState.appLimitSecondOptions, { editLimit = false }, {
            if (rule == null) onAddAppLimit(app.packageName, it) else onLimitSelected(app.packageName, it)
        })
    }
}

@Composable
internal fun InstagramPreferences(
    uiState: MoreFocusUiState,
    onReels: (Boolean) -> Unit,
    onHide: (Boolean) -> Unit
) {
    ToggleRow(stringResource(R.string.preference_reels_enabled_title), uiState.instagramReelsEnabled,
        onReels, stringResource(R.string.shared_limit_note))
    HorizontalDivider()
    ToggleRow(stringResource(R.string.preference_hide_feed_reels_title), uiState.hideFeedReels,
        onHide, stringResource(R.string.preference_hide_feed_reels_description))
}

@Composable
internal fun YouTubePreferences(
    uiState: MoreFocusUiState,
    onShorts: (Boolean) -> Unit,
    onHide: (Boolean) -> Unit
) {
    ToggleRow(stringResource(R.string.preference_youtube_shorts_enabled_title), uiState.youtubeShortsEnabled,
        onShorts, stringResource(R.string.shared_limit_note))
    HorizontalDivider()
    ToggleRow(stringResource(R.string.preference_hide_feed_shorts_title), uiState.hideFeedShorts,
        onHide, stringResource(R.string.preference_hide_feed_shorts_description))
}

@Composable
private fun LimitDialog(
    @StringRes title: Int,
    selected: Int?,
    options: List<Int>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    description: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (description != null) Text(description)
                LimitOptionChips(selected, options) {
                    onSelect(it)
                    onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun PreferenceLink(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = title }.heightIn(min = 56.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked, onCheckedChange = null)
    }
}
