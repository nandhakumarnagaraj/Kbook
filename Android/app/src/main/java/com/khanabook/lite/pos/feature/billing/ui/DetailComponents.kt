@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.billing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight

@Composable
internal fun OrderItemsTable(
    items: List<BillItemEntity>,
    total: String,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val snoWidth = layout.orderTableIndexWidth
    val countWidth = layout.orderTableCountWidth
    val priceWidth = layout.orderTablePriceWidth
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.md
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "##",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(snoWidth)
                )
                Text(
                    text = "Items",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Count",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(countWidth)
                )
                Text(
                    text = "Price",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(priceWidth)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = spacing.extraSmall),
                color = BorderGold.copy(alpha = 0.4f)
            )
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(snoWidth)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.itemName,
                            color = TextLight,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.variantName.isNullOrBlank() || !item.specialInstruction.isNullOrBlank()) {
                            Text(
                                text = listOfNotNull(
                                    item.variantName?.takeIf { it.isNotBlank() },
                                    item.specialInstruction?.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = item.quantity.toString(),
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(countWidth)
                    )
                    Text(
                        text = CurrencyUtils.formatPrice(item.itemTotal),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(priceWidth)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = spacing.small),
                color = BorderGold.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = CurrencyUtils.formatPrice(total),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(priceWidth)
                )
            }
        }
    }
}
