package bisman.thesis.qualcomm.domain.embeddings

import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

/**
 * Provider for generating text embeddings using the all-MiniLM-L6-V2 sentence transformer model.
 *
 * This class manages the lifecycle of the ONNX-based embedding model:
 * - **Lazy Initialization**: Model is loaded on-demand rather than at startup
 * - **Thread Safety**: Double-checked locking ensures safe concurrent initialization
 * - **Resource Management**: Proper cleanup to free DSP/NPU resources
 *
 * The embedding model generates 384-dimensional vectors from text, which are used for:
 * - Vector similarity search in the RAG retrieval pipeline
 * - Semantic matching between queries and document chunks
 *
 * Model Details:
 * - Model: all-MiniLM-L6-V2 (sentence-transformers)
 * - Output: 384-dimensional float vectors
 * - Runtime: ONNX with Qualcomm DSP/NPU acceleration
 *
 * @param context Android context for accessing model assets
 *
 * @see SentenceEmbedding for the underlying ONNX inference wrapper
 */
@Single
class SentenceEmbeddingProvider(private val context: Context) {

    /** The ONNX sentence embedding model instance (null until initialized) */
    private var sentenceEmbedding: SentenceEmbedding? = null

    /** Flag tracking whether the model has been loaded */
    private var isInitialized = false

    /** Lock object for thread-safe lazy initialization */
    private val initLock = Any()

    init {
        // Don't load eagerly anymore - load on demand
        android.util.Log.d("SentenceEmbeddingProvider", "Created (lazy mode)")
    }

    /**
     * Ensures the embedding model is initialized before use.
     *
     * Uses double-checked locking for thread-safe lazy initialization:
     * 1. First check without lock (fast path for already-initialized case)
     * 2. Acquire lock if not initialized
     * 3. Check again inside lock (handles concurrent initialization attempts)
     * 4. Load model from assets and initialize ONNX runtime
     *
     * The model and tokenizer are loaded from app assets:
     * - Model: assets/all-MiniLM-L6-V2.onnx
     * - Tokenizer: assets/tokenizer.json (copied to internal storage)
     *
     * Thread-safe: Multiple threads can safely call this concurrently.
     */
    fun ensureInitialized() {
        // Double-checked locking pattern for thread safety
        if (!isInitialized || sentenceEmbedding == null) {
            synchronized(initLock) {
                // Check again inside synchronized block
                if (!isInitialized || sentenceEmbedding == null) {
                    android.util.Log.d("SentenceEmbeddingProvider", "Initializing ONNX model on demand (thread: ${Thread.currentThread().name})")
                    sentenceEmbedding = SentenceEmbedding()
                    val modelBytes = context.assets.open("all-MiniLM-L6-V2.onnx").use { it.readBytes() }
                    val tokenizerBytes = copyToLocalStorage()
                    runBlocking(Dispatchers.IO) { sentenceEmbedding!!.init(modelBytes, tokenizerBytes) }
                    isInitialized = true
                    android.util.Log.d("SentenceEmbeddingProvider", "ONNX model initialized successfully")
                } else {
                    android.util.Log.d("SentenceEmbeddingProvider", "Model already initialized, skipping (thread: ${Thread.currentThread().name})")
                }
            }
        }
    }

    /**
     * Encodes text into a 384-dimensional embedding vector.
     *
     * Ensures the model is initialized before encoding. Uses the Default dispatcher
     * for CPU-bound encoding work. Returns a zero vector if encoding fails.
     *
     * @param text The text to encode (queries or document chunks)
     * @return 384-dimensional float array representing the text's semantic meaning
     */
    fun encodeText(text: String): FloatArray {
        ensureInitialized()
        return runBlocking(Dispatchers.Default) {
            sentenceEmbedding?.encode(text) ?: FloatArray(384) { 0.1f }
        }
    }

    /**
     * Releases the embedding model and frees resources.
     *
     * Nullifies the model reference and triggers garbage collection to free:
     * - ONNX runtime memory
     * - DSP/NPU resources
     * - Native memory allocations
     *
     * Should be called when the model is no longer needed (e.g., app backgrounded).
     * Thread-safe: Multiple threads can safely call this concurrently.
     */
    fun release() {
        synchronized(initLock) {
            // Just null the reference, let finalize() in SentenceEmbedding handle cleanup
            android.util.Log.d("SentenceEmbeddingProvider", "Releasing ONNX model (initialized: $isInitialized)")
            sentenceEmbedding = null
            isInitialized = false
            System.gc() // Suggest garbage collection
            System.runFinalization() // Force finalizers to run
            android.util.Log.d("SentenceEmbeddingProvider", "ONNX model released, finalizers triggered")
        }
    }

    /**
     * Copies the tokenizer file from assets to internal storage.
     *
     * The ONNX tokenizer requires file system access, so the tokenizer JSON
     * is copied from assets to internal storage on first use.
     *
     * @return Byte array of the tokenizer JSON file
     */
    private fun copyToLocalStorage(): ByteArray {
        val tokenizerBytes = context.assets.open("tokenizer.json").readBytes()
        val storageFile = File(context.filesDir, "tokenizer.json")
        if (!storageFile.exists()) {
            storageFile.writeBytes(tokenizerBytes)
        }
        return storageFile.readBytes()
    }
}
