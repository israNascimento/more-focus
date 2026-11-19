package br.com.morefocus

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.ui.MoreFocusUiState

@Composable
internal fun UsageScreen(uiState: MoreFocusUiState, onOpenAccessibilitySettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenHeader(R.string.usage_today_title)
        if (!uiState.serviceEnabled) AccessibilityStatus(false, onOpenAccessibilitySettings)
        SharedUsageCard(uiState)
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
        !uiState.instagramReelsEnabled && !uiState.youtubeShortsEnabled &&
            !uiState.xVideosEnabled && !uiState.tikTokForYouEnabled && !uiState.facebookReelsEnabled -> R.string.usage_rule_disabled
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
