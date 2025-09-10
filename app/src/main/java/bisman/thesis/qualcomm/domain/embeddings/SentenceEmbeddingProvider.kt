package bisman.thesis.qualcomm.domain.embeddings

import android.content.Context
import android.util.Log
import bisman.thesis.qualcomm.domain.embeddings.tokenization.FullTokenizer
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SentenceEmbeddingProvider(context: Context) {

    companion object {
        private const val TAG = "SentenceEmbedding"
        init {
            Log.d(TAG, "SentenceEmbeddingProvider class loaded")
        }
        
        private fun Double.format(digits: Int) = "%.${digits}f".format(this)
    }
    
    // Simple performance tracking
    private var totalEmbeddingTime = 0L
    private var totalEmbeddingCount = 0

    private val context: Context = context.also {
        Log.d(TAG, "Context received: $it")
    }
    
    private lateinit var tfliteInterpreter: Interpreter
    private lateinit var tokenizer: FullTokenizer
    private lateinit var vocabulary: Map<String, Int>
    private val embeddingSize = 384 // all-MiniLM-L6-v2 output dimension
    private val maxSeqLength = 128 // Maximum sequence length for MiniLM
    
    // Special tokens
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    init {
        System.out.println("=== SentenceEmbeddingProvider INIT STARTING ===")
        android.util.Log.e("INIT_TEST", "SentenceEmbeddingProvider constructor called!")
        Log.d(TAG, "SentenceEmbeddingProvider init block starting...")
        android.util.Log.e("EMBEDDING_DEBUG", "Init block called")
        try {
            android.util.Log.e("INIT_TEST", "About to load model...")
            Log.d(TAG, "Starting initialization...")
            loadModel()
            android.util.Log.e("INIT_TEST", "Model loaded successfully")
            Log.d(TAG, "Model loaded, loading vocabulary...")
            loadVocabulary()
            android.util.Log.e("INIT_TEST", "Vocabulary loaded successfully")
            Log.d(TAG, "Vocabulary loaded, creating tokenizer...")
            tokenizer = FullTokenizer(vocabulary, true) // doLowerCase = true for MiniLM
            android.util.Log.e("INIT_TEST", "Tokenizer created successfully")
            Log.d(TAG, "SentenceEmbeddingProvider initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("INIT_TEST", "FAILED TO INITIALIZE: ${e.message}")
            Log.e(TAG, "Failed to initialize SentenceEmbeddingProvider: ${e.message}", e)
            Log.e(TAG, "Stack trace:", e)
            e.printStackTrace()
            // Don't crash the app, just log the error
        }
    }

    private fun loadModel() {
        try {
            Log.d(TAG, "Loading TFLite model from models/minilm_fp16.tflite...")
            val modelBuffer = context.assets.open("models/minilm_fp16.tflite").use { inputStream ->
                val bytes = inputStream.readBytes()
                Log.d(TAG, "Model size: ${bytes.size} bytes")
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                buffer.put(bytes)
                buffer.rewind()
                buffer
            }
            
            tfliteInterpreter = Interpreter(modelBuffer)
            Log.d(TAG, "TFLite model loaded successfully")
            
            // Log model input/output details
            Log.d(TAG, "Model has ${tfliteInterpreter.inputTensorCount} inputs and ${tfliteInterpreter.outputTensorCount} outputs")
            for (i in 0 until tfliteInterpreter.inputTensorCount) {
                val tensor = tfliteInterpreter.getInputTensor(i)
                Log.d(TAG, "Input $i: shape=${tensor.shape().contentToString()}, dtype=${tensor.dataType()}")
            }
            for (i in 0 until tfliteInterpreter.outputTensorCount) {
                val tensor = tfliteInterpreter.getOutputTensor(i)
                Log.d(TAG, "Output $i: shape=${tensor.shape().contentToString()}, dtype=${tensor.dataType()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
            throw e
        }
    }

    private fun loadVocabulary() {
        try {
            Log.d(TAG, "Loading vocabulary from tokenizer.json...")
            val vocabMap = mutableMapOf<String, Int>()
            
            // Parse the tokenizer.json file to extract vocabulary
            context.assets.open("tokenizer.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                Log.d(TAG, "Tokenizer JSON size: ${jsonString.length} characters")
                val json = JSONObject(jsonString)
                
                // Extract vocabulary from the model.vocab field
                val model = json.getJSONObject("model")
                val vocab = model.getJSONObject("vocab")
                
                val keys = vocab.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    vocabMap[key] = vocab.getInt(key)
                }
            }
            
            vocabulary = vocabMap
            Log.d(TAG, "Vocabulary loaded successfully with ${vocabulary.size} tokens")
            Log.d(TAG, "Sample tokens: [CLS]=${vocabulary["[CLS]"]}, [SEP]=${vocabulary["[SEP]"]}, [PAD]=${vocabulary["[PAD]"]}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocabulary", e)
            throw e
        }
    }

    fun resetStats() {
        totalEmbeddingTime = 0L
        totalEmbeddingCount = 0
    }
    
    fun printAverageStats() {
        if (totalEmbeddingCount > 0) {
            val avgTime = totalEmbeddingTime.toDouble() / totalEmbeddingCount
            val throughput = 1000.0 / avgTime
            Log.d(TAG, "========== EMBEDDING PERFORMANCE SUMMARY ==========")
            Log.d(TAG, "📊 Total embeddings processed: $totalEmbeddingCount")
            Log.d(TAG, "📊 Average time per embedding: ${avgTime.format(2)}ms")
            Log.d(TAG, "📊 Average throughput: ${throughput.format(2)} embeddings/sec")
            Log.d(TAG, "===================================================")
        }
    }
    
    fun encodeText(text: String, suppressLogs: Boolean = false): FloatArray {
        try {
            val startTime = System.currentTimeMillis()
            
            // Tokenize the text using the proper BERT tokenizer
            val tokens = mutableListOf<String>()
            tokens.add(clsToken)
            val tokenizedText = tokenizer.tokenize(text)
            tokens.addAll(tokenizedText)
            tokens.add(sepToken)
            
            // Truncate if too long (keeping [CLS] and [SEP])
            if (tokens.size > maxSeqLength) {
                val truncated = mutableListOf<String>()
                truncated.addAll(tokens.take(maxSeqLength - 1))
                truncated.add(sepToken)
                tokens.clear()
                tokens.addAll(truncated)
            }
            
            // Convert tokens to IDs
            val inputIds = tokenizer.convertTokensToIds(tokens)
            
            // Create attention mask (1 for real tokens, 0 for padding)
            val attentionMask = IntArray(maxSeqLength) { i ->
                if (i < inputIds.size) 1 else 0
            }
            
            // Pad input IDs to max length
            val paddedInputIds = IntArray(maxSeqLength) { i ->
                if (i < inputIds.size) inputIds[i] else vocabulary[padToken] ?: 0
            }
            
            // Prepare input tensors using ByteBuffer for INT32 support
            val inputIdsBuffer = ByteBuffer.allocateDirect(maxSeqLength * 4).order(ByteOrder.nativeOrder())
            paddedInputIds.forEach { inputIdsBuffer.putInt(it) }
            inputIdsBuffer.rewind()
            
            val attentionMaskBuffer = ByteBuffer.allocateDirect(maxSeqLength * 4).order(ByteOrder.nativeOrder())
            attentionMask.forEach { attentionMaskBuffer.putInt(it) }
            attentionMaskBuffer.rewind()
            
            // Prepare output tensor
            val outputSize = maxSeqLength * embeddingSize * 4
            val outputBuffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
            
            // Run inference
            val inputs = arrayOf(inputIdsBuffer, attentionMaskBuffer)
            val outputs = mapOf(0 to outputBuffer)
            
            tfliteInterpreter.runForMultipleInputsOutputs(inputs, outputs)
            
            // Extract [CLS] token embedding
            outputBuffer.rewind()
            val allEmbeddings = FloatArray(maxSeqLength * embeddingSize)
            outputBuffer.asFloatBuffer().get(allEmbeddings)
            val embedding = allEmbeddings.sliceArray(0 until embeddingSize)
            
            val totalTime = System.currentTimeMillis() - startTime
            
            // Track stats for average calculation
            totalEmbeddingTime += totalTime
            totalEmbeddingCount++
            
            // Only log if not suppressed (for single embeddings like chat queries)
            if (!suppressLogs) {
                Log.d(TAG, "Embedding generated in ${totalTime}ms (${(1000.0/totalTime).format(2)} embeddings/sec)")
            }
            
            return embedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode text", e)
            throw e
        }
    }

    fun close() {
        if (::tfliteInterpreter.isInitialized) {
            tfliteInterpreter.close()
        }
    }
}