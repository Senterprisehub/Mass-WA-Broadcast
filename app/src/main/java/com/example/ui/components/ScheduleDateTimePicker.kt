package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusCrimson
import com.example.util.DateUtils
import java.util.Calendar
import java.util.TimeZone

/**
 * Timezone-safe helpers for combining Material 3 DatePicker (UTC midnight millis)
 * and TimePicker (local hour/minute) without off-by-one drift in IST (UTC+5:30).
 */
object ScheduleDateTimeHelper {

    fun localEpochToUtcDateMidnight(localEpochMs: Long, localTz: TimeZone = TimeZone.getDefault()): Long {
        val localCal = Calendar.getInstance(localTz).apply { timeInMillis = localEpochMs }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis
    }

    fun applyUtcDateToLocalEpoch(
        currentLocalEpochMs: Long,
        selectedUtcDateMillis: Long,
        localTz: TimeZone = TimeZone.getDefault()
    ): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedUtcDateMillis
        }
        val localCal = Calendar.getInstance(localTz).apply {
            timeInMillis = currentLocalEpochMs
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }

    fun applyLocalTimeToEpoch(
        currentLocalEpochMs: Long,
        hourOfDay: Int,
        minute: Int,
        localTz: TimeZone = TimeZone.getDefault()
    ): Long {
        val localCal = Calendar.getInstance(localTz).apply {
            timeInMillis = currentLocalEpochMs
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDateTimePickerSection(
    scheduledEpoch: Long,
    onScheduledEpochChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val isInPast = scheduledEpoch <= System.currentTimeMillis()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Explicit Date & Time Picker buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("schedule_pick_date_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "Pick schedule date",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(DateUtils.formatDateOnly(scheduledEpoch))
            }

            FilledTonalButton(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("schedule_pick_time_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = "Pick schedule time",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(DateUtils.formatTimeOnly(scheduledEpoch))
            }
        }

        // Quick Preset Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = { onScheduledEpochChange(System.currentTimeMillis() + 15L * 60L * 1000L) },
                label = { Text("+15 min") }
            )
            FilterChip(
                selected = false,
                onClick = { onScheduledEpochChange(System.currentTimeMillis() + 60L * 60L * 1000L) },
                label = { Text("+1 hour") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onScheduledEpochChange(cal.timeInMillis)
                },
                label = { Text("Tomorrow 9:00 AM") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    val base = maxOf(scheduledEpoch, System.currentTimeMillis())
                    onScheduledEpochChange(base + 30L * 60L * 1000L)
                },
                label = { Text("+30m") }
            )
        }

        if (isInPast) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected time is in the past. Choose a future date or time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            onScheduledEpochChange(System.currentTimeMillis() + 15L * 60L * 1000L)
                        }
                    ) {
                        Text("Set +15m")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val todayUtcMidnight = remember {
            ScheduleDateTimeHelper.localEpochToUtcDateMidnight(System.currentTimeMillis())
        }
        val initialUtcDate = remember(scheduledEpoch) {
            ScheduleDateTimeHelper.localEpochToUtcDateMidnight(scheduledEpoch)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = maxOf(initialUtcDate, todayUtcMidnight),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayUtcMidnight
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcDate ->
                            onScheduledEpochChange(
                                ScheduleDateTimeHelper.applyUtcDateToLocalEpoch(scheduledEpoch, utcDate)
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                    Text("Set Date")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = remember(scheduledEpoch) {
            Calendar.getInstance().apply { timeInMillis = scheduledEpoch }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        var useKeyboardInput by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Schedule Time", style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = { useKeyboardInput = !useKeyboardInput },
                        modifier = Modifier.testTag("time_picker_toggle_input_mode")
                    ) {
                        Icon(
                            imageVector = if (useKeyboardInput) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            contentDescription = if (useKeyboardInput) "Switch to clock dial" else "Switch to keyboard input"
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (useKeyboardInput) {
                        TimeInput(state = timePickerState)
                    } else {
                        TimePicker(state = timePickerState)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onScheduledEpochChange(
                            ScheduleDateTimeHelper.applyLocalTimeToEpoch(
                                currentLocalEpochMs = scheduledEpoch,
                                hourOfDay = timePickerState.hour,
                                minute = timePickerState.minute
                            )
                        )
                        showTimePicker = false
                    },
                    modifier = Modifier.testTag("time_picker_confirm_button")
                ) {
                    Text("Set Time")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
