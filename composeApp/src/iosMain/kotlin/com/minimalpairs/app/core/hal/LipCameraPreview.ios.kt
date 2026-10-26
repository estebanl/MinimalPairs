package com.minimalpairs.app.core.hal

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.minimalpairs.app.core.cv.*
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AVFoundation.*
import platform.CoreGraphics.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.Foundation.*
import platform.QuartzCore.CATransaction
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.*
import platform.Vision.*
import platform.darwin.*
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LipCameraPreview(modifier: Modifier, onFrame: (LipCameraFrame) -> Unit) {
    val callback by rememberUpdatedState(onFrame)
    val camera = remember { IosLipCamera { callback(it) } }
    UIKitView(factory = { camera.view }, modifier = modifier)
    DisposableEffect(camera) {
        camera.start()
        onDispose { camera.close() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LipPreviewView(val preview: AVCaptureVideoPreviewLayer,
    val orientOutput: (AVCaptureVideoOrientation) -> Unit) : UIView(frame = CGRectZero.readValue()) {
    init { layer.addSublayer(preview) }
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        CATransaction.commit()
        val orientation = when (window?.windowScene?.interfaceOrientation) {
            UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
            UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
            UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
        preview.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) connection.videoOrientation = orientation
            if (connection.isVideoMirroringSupported()) {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = true
            }
        }
        orientOutput(orientation)
    }
}

/** Capture and Vision run on one serial queue; Compose callbacks are delivered on the main queue. */
@OptIn(ExperimentalForeignApi::class)
private class IosLipCamera(val onFrame: (LipCameraFrame) -> Unit) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val alive = MutableStateFlow(true)
    private val queue = dispatch_queue_create("com.minimalpairs.lip-camera", null)
    private val session = AVCaptureSession()
    private val output = AVCaptureVideoDataOutput()
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }
    val view = LipPreviewView(preview) { orientation ->
        dispatch_async(queue) {
            if (alive.value) output.connectionWithMediaType(AVMediaTypeVideo)?.let {
                if (it.isVideoOrientationSupported()) it.videoOrientation = orientation
            }
        }
    }
    private val observers = mutableListOf<NSObjectProtocol>()
    private var lastFrameTime = 0.0

    fun start() {
        val center = NSNotificationCenter.defaultCenter
        for (name in listOf(UIApplicationDidEnterBackgroundNotification, AVCaptureSessionWasInterruptedNotification, AVCaptureSessionRuntimeErrorNotification)) {
            observers += center.addObserverForName(name, `object` = if (name == UIApplicationDidEnterBackgroundNotification) null else session,
                queue = NSOperationQueue.mainQueue) {
                report(LipCameraFrame(if (name == AVCaptureSessionRuntimeErrorNotification) LipCameraStatus.ERROR else LipCameraStatus.PAUSED))
            }
        }
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> configure()
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                if (alive.value) {
                    if (granted) configure() else report(LipCameraFrame(LipCameraStatus.PERMISSION_DENIED))
                }
            }
            else -> report(LipCameraFrame(LipCameraStatus.PERMISSION_DENIED))
        }
    }

    private fun configure() {
        dispatch_async(queue) {
            if (!alive.value) return@dispatch_async
            val device = AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo, position = AVCaptureDevicePositionFront)
            if (device == null) {
                report(LipCameraFrame(LipCameraStatus.UNAVAILABLE))
                return@dispatch_async
            }
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
            if (input == null || !session.canAddInput(input) || !session.canAddOutput(output)) {
                report(LipCameraFrame(LipCameraStatus.ERROR))
                return@dispatch_async
            }
            session.beginConfiguration()
            // The microphone recorder owns its own audio session; the camera must not reconfigure it.
            session.automaticallyConfiguresApplicationAudioSession = false
            if (session.canSetSessionPreset(AVCaptureSessionPreset640x480)) session.sessionPreset = AVCaptureSessionPreset640x480
            session.addInput(input)
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(this, queue)
            session.addOutput(output)
            output.connectionWithMediaType(AVMediaTypeVideo)?.let {
                if (it.isVideoOrientationSupported()) it.videoOrientation = AVCaptureVideoOrientationPortrait
                if (it.isVideoMirroringSupported()) {
                    it.automaticallyAdjustsVideoMirroring = false
                    it.videoMirrored = false
                }
            }
            session.commitConfiguration()
            if (!alive.value) return@dispatch_async
            session.startRunning()
            report(LipCameraFrame(if (session.running) LipCameraStatus.NO_FACE else LipCameraStatus.ERROR))
            dispatch_async(dispatch_get_main_queue()) { if (alive.value) view.setNeedsLayout() }
        }
    }

    override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?, fromConnection: AVCaptureConnection) {
        if (!alive.value || didOutputSampleBuffer == null) return
        val now = CACurrentMediaTime()
        if (now - lastFrameTime < .18) return
        lastFrameTime = now
        val buffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        val request = VNDetectFaceLandmarksRequest()
        val handler = VNImageRequestHandler(cVPixelBuffer = buffer, options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) {
            report(LipCameraFrame(LipCameraStatus.ERROR))
            return
        }
        val faces = request.results?.filterIsInstance<VNFaceObservation>().orEmpty()
        if (faces.isEmpty()) {
            report(LipCameraFrame(LipCameraStatus.NO_FACE))
            return
        }
        val face = faces.singleOrNull()
        val width = CVPixelBufferGetWidth(buffer).toDouble()
        val height = CVPixelBufferGetHeight(buffer).toDouble()
        if (face == null || face.confidence < .6f || (face.landmarks?.confidence ?: 0f) < .5f || abs(face.yaw?.doubleValue ?: 0.0) > .35 ||
            abs(face.roll?.doubleValue ?: 0.0) > .35 || abs(face.pitch?.doubleValue ?: 0.0) > .35 || face.boundingBox.useContents { size.width * width } < 150) {
            report(LipCameraFrame(LipCameraStatus.ADJUST_POSITION))
            return
        }
        fun points(region: VNFaceLandmarkRegion2D?): List<LipPoint> {
            val data = region?.pointsInImageOfSize(CGSizeMake(width, height)) ?: return emptyList()
            return List(region.pointCount.toInt()) { index ->
                LipPoint(data[index].x.toFloat(), (height - data[index].y).toFloat())
            }
        }
        val sample = LipShapeAnalyzer.measure(points(face.landmarks?.outerLips), points(face.landmarks?.innerLips))
        report(LipCameraFrame(if (sample == null) LipCameraStatus.ADJUST_POSITION else LipCameraStatus.TRACKING, sample))
    }

    private fun report(frame: LipCameraFrame) {
        dispatch_async(dispatch_get_main_queue()) { if (alive.value) onFrame(frame) }
    }

    fun close() {
        alive.value = false
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        preview.session = null
        dispatch_async(queue) {
            output.setSampleBufferDelegate(null, null)
            if (session.running) session.stopRunning()
            session.inputs.toList().forEach { session.removeInput(it as AVCaptureInput) }
            session.outputs.toList().forEach { session.removeOutput(it as AVCaptureOutput) }
        }
    }
}
