package com.example.agroscan_app

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    classifier: PlantClassifier,
    // MODIFICATION 1 : On attend maintenant une LISTE de résultats
    onResultReady: (List<ClassificationResult>) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var isAnalyzing by remember { mutableStateOf(false) }
    var isFlashEnabled by remember { mutableStateOf(false) }

    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // ── Zoom state
    var zoomRatio by remember { mutableStateOf(1f) }
    val zoomMin = 1f
    val zoomMax = 8f
    var showZoomBadge by remember { mutableStateOf(false) }

    val circleSize = 280.dp
    val circlePx = with(density) { circleSize.roundToPx() }

    LaunchedEffect(zoomRatio) {
        showZoomBadge = true
        kotlinx.coroutines.delay(1500)
        showZoomBadge = false
    }

    // ── Galerie : décode en software bitmap directement
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                scope.launch {
                    isAnalyzing = true
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(context.contentResolver, it)
                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                            }
                        }
                        bitmap?.let { bmp ->
                            val results = withContext(Dispatchers.Default) {
                                // MODIFICATION 2 : Appel de classifyMultiple
                                classifier.classifyMultiple(bmp)
                            }
                            onResultReady(results)
                        }
                    } catch (e: Exception) {
                        Log.e("AgroScanAI", "Erreur chargement galerie", e)
                        Toast.makeText(context, "Erreur lors du chargement de l'image", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAnalyzing = false
                    }
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    zoomRatio = (zoomRatio * zoomChange).coerceIn(zoomMin, zoomMax)
                }
            }
    ) {
        // Flux Caméra
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            zoomRatio = zoomRatio,
            isFlashEnabled = isFlashEnabled,
            onCameraControllerReady = { cameraController = it },
            onPreviewViewReady = {
                Log.d("AgroScanAI", "PreviewView prête")
                previewView = it
            }
        )

        // ── Cadre de visée
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Cercle pointillé
            Canvas(modifier = Modifier.size(circleSize)) {
                drawCircle(
                    color = Color(0xFFC6FF00).copy(alpha = 0.6f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                    )
                )
            }

            val cornerSize = 24.dp
            val strokeWidth = 3.dp
            val lineColor = Color(0xFFC6FF00)
            val offset = circleSize / 2 - cornerSize / 2

            // Coins du cadre (Haut-Gauche, Haut-Droit, Bas-Gauche, Bas-Droit)
            Canvas(modifier = Modifier.size(cornerSize).align(Alignment.Center).offset(-offset, -offset)) {
                drawLine(lineColor, Offset(0f, cornerSize.toPx()), Offset(0f, 0f), strokeWidth.toPx())
                drawLine(lineColor, Offset(0f, 0f), Offset(cornerSize.toPx(), 0f), strokeWidth.toPx())
            }
            Canvas(modifier = Modifier.size(cornerSize).align(Alignment.Center).offset(offset, -offset)) {
                drawLine(lineColor, Offset(cornerSize.toPx(), cornerSize.toPx()), Offset(cornerSize.toPx(), 0f), strokeWidth.toPx())
                drawLine(lineColor, Offset(0f, 0f), Offset(cornerSize.toPx(), 0f), strokeWidth.toPx())
            }
            Canvas(modifier = Modifier.size(cornerSize).align(Alignment.Center).offset(-offset, offset)) {
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, cornerSize.toPx()), strokeWidth.toPx())
                drawLine(lineColor, Offset(0f, cornerSize.toPx()), Offset(cornerSize.toPx(), cornerSize.toPx()), strokeWidth.toPx())
            }
            Canvas(modifier = Modifier.size(cornerSize).align(Alignment.Center).offset(offset, offset)) {
                drawLine(lineColor, Offset(cornerSize.toPx(), 0f), Offset(cornerSize.toPx(), cornerSize.toPx()), strokeWidth.toPx())
                drawLine(lineColor, Offset(0f, cornerSize.toPx()), Offset(cornerSize.toPx(), cornerSize.toPx()), strokeWidth.toPx())
            }
        }

        // ── Badge zoom
        if (showZoomBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "%.1fx".format(zoomRatio),
                    color = Color(0xFFC6FF00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Bouton Flash
        IconButton(
            onClick = { isFlashEnabled = !isFlashEnabled },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (isFlashEnabled) Color(0xFFC6FF00) else Color.White
            )
        }

        // ── Boutons Zoom + / -
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { zoomRatio = (zoomRatio + 0.5f).coerceAtMost(zoomMax) },
                modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(
                onClick = { zoomRatio = (zoomRatio - 0.5f).coerceAtLeast(zoomMin) },
                modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom -", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        // ── Contrôles bas
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(color = Color(0xFFC6FF00))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bouton Galerie
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Galerie",
                        tint = if (isAnalyzing) Color.Gray else Color.White
                    )
                }

                // Bouton SCANNER
                Button(
                    onClick = {
                        val ctrl = cameraController
                        if (ctrl == null) {
                            Toast.makeText(context, "Patientez... caméra non prête", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isAnalyzing = true
                        ctrl.capture(captureExecutor) { bitmap ->
                            if (bitmap == null) {
                                scope.launch {
                                    Toast.makeText(context, "Erreur de capture", Toast.LENGTH_SHORT).show()
                                    isAnalyzing = false
                                }
                                return@capture
                            }
                            scope.launch {
                                try {
                                    Log.d("AgroScanAI", "ImageCapture → analyse multiple...")
                                    val results = withContext(Dispatchers.Default) {
                                        // MODIFICATION 3 : Appel de classifyMultiple
                                        classifier.classifyMultiple(bitmap)
                                    }
                                    Log.d("AgroScanAI", "Analyse terminée: ${results.size} feuilles trouvées")
                                    onResultReady(results)
                                } catch (e: Exception) {
                                    Log.e("AgroScanAI", "Crash analyse", e)
                                    Toast.makeText(context, "Erreur d'analyse IA", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        }
                    },
                    enabled = !isAnalyzing && cameraController != null,
                    modifier = Modifier.height(64.dp).weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC6FF00),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFFC6FF00).copy(alpha = 0.4f),
                        disabledContentColor = Color.Black.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(
                        text = if (isAnalyzing) "ANALYSE..." else "SCANNER",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}