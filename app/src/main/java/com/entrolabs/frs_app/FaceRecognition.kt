package com.entrolabs.frs_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object FaceRecognition {
    private val firestore = Firebase.firestore
    private lateinit var interpreter: Interpreter
    private var previousEyeOpenProb: Float = -1f
    private var lastTimeChecked: Long = System.currentTimeMillis()

    fun loadModel(context: Context) {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "facenet.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(modelBuffer, options)
            Log.d("FaceRecognition", "FaceNet model loaded successfully!")
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error loading FaceNet model: ${e.message}")
        }
    }

    fun recognizeFace(imagePath: String, context: Context) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        Log.d("FaceRecognition", "Bitmap loaded: width=${bitmap.width}, height=${bitmap.height}")
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->

                /*     for (face in faces) {
                         val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0.5f
                         val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0.5f

                         val currentTime = System.currentTimeMillis()

                         if (previousEyeOpenProb != -1f && currentTime - lastTimeChecked > 1000) {
                             val eyeChange = Math.abs(previousEyeOpenProb - leftEyeOpenProb) + Math.abs(
                                 previousEyeOpenProb - rightEyeOpenProb
                             )

                             if (eyeChange > 0.3) {
                                 Log.d("TAG", "recognizeFacereal: real")
                             } else {
                                 Log.d("TAG", "recognizeFacereal: fake ")
                             }
                             lastTimeChecked = currentTime
                         }

                         previousEyeOpenProb = (leftEyeOpenProb + rightEyeOpenProb) / 2

                     }*/

                Log.d("FaceRecognition", "Faces detected: ${faces.size}")
                if (faces.isNotEmpty()) {
                    val embedding = extractFaceEmbedding(bitmap)
                    Log.d("FaceRecognition", "Extracted Embedding: ${embedding.joinToString()}")
                    matchFace(embedding, context)
                } else {
                    Toast.makeText(context, "No face detected", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Log.e("FaceDetection", "Face detection failed: ${it.message}")
            }
    }

    fun extractFaceEmbedding(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        val inputBuffer = convertBitmapToBuffer(resizedBitmap)

        val outputBuffer =
            TensorBuffer.createFixedSize(intArrayOf(1, 512), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(inputBuffer, outputBuffer.buffer)

        return outputBuffer.floatArray
    }

    private fun convertBitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(160 * 160)
        bitmap.getPixels(intValues, 0, 160, 0, 0, 160, 160)

        for (pixelValue in intValues) {
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 128.0f)
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 128.0f)
            byteBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 128.0f)
        }
        return byteBuffer
    }

    private fun matchFace(embedding: FloatArray, context: Context) {
        firestore.collection("faces").get()
            .addOnSuccessListener { result ->
                var bestMatchName = "Unknown"
                var bestScore = 0.0

                for (document in result) {
                    val storedEmbedding = document.get("embedding") as? List<Float> ?: continue
                    val similarity = cosineSimilarity(embedding, storedEmbedding.toFloatArray())
                    Log.d(
                        "FaceRecognition",
                        "Comparing with ${document.getString("name")}: Similarity = $similarity"
                    )

                    if (similarity > bestScore) {
                        bestScore = similarity.toDouble()
                        bestMatchName = document.getString("name") ?: "Unknown"
                    }
                }

                if (bestScore > 0.6) {
                    Toast.makeText(context, "Welcome, $bestMatchName!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Face not recognized!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Log.e("FaceRecognition", "Error fetching face data: ${it.message}")
            }
    }

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)

        return if (norm1 > 0.0f && norm2 > 0.0f) dotProduct / (norm1 * norm2) else 0.0f
    }
}
