package bisman.thesis.qualcomm.domain.embeddings


import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class SentenceEmbeddingProvider(private val context: Context) {

    private var sentenceEmbedding: SentenceEmbedding? = SentenceEmbedding()

    init {
        val modelBytes = context.assets.open("all-MiniLM-L6-V2.onnx").use { it.readBytes() }
        val tokenizerBytes = copyToLocalStorage()
        runBlocking(Dispatchers.IO) { sentenceEmbedding?.init(modelBytes, tokenizerBytes) }
        android.util.Log.d("SentenceEmbeddingProvider", "ONNX model initialized")
    }

    fun encodeText(text: String): FloatArray =
        runBlocking(Dispatchers.Default) {
            return@runBlocking sentenceEmbedding?.encode(text) ?: FloatArray(384) { 0.1f }
        }

    fun release() {
        // Just null the reference, let finalize() in SentenceEmbedding handle cleanup
        android.util.Log.d("SentenceEmbeddingProvider", "Releasing ONNX model")
        sentenceEmbedding = null
        System.gc() // Suggest garbage collection
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
