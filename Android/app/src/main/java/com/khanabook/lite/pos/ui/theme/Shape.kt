package com.khanabook.lite.pos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// KHANABOOK RADIUS TOKENS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
val KhanaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // Badge shape (6.dp)
    small = RoundedCornerShape(10.dp),      // Input shape (10.dp)
    medium = RoundedCornerShape(12.dp),     // Button shape (12.dp)
    large = RoundedCornerShape(16.dp),      // Card shape (16.dp)
    extraLarge = RoundedCornerShape(20.dp), 
)

object KhanaRadii {
    val sm = RoundedCornerShape(6.dp)       // Badge (6.dp)
    val md = RoundedCornerShape(10.dp)      // Input (10.dp)
    val lg = RoundedCornerShape(12.dp)      // Button (12.dp)
    val xl = RoundedCornerShape(16.dp)      // Card (16.dp)
    
    val button = lg
    val input = md
    val card = xl
    val cardLarge = xl
    val modal = lg
    val pill = RoundedCornerShape(999.dp)
}
