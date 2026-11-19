package br.com.morefocus

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.morefocus.blocking.FocusSchedule
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

private val weekdays = (Calendar.MONDAY..Calendar.SATURDAY) + Calendar.SUNDAY
private fun timeLabel(minutes: Int) = String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

@Composable
internal fun scheduleSummary(schedule: FocusSchedule): String {
    if (!schedule.enabled) return stringResource(R.string.schedule_off)
    val labels = DateFormatSymbols.getInstance().shortWeekdays
    val days = weekdays.filter(schedule::includes).joinToString(", ") { labels[it] }
    return stringResource(R.string.schedule_summary, days, timeLabel(schedule.startMinute), timeLabel(schedule.endMinute))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusScheduleDialog(initial: FocusSchedule, onSave: (FocusSchedule) -> Unit, onDismiss: () -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var days by rememberSaveable { mutableStateOf(initial.days) }
    var start by rememberSaveable { mutableStateOf(initial.startMinute) }
    var end by rememberSaveable { mutableStateOf(initial.endMinute) }
    val context = LocalContext.current
    val labels = DateFormatSymbols.getInstance()
    fun pick(value: Int, setTime: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> setTime(h * 60 + m) }, value / 60, value % 60,
            DateFormat.is24HourFormat(context)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_schedule)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow(stringResource(R.string.schedule_enabled), enabled, { enabled = it })
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (day in weekdays) {
                        val bit = FocusSchedule.dayBit(day)
                        FilterChip(
                            selected = days and bit != 0,
                            onClick = { days = days xor bit },
                            modifier = Modifier.semantics { contentDescription = labels.weekdays[day] },
                            label = { Text(labels.shortWeekdays[day].trimEnd('.')) }
                        )
                    }
                }
                TextButton(onClick = { pick(start) { start = it } }) { Text(stringResource(R.string.schedule_start, timeLabel(start))) }
                TextButton(onClick = { pick(end) { end = it } }) { Text(stringResource(R.string.schedule_end, timeLabel(end))) }
                if (end < start) Text(stringResource(R.string.schedule_overnight))
                if (end == start) Text(stringResource(R.string.schedule_equal_times), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = days != 0 && start != end, onClick = { onSave(FocusSchedule(enabled, days, start, end)) }) {
                Text(stringResource(R.string.schedule_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
