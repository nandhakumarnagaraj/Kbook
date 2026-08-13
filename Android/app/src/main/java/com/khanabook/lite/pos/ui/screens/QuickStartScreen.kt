@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.QuickStartViewModel

data class QuickMenuItem(
    val name: String = "",
    val price: String = ""
)

@Composable
fun QuickStartScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: QuickStartViewModel = hiltViewModel()
) {
    val spacing = KhanaBookTheme.spacing
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var shopName by remember { mutableStateOf("") }
    var menuItems by remember {
        mutableStateOf(
            listOf(
                QuickMenuItem(),
                QuickMenuItem(),
                QuickMenuItem()
            )
        )
    }

    val canSubmit = shopName.isNotBlank() && menuItems.any { it.name.isNotBlank() && it.price.isNotBlank() }

    LaunchedEffect(Unit) {
        viewModel.completionEvent.collect {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(spacing.large),
            verticalArrangement = Arrangement.Top
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PrimaryGold,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onSkip) {
                    Text("Skip", color = TextGold)
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                text = "Let's get you billing in under a minute",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLight.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // Shop Name Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = KhanaRadii.card,
                colors = CardDefaults.cardColors(containerColor = DarkBrown2)
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text(
                            text = "Your Shop",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextLight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))

                    KhanaBookInputField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = "Shop name",
                        placeholder = "e.g. Sharma's Chai Point",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Menu Items Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = KhanaRadii.card,
                colors = CardDefaults.cardColors(containerColor = DarkBrown2)
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text(
                            text = "Your Menu",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextLight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.extraSmall))

                    Text(
                        text = "Add your most popular items. You can add more later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))

                    menuItems.forEachIndexed { index, item ->
                        QuickMenuItemRow(
                            index = index,
                            item = item,
                            onNameChange = { newName ->
                                menuItems = menuItems.toMutableList().also {
                                    it[index] = it[index].copy(name = newName)
                                }
                            },
                            onPriceChange = { newPrice ->
                                menuItems = menuItems.toMutableList().also {
                                    it[index] = it[index].copy(price = newPrice)
                                }
                            },
                            onRemove = if (menuItems.size > 1) {
                                { menuItems = menuItems.toMutableList().also { it.removeAt(index) } }
                            } else null
                        )

                        if (index < menuItems.lastIndex) {
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                    }

                    if (menuItems.size < 10) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        OutlinedButton(
                            onClick = {
                                menuItems = menuItems + QuickMenuItem()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = KhanaRadii.button,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextGold
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text("Add another item")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Error display
            if (error != null) {
                Text(
                    text = error ?: "",
                    color = ErrorPink,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = spacing.small)
                )
            }

            // Submit button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val validItems = menuItems.filter { it.name.isNotBlank() && it.price.isNotBlank() }
                    viewModel.completeQuickStart(shopName.trim(), validItems)
                },
                enabled = canSubmit && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = KhanaRadii.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGold,
                    disabledContainerColor = PrimaryGold.copy(alpha = 0.3f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DarkBrown1,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Start Billing →",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkBrown1,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Text(
                text = "You can always add categories, variants, GST, and printer settings later from the Profile tab.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLight.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(spacing.large))
        }

        // Loading overlay
        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = LoadingType.SAVING,
            message = "Setting up your shop..."
        )
    }
}

@Composable
private fun QuickMenuItemRow(
    index: Int,
    item: QuickMenuItem,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    val spacing = KhanaBookTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Item number badge
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .size(24.dp)
                .background(PrimaryGold.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGold
            )
        }

        // Name field
        KhanaBookInputField(
            value = item.name,
            onValueChange = onNameChange,
            label = "Item name",
            placeholder = "e.g. Chai",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.weight(1f)
        )

        // Price field
        KhanaBookInputField(
            value = item.price,
            onValueChange = { newValue ->
                val filtered = newValue.filter { ch -> ch.isDigit() || ch == '.' }
                onPriceChange(filtered)
            },
            label = "₹ Price",
            placeholder = "₹",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.width(90.dp)
        )

        // Remove button
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove item",
                    tint = TextLight.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(spacing.extraLarge))
        }
    }
}
