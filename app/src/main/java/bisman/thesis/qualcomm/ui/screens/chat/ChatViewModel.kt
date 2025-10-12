package bisman.thesis.qualcomm.ui.screens.chat

import androidx.lifecycle.ViewModel
import bisman.thesis.qualcomm.MainComposeActivity
import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.data.RetrievedContext
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.GenieWrapper
import bisman.thesis.qualcomm.StringCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * ViewModel for the Chat screen implementing RAG (Retrieval Augmented Generation) functionality.
 *
 * This ViewModel coordinates between multiple components to provide document-aware chat:
 * - **RAG Pipeline**: Retrieves relevant document chunks based on query embeddings
 * - **LLM Integration**: Manages Qualcomm Genie LLM for on-device inference
 * - **State Management**: Exposes UI state via StateFlows for Compose integration
 * - **Performance Tracking**: Logs detailed metrics for RAG retrieval and inference
 *
 * Architecture:
 * 1. User submits query
 * 2. Query is embedded using sentence transformer
 * 3. Top-K similar chunks are retrieved from vector database
 * 4. Retrieved context is concatenated with query
 * 5. Enhanced prompt is sent to LLM for streaming response
 *
 * Performance Metrics Tracked:
 * - RAG retrieval time
 * - Time to First Token (TTFT)
 * - Total inference time
 * - Tokens per second
 *
 * @param documentsDB Repository for accessing stored documents
 * @param chunksDB Repository for vector similarity search on document chunks
 * @param sentenceEncoder Provider for generating text embeddings
 *
 * @see GenieWrapper for LLM inference
 * @see SentenceEmbeddingProvider for embedding generation
 */
class ChatViewModel(
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {

    /** Helper extension to format doubles with specified decimal places */
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    /** Mutable state for the current user question/query */
    private val _questionState = MutableStateFlow("")
    /** Exposed immutable state for the current user question */
    val questionState: StateFlow<String> = _questionState

    /** Mutable state for the LLM's streaming response */
    private val _responseState = MutableStateFlow("")
    /** Exposed immutable state for the LLM's response (updated token by token) */
    val responseState: StateFlow<String> = _responseState

    /** Mutable state tracking whether response generation is in progress */
    private val _isGeneratingResponseState = MutableStateFlow(false)
    /** Exposed immutable state for response generation status */
    val isGeneratingResponseState: StateFlow<Boolean> = _isGeneratingResponseState

    /** Mutable state for the list of retrieved document chunks used in RAG */
    private val _retrievedContextListState = MutableStateFlow(emptyList<RetrievedContext>())
    /** Exposed immutable state for retrieved context (for UI display) */
    val retrievedContextListState: StateFlow<List<RetrievedContext>> = _retrievedContextListState

    /** Wrapper instance for Qualcomm Genie LLM inference */
    private var genieWrapper: GenieWrapper? = null

    /** Mutex to ensure thread-safe access to GenieWrapper (prevents concurrent inference calls) */
    private val genieMutex = Mutex()

    /**
     * Initializes the ViewModel by loading the LLM model.
     *
     * Initialization sequence:
     * 1. Cleans up any residual embedding model state
     * 2. Loads Qualcomm Genie LLM from model directory
     * 3. Re-initializes embeddings after LLM load
     *
     * This eager initialization ensures the model is ready when the user first interacts
     * with the chat screen, avoiding delays on first query.
     */
    init {
        // Try to clean any residual state first
        try {
            Log.d(TAG, "Pre-init cleanup: releasing any existing embeddings")
            sentenceEncoder.release()
            System.gc()
            Thread.sleep(100) // Small delay for cleanup
        } catch (e: Exception) {
            Log.w(TAG, "Pre-init cleanup error (can be ignored): ${e.message}")
        }

        // Eager initialization like ChatApp's onCreate
        try {
            val modelDir = MainComposeActivity.modelDirectory
            val htpConfigPath = MainComposeActivity.htpConfigPath

            if (modelDir == null || htpConfigPath == null) {
                Log.e(TAG, "Error getting additional info from bundle.")
                _responseState.value = "Model paths not initialized. Please restart app."
            } else {
                // Load Model - exactly like ChatApp does in onCreate
                val constructor = GenieWrapper::class.java.getDeclaredConstructor(String::class.java, String::class.java)
                constructor.isAccessible = true
                genieWrapper = constructor.newInstance(modelDir, htpConfigPath)
                Log.i(TAG, "llm Loaded.")

                // Re-initialize embeddings after Genie is loaded
                // This ensures both models are ready
                Log.d(TAG, "Re-initializing embeddings after Genie load")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization: ${e.toString()}")
            // Don't crash, just set error state
            _responseState.value = "Error initializing. Please restart app."
        }
    }

    /**
     * Processes a user query using RAG and generates a response via LLM.
     *
     * This is the main entry point for the chat functionality. It orchestrates:
     * 1. **RAG Retrieval**: Encodes query and retrieves top-3 similar chunks from vector DB
     * 2. **Context Building**: Concatenates retrieved chunks into context string
     * 3. **Prompt Construction**: Combines context and query into LLM prompt
     * 4. **Streaming Inference**: Sends prompt to Genie LLM and streams tokens back
     * 5. **Performance Logging**: Tracks and logs detailed timing metrics
     *
     * The method updates StateFlows throughout the process to keep the UI in sync.
     * All heavy operations run on IO dispatcher to avoid blocking the main thread.
     *
     * Performance metrics logged:
     * - RAG retrieval time (embedding generation + vector search)
     * - Time to First Token (TTFT) - latency before first token appears
     * - Total inference time
     * - Tokens per second throughput
     * - End-to-end total time
     *
     * @param query The user's question or prompt
     */
    fun getAnswer(query: String) {
        Log.d(TAG, "===== getAnswer START =====")
        Log.d(TAG, "Query: $query")

        val startTime = System.currentTimeMillis()
        var ragTime = 0L
        var ttftTime = 0L
        var firstTokenReceived = false
        var tokenCount = 0

        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""
        _retrievedContextListState.value = emptyList()

        try {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Launching coroutine for RAG and inference...")

                val retrievedContextList = ArrayList<RetrievedContext>()
                var jointContext = ""

                // Check if we have documents to search
                if (documentsDB.getDocsCount() > 0) {
                    val ragStartTime = System.currentTimeMillis()
                    val queryEmbedding = sentenceEncoder.encodeText(query)

                    Log.d(TAG, "Getting similar chunks from database...")
                    val similarChunks = chunksDB.getSimilarChunks(queryEmbedding, n = 3)

                    // Build context and retrieved list
                    similarChunks.forEach {
                        retrievedContextList.add(RetrievedContext(it.second.docFileName, it.second.chunkData))
                        Log.d(TAG, "Retrieved chunk from: ${it.second.docFileName}, size: ${it.second.chunkData.length}")
                    }

                    // Efficiently concatenate all chunks
                    jointContext = similarChunks.joinToString(" ") { it.second.chunkData }

                    _retrievedContextListState.value = retrievedContextList
                    ragTime = System.currentTimeMillis() - ragStartTime
                    Log.i(TAG, "⏱️ RAG Retrieval Time: ${ragTime}ms")
                }

                Log.d(TAG, "Total context length: ${jointContext.length}")

                val promptText = if (jointContext.isNotEmpty()) {
                    "Context: $jointContext\n\nQuery: $query"
                } else {
                    query
                }

                // Synchronize access to GenieWrapper to prevent concurrent calls (coroutine-friendly)
                genieMutex.withLock {
                    if (genieWrapper != null) {
                        Log.d(TAG, "GenieWrapper is ready, sending prompt... (thread: ${Thread.currentThread().name})")
                        Log.d(TAG, "Prompt text length: ${promptText.length}")

                        val inferenceStartTime = System.currentTimeMillis()

                        genieWrapper!!.getResponseForPrompt(
                            promptText,
                            object : StringCallback {
                                override fun onNewString(str: String?) {
                                    str?.let { token ->
                                        tokenCount++

                                        // Track Time to First Token
                                        if (!firstTokenReceived) {
                                            firstTokenReceived = true
                                            ttftTime = System.currentTimeMillis() - inferenceStartTime
                                            Log.i(TAG, "🚀 Time to First Token (TTFT): ${ttftTime}ms")
                                        }

                                        Log.d(TAG, "Token #$tokenCount: '${token}' (${token.length} chars)")

                                        // Update StateFlow directly (thread-safe)
                                        val currentResponse = _responseState.value
                                        val newResponse = currentResponse + token
                                        _responseState.value = newResponse
                                    }
                                }
                            }
                        )

                        val totalInferenceTime = System.currentTimeMillis() - inferenceStartTime
                        val totalTime = System.currentTimeMillis() - startTime

                        Log.i(TAG, "═══════════════════════════════════════")
                        Log.i(TAG, "📊 PERFORMANCE METRICS:")
                        Log.i(TAG, "⏱️ RAG Retrieval: ${ragTime}ms")
                        Log.i(TAG, "🚀 Time to First Token: ${ttftTime}ms")
                        Log.i(TAG, "⚡ Total Inference: ${totalInferenceTime}ms")
                        Log.i(TAG, "📝 Total Tokens: $tokenCount")
                        Log.i(TAG, "💨 Tokens/Second: ${if (totalInferenceTime > 0) (tokenCount * 1000.0 / totalInferenceTime).format(2) else "N/A"}")
                        Log.i(TAG, "⏰ Total Time: ${totalTime}ms")
                        Log.i(TAG, "═══════════════════════════════════════")

                        _isGeneratingResponseState.value = false
                    } else {
                        Log.e(TAG, "GenieWrapper is null")
                        _responseState.value = "Error: Model not initialized. Please restart the app."
                        _isGeneratingResponseState.value = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAnswer", e)
            _isGeneratingResponseState.value = false
            _questionState.value = ""
            _responseState.value = "Error: ${e.message}"
        }
        Log.d(TAG, "===== getAnswer END =====")
    }

    /**
     * Checks if any documents have been added to the database.
     *
     * @return true if at least one document exists, false otherwise
     */
    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    /**
     * Called when the ViewModel is about to be destroyed.
     *
     * Releases all ML resources to free memory and DSP:
     * - Releases sentence embedding model
     * - Nullifies GenieWrapper to allow garbage collection
     *
     * This ensures clean resource cleanup and prevents memory leaks from native models.
     */
    override fun onCleared() {
        super.onCleared()
        // Release ONNX model to free DSP for next app launch
        Log.d(TAG, "ChatViewModel onCleared - releasing embeddings")
        sentenceEncoder.release()
        // Let GenieWrapper cleanup naturally through finalize()
        genieWrapper = null
    }
}