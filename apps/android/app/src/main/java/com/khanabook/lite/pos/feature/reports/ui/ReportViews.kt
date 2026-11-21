@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.OrderType
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.core.util.BusinessDate
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.feature.billing.ui.HeaderCell
import com.khanabook.lite.pos.feature.billing.ui.TableCell
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) OrdersGold else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) OrdersBg else OrdersTextPrimary,
        animationSpec = tween(200),
        label = "chip_content"
    )
    val typeScale = KhanaBookTheme.typeScale
    val chipMinHeight = when (typeScale) {
        TypeScaleTier.Tablet -> 44.dp
        TypeScaleTier.LargePhone -> 40.dp
        else -> 36.dp
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = chipMinHeight),
        shape = KhanaRadii.md,
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, OrdersGold.copy(alpha = 0.45f)),
        contentColor = contentColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = KhanaBookTheme.spacing.smallMedium, vertical = KhanaBookTheme.spacing.small)
        ) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Date Filter Tab Bar (Today / Yesterday / Custom) for Orders Screen.
 *
 * [customRangeLabel] replaces the "Custom" chip text once a range is chosen, so the
 * selected dates stay visible without reopening the picker. It is ignored while another
 * filter is active, and never applied when the user has not picked a range yet.
 *
 * [dateLabels] works the same way for the preset chips: the selected `Today` / `Yesterday`
 * chip shows the business date it resolved to (`14 Nov`), so the row states the day the
 * table covers instead of a word that can be a calendar day earlier for overnight shops.
 */
@Composable
fun OrderDateFilterComponent(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    canViewFullReports: Boolean = true,
    customRangeLabel: String? = null,
    dateLabels: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val availableFilters = if (canViewFullReports) {
        listOf("Today", "Yesterday", "Custom")
    } else {
        listOf("Today")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        availableFilters.forEach { filter ->
            // Only the selected chip swaps its name for a resolved day/range, so the row
            // stays readable: the selected chip says which days the table is showing.
            val resolved = if (filter == "Custom") customRangeLabel else dateLabels[filter]
            val label = if (selectedFilter == filter && !resolved.isNullOrBlank()) {
                resolved
            } else {
                filter
            }
            FilterChip(
                label = label,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Column geometry ───────────────────────────────────────────────────────────
// ONE definition shared by the header and every row. The two used to carry their own
// padding and only the header carried the weights, which is how the columns drifted out
// of alignment; alignment is now a property of these weights, not of each cell.
//
// OrderNo is deliberately the narrowest: its text is short, so a wide first column left a
// large empty gap before Amount. Amount, Time and Action take equal shares, which evens the
// gaps between their contents instead of squeezing Action against the panel edge. 0.85 keeps
// enough width for the "OrderNo" header on 320dp phones, where the whole row is ~264dp.
private const val COL_ORDER  = 0.85f
private const val COL_AMOUNT = 1.05f
private const val COL_TIME   = 1.05f
private const val COL_ACTION = 1.05f

// Hoisted out of the row composable: these were built per row per recomposition, i.e. once per
// visible row on every scroll frame. DateTimeFormatter is immutable and thread-safe.
private val ORDER_ROW_TIME_FORMAT =
    java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US)
private val ORDER_ROW_DATE_TIME_FORMAT =
    java.time.format.DateTimeFormatter.ofPattern("dd MMM\nhh:mm a", java.util.Locale.US)

/**
 * Timestamp shown in the Orders table "Time" column, rendered in the shop's zone.
 *
 * WHY the profile zone: every business-day window ([BusinessDate.windowStart]) is resolved in
 * the restaurant's timezone, so rendering row times with the device zone showed a time that
 * disagreed with the window when the terminal clock is set elsewhere.
 *
 * WHY [includeDate]: a single business day only needs hh:mm, but a multi-day range puts several
 * orders with the same number and similar times next to each other. The two-line `dd MMM` /
 * `hh:mm a` form keeps the existing four-column layout.
 */
private fun formatOrderRowTime(
    row: com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow,
    profile: RestaurantProfileEntity?,
    includeDate: Boolean
): String {
    if (row.createdAt > 0L) {
        val zone = BusinessDate.zoneOf(profile?.timezone)
        return java.time.Instant.ofEpochMilli(row.createdAt)
            .atZone(zone)
            .format(if (includeDate) ORDER_ROW_DATE_TIME_FORMAT else ORDER_ROW_TIME_FORMAT)
    }
    // Legacy rows: `date` already carries "dd MMM yyyy, hh:mm a".
    return if (includeDate) {
        DateUtils.formatTableDate(row.date)
    } else {
        row.date.substringAfter(", ", row.date)
    }
}

/**
 * Amount as the Orders table shows it: no space between the currency symbol and the
 * number ("\u20b9470.00"). Scoped to this table so receipts, PDFs and invoices keep
 * the shared CurrencyUtils spacing.
 */
private fun formatOrderRowAmount(amount: String?): String =
    com.khanabook.lite.pos.core.util.CurrencyUtils.formatPrice(amount)
        .replace("\u20B9 ", "\u20B9")

/**
 * One column of the table. Both the header and the rows route every cell through this, so a
 * column's width and horizontal alignment are decided in one place.
 */
@Composable
private fun RowScope.OrderTableCell(
    weight: Float,
    align: TextAlign,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.weight(weight),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        content()
    }
}

/**
 * Table header for the Orders table: OrderNo, Amount, Time, Action.
 *
 * No fill, border or radius of its own: the header is the first band of the table panel
 * ([OrderHistoryTableComponent]), which owns the outline. The band tint separates the head from
 * the first row. It lives outside the [LazyColumn] so it pins while the list scrolls.
 */
@Composable
fun OrderTableHeader(
    modifier: Modifier = Modifier,
    includeDate: Boolean = false
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val headerStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = layout.tableHeaderFontSize,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun RowScope.headerCell(text: String, weight: Float, align: TextAlign) {
        OrderTableCell(weight, align) {
            Text(text = text, color = TextGold, style = headerStyle, maxLines = 1)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OrdersHeaderBand, KhanaRadii.sm)
            .padding(
                horizontal = spacing.smallMedium,
                vertical = spacing.smallMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // One word, matching the OrderNo column in the report tables. Centred like the other
        // headers so the short ids do not leave a one-sided gap before Amount.
        headerCell("OrderNo", COL_ORDER, TextAlign.Center)
        headerCell("Amount", COL_AMOUNT, TextAlign.Center)
        headerCell(if (includeDate) "Date/Time" else "Time", COL_TIME, TextAlign.Center)
        headerCell("Action", COL_ACTION, TextAlign.Center)
    }
}

/**
 * The row's action control: a compact outlined "View" pill inside a 48dp hit area.
 *
 * The pill is 22dp tall with 1dp gold outline and 5dp corners; the label is white like the
 * other row values, and the transparent fill lets the row show through. The outer 48dp box
 * keeps the Material minimum touch target.
 */
@Composable
fun ViewOrderAction(
    dailyId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                onClickLabel = "View order $dailyId",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(22.dp)
                .border(
                    width = 1.dp,
                    color = OrdersGold.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(5.dp)
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "View",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

/**
 * Individual row in the Orders table.
 *
 * A row is a plain [Row] inside the table panel, not a card: the panel owns the fill, radius and
 * outer border, and a 1 dp hairline between rows does the separating. The whole row opens the
 * order. Text sizes come from the responsive table tokens, and every cell is the same
 * [OrderTableCell] the header uses, so the columns cannot drift.
 *
 * Cancelled rows are marked with a 3 dp red edge and a 5% red wash instead of a red card border,
 * which no longer exists now that rows share the panel outline.
 */
@Composable
fun OrderTableRow(
    row: com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow,
    profile: RestaurantProfileEntity?,
    compactLayout: Boolean = false,
    includeDate: Boolean = false,
    onViewDetails: (Long, Float) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    // Vertical position of this row in the window. The dialog uses it as the pivot of
    // its short entrance scale so it appears to grow from the row that was tapped.
    val windowHeight = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()
        .coerceAtLeast(1f)
    var originY by remember { mutableFloatStateOf(0.5f) }
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED
    val muted = OrdersTextSecondary.copy(alpha = 0.5f)
    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = layout.tableCellFontSize,
        fontFeatureSettings = "tnum"
    )

    // Each order is its own rounded card, like the Orders Report table: the list spaces the
    // cards apart, so there is no shared panel and no divider between rows. No vertical
    // padding: the 48dp action target already sets the content height.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactLayout) 52.dp else 60.dp)
            .onGloballyPositioned { coords ->
                originY = (coords.boundsInWindow().center.y / windowHeight).coerceIn(0f, 1f)
            }
            .clip(KhanaRadii.sm)
            .background(OrdersRowCard)
            .then(
                if (isCancelled) Modifier.background(DangerRed.copy(alpha = 0.05f)) else Modifier
            )
            .drawBehind {
                if (isCancelled) {
                    drawRect(
                        color = DangerRed.copy(alpha = 0.85f),
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
            }
            .clickable { onViewDetails(row.billId, originY) }
            .padding(horizontal = spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. OrderNo — centred so "A02" sits under the header instead of hugging the left edge
        // and leaving a wide gap before Amount. Printed without `#` at product request
        // (2026-11-14); search and share text still prefix it.
        OrderTableCell(COL_ORDER, TextAlign.Center) {
            Text(
                text = row.dailyId,
                color = if (isCancelled) muted else Color.White,
                style = cellStyle.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 2. Amount — centred, tabular figures, white like the other row values (not gold),
        // struck through when cancelled.
        OrderTableCell(COL_AMOUNT, TextAlign.Center) {
            Text(
                text = formatOrderRowAmount(row.totalAmount),
                color = if (isCancelled) muted else Color.White,
                style = cellStyle.copy(fontWeight = FontWeight.Bold),
                textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3. Time — centred. Two lines only when the list spans more than the current business
        // day, so a multi-day range cannot show two different days' #A01 alike.
        OrderTableCell(COL_TIME, TextAlign.Center) {
            Text(
                text = formatOrderRowTime(row, profile, includeDate),
                color = if (isCancelled) muted else Color.White,
                style = cellStyle.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = if (includeDate) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 4. Action — opens the order details, same as tapping the row.
        OrderTableCell(COL_ACTION, TextAlign.Center) {
            ViewOrderAction(
                dailyId = row.dailyId,
                onClick = { onViewDetails(row.billId, originY) }
            )
        }
    }
}

/**
 * Orders table: one panel holding a pinned header band, a hairline, and the scrolling rows.
 *
 * The panel clips, fills and outlines; the header is its first band and a 1 dp hairline
 * separates each row, with no trailing line under the last one. The header stays outside the
 * [LazyColumn] so it pins while the list scrolls, and rows carry their bill id as a key so a
 * refresh (cancel, payment-mode change, filter change) updates rows in place instead of
 * rebuilding the list.
 */
@Composable
fun OrderHistoryTableComponent(
    rows: List<com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow>,
    profile: RestaurantProfileEntity?,
    compactLayout: Boolean = false,
    includeDate: Boolean = false,
    onViewDetails: (Long, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing

    // No enclosing panel: the header is its own band and every order is a card, matching the
    // Orders Report table. The list hugs its rows (`fill = false`) so two orders do not leave
    // a tall empty card, while a long list still takes the remaining height and scrolls.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            OrderTableHeader(
                modifier = Modifier.padding(horizontal = spacing.medium),
                includeDate = includeDate
            )
            // Gold hairline closes the header band instead of boxing it in.
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.medium),
                thickness = 1.dp,
                color = OrdersGold.copy(alpha = 0.35f)
            )

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KhanaEmptyState(
                        title = "No orders in this period",
                        message = "Try another date or report filter.",
                        icon = Icons.Default.Description
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .verticalScrollbar(listState),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    contentPadding = PaddingValues(
                        top = spacing.small,
                        bottom = spacing.small
                    )
                ) {
                    itemsIndexed(rows, key = { _, row -> row.billId }) { _, row ->
                        OrderTableRow(
                            row = row,
                            profile = profile,
                            compactLayout = compactLayout,
                            includeDate = includeDate,
                            onViewDetails = onViewDetails
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium)
            .padding(bottom = spacing.smallMedium)
    ) {
        KhanaBookInputField(
            value = query,
            onValueChange = onQueryChange,
            label = "Search orders",
            placeholder = "OrderNo, InvoiceNo, CustomerName, PhoneNumber, BillAmount, PaymentMode, OrderType",
            marqueePlaceholder = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextGold
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
