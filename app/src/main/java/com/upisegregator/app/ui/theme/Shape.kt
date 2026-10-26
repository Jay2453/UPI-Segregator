package com.upisegregator.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Specific component shapes for buttons and inputs
val ButtonShape = RoundedCornerShape(14.dp)
val InputFieldShape = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(18.dp)
val BannerShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(50.dp)
