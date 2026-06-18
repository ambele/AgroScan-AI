package com.example.agroscan_app

import android.content.Context
import android.graphics.*
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class ClassificationResult(
    val label: String,
    val confidence: Int,
    val isHealthy: Boolean,
    val originalBitmap: Bitmap,
    val heatmapBitmap: Bitmap? = null,
    val diseaseInfo: DiseaseInfo? = null,
    val leafDetected: Boolean = false,
    val boundingBox: RectF? = null,
    val detectedSpecies: String = ""
)

data class DiseaseInfo(
    val name: String,
    val description: String,
    val symptoms: List<String>,
    val recommendations: String
)

private data class Detection(val rect: RectF, val score: Float, val species: String)

class PlantClassifier(private val context: Context) {

    private var yoloInterpreter: Interpreter? = null
    private var pytorchModule: Module? = null
    private var classLabels: List<String> = emptyList()
    private var diseaseDatabase: Map<String, DiseaseInfo> = emptyMap()

    private val yoloModelPath    = "yolov8n_species.tflite"
    private val pytorchModelPath = "model_gradcam.ptl"
    private val jsonPath         = "etiquettes_symptomes_recommendations.json"
    private val labelsPath       = "etiquettes.txt"

    private val yoloSpeciesClasses = listOf(
        "Apple", "Blueberry", "Cherry_(including_sour)", "Corn_(maize)", "Grape",
        "Orange", "Peach", "Pepper,_bell", "Potato", "Raspberry",
        "Soybean", "Squash", "Strawberry", "Tomato"
    )

    private var yoloInputSize     = 640
    private var isYoloNCHW        = false
    private val CLASS_INPUT_SIZE  = 224

    // CONFIGURATION DES SEUILS (Ajustés pour les scores réels TFLite sans double-sigmoid)
    private val CANDIDATE_THRESHOLD = 0.30f
    private val LEAF_PRESENCE_THRESHOLD = 0.35f
    private val NMS_IOU_THRESHOLD = 0.45f

    private val CAM_H = 7
    private val CAM_W = 7
    private var temperature: Float = 1.0f

    private var yoloInputBuffer: ByteBuffer? = null
    private var yoloOutputArray: Array<Array<FloatArray>>? = null
    private var yoloPixelsBuffer: IntArray? = null

    private var isOutputTransposed = false
    private var numAnchors = 0

    private val paintRectHealthy = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.GREEN
    }

    private val paintRectSick = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.RED
    }

    init {
        CoroutineScope(Dispatchers.IO).launch { loadAssets() }
    }

    private fun loadAssets() {
        try {
            context.assets.open(labelsPath).bufferedReader().useLines { lines ->
                classLabels = lines.filter { it.isNotBlank() }.map { it.trim() }.toList()
            }

            val model = FileUtil.loadMappedFile(context, yoloModelPath)
            yoloInterpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))

            val shape = yoloInterpreter!!.getInputTensor(0).shape()
            isYoloNCHW = shape[1] == 3
            yoloInputSize = if (isYoloNCHW) shape[2] else shape[1]

            val outputShape = yoloInterpreter!!.getOutputTensor(0).shape()
            val outRows = outputShape[1]
            val outCols = outputShape[2]

            isOutputTransposed = outRows > outCols

            yoloInputBuffer = ByteBuffer.allocateDirect(1 * yoloInputSize * yoloInputSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            yoloOutputArray = Array(1) { Array(outRows) { FloatArray(outCols) } }
            yoloPixelsBuffer = IntArray(yoloInputSize * yoloInputSize)

            numAnchors = if (isOutputTransposed) outRows else outCols

            pytorchModule = LiteModuleLoader.loadModuleFromAsset(context.assets, pytorchModelPath)

            val db = mutableMapOf<String, DiseaseInfo>()
            context.assets.open(jsonPath).use { stream ->
                val reader = JsonReader(InputStreamReader(stream))
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    var nomFr = ""; var desc = ""
                    val symp = mutableListOf<String>()
                    val reco = mutableListOf<String>()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "nom_fr" -> nomFr = reader.nextString()
                            "description" -> desc = reader.nextString()
                            "symptomes" -> { reader.beginArray(); while(reader.hasNext()) symp.add(reader.nextString()); reader.endArray() }
                            "recommendations" -> { reader.beginArray(); while(reader.hasNext()) reco.add(reader.nextString()); reader.endArray() }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    db[key] = DiseaseInfo(nomFr, desc, symp, reco.joinToString("\n"))
                }
                reader.endObject()
            }
            diseaseDatabase = db

            try {
                context.assets.open("temperature.txt").bufferedReader().use { br ->
                    val line = br.readLine()
                    if (line != null) temperature = line.trim().toFloat()
                }
            } catch (e: Exception) { temperature = 1.0f }

            Log.d("AgroScanAI", "✓ IA en Cascade prête")
        } catch (e: Exception) {
            Log.e("AgroScanAI", "Erreur initialisation IA", e)
        }
    }

    private fun toSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config != Bitmap.Config.HARDWARE) bitmap
        else {
            val soft = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(soft).drawBitmap(bitmap, 0f, 0f, null)
            soft
        }
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersectionArea = max(0f, right - left) * max(0f, bottom - top)
        if (intersectionArea == 0f) return 0f
        val areaA = a.width() * a.height()
        val areaB = b.width() * b.height()
        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.score }.toMutableList()
        val selectedDetections = mutableListOf<Detection>()
        while (sortedDetections.isNotEmpty()) {
            val best = sortedDetections.removeAt(0)
            selectedDetections.add(best)
            sortedDetections.removeAll { calculateIoU(best.rect, it.rect) > iouThreshold }
        }
        return selectedDetections
    }

    private fun filterContainedBoxes(detections: List<Detection>): List<Detection> {
        val keep = mutableListOf<Detection>()
        val sortedDetections = detections.sortedByDescending { it.score }

        for (box in sortedDetections) {
            var isContained = false
            val boxArea = box.rect.width() * box.rect.height()

            for (keptBox in keep) {
                val intersectLeft = max(box.rect.left, keptBox.rect.left)
                val intersectTop = max(box.rect.top, keptBox.rect.top)
                val intersectRight = min(box.rect.right, keptBox.rect.right)
                val intersectBottom = min(box.rect.bottom, keptBox.rect.bottom)

                if (intersectLeft < intersectRight && intersectTop < intersectBottom) {
                    val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
                    val ioa = intersectionArea / boxArea

                    if (ioa > 0.75f) {
                        isContained = true
                        break
                    }
                }
            }
            if (!isContained) {
                keep.add(box)
            }
        }
        return keep
    }

    private fun detectLeaves(bitmap: Bitmap): List<Detection> {
        val yolo = yoloInterpreter ?: return emptyList()
        val input = yoloInputBuffer ?: return emptyList()
        val output = yoloOutputArray ?: return emptyList()
        val pixels = yoloPixelsBuffer ?: return emptyList()

        input.rewind()
        val resized = Bitmap.createScaledBitmap(bitmap, yoloInputSize, yoloInputSize, true)
        resized.getPixels(pixels, 0, yoloInputSize, 0, 0, yoloInputSize, yoloInputSize)
        if (resized != bitmap) resized.recycle()

        if (isYoloNCHW) {
            for (c in 0 until 3) {
                for (px in pixels) {
                    val v = when(c) { 0 -> (px shr 16) and 0xFF; 1 -> (px shr 8) and 0xFF; else -> px and 0xFF }
                    input.putFloat(v / 255f)
                }
            }
        } else {
            for (px in pixels) {
                input.putFloat(((px shr 16) and 0xFF) / 255f)
                input.putFloat(((px shr 8) and 0xFF) / 255f)
                input.putFloat((px and 0xFF) / 255f)
            }
        }

        input.rewind()
        yolo.run(input, output)

        val allDetections = mutableListOf<Detection>()

        var usesNormalizedCoords = true
        for (i in 0 until minOf(50, numAnchors)) {
            val cx = if (isOutputTransposed) output[0][i][0] else output[0][0][i]
            if (cx > 1.5f) {
                usesNormalizedCoords = false
                break
            }
        }

        val xF = if (usesNormalizedCoords) bitmap.width.toFloat() else (bitmap.width.toFloat() / yoloInputSize)
        val yF = if (usesNormalizedCoords) bitmap.height.toFloat() else (bitmap.height.toFloat() / yoloInputSize)

        for (i in 0 until numAnchors) {
            var maxSpeciesScore = 0f
            var bestSpeciesIdx = -1

            for (c in 0 until yoloSpeciesClasses.size) {
                val channelIdx = c + 4
                val score = if (isOutputTransposed) output[0][i][channelIdx] else output[0][channelIdx][i]

                if (score > maxSpeciesScore) {
                    maxSpeciesScore = score
                    bestSpeciesIdx = c
                }
            }

            if (maxSpeciesScore > CANDIDATE_THRESHOLD && bestSpeciesIdx != -1) {
                val rawCx = if (isOutputTransposed) output[0][i][0] else output[0][0][i]
                val rawCy = if (isOutputTransposed) output[0][i][1] else output[0][1][i]
                val rawW  = if (isOutputTransposed) output[0][i][2] else output[0][2][i]
                val rawH  = if (isOutputTransposed) output[0][i][3] else output[0][3][i]

                val rect = RectF(
                    (rawCx - rawW / 2f) * xF,
                    (rawCy - rawH / 2f) * yF,
                    (rawCx + rawW / 2f) * xF,
                    (rawCy + rawH / 2f) * yF
                )

                val speciesName = yoloSpeciesClasses[bestSpeciesIdx]
                allDetections.add(Detection(rect, maxSpeciesScore, speciesName))
            }
        }

        val nmsDetections = applyNMS(allDetections, NMS_IOU_THRESHOLD)
        val cleanedDetections = filterContainedBoxes(nmsDetections)

        val finalDetections = cleanedDetections.filter { it.score >= LEAF_PRESENCE_THRESHOLD }

        if (finalDetections.isEmpty() && cleanedDetections.isNotEmpty()) {
            Log.d("AgroScanAI", "Boîtes rejetées sous le seuil de présence. Meilleur score : ${cleanedDetections.first().score}")
        }

        return finalDetections
    }

    fun classifyMultiple(rawBitmap: Bitmap): List<ClassificationResult> {
        val soft = toSoftwareBitmap(rawBitmap)

        val maxDim = 800f
        val scale = maxDim / maxOf(soft.width, soft.height).toFloat()
        val targetWidth = (soft.width * scale).toInt()
        val targetHeight = (soft.height * scale).toInt()
        val display = Bitmap.createScaledBitmap(soft, targetWidth, targetHeight, true)

        val detections = detectLeaves(display)
        if (detections.isEmpty()) {
            return listOf(ClassificationResult("Végétal non détecté", 0, false, display, leafDetected = false))
        }

        val globalSpecies = detections
            .groupBy { it.species }
            .maxByOrNull { it.value.size }
            ?.key ?: detections.first().species

        Log.d("AgroScanAI", "Cascade - Espèce élue par vote majoritaire YOLO : $globalSpecies")

        val masterPhoto = display.copy(Bitmap.Config.ARGB_8888, true)
        val masterHeatmap = display.copy(Bitmap.Config.ARGB_8888, true)
        val canvasPhoto = Canvas(masterPhoto)
        val canvasHeatmap = Canvas(masterHeatmap)

        val results = mutableListOf<ClassificationResult>()

        for (detection in detections) {
            val rect = detection.rect

            val x = rect.left.toInt().coerceIn(0, display.width - 5)
            val y = rect.top.toInt().coerceIn(0, display.height - 5)
            val w = rect.width().toInt().coerceIn(5, display.width - x)
            val h = rect.height().toInt().coerceIn(5, display.height - y)

            val crop = Bitmap.createBitmap(display, x, y, w, h)

            val result = runInference(crop, rect, canvasPhoto, canvasHeatmap, globalSpecies)
            results.add(result)
            crop.recycle()
        }

        return results.map { it.copy(originalBitmap = masterPhoto, heatmapBitmap = masterHeatmap) }
    }

    private fun runInference(
        leaf: Bitmap,
        rect: RectF,
        canvasPhoto: Canvas,
        canvasHeatmap: Canvas,
        globalSpecies: String
    ): ClassificationResult {
        val module = pytorchModule ?: return ClassificationResult("Erreur PyTorch", 0, false, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

        val resizedLeaf = Bitmap.createScaledBitmap(leaf, CLASS_INPUT_SIZE, CLASS_INPUT_SIZE, true)
        val input = TensorImageUtils.bitmapToFloat32Tensor(
            resizedLeaf, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        if (resizedLeaf != leaf) resizedLeaf.recycle()

        val output = module.forward(IValue.from(input))
        val (logits, cam) = if (output.isTuple) {
            val t = output.toTuple()
            Pair(t[0].toTensor().dataAsFloatArray, t[1].toTensor().dataAsFloatArray)
        } else Pair(output.toTensor().dataAsFloatArray, null)

        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(((it - maxLogit) / temperature).toDouble()).toFloat() }
        val sum = exps.sum()
        val probs = exps.map { it / sum }

        var yoloMatchIdx = -1
        var yoloMatchProb = -1f

        for (i in probs.indices) {
            val labelRaw = classLabels.getOrElse(i) { "" }
            if (labelRaw.startsWith("${globalSpecies}___", ignoreCase = true)) {
                if (probs[i] > yoloMatchProb) {
                    yoloMatchProb = probs[i]
                    yoloMatchIdx = i
                }
            }
        }

        val absoluteBestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val absoluteBestProb = probs[absoluteBestIdx]
        val absoluteLabelRaw = classLabels.getOrElse(absoluteBestIdx) { "Inconnu" }
        val absoluteSpecies = absoluteLabelRaw.substringBefore("___", "")

        val finalIdx: Int
        val finalProb: Float
        val finalSpecies: String

        // On applique strictement le choix de YOLO s'il a trouvé une correspondance
        if (yoloMatchIdx != -1) {
            finalIdx = yoloMatchIdx
            finalProb = yoloMatchProb
            finalSpecies = globalSpecies
        } else {
            // Repli sur le meilleur résultat global uniquement si l'espèce YOLO n'existe pas dans le dictionnaire PyTorch
            finalIdx = absoluteBestIdx
            finalProb = absoluteBestProb
            finalSpecies = absoluteSpecies
        }

        val finalLabel = classLabels.getOrElse(finalIdx) { "Inconnu" }
        val info = diseaseDatabase[finalLabel]
        val healthy = finalLabel.contains("healthy", true)

        val paint = if (healthy) paintRectHealthy else paintRectSick

        if (cam != null) {
            // Rendu de la heatmap (isHealthy est passé, mais la fonction utilise désormais une logique unifiée)
            drawHeatmap(canvasHeatmap, cam, rect, healthy)
            canvasHeatmap.drawRect(rect, paint)
        }
        canvasPhoto.drawRect(rect, paint)

        val tempBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        return ClassificationResult(
            label = info?.name ?: finalLabel,
            confidence = (finalProb * 100).toInt(),
            isHealthy = healthy,
            originalBitmap = tempBitmap,
            heatmapBitmap = tempBitmap,
            diseaseInfo = info,
            leafDetected = true,
            boundingBox = rect,
            detectedSpecies = finalSpecies
        )
    }

    // MODIFICATION : Logique de couleur unifiée et standardisée (Palette Jet continue)
    private fun drawHeatmap(canvas: Canvas, cam: FloatArray, rect: RectF, isHealthy: Boolean) {
        val hw = CAM_H * CAM_W
        if (cam.isEmpty()) return
        val channels = cam.size / hw
        val map2d = FloatArray(hw)
        for (c in 0 until channels) {
            val offset = c * hw
            for (i in 0 until hw) map2d[i] += max(0f, cam[offset + i])
        }
        val minV = map2d.minOrNull() ?: 0f
        val maxV = map2d.maxOrNull() ?: 1f
        val range = if (maxV > minV) maxV - minV else 1f
        val pixels = IntArray(hw)

        for (i in 0 until hw) {
            // Valeur normalisée directe de 0.0 (froid/bleu) à 1.0 (chaud/rouge)
            val v = ((map2d[i] - minV) / range).coerceIn(0f, 1f)

            // Équations de la palette Jet classique : transition fluide Bleu -> Cyan -> Vert -> Jaune -> Rouge
            val r = (max(0f, minOf(1f, 4f * v - 1.5f)) * 255).toInt()
            val g = (max(0f, minOf(1f, 2f - 4f * abs(v - 0.5f))) * 255).toInt()
            val b = (max(0f, minOf(1f, 1.5f - 4f * v)) * 255).toInt()

            // Opacité constante à 140 pour fusionner la carte avec la texture de la feuille
            pixels[i] = Color.argb(140, r, g, b)
        }

        val bmp = Bitmap.createBitmap(CAM_W, CAM_H, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, CAM_W, 0, 0, CAM_W, CAM_H)
        val scaled = Bitmap.createScaledBitmap(bmp, rect.width().toInt().coerceAtLeast(1), rect.height().toInt().coerceAtLeast(1), true)
        canvas.drawBitmap(scaled, rect.left, rect.top, null)
        bmp.recycle()
        scaled.recycle()
    }

    fun close() {
        yoloInterpreter?.close()
        pytorchModule?.destroy()
        yoloInputBuffer = null
        yoloOutputArray = null
        yoloPixelsBuffer = null
    }
}