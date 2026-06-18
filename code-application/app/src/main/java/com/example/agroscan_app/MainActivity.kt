package com.example.agroscan_app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// AJOUT : Le jalon d'état Detail embarque maintenant l'indicateur de santé
sealed class ScanState {
    object Splash : ScanState()
    object Scanner : ScanState()
    data class Summary(val results: List<ClassificationResult>) : ScanState()
    data class Detail(
        val info: DiseaseInfo,
        val isHealthy: Boolean, // AJOUT : permet de porter l'information de thème
        val results: List<ClassificationResult>
    ) : ScanState()
}

class AgroScanViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf<ScanState>(ScanState.Splash)
    val classifier = PlantClassifier(application)

    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) setContent { AgroScanApp() }
        else Toast.makeText(this, "Permission caméra requise pour AgroScan", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            setContent { AgroScanApp() }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun AgroScanApp(viewModel: AgroScanViewModel = viewModel()) {
    AgroScanTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val current = viewModel.state) {
                is ScanState.Splash -> {
                    SplashScreen(
                        onSplashFinished = {
                            viewModel.state = ScanState.Scanner
                        }
                    )
                }

                is ScanState.Scanner -> {
                    ScannerScreen(
                        classifier    = viewModel.classifier,
                        onResultReady = { results -> viewModel.state = ScanState.Summary(results) }
                    )
                }

                is ScanState.Summary -> {
                    BackHandler { viewModel.state = ScanState.Scanner }

                    // MODIFICATION : Ici, 'onLeafSelected' reçoit l'élément complet du scan (ClassificationResult)
                    // au lieu de seulement l'objet d'informations textuelles.
                    ScanResultsSummaryScreen(
                        results = current.results,
                        onRetake = { viewModel.state = ScanState.Scanner },
                        onLeafSelected = { selectedResult ->
                            viewModel.state = ScanState.Detail(
                                info = selectedResult.diseaseInfo ?: return@ScanResultsSummaryScreen,
                                isHealthy = selectedResult.isHealthy, // Transmission de l'état exact au State
                                results = current.results
                            )
                        }
                    )
                }

                is ScanState.Detail -> {
                    BackHandler { viewModel.state = ScanState.Summary(current.results) }

                    // MODIFICATION : On injecte dynamiquement l'état de santé issu du State courant
                    DiseaseDetailScreen(
                        info      = current.info,
                        isHealthy = current.isHealthy,
                        onBack    = { viewModel.state = ScanState.Summary(current.results) }
                    )
                }
            }
        }
    }
}

@Composable
fun AgroScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary    = Color(0xFF1B5E20),
            onPrimary  = Color.White,
            background = Color(0xFFF9FBF9),
            surface    = Color.White
        ),
        content = content
    )
}