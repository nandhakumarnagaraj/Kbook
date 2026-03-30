package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


class TextRecognitionHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    
    fun processImage(
        image: InputImage,
        onSuccess: (String, Text) -> Unit,
        onFailure: (Exception) -> Unit
    ): Task<Text> {
        return recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onSuccess(visionText.text, visionText)
            }
            .addOnFailureListener { e ->
                Log.e("OCR_HELPER", "Processing failed", e)
                onFailure(e)
            }
    }

    
    fun processUri(
        context: Context,
        uri: Uri,
        onSuccess: (String, Text) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            processImage(image, onSuccess, onFailure)
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    
    fun close() {
        recognizer.close()
    }
}
