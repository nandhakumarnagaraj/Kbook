@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.auth.ui
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import com.khanabook.lite.pos.core.theme.*

@Composable
internal fun outlinedTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = DarkBrown1,
                focusedContainerColor = DarkBrown2,
                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                focusedBorderColor = PrimaryGold,
                cursorColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                errorBorderColor = ErrorPink,
                errorLabelColor = ErrorPink
        )
