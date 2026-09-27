package com.khanabook.lite.pos.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.core.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.core.theme.*
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
private const val DAYS_PER_WEEK = 7

/**
 * Caps the sheet on tablets/foldables. Seven columns across 600dp would give ~85dp cells,
 * which reads as oversized; 400dp keeps every cell near 54dp so the grid stays compact.
 */
private val CALENDAR_MAX_WIDTH = 400.dp

/** Height of the range band, and the diameter of the selection circle drawn over it. */
private val SELECTION_SIZE = 34.dp

/** Low-alpha gold fill connecting the days between the range start and end. */
private val RANGE_BAND_COLOR = PrimaryGold.copy(alpha = 0.16f)

/** The app's one background gradient, shared by every screen. */
private val APP_BACKGROUND_BRUSH = Brush.verticalGradient(
    listOf(DarkBrown1, DarkBrown2, RichEspresso)
)

/**
 * Custom single-month range picker.
 *
 * Material3's own [DateRangePicker] cannot be used for this: it renders every month in
 * `yearRange` as one tall vertically-scrolling [LazyColumn] and exposes only `title` /
 * `headline` slots. That produces a duplicated month heading (M3's scrolling subhead plus
 * any nav row we add), the following month bleeding into view at the bottom, and a large
 * amount of dead vertical space.
 *
 * So the grid is laid out here instead. [DateRangePickerState] is still the single source of
 * truth for the selection, which keeps range semantics, the quick presets, `yearRange`
 * bounds and the public signature of this composable unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerDialog(
    initialStartDateMillis: Long? = null,
    initialEndDateMillis: Long? = null,
    state: DateRangePickerState? = null,
    showQuickPresets: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDateMillis?.let(::localDayToUtcMillis)
            ?: state?.selectedStartDateMillis
            ?: localDayToUtcMillis(getStartOfDayMillis()),
        initialSelectedEndDateMillis = initialEndDateMillis?.let(::localDayToUtcMillis)
            ?: state?.selectedEndDateMillis
            ?: localDayToUtcMillis(getEndOfDayMillis())
    )

    val startUtc = rangeState.selectedStartDateMillis
    val endUtc = rangeState.selectedEndDateMillis
    val canApply = startUtc != null && endUtc != null
    val yearRange = rangeState.yearRange

    // Content-sized, but never taller than the window: on short or landscape screens the
    // column scrolls instead of clipping, and on normal phones no scrollbar ever appears.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(layout.dialogWidthFraction)
                .widthIn(max = minOf(layout.dialogMaxWidth, CALENDAR_MAX_WIDTH))
                // Every screen in the app sits on this exact three-stop gradient. A flat
                // fill here read as a third, mismatched colour floating over the app, so
                // the sheet carries the same background as the screen behind it.
                .background(APP_BACKGROUND_BRUSH, KhanaRadii.xl),
            shape = KhanaRadii.xl,
            color = Color.Transparent,
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(vertical = spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MonthHeader(
                    monthMillis = rangeState.displayedMonthMillis,
                    canGoBack = utcYear(shiftedMonth(rangeState.displayedMonthMillis, -1)) in yearRange,
                    canGoForward = utcYear(shiftedMonth(rangeState.displayedMonthMillis, 1)) in yearRange,
                    onPrev = { rangeState.displayedMonthMillis = shiftedMonth(rangeState.displayedMonthMillis, -1) },
                    onNext = { rangeState.displayedMonthMillis = shiftedMonth(rangeState.displayedMonthMillis, 1) }
                )

                Spacer(modifier = Modifier.height(spacing.smallMedium))

                RangeSummary(startUtc = startUtc, endUtc = endUtc)

                if (showQuickPresets) {
                    Spacer(modifier = Modifier.height(spacing.smallMedium))
                    QuickPresets(
                        onSelect = { s, e -> rangeState.setSelection(s, e) }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.smallMedium))
                GoldDivider()
                Spacer(modifier = Modifier.height(spacing.smallMedium))

                MonthGrid(
                    monthMillis = rangeState.displayedMonthMillis,
                    startUtc = startUtc,
                    endUtc = endUtc,
                    onDayClick = { dayUtc ->
                        val currentStart = rangeState.selectedStartDateMillis
                        val currentEnd = rangeState.selectedEndDateMillis
                        // Tapping while a complete range is showing restarts it, so a second
                        // tap never silently rewrites the range the user just made.
                        if (currentStart == null || currentEnd != null) {
                            rangeState.setSelection(dayUtc, null)
                        } else if (dayUtc < currentStart) {
                            rangeState.setSelection(dayUtc, currentStart)
                        } else {
                            rangeState.setSelection(currentStart, dayUtc)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(spacing.smallMedium))
                GoldDivider()
                Spacer(modifier = Modifier.height(spacing.smallMedium))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                ) {
                    KhanaSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    KhanaPrimaryButton(
                        text = "Apply",
                        onClick = {
                            val s = rangeState.selectedStartDateMillis
                            val e = rangeState.selectedEndDateMillis
                            if (s != null && e != null) {
                                onConfirm(
                                    getStartOfDayMillis(localCalendarForUtcDay(s)),
                                    getEndOfDayMillis(localCalendarForUtcDay(e))
                                )
                            }
                            onDismiss()
                        },
                        enabled = canApply,
                        modifier = Modifier.weight(1f),
                        height = spacing.buttonHeightLarge
                    )
                }
            }
        }
    }
}

/** Month title flanked by its navigation arrows, so the month is named exactly once. */
@Composable
private fun MonthHeader(
    monthMillis: Long,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthNavButton(Icons.Default.ChevronLeft, "Previous month", canGoBack, onPrev)
        Text(
            text = formatMonthYear(monthMillis),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PrimaryGold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.small)
        )
        MonthNavButton(Icons.Default.ChevronRight, "Next month", canGoForward, onNext)
    }
}

@Composable
private fun MonthNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = DarkBrown1.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) PrimaryGold else TextGold.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Selected range plus its day count, sitting directly under the month header. */
@Composable
private fun RangeSummary(startUtc: Long?, endUtc: Long?) {
    val spacing = KhanaBookTheme.spacing
    val withYear = listOfNotNull(startUtc, endUtc)
        .any { utcYear(it) != Calendar.getInstance().get(Calendar.YEAR) }

    val (label, days) = when {
        startUtc != null && endUtc != null ->
            "${formatUtcDay(startUtc, withYear)}  –  ${formatUtcDay(endUtc, withYear)}" to
                (endUtc - startUtc) / MILLIS_PER_DAY + 1
        startUtc != null -> "${formatUtcDay(startUtc, withYear)}  –  pick end date" to null
        else -> "Select a start date" to null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (days != null) {
            Spacer(modifier = Modifier.width(spacing.small))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PrimaryGold.copy(alpha = 0.16f))
                    .padding(horizontal = spacing.small, vertical = 3.dp)
            ) {
                Text(
                    text = "$days ${if (days == 1L) "day" else "days"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryGold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Seven-column grid for a single month.
 *
 * Only the displayed month is drawn. Adjacent-month days are omitted rather than dimmed,
 * because a trailing day from the next month is what made the old layout read as broken
 * (and M3 paired it with a second month heading).
 */
@Composable
private fun MonthGrid(
    monthMillis: Long,
    startUtc: Long?,
    endUtc: Long?,
    onDayClick: (Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val todayUtc = localDayToUtcMillis(getStartOfDayMillis())
    val monthStart = startOfUtcMonth(monthMillis)
    val daysInMonth = daysInUtcMonth(monthMillis)
    val leadingBlanks = startOfUtcMonthDayOfWeek(monthMillis)
    val weekdayLabels = shortWeekdayLabels()
    val cellCount = leadingBlanks + daysInMonth
    val rowCount = (cellCount + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
    val isSingleDay = startUtc != null && startUtc == endUtc

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        repeat(rowCount) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(DAYS_PER_WEEK) { column ->
                    val cellIndex = row * DAYS_PER_WEEK + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val dayUtc = addDaysUtc(monthStart, dayOfMonth - 1)
                        DayCell(
                            dayUtc = dayUtc,
                            dayOfMonth = dayOfMonth,
                            isStart = dayUtc == startUtc,
                            isEnd = dayUtc == endUtc,
                            inRange = !isSingleDay && startUtc != null && endUtc != null &&
                                dayUtc in startUtc..endUtc,
                            isSingleDayRange = isSingleDay,
                            isToday = dayUtc == todayUtc,
                            onClick = onDayClick
                        )
                    } else {
                        // Leading/trailing blanks keep the 7-column rhythm without
                        // drawing anything, so the grid never shows a phantom date.
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One date cell.
 *
 * The selection is drawn as a band that fills the cell width and a circle sized to match
 * that band's height, so the circle sits exactly inside the cell and the range reads as one
 * continuous shape instead of a circle floating between columns.
 */
@Composable
private fun RowScope.DayCell(
    dayUtc: Long,
    dayOfMonth: Int,
    isStart: Boolean,
    isEnd: Boolean,
    inRange: Boolean,
    isSingleDayRange: Boolean,
    isToday: Boolean,
    onClick: (Long) -> Unit
) {
    val isSelected = isStart || isEnd
    val bandShape = when {
        isStart -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        isEnd -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
        else -> RoundedCornerShape(0.dp)
    }
    val label = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).let { sdf ->
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.format(Date(dayUtc))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(bandShape)
            .background(if (inRange) RANGE_BAND_COLOR else Color.Transparent)
            .clickable { onClick(dayUtc) }
            .semantics { contentDescription = label }
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(SELECTION_SIZE)
                    .clip(CircleShape)
                    .background(PrimaryGold)
            )
        } else if (isToday) {
            Box(
                modifier = Modifier
                    .size(SELECTION_SIZE)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.dp, PrimaryGold, CircleShape)
            )
        }
        Text(
            text = dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = when {
                isSelected -> DarkBrown1
                isToday -> PrimaryGold
                else -> TextLight
            },
            maxLines = 1
        )
    }
}

@Composable
private fun QuickPresets(onSelect: (startUtc: Long, endUtc: Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        DatePresetChip("Today", Modifier.weight(1f)) {
            onSelect(
                localDayToUtcMillis(getStartOfDayMillis()),
                localDayToUtcMillis(getEndOfDayMillis())
            )
        }
        DatePresetChip("Yesterday", Modifier.weight(1f)) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            onSelect(
                localDayToUtcMillis(getStartOfDayMillis(cal)),
                localDayToUtcMillis(getEndOfDayMillis(cal))
            )
        }
        DatePresetChip("7 Days", Modifier.weight(1f)) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            onSelect(
                localDayToUtcMillis(getStartOfDayMillis(cal)),
                localDayToUtcMillis(getEndOfDayMillis(cal))
            )
        }
        DatePresetChip("Month", Modifier.weight(1f)) {
            val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
            onSelect(
                localDayToUtcMillis(getStartOfDayMillis(cal)),
                localDayToUtcMillis(getEndOfDayMillis(cal))
            )
        }
    }
}

@Composable
private fun DatePresetChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = KhanaRadii.sm,
        color = DarkBrown1.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = TextLight,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GoldDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = BorderGold.copy(alpha = 0.25f)
    )
}

private fun localDayToUtcMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = utcCalendar()
    utc.clear()
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    return utc.timeInMillis
}

private fun localCalendarForUtcDay(utcMillis: Long): Calendar {
    val utc = utcCalendar().apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance()
    local.clear()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    return local
}

private fun utcCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

private fun utcYear(utcMillis: Long): Int = utcCalendar().apply { timeInMillis = utcMillis }.get(Calendar.YEAR)

/** UTC midnight on the 1st of the month containing [millis]. */
private fun startOfUtcMonth(millis: Long): Long = utcCalendar().apply {
    timeInMillis = millis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun daysInUtcMonth(millis: Long): Int = utcCalendar().apply {
    timeInMillis = millis
}.getActualMaximum(Calendar.DAY_OF_MONTH)

/**
 * Column offset of the 1st, relative to the device locale's first day of week, so the
 * leading blanks always line the dates up under the right weekday label.
 */
private fun startOfUtcMonthDayOfWeek(millis: Long): Int {
    val firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
    val dayOfWeek = utcCalendar().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    return (dayOfWeek - firstDayOfWeek + DAYS_PER_WEEK) % DAYS_PER_WEEK
}

/** Short weekday names rotated to start on the locale's first day of week. */
private fun shortWeekdayLabels(): List<String> {
    val symbols = DateFormatSymbols.getInstance()
    val firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
    return (0 until DAYS_PER_WEEK).map { i ->
        symbols.shortWeekdays[(firstDayOfWeek - 1 + i) % DAYS_PER_WEEK]
    }
}

private fun addDaysUtc(millis: Long, days: Int): Long = utcCalendar().apply {
    timeInMillis = millis
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

// Selected days arrive as UTC midnight. Formatting them in the device zone would
// render the previous day anywhere west of UTC, so pin the formatter to UTC.
private fun formatUtcDay(utcMillis: Long, withYear: Boolean): String {
    val sdf = SimpleDateFormat(if (withYear) "dd MMM yyyy" else "dd MMM", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(utcMillis))
}

private fun shiftedMonth(monthMillis: Long, delta: Int): Long = utcCalendar().apply {
    timeInMillis = monthMillis
    add(Calendar.MONTH, delta)
}.timeInMillis

private fun formatMonthYear(monthMillis: Long): String {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(monthMillis))
}

private fun getStartOfDayMillis(cal: Calendar = Calendar.getInstance()): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun getEndOfDayMillis(cal: Calendar = Calendar.getInstance()): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 23)
    c.set(Calendar.MINUTE, 59)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}
