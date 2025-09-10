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

    fun initializeGenie() {
        Log.d(TAG, "===== initializeGenie START =====")
        if (genieWrapper == null) {
            try {
                // Ensure native library is loaded
                try {
                    System.loadLibrary("chatapp")
                    Log.d(TAG, "Native library loaded successfully before creating GenieWrapper")
                } catch (e: UnsatisfiedLinkError) {
                    // Library might already be loaded, which is fine
                    Log.d(TAG, "Native library already loaded or error: ${e.message}")
                }
                
                val modelDir = MainComposeActivity.modelDirectory
                val htpConfigPath = MainComposeActivity.htpConfigPath
                
                Log.d(TAG, "Model directory from MainActivity: $modelDir")
                Log.d(TAG, "HTP config path from MainActivity: $htpConfigPath")
                
                if (modelDir == null || htpConfigPath == null) {
                    Log.e(TAG, "Model files not yet copied - modelDir: $modelDir, htpConfigPath: $htpConfigPath")
                    _responseState.value = "Error: Model files not yet copied. Please wait and try again."
                    return
                }
                
                // Verify files exist before attempting to create GenieWrapper
                val modelDirFile = java.io.File(modelDir)
                val htpConfigFile = java.io.File(htpConfigPath)
                
                if (!modelDirFile.exists()) {
                    Log.e(TAG, "Model directory does not exist: $modelDir")
                    _responseState.value = "Error: Model directory not found. Please restart the app."
                    return
                }
                
                if (!htpConfigFile.exists()) {
                    Log.e(TAG, "HTP config file does not exist: $htpConfigPath")
                    _responseState.value = "Error: HTP config not found. Please restart the app."
                    return
                }
                
                Log.d(TAG, "Creating GenieWrapper with:")
                Log.d(TAG, "  modelDir: $modelDir")
                Log.d(TAG, "  htpConfigPath: $htpConfigPath")
                
                // Use reflection to create GenieWrapper since constructor is package-private
                val constructor = GenieWrapper::class.java.getDeclaredConstructor(String::class.java, String::class.java)
                constructor.isAccessible = true
                
                // Log memory before loading model
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                Log.d(TAG, "Memory before loading model: ${usedMemory}MB used of ${maxMemory}MB max")
                
                Log.d(TAG, "About to create GenieWrapper instance...")
                genieWrapper = constructor.newInstance(modelDir, htpConfigPath)
                Log.d(TAG, "GenieWrapper constructor returned")
                
                val usedMemoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.d(TAG, "Memory after loading model: ${usedMemoryAfter}MB used (delta: ${usedMemoryAfter - usedMemory}MB)")
                
                if (genieWrapper != null) {
                    Log.d(TAG, "GenieWrapper created successfully, instance: $genieWrapper")
                } else {
                    Log.e(TAG, "GenieWrapper is null after creation")
                    _responseState.value = "Error: Failed to initialize model"
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                Log.e(TAG, "InvocationTargetException during model initialization", e)
                Log.e(TAG, "Cause: ${e.cause?.message}", e.cause)
                _responseState.value = "Error initializing model: ${e.cause?.message ?: e.message}"
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not loaded", e)
                _responseState.value = "Error: Native library not loaded. Please restart the app."
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing model", e)
                _responseState.value = "Error initializing model: ${e.message}"
            }
        } else {
            Log.d(TAG, "GenieWrapper already initialized")
        }
        Log.d(TAG, "===== initializeGenie END =====")
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
                initializeGenie()
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
                    Log.e(TAG, "GenieWrapper is null after initialization")
                    _responseState.value = "Error: Model not initialized"
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

    override fun onCleared() {
        super.onCleared()
        genieWrapper = null
    }
}