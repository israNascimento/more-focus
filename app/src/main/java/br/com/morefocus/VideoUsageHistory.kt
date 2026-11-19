package br.com.morefocus

import android.app.DatePickerDialog
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.morefocus.blocking.VideoUsageDay
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
internal fun VideoUsageHistory(days: List<VideoUsageDay>) {
    if (days.isEmpty()) return
    val context = LocalContext.current
    val dateFormat = DateFormat.getLongDateFormat(context)
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = days.indexOfFirst { it.dateMillis == selectedDate }.coerceAtLeast(0)
    val day = days[selected]
    val colors = MaterialTheme.colorScheme
    val dateList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(R.string.weekly_videos)
            TextButton(onClick = {
                val calendar = Calendar.getInstance().apply { timeInMillis = day.dateMillis }
                DatePickerDialog(context, { _, year, month, date ->
                    calendar.clear()
                    calendar.set(year, month, date)
                    val index = days.indexOfFirst { it.dateMillis == calendar.timeInMillis }
                    if (index >= 0) {
                        selectedDate = if (index == 0) null else days[index].dateMillis
                        scope.launch { dateList.scrollToItem(index) }
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = days.last().dateMillis
                    datePicker.maxDate = days.first().dateMillis
                }.show()
            }) { Text(stringResource(R.string.history_calendar)) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth / 7).coerceAtLeast(44.dp)
            LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = dateList, reverseLayout = true) {
                items(days.size, key = { days[it].dateMillis }) { index ->
                    val active = index == selected
                    val millis = days[index].dateMillis
                    val dateLabel = dateFormat.format(millis)
                    Column(
                        Modifier.width(dayWidth).clip(RoundedCornerShape(14.dp))
                            .background(if (active) colors.primary else Color.Transparent)
                            .selectable(active, role = Role.RadioButton) {
                                selectedDate = if (index == 0) null else millis
                            }
                            .semantics { contentDescription = dateLabel }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val weekday = if (index == 0) stringResource(R.string.history_today)
                            else DateFormat.format("EEE", millis).toString().trimEnd('.')
                        Text(weekday, style = MaterialTheme.typography.labelMedium,
                            color = if (active) colors.onPrimary else colors.onSurfaceVariant)
                        Text(DateFormat.format("d", millis).toString(), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = if (active) colors.onPrimary else colors.onSurface)
                    }
                }
            }
        }
        val total = day.usedMillis
        val unattributedMillis = (total ?: 0L) - day.videoMillisByPackage.values.sum()
        Surface(shape = RoundedCornerShape(18.dp), color = colors.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(DateUtils.formatDateTime(context, day.dateMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR),
                    style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.general_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text(if (total == null) stringResource(R.string.weekly_unrecorded) else videoDuration(total),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (total != null) {
                    val extensions = if (day.extensions == 0) stringResource(R.string.history_no_extensions)
                        else pluralStringResource(R.plurals.history_extensions, day.extensions, day.extensions)
                    Text(extensions, style = MaterialTheme.typography.bodySmall)
                    if (unattributedMillis > 0L) {
                        Text(stringResource(R.string.history_before_breakdown, videoDuration(unattributedMillis)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (total != null) {
            val apps = FeaturedApp.entries.sortedByDescending { day.videoMillisByPackage[it.packageName] ?: 0L }
            apps.forEach { app ->
                val used = day.videoMillisByPackage[app.packageName] ?: if (unattributedMillis > 0L) null else 0L
                VideoAppRow(app, used, total)
            }
        }
    }
}

@Composable
private fun VideoAppRow(app: FeaturedApp, used: Long?, total: Long) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(app.logo), null, Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(app.nameRes), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (used != null) {
                    Text(videoDuration(used), style = MaterialTheme.typography.labelLarge)
                } else {
                    val unknown = stringResource(R.string.history_no_breakdown)
                    Text("—", Modifier.clearAndSetSemantics { contentDescription = unknown })
                }
            }
            LinearProgressIndicator(progress = { if (total > 0L) (used ?: 0L).toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
        }
    }
}

@Composable
private fun videoDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        millis in 1L..999L -> stringResource(R.string.history_under_second)
        seconds >= 60 && seconds % 60 != 0L -> stringResource(R.string.history_minutes_seconds, seconds / 60, seconds % 60)
        else -> durationLabel(millis)
    }
}
