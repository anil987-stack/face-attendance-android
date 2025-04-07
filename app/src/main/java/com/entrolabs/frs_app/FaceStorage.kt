import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.entrolabs.frs_app.FaceRecognition
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

object FaceStorage {
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    fun saveFace(imagePath: String, context: Context) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val embedding = FaceRecognition.extractFaceEmbedding(bitmap)

        val userName = "User_${System.currentTimeMillis()}"
        val fileName = "$userName.jpg"
        val storageRef = storage.reference.child("faces/$fileName")

        storageRef.putFile(Uri.fromFile(File(imagePath)))
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    firestore.collection("faces")
                        .document(userName)
                        .set(mapOf("name" to userName, "imageUrl" to uri.toString(), "embedding" to embedding.toList()))
                }
            }
    }
}


