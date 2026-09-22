package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentBlue
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.AccentZLink
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

@Composable
fun QuickLaunchCards(
    dvrApp: AppInfo?,
    zlinkLabel: String,
    navApp: AppInfo?,
    musicApp: AppInfo?,
    radioStation: String? = null,
    onLaunchDvr: () -> Unit,
    onLongClickDvr: () -> Unit,
    onLaunchZLink: () -> Unit,
    onLaunchNavigation: () -> Unit,
    onLaunchMusic: () -> Unit,
    onLongClickNavigation: () -> Unit,
    onLongClickMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Dashcam / DVR Card (Takes 55% width)
        DvrHeroCard(
            dvrApp = dvrApp,
            onClick = onLaunchDvr,
            onLongClick = onLongClickDvr,
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
        )

        // Right Column: Phone Projection (ZLink), Navigation, Music / Radio
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                title = "Phone Projection",
                subtitle = zlinkLabel,
                icon = Icons.Default.DirectionsCar,
                accentColor = AccentZLink,
                onClick = onLaunchZLink,
                modifier = Modifier.weight(1f)
            )

            ActionTile(
                title = "Navigation",
                subtitle = navApp?.label ?: "Google Maps / GPS",
                icon = Icons.Default.Navigation,
                accentColor = AccentBlue,
                onClick = onLaunchNavigation,
                onLongClick = onLongClickNavigation,
                modifier = Modifier.weight(1f)
            )

            val hasRadioStation = !radioStation.isNullOrBlank()
            val isRadio = hasRadioStation || (musicApp?.label?.contains("radio", ignoreCase = true) == true)
            val tileTitle = if (hasRadioStation) "Radio" else (musicApp?.label ?: "Music")
            val tileSubtitle = if (hasRadioStation) radioStation!! else (musicApp?.label ?: "Audio & Streaming")
            val tileIcon = if (isRadio) Icons.Default.Radio else Icons.Default.MusicNote
            val tileColor = if (hasRadioStation) AccentAmber else AccentCyan

            ActionTile(
                title = tileTitle,
                subtitle = tileSubtitle,
                icon = tileIcon,
                accentColor = tileColor,
                onClick = onLaunchMusic,
                onLongClick = onLongClickMusic,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DvrHeroCard(
    dvrApp: AppInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep cockpit slate
            CarSurface
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(gradientBrush)
            .border(1.5.dp, AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Camera Icon & Live DVR Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Dashcam DVR",
                        tint = AccentCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // REC Live Status Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE DVR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Bottom Labels & Placement Guide
            Column {
                Text(
                    text = "Dashcam",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dvrApp?.label ?: "Tap to launch DVR • Long-press to choose app",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dock your DVR floating window inside this card",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CarSurface)
            .border(1.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextMuted
            )
        }
    }
}
