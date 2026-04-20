package com.khanabook.lite.pos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// KHANABOOK RADIUS TOKENS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
val KhanaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // --radius-sm
    small = RoundedCornerShape(10.dp),      // --radius-md (buttons, inputs)
    medium = RoundedCornerShape(14.dp),     // --radius-lg (cards, modals)
    large = RoundedCornerShape(20.dp),      // --radius-xl (FAB, pill)
    extraLarge = RoundedCornerShape(24.dp), 
)

object KhanaRadii {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(14.dp)
    val xl = RoundedCornerShape(20.dp)
    
    val button = md
    val input = md
    val card = lg
    val cardLarge = xl
    val modal = lg
    val pill = RoundedCornerShape(999.dp)
}
