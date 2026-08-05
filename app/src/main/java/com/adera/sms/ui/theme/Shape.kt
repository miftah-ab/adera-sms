package com.adera.sms.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// M3 Expressive shapes: 
// Cards: medium-rounded (16-20dp)
// Buttons: fully rounded (Stadium)
// Master toggle card: slightly more rounded (e.g., 24dp)

val AderaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp), // Standard cards
    large = RoundedCornerShape(24.dp),  // Master toggle priority card
    extraLarge = RoundedCornerShape(32.dp)
)
