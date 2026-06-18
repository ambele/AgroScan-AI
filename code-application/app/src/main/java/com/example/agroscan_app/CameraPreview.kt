package com.example.agroscan_app

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1f,
    isFlashEnabled: Boolean = false,
    onCameraControllerReady: (CameraController) -> Unit = {},
    onPreviewViewReady: (PreviewView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(zoomRatio) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }
    LaunchedEffect(isFlashEnabled) {
        camera?.cameraControl?.enableTorch(isFlashEnabled)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                // COMPATIBLE + FILL_CENTER empêche l'étirement de l'aperçu sur l'écran
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onPreviewViewReady(this)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    try {
                        cameraProvider.unbindAll()

                        // Liaison directe des flux sans forcer de ViewPort matériel contraignant
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        camera = boundCamera

                        // CORRECTION : On passe l'instance de PreviewView pour obtenir ses dimensions réelles au moment du clic
                        onCameraControllerReady(CameraController(boundCamera, imageCapture, this))
                    } catch (e: Exception) {
                        Log.e("AgroScanAI", "Erreur CameraX", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

data class CameraController(
    val camera: Camera,
    val imageCapture: ImageCapture,
    val previewView: PreviewView // Contient la référence de la vue écran
) {
    fun capture(executor: java.util.concurrent.Executor, onBitmap: (Bitmap?) -> Unit) {
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // 1. On récupère le bitmap entier redressé correctement par CameraX
                val fullBitmap = image.toBitmap()
                image.close()

                // 2. Recadrage intelligent (Center-Crop) calqué sur le ratio de ton écran
                val finalBitmap = try {
                    val viewWidth = previewView.width
                    val viewHeight = previewView.height

                    if (viewWidth > 0 && viewHeight > 0) {
                        val previewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                        val bitmapRatio = fullBitmap.width.toFloat() / fullBitmap.height.toFloat()

                        var xOffset = 0
                        var yOffset = 0
                        var targetWidth = fullBitmap.width
                        var targetHeight = fullBitmap.height

                        if (bitmapRatio > previewRatio) {
                            // Le bitmap de capture est plus large que l'écran -> On coupe les bandes blanches à gauche et à droite
                            targetWidth = (fullBitmap.height * previewRatio).toInt()
                            xOffset = (fullBitmap.width - targetWidth) / 2
                        } else if (bitmapRatio < previewRatio) {
                            // Le bitmap de capture est plus long que l'écran -> On coupe le haut et le bas
                            targetHeight = (fullBitmap.width / previewRatio).toInt()
                            yOffset = (fullBitmap.height - targetHeight) / 2
                        }

                        // Découpe propre avec les bonnes dimensions réalignées
                        Bitmap.createBitmap(fullBitmap, xOffset, yOffset, targetWidth, targetHeight)
                    } else {
                        fullBitmap
                    }
                } catch (e: Exception) {
                    Log.e("AgroScanAI", "Erreur lors du Center-Crop, retour au bitmap d'origine", e)
                    fullBitmap
                }

                onBitmap(finalBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                onBitmap(null)
            }
        })
    }
}