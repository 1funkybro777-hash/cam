package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.camera.FlashSetting
import com.example.camera.FrameLockViewModel
import com.example.camera.PhotoStorage
import com.example.camera.VoiceCoach
import com.example.camera.VoicePrompt
import com.example.ui.components.BottomControlDeck
import com.example.ui.components.CameraPermissionGate
import com.example.ui.components.CapturedPhotoModal
import com.example.ui.components.TopGuidanceBanner
import com.example.ui.components.TrackingOverlayCanvas
import com.example.ui.theme.ObsidianBg
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors

@Composable
fun FrameLockScreen(
    viewModel: FrameLockViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAskedPermissionOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAskedPermissionOnce = true
        hasCameraPermission = granted
    }

    // Re-check permission when returning from App Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Request camera permission automatically on first launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionGate(
            showSettingsFallback = hasAskedPermissionOnce,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        )
        return
    }

    CameraViewfinderContent(
        viewModel = viewModel,
        hudState = hudState,
    )
}

@Composable
private fun CameraViewfinderContent(
    viewModel: FrameLockViewModel,
    hudState: com.example.camera.CameraHudState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val alignmentRadiusPx = with(density) { 42.dp.toPx() }

    val voiceCoach = remember(context) { VoiceCoach(context) }
    DisposableEffect(voiceCoach) {
        onDispose { voiceCoach.shutdown() }
    }

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val objectDetector = remember {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    val imageLabeler = remember {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.55f)
            .build()
        ImageLabeling.getClient(options)
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS,
            )
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // Configure ML Kit analyzer
    DisposableEffect(cameraController, previewSize) {
        val analyzer = MlKitAnalyzer(
            listOf(objectDetector, imageLabeler),
            COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor,
        ) { result ->
            if (previewSize.width <= 0 || previewSize.height <= 0) return@MlKitAnalyzer
            val detectedObjects = result.getValue(objectDetector).orEmpty()
            val detectedLabels = result.getValue(imageLabeler).orEmpty()
            viewModel.onAnalysisResult(
                objects = detectedObjects,
                sceneLabels = detectedLabels,
                viewCenterX = previewSize.width / 2f,
                viewCenterY = previewSize.height / 2f,
                alignmentRadiusPx = alignmentRadiusPx,
            )
        }
        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
        }
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
        }
    }

    DisposableEffect(objectDetector, imageLabeler, analysisExecutor) {
        onDispose {
            objectDetector.close()
            imageLabeler.close()
            analysisExecutor.shutdown()
        }
    }

    // Sync camera selector & flash mode with HUD state
    LaunchedEffect(hudState.isFrontCamera) {
        cameraController.cameraSelector = if (hudState.isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    LaunchedEffect(hudState.flashSetting) {
        cameraController.imageCaptureFlashMode = when (hudState.flashSetting) {
            FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
            FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    val capturePhoto: (Boolean) -> Unit = remember(cameraController, context, viewModel) {
        { wasAuto ->
            val currentState = viewModel.hudState.value
            if (!currentState.isCapturing && currentState.activePreviewPhoto == null) {
                viewModel.onCaptureStarted()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val tempFile = PhotoStorage.createTempCaptureFile(context)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                cameraController.takePicture(
                    outputOptions,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: Uri.fromFile(tempFile)
                            viewModel.onPhotoCaptured(
                                uri = savedUri,
                                filePath = tempFile.absolutePath,
                                wasAutoCaptured = wasAuto,
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            viewModel.onCaptureFailed(
                                exception.message ?: "Capture failed",
                            )
                        }
                    },
                )
            }
        }
    }

    // Handle auto-capture events
    LaunchedEffect(Unit) {
        viewModel.autoCaptureEvents.collectLatest {
            capturePhoto(true)
        }
    }

    // Handle voice guidance prompts
    LaunchedEffect(hudState.isVoiceEnabled) {
        if (!hudState.isVoiceEnabled) {
            voiceCoach.stop()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.voicePrompts.collect { prompt ->
            val enabled = viewModel.hudState.value.isVoiceEnabled
            when (prompt) {
                VoicePrompt.Ready -> voiceCoach.speak(
                    text = context.getString(R.string.voice_ready_prompt),
                    enabled = enabled,
                    force = true,
                )
                is VoicePrompt.MoveTowardDot -> voiceCoach.speak(
                    text = context.getString(R.string.voice_move_prompt),
                    enabled = enabled,
                )
                VoicePrompt.HoldStill -> voiceCoach.speak(
                    text = context.getString(R.string.voice_hold_prompt),
                    enabled = enabled,
                    force = true,
                )
                VoicePrompt.Captured -> voiceCoach.speak(
                    text = context.getString(R.string.voice_captured_prompt),
                    enabled = enabled,
                    force = true,
                )
            }
        }
    }

    // Track saved state changes to show confirmation toast
    var lastSavedCount by remember { mutableIntStateOf(0) }
    val savedCount = hudState.recentPhotos.count { it.savedGalleryUri != null }
    LaunchedEffect(savedCount) {
        if (savedCount > lastSavedCount) {
            lastSavedCount = savedCount
            Toast.makeText(context, R.string.saved_toast, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(hudState.statusBannerMessage) {
        val msg = hudState.statusBannerMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearBannerMessage()
    }

    // Support system back button when photo preview modal is open
    BackHandler(enabled = hudState.activePreviewPhoto != null) {
        viewModel.dismissPreview()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .onSizeChanged { previewSize = it },
    ) {
        // Live CameraX PreviewView
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Real-Time Tracking Reticle & Guide Vector Overlay
        TrackingOverlayCanvas(
            hudState = hudState,
            modifier = Modifier.fillMaxSize(),
        )

        // Top AI Guidance Pill
        TopGuidanceBanner(
            hudState = hudState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom Camera Control Deck
        BottomControlDeck(
            hudState = hudState,
            onToggleAutoCapture = viewModel::toggleAutoCapture,
            onToggleGrid = viewModel::toggleGrid,
            onCycleFlash = viewModel::cycleFlash,
            onToggleVoice = viewModel::toggleVoice,
            onCaptureClick = { capturePhoto(false) },
            onFlipCamera = viewModel::flipCamera,
            onOpenLastPhoto = viewModel::openLastCapturedPhoto,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Screen flash feedback overlay
        AnimatedVisibility(
            visible = hudState.showFlashPulse,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }

        // Captured Photo Preview Modal
        val activePhoto = hudState.activePreviewPhoto
        if (activePhoto != null) {
            CapturedPhotoModal(
                photo = activePhoto,
                isSaving = hudState.isSavingToGallery,
                onSaveClick = { viewModel.saveActivePhotoToGallery(context) },
                onShareClick = {
                    val shareIntent = PhotoStorage.createShareIntent(context, activePhoto)
                    context.startActivity(
                        Intent.createChooser(shareIntent, context.getString(R.string.action_share)),
                    )
                },
                onRetakeClick = viewModel::dismissPreview,
            )
        }
    }
}
