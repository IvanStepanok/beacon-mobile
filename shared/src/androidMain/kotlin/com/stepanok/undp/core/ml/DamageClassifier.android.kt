package com.stepanok.undp.core.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.stepanok.undp.core.android.AndroidAppContext
import com.stepanok.undp.domain.model.DamageTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MODEL_ASSET = "damage_classifier.tflite"
private const val INPUT = 224

/** Output index → Beacon tier (the model's softmax order, matching ml/train_damage.py). */
private val TIER_ORDER = arrayOf(DamageTier.MINIMAL, DamageTier.PARTIAL, DamageTier.COMPLETE)

/**
 * Android advisory damage classifier (B2): on-device, OFFLINE TFLite (MobileNetV3-Small) inference.
 * The model bundles its own preprocessing, so we feed a 224×224 RGB image as float [0,255]. If the
 * model asset isn't bundled, or anything fails, it ABSTAINS (DamageSuggestion()) — never throws,
 * never blocks the capture flow. Sub-floor confidence also abstains.
 */
private class AndroidDamageClassifier : DamageClassifier {

    // Single lazy interpreter load; null when no model is bundled (→ abstain).
    private val interpreter: Interpreter? by lazy {
        runCatching {
            val bytes = AndroidAppContext.require().assets.open(MODEL_ASSET).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).rewind()
            Interpreter(buf).also { Log.i("BeaconDamage", "interpreter loaded (${bytes.size} bytes)") }
        }.onFailure { Log.w("BeaconDamage", "interpreter load FAILED", it) }.getOrNull()
    }

    override suspend fun classify(imagePath: String): DamageSuggestion = withContext(Dispatchers.Default) {
        val itp = interpreter ?: return@withContext DamageSuggestion()
        runCatching {
            val bmp = BitmapFactory.decodeFile(imagePath) ?: return@runCatching DamageSuggestion()
            val scaled = Bitmap.createScaledBitmap(bmp, INPUT, INPUT, true)

            val input = ByteBuffer.allocateDirect(4 * INPUT * INPUT * 3).order(ByteOrder.nativeOrder())
            val px = IntArray(INPUT * INPUT)
            scaled.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
            for (p in px) {
                input.putFloat(((p shr 16) and 0xFF).toFloat()) // R
                input.putFloat(((p shr 8) and 0xFF).toFloat())  // G
                input.putFloat((p and 0xFF).toFloat())          // B
            }
            input.rewind()

            val out = Array(1) { FloatArray(TIER_ORDER.size) }
            itp.run(input, out)
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()

            val probs = out[0]
            var best = 0
            for (i in 1 until probs.size) if (probs[i] > probs[best]) best = i
            val confidence = probs[best]
            Log.i("BeaconDamage", "probs=[" + probs.joinToString { it.toString() } + "] best=" + TIER_ORDER[best] + " conf=" + confidence)
            if (confidence < AI_CONFIDENCE_FLOOR) DamageSuggestion() else DamageSuggestion(TIER_ORDER[best], confidence)
        }.getOrElse { Log.w("BeaconDamage", "inference FAILED -> abstain", it); DamageSuggestion() }
    }
}

actual fun createDamageClassifier(): DamageClassifier = AndroidDamageClassifier()
