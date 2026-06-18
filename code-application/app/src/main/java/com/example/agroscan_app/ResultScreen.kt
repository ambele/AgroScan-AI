package com.example.agroscan_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ResultScreen(
    result: ClassificationResult,
    onBack: () -> Unit,
    onSeeDetails: () -> Unit
) {
    val isNotDetected = result.label == "Végétal non détecté"
    val isError = result.label == "Erreur"

    val conf = result.confidence
    val isHighConf = conf >= 70
    val isMediumConf = conf in 50 until 70
    val isLowConf = conf < 50

    val isUncertain = isLowConf && !isNotDetected && !isError

    val statusColor = when {
        isNotDetected || isError -> Color(0xFF757575)
        isLowConf -> Color(0xFFFF9800)
        isMediumConf -> Color(0xFFFFC107)
        result.isHealthy && isHighConf -> Color(0xFF4CAF50)
        !result.isHealthy && isHighConf -> Color(0xFFD32F2F)
        else -> Color(0xFF757575)
    }

    val statusText = when {
        isNotDetected -> "Sujet non détecté"
        isError -> "Échec de l'analyse"
        isLowConf -> "Analyse incertaine (< 50 %)"
        isMediumConf -> "Analyse relativement incertaine (50–69 %)"
        isHighConf && result.isHealthy -> "Diagnostic fiable — plante saine ✓"
        isHighConf && !result.isHealthy -> "Diagnostic fiable — maladie détectée"
        else -> "Analyse en cours"
    }

    val statusIcon = when {
        isNotDetected || isError -> Icons.Default.HideSource
        isLowConf -> Icons.Default.HelpOutline
        isMediumConf -> Icons.Default.Info
        result.isHealthy && isHighConf -> Icons.Default.CheckCircle
        !result.isHealthy && isHighConf -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    // Dans ResultScreen.kt, forcez l'affichage si le bitmap existe
    val showGradcam = result.heatmapBitmap != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FBF9))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header (Correction du chevauchement barre d'état)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.12f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding() // Ajoute l'espace nécessaire pour la barre d'état (heure/batterie)
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp) // Padding interne
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        statusText.uppercase(), 
                        color = statusColor,
                        fontWeight = FontWeight.Black, 
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isUncertain) "Incertain" else result.label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A1A),
                        lineHeight = 26.sp
                    )
                }
            }
        }

        // ── Barre de confiance
        if (!isNotDetected && !isError) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Indice de confiance", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text(
                        "${result.confidence}%", fontSize = 18.sp,
                        fontWeight = FontWeight.Black, color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { result.confidence / 100f },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        // ── Tabs
        if (showGradcam) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = Color.Transparent,
                contentColor = Color(0xFF1B5E20)
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("PHOTO", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("GRAD-CAM", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ── Image
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp).aspectRatio(1.2f),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            val displayBitmap = if (selectedTab == 1 && showGradcam) result.heatmapBitmap!! else result.originalBitmap
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Boutons
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (result.diseaseInfo != null) {
                Button(
                    onClick = onSeeDetails,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CONSEILS DE TRAITEMENT", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1B5E20))
            ) {
                Icon(Icons.Default.Replay, contentDescription = null, tint = Color(0xFF1B5E20))
                Spacer(Modifier.width(12.dp))
                Text("NOUVELLE ANALYSE", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
            }
        }
        
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
