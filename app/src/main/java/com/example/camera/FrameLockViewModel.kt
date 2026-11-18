package com.example.camera

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.objects.DetectedObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

sealed interface VoicePrompt {
    data object Ready : VoicePrompt
    data class MoveTowardDot(val subjectLabel: String) : VoicePrompt
    data object HoldStill : VoicePrompt
    data object Captured : VoicePrompt
}

class FrameLockViewModel : ViewModel() {

    private val _hudState = MutableStateFlow(CameraHudState())
    val hudState: StateFlow<CameraHudState> = _hudState.asStateFlow()

    private val _voicePrompts = MutableSharedFlow<VoicePrompt>(extraBufferCapacity = 4)
    val voicePrompts: SharedFlow<VoicePrompt> = _voicePrompts.asSharedFlow()

    private val _autoCaptureEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoCaptureEvents: SharedFlow<Unit> = _autoCaptureEvents.asSharedFlow()

    private var holdFrameCount = 0
    private var lastAutoCaptureTimeMs = 0L
    private var activeTrackingId: Int? = null
    private var hasAnnouncedReady = false

    fun onAnalyzerReady() {
        val firstReady = !_hudState.value.isModelReady
        _hudState.update { it.copy(isModelReady = true) }
        if (firstReady && !hasAnnouncedReady) {
            hasAnnouncedReady = true
            _voicePrompts.tryEmit(VoicePrompt.Ready)
        }
    }

    fun onAnalysisResult(
        objects: List<DetectedObject>,
        sceneLabels: List<ImageLabel>,
        viewCenterX: Float,
        viewCenterY: Float,
        alignmentRadiusPx: Float,
    ) {
        val currentState = _hudState.value
        if (currentState.activePreviewPhoto != null || currentState.isCapturing) {
            return
        }

        if (!currentState.isModelReady) {
            onAnalyzerReady()
        }

        val bestObject = selectPrimaryObject(objects)
        if (bestObject == null) {
            holdFrameCount = 0
            activeTrackingId = null
            _hudState.update {
                it.copy(
                    detectedTarget = null,
                    isAligned = false,
                    holdProgress = 0f,
                    directionCue = DirectionCue.NONE,
                )
            }
            return
        }

        activeTrackingId = bestObject.trackingId
        val box: Rect = bestObject.boundingBox
        val focalX = box.exactCenterX()
        val focalY = box.top + box.height() * 0.42f

        val (resolvedLabel, confidencePercent) = resolveSubjectLabel(bestObject, sceneLabels)

        val prevState = _hudState.value
        val smoothX = if (prevState.hasSmoothPosition) {
            prevState.smoothDotX + (focalX - prevState.smoothDotX) * SMOOTHING_ALPHA
        } else {
            focalX
        }
        val smoothY = if (prevState.hasSmoothPosition) {
            prevState.smoothDotY + (focalY - prevState.smoothDotY) * SMOOTHING_ALPHA
        } else {
            focalY
        }

        val dx = smoothX - viewCenterX
        val dy = smoothY - viewCenterY
        val distance = hypot(dx, dy)
        val aligned = distance <= alignmentRadiusPx

        val target = DetectedTarget(
            left = box.left.toFloat(),
            top = box.top.toFloat(),
            right = box.right.toFloat(),
            bottom = box.bottom.toFloat(),
            focalX = focalX,
            focalY = focalY,
            label = resolvedLabel,
            confidencePercent = confidencePercent,
            trackingId = bestObject.trackingId,
        )

        var shouldTriggerAutoCapture = false
        val nextDirection: DirectionCue
        val nextHoldProgress: Float

        if (aligned) {
            holdFrameCount = (holdFrameCount + 1).coerceAtMost(HOLD_FRAMES_REQUIRED)
            nextDirection = DirectionCue.HOLD
            nextHoldProgress = holdFrameCount.toFloat() / HOLD_FRAMES_REQUIRED.toFloat()

            if (holdFrameCount == HOLD_VOICE_FRAME) {
                _voicePrompts.tryEmit(VoicePrompt.HoldStill)
            }

            val now = System.currentTimeMillis()
            if (prevState.isAutoCaptureEnabled &&
                holdFrameCount >= HOLD_FRAMES_REQUIRED &&
                now - lastAutoCaptureTimeMs > AUTO_CAPTURE_COOLDOWN_MS
            ) {
                lastAutoCaptureTimeMs = now
                shouldTriggerAutoCapture = true
            }
        } else {
            holdFrameCount = 0
            nextHoldProgress = 0f
            nextDirection = if (abs(dx) > abs(dy)) {
                if (dx > 0f) DirectionCue.RIGHT else DirectionCue.LEFT
            } else {
                if (dy > 0f) DirectionCue.DOWN else DirectionCue.UP
            }
            _voicePrompts.tryEmit(VoicePrompt.MoveTowardDot(resolvedLabel))
        }

        _hudState.update {
            it.copy(
                detectedTarget = target,
                smoothDotX = smoothX,
                smoothDotY = smoothY,
                hasSmoothPosition = true,
                isAligned = aligned,
                holdProgress = nextHoldProgress,
                directionCue = nextDirection,
            )
        }

        if (shouldTriggerAutoCapture) {
            _autoCaptureEvents.tryEmit(Unit)
        }
    }

    fun onCaptureStarted() {
        holdFrameCount = 0
        _hudState.update {
            it.copy(
                isCapturing = true,
                showFlashPulse = true,
                holdProgress = 0f,
            )
        }
        viewModelScope.launch {
            delay(180L)
            _hudState.update { it.copy(showFlashPulse = false) }
        }
    }

    fun onPhotoCaptured(
        uri: Uri,
        filePath: String,
        wasAutoCaptured: Boolean,
    ) {
        val currentTarget = _hudState.value.detectedTarget
        val photo = CapturedPhoto(
            uri = uri,
            filePath = filePath,
            subjectLabel = currentTarget?.label,
            confidencePercent = currentTarget?.confidencePercent,
            wasAutoCaptured = wasAutoCaptured,
        )
        _hudState.update { state ->
            state.copy(
                isCapturing = false,
                activePreviewPhoto = photo,
                recentPhotos = (listOf(photo) + state.recentPhotos).take(8),
                isAligned = false,
                holdProgress = 0f,
                directionCue = DirectionCue.NONE,
            )
        }
        _voicePrompts.tryEmit(VoicePrompt.Captured)
    }

    fun onCaptureFailed(message: String) {
        holdFrameCount = 0
        _hudState.update {
            it.copy(
                isCapturing = false,
                showFlashPulse = false,
                statusBannerMessage = message,
            )
        }
    }

    fun dismissPreview() {
        holdFrameCount = 0
        _hudState.update {
            it.copy(
                activePreviewPhoto = null,
                hasSmoothPosition = false,
                holdProgress = 0f,
                isAligned = false,
            )
        }
    }

    fun openLastCapturedPhoto() {
        val latest = _hudState.value.recentPhotos.firstOrNull() ?: return
        _hudState.update { it.copy(activePreviewPhoto = latest) }
    }

    fun saveActivePhotoToGallery(context: Context) {
        val activePhoto = _hudState.value.activePreviewPhoto ?: return
        if (activePhoto.savedGalleryUri != null || _hudState.value.isSavingToGallery) return

        viewModelScope.launch {
            _hudState.update { it.copy(isSavingToGallery = true) }
            val savedUri = PhotoStorage.saveToGallery(context, activePhoto.filePath)
            _hudState.update { state ->
                val updatedPhoto = if (savedUri != null) {
                    activePhoto.copy(savedGalleryUri = savedUri)
                } else {
                    activePhoto
                }
                val updatedRecent = state.recentPhotos.map { item ->
                    if (item.filePath == updatedPhoto.filePath) updatedPhoto else item
                }
                state.copy(
                    isSavingToGallery = false,
                    activePreviewPhoto = if (state.activePreviewPhoto?.filePath == updatedPhoto.filePath) {
                        updatedPhoto
                    } else {
                        state.activePreviewPhoto
                    },
                    recentPhotos = updatedRecent,
                )
            }
        }
    }

    fun toggleVoice() {
        _hudState.update { it.copy(isVoiceEnabled = !it.isVoiceEnabled) }
    }

    fun toggleAutoCapture() {
        holdFrameCount = 0
        _hudState.update {
            it.copy(
                isAutoCaptureEnabled = !it.isAutoCaptureEnabled,
                holdProgress = 0f,
            )
        }
    }

    fun toggleGrid() {
        _hudState.update { it.copy(isGridVisible = !it.isGridVisible) }
    }

    fun flipCamera() {
        holdFrameCount = 0
        activeTrackingId = null
        _hudState.update {
            it.copy(
                isFrontCamera = !it.isFrontCamera,
                detectedTarget = null,
                hasSmoothPosition = false,
                isAligned = false,
                holdProgress = 0f,
                directionCue = DirectionCue.NONE,
            )
        }
    }

    fun cycleFlash() {
        _hudState.update { state ->
            val next = when (state.flashSetting) {
                FlashSetting.AUTO -> FlashSetting.ON
                FlashSetting.ON -> FlashSetting.OFF
                FlashSetting.OFF -> FlashSetting.AUTO
            }
            state.copy(flashSetting = next)
        }
    }

    fun clearBannerMessage() {
        _hudState.update { it.copy(statusBannerMessage = null) }
    }

    private fun selectPrimaryObject(objects: List<DetectedObject>): DetectedObject? {
        if (objects.isEmpty()) return null
        val tracked = activeTrackingId?.let { id ->
            objects.firstOrNull { it.trackingId == id && it.boundingBox.width() > 36 && it.boundingBox.height() > 36 }
        }
        return tracked ?: objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    }

    private fun resolveSubjectLabel(
        detectedObject: DetectedObject,
        sceneLabels: List<ImageLabel>,
    ): Pair<String, Int> {
        val objectCategory = detectedObject.labels.firstOrNull()
        val specificLabel = sceneLabels
            .filter { it.confidence >= 0.58f && it.text.isNotBlank() }
            .firstOrNull { candidate ->
                val lower = candidate.text.lowercase()
                lower !in GENERIC_SCENE_LABELS
            } ?: sceneLabels.firstOrNull { it.confidence >= 0.55f }

        val finalLabel = when {
            specificLabel != null -> specificLabel.text
            objectCategory != null && objectCategory.text.isNotBlank() -> objectCategory.text
            else -> "Subject"
        }

        val confidence = when {
            specificLabel != null -> (specificLabel.confidence * 100f).roundToInt()
            objectCategory != null -> (objectCategory.confidence * 100f).roundToInt()
            else -> 84
        }.coerceIn(50, 99)

        return finalLabel to confidence
    }

    companion object {
        private const val SMOOTHING_ALPHA = 0.28f
        private const val HOLD_FRAMES_REQUIRED = 18
        private const val HOLD_VOICE_FRAME = 3
        private const val AUTO_CAPTURE_COOLDOWN_MS = 2500L

        private val GENERIC_SCENE_LABELS = setOf(
            "room",
            "wall",
            "floor",
            "material",
            "pattern",
            "rectangle",
            "square",
            "circle",
            "snapshot",
            "photography",
        )
    }
}
