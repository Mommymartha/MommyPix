package com.project.mommypix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project.mommypix.ui.theme.RatingExplicitColor
import com.project.mommypix.ui.theme.RatingQuestionableColor
import com.project.mommypix.ui.theme.RatingSafeColor

@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    val (label, bgColor) = when (rating.lowercase()) {
        "s", "safe", "general" -> "SAFE" to RatingSafeColor
        "q", "questionable" -> "QUESTIONABLE" to RatingQuestionableColor
        "e", "explicit" -> "EXPLICIT" to RatingExplicitColor
        else -> rating.uppercase() to MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier
            .background(bgColor.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

