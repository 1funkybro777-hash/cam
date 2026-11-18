package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.CameraHudState
import com.example.camera.CapturedPhoto
import com.example.camera.DirectionCue
import com.example.camera.FlashSetting
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudSurfaceSolid
import com.example.ui.theme.LaserAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun TopGuidanceBanner(
    hudState: CameraHudState,
    modifier: Modifier = Modifier,
) {
    val target = hudState.detectedTarget
    val isAligned = hudState.isAligned

    val borderColor by animateColorAsState(
        targetValue = when {
            isAligned -> EmeraldLock.copy(alpha = 0.8f)
            target != null -> LaserAmber.copy(alpha = 0.55f)
            else -> HudBorder
        },
        label = "bannerBorder",
    )

    val statusColor = when {
        isAligned -> EmeraldGlow
        target != null -> SignalCyan
        else -> TextSecondary
    }

    val statusText = when {
        !hudState.isModelReady -> stringResource(R.string.ai_status_ready)
        target == null -> stringResource(R.string.ai_status_scanning)
        else -> "${target.label.uppercase()} · ${target.confidencePercent}%"
    }

    val guidanceText = when {
        target == null -> stringResource(R.string.ai_guidance_point)
        isAligned -> {
            val pct = (hudState.holdProgress * 100f).roundToInt().coerceIn(0, 100)
            if (hudState.isAutoCaptureEnabled) {
                "${stringResource(R.string.ai_guidance_hold)} ($pct%)"
            } else {
                stringResource(R.string.ai_guidance_hold)
            }
        }
        else -> when (hudState.directionCue) {
            DirectionCue.LEFT -> stringResource(R.string.ai_guidance_move_left)
            DirectionCue.RIGHT -> stringResource(R.string.ai_guidance_move_right)
            DirectionCue.UP -> stringResource(R.string.ai_guidance_tilt_up)
            DirectionCue.DOWN -> stringResource(R.string.ai_guidance_tilt_down)
            DirectionCue.HOLD -> stringResource(R.string.ai_guidance_hold)
            DirectionCue.NONE -> stringResource(R.string.ai_guidance_point)
        }
    }

    val directionIcon: ImageVector? = when (hudState.directionCue) {
        DirectionCue.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
        DirectionCue.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
        DirectionCue.UP -> Icons.Rounded.ArrowUpward
        DirectionCue.DOWN -> Icons.Rounded.ArrowDownward
        DirectionCue.HOLD -> Icons.Rounded.AutoAwesome
        DirectionCue.NONE -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 460.dp)
                .semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(24.dp),
            color = HudSurface,
            border = BorderStroke(1.2.dp, borderColor),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (directionIcon != null) {
                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            tint = if (isAligned) EmeraldGlow else LaserAmber,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = guidanceText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAligned) EmeraldGlow else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isAligned && hudState.isAutoCaptureEnabled && hudState.holdProgress > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { hudState.holdProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldGlow,
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}

@Composable
fun BottomControlDeck(
    hudState: CameraHudState,
    onToggleAutoCapture: () -> Unit,
    onToggleGrid: () -> Unit,
    onCycleFlash: () -> Unit,
    onToggleVoice: () -> Unit,
    onCaptureClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenLastPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shutterColor by animateColorAsState(
        targetValue = if (hudState.isAligned) EmeraldGlow else Color.White,
        label = "shutterColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.82f)),
                ),
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Secondary precision controls strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudControlChip(
                    label = if (hudState.isAutoCaptureEnabled) {
                        stringResource(R.string.auto_lock_badge)
                    } else {
                        stringResource(R.string.manual_badge)
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    active = hudState.isAutoCaptureEnabled,
                    activeColor = EmeraldGlow,
                    onClick = onToggleAutoCapture,
                    testTag = "auto_lock_chip",
                )

                HudControlChip(
                    label = "GRID",
                    icon = if (hudState.isGridVisible) Icons.Outlined.GridOn else Icons.Outlined.GridOff,
                    active = hudState.isGridVisible,
                    activeColor = SignalCyan,
                    onClick = onToggleGrid,
                    testTag = "grid_toggle_chip",
                )

                if (!hudState.isFrontCamera) {
                    val (flashIcon, flashLabel) = when (hudState.flashSetting) {
                        FlashSetting.AUTO -> Icons.Rounded.FlashAuto to "AUTO"
                        FlashSetting.ON -> Icons.Rounded.FlashOn to "ON"
                        FlashSetting.OFF -> Icons.Rounded.FlashOff to "OFF"
                    }
                    HudControlChip(
                        label = flashLabel,
                        icon = flashIcon,
                        active = hudState.flashSetting != FlashSetting.OFF,
                        activeColor = LaserAmber,
                        onClick = onCycleFlash,
                        testTag = "flash_toggle_chip",
                    )
                }
            }

            // Primary shutter & utility row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Voice AI Toggle
                CircularHudButton(
                    icon = if (hudState.isVoiceEnabled) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = stringResource(R.string.action_toggle_voice),
                    isActive = hudState.isVoiceEnabled,
                    activeTint = SignalCyan,
                    onClick = onToggleVoice,
                    testTag = "voice_toggle_button",
                )

                // Main Shutter Button
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            color = if (hudState.isAligned) {
                                EmeraldLock.copy(alpha = 0.7f)
                            } else {
                                Color.White.copy(alpha = 0.42f)
                            },
                            shape = CircleShape,
                        )
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(shutterColor)
                        .clickable(enabled = !hudState.isCapturing, onClick = onCaptureClick)
                        .testTag("shutter_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hudState.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = ObsidianBg,
                            strokeWidth = 3.dp,
                        )
                    }
                }

                // Flip camera + optional recent shot thumbnail
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val latestPhoto = hudState.recentPhotos.firstOrNull()
                    if (latestPhoto != null) {
                        AsyncImage(
                            model = latestPhoto.uri,
                            contentDescription = stringResource(R.string.last_photo_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenLastPhoto)
                                .testTag("last_photo_thumbnail"),
                        )
                    }

                    CircularHudButton(
                        icon = Icons.Rounded.FlipCameraAndroid,
                        contentDescription = stringResource(R.string.action_flip_camera),
                        isActive = hudState.isFrontCamera,
                        activeTint = SignalCyan,
                        onClick = onFlipCamera,
                        testTag = "flip_camera_button",
                    )
                }
            }
        }
    }
}

@Composable
private fun HudControlChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .testTag(testTag)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(50),
        color = if (active) activeColor.copy(alpha = 0.16f) else HudSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) activeColor.copy(alpha = 0.65f) else HudBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) activeColor else TextSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) activeColor else TextSecondary,
            )
        }
    }
}

@Composable
private fun CircularHudButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeTint: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .testTag(testTag),
        shape = CircleShape,
        color = HudSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) activeTint.copy(alpha = 0.6f) else HudBorder,
        ),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) activeTint else TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun CapturedPhotoModal(
    photo: CapturedPhoto,
    isSaving: Boolean,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onRetakeClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg.copy(alpha = 0.94f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (photo.wasAutoCaptured) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.Rounded.CameraAlt
                        },
                        contentDescription = null,
                        tint = EmeraldGlow,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(
                            if (photo.wasAutoCaptured) {
                                R.string.preview_title_auto
                            } else {
                                R.string.preview_title_manual
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldGlow,
                    )
                }

                if (!photo.subjectLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = HudSurfaceElevated,
                        border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.45f)),
                    ) {
                        val confSuffix = photo.confidencePercent?.let { " · $it%" }.orEmpty()
                        Text(
                            text = "${photo.subjectLabel.uppercase()}$confSuffix",
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                AsyncImage(
                    model = photo.uri,
                    contentDescription = stringResource(R.string.captured_photo_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, EmeraldLock, RoundedCornerShape(18.dp))
                        .background(HudSurfaceSolid),
                )

                val isSaved = photo.savedGalleryUri != null

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onSaveClick,
                        enabled = !isSaving && !isSaved,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("save_photo_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLock,
                            contentColor = Color(0xFF041A0B),
                            disabledContainerColor = EmeraldLock.copy(alpha = 0.35f),
                            disabledContentColor = EmeraldGlow,
                        ),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF041A0B),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (isSaved) R.string.action_saved_photo else R.string.action_save_photo,
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("share_photo_button"),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, HudBorder),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = TextPrimary,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRetakeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("retake_photo_button"),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.dp, HudBorder),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_retake),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPermissionGate(
    showSettingsFallback: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF132238), ObsidianBg),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Precision optical reticle graphic
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = SignalCyan.copy(alpha = 0.25f),
                        radius = size.minDimension * 0.46f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = EmeraldLock.copy(alpha = 0.65f),
                        radius = size.minDimension * 0.34f,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                    drawCircle(
                        color = LaserAmber,
                        radius = 12.dp.toPx(),
                        center = center,
                    )
                    val tick = 14.dp.toPx()
                    drawLine(
                        color = SignalCyan,
                        start = Offset(center.x, 4.dp.toPx()),
                        end = Offset(center.x, 4.dp.toPx() + tick),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = SignalCyan,
                        start = Offset(center.x, size.height - 4.dp.toPx() - tick),
                        end = Offset(center.x, size.height - 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = null,
                    tint = TextPrimary.copy(alpha = 0.22f),
                    modifier = Modifier.size(72.dp),
                )
            }

            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.camera_permission_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = if (showSettingsFallback) onOpenSettings else onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag(if (showSettingsFallback) "open_settings_button" else "grant_camera_button"),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalCyan,
                    contentColor = Color(0xFF04131D),
                ),
            ) {
                Icon(
                    imageVector = if (showSettingsFallback) Icons.Rounded.Settings else Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (showSettingsFallback) {
                            R.string.camera_permission_settings
                        } else {
                            R.string.camera_permission_grant
                        },
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
