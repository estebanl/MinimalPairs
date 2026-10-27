package com.minimalpairs.app.core.hal

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.minimalpairs.app.core.cv.*
import java.util.concurrent.Executors
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
actual fun LipCameraPreview(modifier: Modifier, onFrame: (LipCameraFrame) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onFrame)
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) callback(LipCameraFrame(LipCameraStatus.PERMISSION_DENIED))
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) return
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    // Rebind after a configuration change so analysis and preview use the current display rotation.
    val orientation = LocalConfiguration.current.orientation
    AndroidView(factory = { previewView }, modifier = modifier)
    DisposableEffect(previewView, lifecycleOwner, orientation) {
        val disposed = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val executor = Executors.newSingleThreadExecutor()
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(.2f).build())
        val preview = Preview.Builder().setTargetRotation(previewView.display?.rotation ?: 0).build()
        val analysis = ImageAnalysis.Builder().setTargetRotation(previewView.display?.rotation ?: 0)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val pacer = LipFramePacer()
        val delivery = LipFrameMailbox({ mainExecutor.execute(it) }) { frame ->
            if (!disposed.get()) callback(frame)
        }
        fun report(frame: LipCameraFrame) = delivery.offer(frame)
        analysis.setAnalyzer(executor) { image ->
            val capturedAt = image.imageInfo.timestamp / 1_000_000_000.0
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val mediaImage = image.image
            if (disposed.get() || mediaImage == null || !pacer.shouldAnalyze(capturedAt)) {
                image.close()
            } else {
                // Keep the ImageProxy open until inference completes: CameraX drops older
                // frames instead of queuing concurrent detector tasks.
                fun analyzed(frame: LipCameraFrame) = report(frame.copy(capturedAtSeconds = capturedAt,
                    inferenceDurationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0))
                try {
                    detector.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
                        .addOnSuccessListener(executor) { faces ->
                            if (!disposed.get()) {
                                val face = faces.singleOrNull()
                                if (faces.isEmpty()) analyzed(LipCameraFrame(LipCameraStatus.NO_FACE))
                                else if (face == null || abs(face.headEulerAngleY) > 20f || abs(face.headEulerAngleZ) > 20f ||
                                    abs(face.headEulerAngleX) > 20f || face.boundingBox.width() < 180) {
                                    analyzed(LipCameraFrame(LipCameraStatus.ADJUST_POSITION))
                                } else {
                                    fun contour(type: Int) = face.getContour(type)?.points.orEmpty().map { LipPoint(it.x, it.y) }
                                    fun join(upper: List<LipPoint>, lower: List<LipPoint>): List<LipPoint> {
                                        if (upper.isEmpty() || lower.isEmpty()) return emptyList()
                                        val end = upper.last()
                                        fun distance(p: LipPoint) = (p.x - end.x) * (p.x - end.x) + (p.y - end.y) * (p.y - end.y)
                                        return upper + if (distance(lower.first()) < distance(lower.last())) lower else lower.reversed()
                                    }
                                    val sample = LipShapeAnalyzer.measure(
                                        join(contour(FaceContour.UPPER_LIP_TOP), contour(FaceContour.LOWER_LIP_BOTTOM)),
                                        join(contour(FaceContour.UPPER_LIP_BOTTOM), contour(FaceContour.LOWER_LIP_TOP)))
                                    analyzed(LipCameraFrame(if (sample == null) LipCameraStatus.ADJUST_POSITION else LipCameraStatus.TRACKING, sample))
                                }
                            }
                        }.addOnFailureListener(executor) { analyzed(LipCameraFrame(LipCameraStatus.ERROR)) }
                        .addOnCompleteListener { image.close() }
                } catch (_: Exception) {
                    image.close()
                    mainExecutor.execute { analyzed(LipCameraFrame(LipCameraStatus.ERROR)) }
                }
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var camera: Camera? = null
        val cameraObserver = Observer<CameraState> { state ->
            if (state.error != null) report(LipCameraFrame(LipCameraStatus.ERROR))
        }
        future.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        report(LipCameraFrame(LipCameraStatus.UNAVAILABLE))
                    } else {
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                        camera?.cameraInfo?.cameraState?.observe(lifecycleOwner, cameraObserver)
                        report(LipCameraFrame(LipCameraStatus.NO_FACE))
                    }
                } catch (_: Exception) { report(LipCameraFrame(LipCameraStatus.ERROR)) }
            }
        }, mainExecutor)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            provider?.unbind(preview, analysis)
            detector.close()
            executor.shutdown()
        }
    }
}
