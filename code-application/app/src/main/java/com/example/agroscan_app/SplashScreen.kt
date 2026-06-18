package com.example.agroscan_app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // État pour déclencher l'animation
    var startAnimation by remember { mutableStateOf(false) }

    // Animation d'opacité (fade-in) sur 1.5 secondes
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "alpha_anim"
    )

    // Déclenchement au lancement de l'écran
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2500) // Maintient l'écran visible pendant 2.5 secondes au total
        onSplashFinished()
    }

    // Interface utilisateur
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E7D32)), // Le vert principal de ton application
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Modification ici : Utilisation de ton logo PNG personnalisé
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "Logo AgroScan",
            modifier = Modifier
                .size(160.dp) // Légèrement agrandi pour un rendu optimal à l'écran
                .alpha(alphaAnim.value) // Application de l'animation de fondu
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AgroScan AI",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alphaAnim.value)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Votre assistant de diagnostic végétal",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            modifier = Modifier.alpha(alphaAnim.value)
        )
    }
}