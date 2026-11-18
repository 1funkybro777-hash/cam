package com.example.camera

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class DirectionCue {
    NONE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    HOLD,
}

enum class FlashSetting {
    OFF,
    AUTO,
    ON,
}

@Immutable
data class DetectedTarget(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val focalX: Float,
    val focalY: Float,
    val label: String,
    val confidencePercent: Int,
    val trackingId: Int?,
)

@Immutable
data class CapturedPhoto(
    val uri: Uri,
    val filePath: String,
    val subjectLabel: String?,
    val confidencePercent: Int?,
    val wasAutoCaptured: Boolean,
    val savedGalleryUri: Uri? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Immutable
data class CameraHudState(
    val isModelReady: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isVoiceEnabled: Boolean = true,
    val isAutoCaptureEnabled: Boolean = true,
    val isGridVisible: Boolean = true,
    val flashSetting: FlashSetting = FlashSetting.AUTO,
    val detectedTarget: DetectedTarget? = null,
    val smoothDotX: Float = 0f,
    val smoothDotY: Float = 0f,
    val hasSmoothPosition: Boolean = false,
    val isAligned: Boolean = false,
    val holdProgress: Float = 0f,
    val directionCue: DirectionCue = DirectionCue.NONE,
    val isCapturing: Boolean = false,
    val showFlashPulse: Boolean = false,
    val activePreviewPhoto: CapturedPhoto? = null,
    val recentPhotos: List<CapturedPhoto> = emptyList(),
    val isSavingToGallery: Boolean = false,
    val statusBannerMessage: String? = null,
)
