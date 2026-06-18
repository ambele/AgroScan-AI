package com.example.agroscan_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiseaseDetailScreen(
    info: DiseaseInfo?,
    isHealthy: Boolean, // AJOUT : État passé par l'architecture de navigation
    onBack: () -> Unit
) {
    // Sécurité si info est null
    if (info == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Données indisponibles", color = Color.Gray)
        }
        return
    }

    // Sélection dynamique du thème visuel (Vert pour sain, Rouge pour maladie)
    val accentColor = if (isHealthy) Color(0xFF2E7D32) else Color(0xFFC62828)
    val bgColor     = if (isHealthy) Color(0xFFF1F8E9) else Color(0xFFFFF3F3)
    val headerIcon  = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.BugReport

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header coloré avec bouton retour intégré
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }

                Icon(
                    headerIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isHealthy) "Plante saine" else "Maladie détectée",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        info.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Intégration de la bannière externe
        DiagnosticDisclaimerBanner()

        Column(modifier = Modifier.padding(16.dp)) {
            // Description
            DetailCard(
                icon  = Icons.Default.Description,
                title = "Description",
                color = accentColor
            ) {
                Text(info.description, fontSize = 15.sp, color = Color.DarkGray, lineHeight = 22.sp)
            }

            // Symptômes (affichés uniquement si la liste n'est pas vide)
            if (info.symptoms.isNotEmpty()) {
                DetailCard(
                    icon  = Icons.Default.Warning,
                    title = "Symptômes observés",
                    color = accentColor
                ) {
                    info.symptoms.forEach { symptom ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "•",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(symptom, fontSize = 15.sp, color = Color.DarkGray, lineHeight = 22.sp)
                        }
                    }
                }
            }

            // Recommandations / Conseils
            if (info.recommendations.isNotBlank()) {
                DetailCard(
                    // Icône de feuille si saine, icône de soin si malade
                    icon  = if (isHealthy) Icons.Default.Eco else Icons.Default.Healing,
                    title = if (isHealthy) "Conseils de suivi et d'entretien" else "Recommandations de traitement",
                    color = accentColor
                ) {
                    val recosList = info.recommendations.split("\n").filter { it.isNotBlank() }

                    recosList.forEach { reco ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 6.dp, top = 2.dp)
                            )
                            Text(reco.trim(), fontSize = 15.sp, color = Color.DarkGray, lineHeight = 22.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gros Bouton retour en bas
            Button(
                onClick  = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape    = RoundedCornerShape(28.dp)
            ) {
                Text("RETOUR AUX RÉSULTATS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailCard(
    icon: ImageVector,
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}