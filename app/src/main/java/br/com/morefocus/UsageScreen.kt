package br.com.morefocus

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.ui.MoreFocusUiState
import java.util.Date

@Composable
internal fun UsageScreen(
    uiState: MoreFocusUiState,
    onStartFocusSession: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenHeader(R.string.usage_today_title)
        if (!uiState.serviceEnabled) AccessibilityStatus(false, onOpenAccessibilitySettings)
        SharedUsageCard(uiState)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (uiState.focusSessionEndsAtMillis > 0L) {
                val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(uiState.focusSessionEndsAtMillis))
                Text(stringResource(R.string.focus_session_until, time), style = MaterialTheme.typography.titleMedium)
            } else {
                Button(
                    onClick = onStartFocusSession,
                    enabled = uiState.focusEnabled && uiState.serviceEnabled && uiState.hasVerticalVideoRule
                ) { Text(stringResource(R.string.start_focus_session)) }
            }
            val description = when {
                !uiState.focusEnabled -> R.string.focus_session_enable_focus
                !uiState.hasVerticalVideoRule -> R.string.focus_session_enable_rules
                else -> R.string.focus_session_description
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(R.string.usage_apps_title)
            Text(
                stringResource(if (uiState.appLimitRules.isEmpty()) R.string.usage_apps_empty else R.string.usage_apps_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.appLimitRules.forEach { rule ->
                AppUsageRow(rule)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SharedUsageCard(uiState: MoreFocusUiState) {
    val used = uiState.dailyVerticalVideosUsageMillis
    val limit = uiState.dailyVerticalVideosLimitSeconds * 1000L
    val remaining = (limit - used).coerceAtLeast(0L)
    val caption = when {
        !uiState.hasVerticalVideoRule -> R.string.usage_rule_disabled
        used >= limit -> R.string.usage_limit_reached
        else -> R.string.usage_remaining_today
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.usage_vertical_videos_title), style = MaterialTheme.typography.titleMedium)
                    Text(durationLabel(remaining), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(caption), style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { usageFraction(used, limit) }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f)
                    )
                    Icon(painterResource(R.drawable.ic_usage_notification), null, Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .15f))
            Text(stringResource(R.string.usage_time, durationLabel(used), durationLabel(limit)),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppUsageRow(rule: AppLimitRule) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val app = featuredApp(rule.packageName)
        if (app != null) {
            Image(painterResource(app.logo), null, Modifier.size(28.dp))
        } else {
            Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text(rule.appName.take(1), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rule.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.usage_time, durationLabel(rule.usageMillis), durationLabel(rule.limitMillis)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { usageFraction(rule.usageMillis, rule.limitMillis) }, modifier = Modifier.fillMaxWidth(),
                color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (!rule.enabled || rule.usageMillis >= rule.limitMillis) {
                Text(stringResource(if (rule.enabled) R.string.usage_limit_reached else R.string.usage_rule_disabled),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun usageFraction(used: Long, limit: Long) =
    (used.toFloat() / limit.coerceAtLeast(1L)).coerceIn(0f, 1f)
