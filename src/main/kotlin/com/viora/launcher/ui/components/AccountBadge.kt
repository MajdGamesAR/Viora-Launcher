package com.viora.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.auth.model.AccountType
import com.viora.launcher.ui.theme.VioraTheme

@Composable
fun AccountBadge(
    account: Account,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VioraTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ Avatar — يمرر skinUrl + uuid + username
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        ) {
            SteveAvatar(
                skinUrl = account.skinUrl,
                username = account.username,
                uuid = account.uuid,
                size = 36
            )
        }

        Spacer(Modifier.width(10.dp))

        Column {
            Text(
                text = account.username,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (account.type == AccountType.MICROSOFT) "Microsoft" else "Viora",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}