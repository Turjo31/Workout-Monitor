package com.kuet.gymtest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object WorkoutShapes {
    val Card = RoundedCornerShape(24.dp)
    val Exercise = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(20.dp)
    val Search = RoundedCornerShape(28.dp)
    val Navigation = RoundedCornerShape(28.dp)
    val Stat = RoundedCornerShape(20.dp)
}

object Space {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Section = 32.dp
}

val WorkoutMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = WorkoutShapes.Control,
    large = WorkoutShapes.Card,
    extraLarge = WorkoutShapes.Navigation
)
