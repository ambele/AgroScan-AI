package com.example.agroscan_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas

val speciesTranslationMap = mapOf(
    "Apple" to "Pommier",
    "Blueberry" to "Myrtillier",
    "Cherry_(including_sour)" to "Cerisier",
    "Corn_(maize)" to "Maïs",
    "Grape" to "Vigne",
    "Orange" to "Oranger",
    "Peach" to "Pêcher",
    "Pepper,_bell" to "Poivron",
    "Potato" to "Pomme de terre",
    "Raspberry" to "Framboisier",
    "Soybean" to "Soja",
    "Squash" to "Courge",
    "Strawberry" to "Fraisier",
    "Tomato" to "Tomate"
)

fun getConsolidatedDiagnostic(results: List<ClassificationResult>): ClassificationResult? {
    if (results.isEmpty()) return null

    val sickLeaves = results.filter { !it.isHealthy }

    return if (sickLeaves.isEmpty()) {
        results.maxByOrNull { it.confidence }
    } else {
        val dominantDiseaseGroup = sickLeaves.groupBy { it.label }
            .maxByOrNull { it.value.size }
            ?.value

        dominantDiseaseGroup?.maxByOrNull { it.confidence }
    }
}

@Composable
fun ScanResultsSummaryScreen(
    results: List<ClassificationResult>,
    onLeafSelected: (ClassificationResult) -> Unit,
    onRetake: () -> Unit
) {
    var showHeatmap by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
    ) {
        DiagnosticDisclaimerBanner()

        val firstResult = results.firstOrNull()
        val globalDiagnostic = getConsolidatedDiagnostic(results)

        if (firstResult != null && firstResult.leafDetected) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .pointerInput(results) {
                        detectTapGestures { tapOffset ->
                            val imgWidth = firstResult.originalBitmap.width.toFloat()
                            val imgHeight = firstResult.originalBitmap.height.toFloat()

                            val scale = maxOf(size.width / imgWidth, size.height / imgHeight)
                            val dx = (size.width - imgWidth * scale) / 2f
                            val dy = (size.height - imgHeight * scale) / 2f

                            val clickedResult = results.find { result ->
                                val bbox = result.boundingBox
                                if (bbox != null) {
                                    val left = bbox.left * scale + dx
                                    val top = bbox.top * scale + dy
                                    val right = bbox.right * scale + dx
                                    val bottom = bbox.bottom * scale + dy
                                    tapOffset.x in left..right && tapOffset.y in top..bottom
                                } else {
                                    false
                                }
                            }
                            if (clickedResult?.diseaseInfo != null) {
                                onLeafSelected(clickedResult)
                            }
                        }
                    }
            ) {
                val bitmapToDisplay = if (showHeatmap && firstResult.heatmapBitmap != null) {
                    firstResult.heatmapBitmap.asImageBitmap()
                } else {
                    firstResult.originalBitmap.asImageBitmap()
                }

                Image(
                    bitmap = bitmapToDisplay,
                    contentDescription = "Résultat du scan",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imgWidth = firstResult.originalBitmap.width.toFloat()
                    val imgHeight = firstResult.originalBitmap.height.toFloat()

                    if (imgWidth > 0 && imgHeight > 0) {
                        val scale = maxOf(size.width / imgWidth, size.height / imgHeight)
                        val dx = (size.width - imgWidth * scale) / 2f
                        val dy = (size.height - imgHeight * scale) / 2f

                        results.forEachIndexed { index, result ->
                            result.boundingBox?.let { bbox ->
                                val left = bbox.left * scale + dx
                                val top = bbox.top * scale + dy

                                val badgeRadius = 13.dp.toPx()
                                val centerOffset = Offset(left + 6.dp.toPx(), top + 6.dp.toPx())
                                val themeColor = if (result.isHealthy) Color(0xFF2E7D32) else Color(0xFFC62828)

                                drawCircle(color = themeColor, radius = badgeRadius, center = centerOffset)
                                drawCircle(color = Color.White, radius = badgeRadius, center = centerOffset, style = Stroke(width = 2.dp.toPx()))

                                val textLayoutResult = textMeasurer.measure(
                                    text = "${index + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )

                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(centerOffset.x - (textLayoutResult.size.width / 2f), centerOffset.y - (textLayoutResult.size.height / 2f))
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (showHeatmap) "Mode Grad-CAM" else "Mode Détection",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Analyse thermique (Grad-CAM)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "Visualiser les zones malades", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = showHeatmap,
                    onCheckedChange = { showHeatmap = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFC62828))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rawSpecies = firstResult.detectedSpecies
            val displaySpecies = speciesTranslationMap[rawSpecies] ?: rawSpecies

            if (displaySpecies.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Plante identifiée", fontSize = 12.sp, color = Color(0xFF1565C0))
                            Text(text = displaySpecies, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0D47A1))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Diagnostic Global",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (globalDiagnostic != null) {
            val cardColor = if (globalDiagnostic.isHealthy) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            val iconColor = if (globalDiagnostic.isHealthy) Color(0xFF2E7D32) else Color(0xFFC62828)
            val cleanLabel = globalDiagnostic.label.substringAfter("___").replace("_", " ")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = globalDiagnostic.diseaseInfo != null) {
                        onLeafSelected(globalDiagnostic)
                    },
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (globalDiagnostic.isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cleanLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        // AJUSTEMENT : Affichage de la confiance pour TOUS les diagnostics
                        Text(
                            text = if (globalDiagnostic.isHealthy) "Aucune maladie détectée - Confiance : ${globalDiagnostic.confidence}%" else "Confiance : ${globalDiagnostic.confidence}%",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onRetake,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("PRENDRE UNE AUTRE PHOTO")
        }
    }
}