package bisman.thesis.qualcomm.domain.embeddings


import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class SentenceEmbeddingProvider(private val context: Context) {

    private var sentenceEmbedding: SentenceEmbedding? = null
    private var isInitialized = false

    init {
        // Don't load eagerly anymore - load on demand
        android.util.Log.d("SentenceEmbeddingProvider", "Created (lazy mode)")
    }

    fun ensureInitialized() {
        if (!isInitialized || sentenceEmbedding == null) {
            android.util.Log.d("SentenceEmbeddingProvider", "Initializing ONNX model on demand")
            sentenceEmbedding = SentenceEmbedding()
            val modelBytes = context.assets.open("all-MiniLM-L6-V2.onnx").use { it.readBytes() }
            val tokenizerBytes = copyToLocalStorage()
            runBlocking(Dispatchers.IO) { sentenceEmbedding!!.init(modelBytes, tokenizerBytes) }
            isInitialized = true
            android.util.Log.d("SentenceEmbeddingProvider", "ONNX model initialized successfully")
        }
    }

    fun encodeText(text: String): FloatArray {
        ensureInitialized()
        return runBlocking(Dispatchers.Default) {
            sentenceEmbedding?.encode(text) ?: FloatArray(384) { 0.1f }
        }
    }

    fun release() {
        // Just null the reference, let finalize() in SentenceEmbedding handle cleanup
        android.util.Log.d("SentenceEmbeddingProvider", "Releasing ONNX model (initialized: $isInitialized)")
        sentenceEmbedding = null
        isInitialized = false
        System.gc() // Suggest garbage collection
        System.runFinalization() // Force finalizers to run
        android.util.Log.d("SentenceEmbeddingProvider", "ONNX model released, finalizers triggered")
    }

    private fun copyToLocalStorage(): ByteArray {
        val tokenizerBytes = context.assets.open("tokenizer.json").readBytes()
        val storageFile = File(context.filesDir, "tokenizer.json")
        if (!storageFile.exists()) {
            storageFile.writeBytes(tokenizerBytes)
        }
        return storageFile.readBytes()
    }
}
