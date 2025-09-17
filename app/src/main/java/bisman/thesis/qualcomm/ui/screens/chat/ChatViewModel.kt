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
import kotlinx.coroutines.withContext
import android.util.Log

class ChatViewModel(
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _questionState = MutableStateFlow("")
    val questionState: StateFlow<String> = _questionState

    private val _responseState = MutableStateFlow("")
    val responseState: StateFlow<String> = _responseState

    private val _isGeneratingResponseState = MutableStateFlow(false)
    val isGeneratingResponseState: StateFlow<Boolean> = _isGeneratingResponseState

    private val _retrievedContextListState = MutableStateFlow(emptyList<RetrievedContext>())
    val retrievedContextListState: StateFlow<List<RetrievedContext>> = _retrievedContextListState

    private var genieWrapper: GenieWrapper? = null

    init {
        // Eager initialization like ChatApp's onCreate
        try {
            val modelDir = MainComposeActivity.modelDirectory
            val htpConfigPath = MainComposeActivity.htpConfigPath

            if (modelDir == null || htpConfigPath == null) {
                Log.e(TAG, "Error getting additional info from bundle.")
                _responseState.value = "Unexpected error observed. Exiting app."
            } else {
                // Load Model - exactly like ChatApp does in onCreate
                val constructor = GenieWrapper::class.java.getDeclaredConstructor(String::class.java, String::class.java)
                constructor.isAccessible = true
                genieWrapper = constructor.newInstance(modelDir, htpConfigPath)
                Log.i(TAG, "llm Loaded.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during conversation with Chatbot: ${e.toString()}")
            _responseState.value = "Unexpected error observed. Exiting app."
        }
    }

    fun getAnswer(query: String) {
        Log.d(TAG, "===== getAnswer START =====")
        Log.d(TAG, "Query: $query")
        
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""
        _retrievedContextListState.value = emptyList()
        
        try {
            var jointContext = ""
            val retrievedContextList = ArrayList<RetrievedContext>()
            
            // Check if we have documents to search
            if (documentsDB.getDocsCount() > 0) {
                val queryEmbedding = sentenceEncoder.encodeText(query)
                
                Log.d(TAG, "Getting similar chunks from database...")
                chunksDB.getSimilarChunks(queryEmbedding, n = 3).forEach {
                    jointContext += " " + it.second.chunkData
                    retrievedContextList.add(RetrievedContext(it.second.docFileName, it.second.chunkData))
                    Log.d(TAG, "Retrieved chunk from: ${it.second.docFileName}, size: ${it.second.chunkData.length}")
                }
                _retrievedContextListState.value = retrievedContextList
            }
            
            Log.d(TAG, "Total context length: ${jointContext.length}")
            
            val promptText = if (jointContext.isNotEmpty()) {
                "Context: $jointContext\n\nQuery: $query"
            } else {
                query
            }

            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Launching coroutine for model inference...")
                if (genieWrapper != null) {
                    Log.d(TAG, "GenieWrapper is ready, sending prompt...")
                    Log.d(TAG, "Prompt text length: ${promptText.length}")
                    
                    genieWrapper!!.getResponseForPrompt(
                        promptText,
                        object : StringCallback {
                            override fun onNewString(str: String?) {
                                str?.let { token ->
                                    Log.d(TAG, "Received token: '${token}' (${token.length} chars)")
                                    // Update on Main thread for immediate UI update
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val currentResponse = _responseState.value
                                        val newResponse = currentResponse + token
                                        Log.d(TAG, "Updating response from ${currentResponse.length} to ${newResponse.length} chars")
                                        _responseState.value = newResponse
                                        Log.d(TAG, "Response state updated, current text: '${newResponse.takeLast(50)}'")
                                    }
                                }
                            }
                        }
                    )
                    Log.d(TAG, "Response generation completed")
                    _isGeneratingResponseState.value = false
                } else {
                    Log.e(TAG, "GenieWrapper is null")
                    _responseState.value = "Error: Model not initialized. Please restart the app."
                    _isGeneratingResponseState.value = false
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

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    // Removed onCleared - let Java's GC handle everything like ChatApp does
}