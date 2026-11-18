package com.example

import android.graphics.Rect
import com.example.camera.DirectionCue
import com.example.camera.FlashSetting
import com.example.camera.FrameLockViewModel
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.objects.DetectedObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FrameLockViewModelTest {

    private fun fakeDetectedObject(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        trackingId: Int = 1,
        labelText: String = "Home good",
        confidence: Float = 0.88f,
    ): DetectedObject {
        val box = Rect(left, top, right, bottom)
        val labels = listOf(DetectedObject.Label(labelText, confidence, 0))
        return DetectedObject(box, trackingId, labels)
    }

    @Test
    fun `off-center object to the right emits RIGHT direction cue`() {
        val vm = FrameLockViewModel()
        val obj = fakeDetectedObject(left = 750, top = 400, right = 950, bottom = 600)
        val labels = listOf(ImageLabel("Cup", 0.91f, 0))

        vm.onAnalysisResult(
            objects = listOf(obj),
            sceneLabels = labels,
            viewCenterX = 500f,
            viewCenterY = 500f,
            alignmentRadiusPx = 80f,
        )

        val state = vm.hudState.value
        assertEquals("Cup", state.detectedTarget?.label)
        assertEquals(91, state.detectedTarget?.confidencePercent)
        assertFalse(state.isAligned)
        assertEquals(DirectionCue.RIGHT, state.directionCue)
    }

    @Test
    fun `aligned object increments holdProgress and triggers auto capture`() = runTest {
        val vm = FrameLockViewModel()
        // Center of 400..600 is 500; 42% of height (395..645 = 250) is 395 + 105 = 500
        val obj = fakeDetectedObject(left = 400, top = 395, right = 600, bottom = 645)
        val labels = listOf(ImageLabel("Smartphone", 0.94f, 1))

        val autoCaptureDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            vm.autoCaptureEvents.first()
        }

        repeat(18) {
            vm.onAnalysisResult(
                objects = listOf(obj),
                sceneLabels = labels,
                viewCenterX = 500f,
                viewCenterY = 500f,
                alignmentRadiusPx = 80f,
            )
        }

        val state = vm.hudState.value
        assertTrue(state.isAligned)
        assertEquals(DirectionCue.HOLD, state.directionCue)
        assertEquals(1.0f, state.holdProgress, 0.001f)
        autoCaptureDeferred.await()
    }

    @Test
    fun `control toggles update hudState correctly`() {
        val vm = FrameLockViewModel()

        assertTrue(vm.hudState.value.isVoiceEnabled)
        vm.toggleVoice()
        assertFalse(vm.hudState.value.isVoiceEnabled)

        assertTrue(vm.hudState.value.isAutoCaptureEnabled)
        vm.toggleAutoCapture()
        assertFalse(vm.hudState.value.isAutoCaptureEnabled)

        assertTrue(vm.hudState.value.isGridVisible)
        vm.toggleGrid()
        assertFalse(vm.hudState.value.isGridVisible)

        assertEquals(FlashSetting.AUTO, vm.hudState.value.flashSetting)
        vm.cycleFlash()
        assertEquals(FlashSetting.ON, vm.hudState.value.flashSetting)
        vm.cycleFlash()
        assertEquals(FlashSetting.OFF, vm.hudState.value.flashSetting)

        assertFalse(vm.hudState.value.isFrontCamera)
        vm.flipCamera()
        assertTrue(vm.hudState.value.isFrontCamera)
    }
}
