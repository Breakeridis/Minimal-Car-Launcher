package com.minimal.carlauncher.ui.dashboard

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary

/**
 * Lightweight Head Unit & Radio Inspector Card placed in the left column slot.
 * Displays instant hardware summary (Model, Board/SoC, Detected Radio App), opens
 * the full Head Unit Hardware & Radio Inspector popup, and supports 1-tap Cloud Upload.
 */
@Composable
fun RadioStationWidget(
    detectedRadioSummary: String?,
    isUploading: Boolean = false,
    uploadStatus: String? = null,
    onOpenInspectorPopup: () -> Unit,
    onUploadReport: () -> Unit = {},
    onLaunchRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            CarSurfaceVariant.copy(alpha = 0.5f),
            CarSurface
        )
    )

    val modelSummary = remember {
        "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: "Head Unit"}".trim()
    }
    val boardSummary = remember {
        "Board: ${Build.BOARD ?: "?"} • HW: ${Build.HARDWARE ?: "?"}"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
            .border(1.dp, CarBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable { onOpenInspectorPopup() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Top Header: Icon, Title, and Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Head Unit Inspector",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "HEAD UNIT & RADIO INFO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUploading) "UPLOADING..." else "INSPECTOR",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Center Hardware & Radio App Summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarSurfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, CarBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = modelSummary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = boardSummary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uploadStatus ?: "Radio App: ${detectedRadioSummary ?: "Scanning..."}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = if (uploadStatus != null) AccentGreen else if (detectedRadioSummary != null) AccentCyan else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Action Buttons: All Info Popup + 1-Tap Cloud Upload + Radio App
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentCyan)
                        .clickable { onOpenInspectorPopup() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ALL INFO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        letterSpacing = 0.4.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentGreen)
                        .clickable(enabled = !isUploading) { onUploadReport() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Report",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isUploading) "SENDING..." else "UPLOAD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CarSurfaceVariant)
                        .border(1.dp, AccentAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable { onLaunchRadio() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Open Radio",
                            tint = AccentAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "RADIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber
                        )
                    }
                }
            }
        }
    }
}
