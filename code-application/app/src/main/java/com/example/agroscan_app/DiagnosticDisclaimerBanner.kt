package com.example.agroscan_app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagnosticDisclaimerBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        // Fond orange très clair pour signaler un avertissement/clause de non-responsabilité
        color = Color(0xFFFFF3E0),
        contentColor = Color(0xFFE65100)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Clause de non-responsabilité",
                tint = Color(0xFFEF6C00),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AgrosCan IA est un assistant d’aide au diagnostic et ne constitue pas une décision agronomique définitive.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}