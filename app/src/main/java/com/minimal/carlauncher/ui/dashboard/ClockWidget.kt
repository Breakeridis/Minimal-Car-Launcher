package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

@Composable
fun ClockWidget(
    time: String,
    seconds: String,
    date: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarSurface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = if (time.isNotBlank()) time else "--:--",
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (seconds.isNotBlank()) seconds else "00",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = AccentCyan,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (date.isNotBlank()) date else "Loading date…",
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary
        )
    }
}
