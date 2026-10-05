package com.viora.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.ui.theme.VioraColors
import com.viora.launcher.ui.theme.VioraTheme

data class NewsItem(
    val title: String,
    val description: String,
    val date: String,
    val tag: String
)

@Composable
fun NewsCard(
    news: NewsItem,
    modifier: Modifier = Modifier
) {
    val colors = VioraTheme.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(VioraColors.VioraMagenta.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = news.tag,
                    color = VioraColors.VioraMagenta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = news.date,
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = news.title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = news.description,
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}