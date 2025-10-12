package bisman.thesis.qualcomm

import android.app.Application
import android.util.Log
import bisman.thesis.qualcomm.data.ObjectBoxStore
import bisman.thesis.qualcomm.di.appModule
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Main application class for the Qualcomm Thesis Chat Application.
 *
 * This class extends [Application] and implements [KoinComponent] to manage the application lifecycle
 * and dependency injection framework. It handles the initialization of:
 * - ObjectBox database for local data persistence
 * - Koin dependency injection framework
 * - ML model cleanup and resource management
 *
 * The application integrates Qualcomm's on-device AI capabilities with a RAG (Retrieval Augmented Generation)
 * system for document-based chat interactions.
 *
 * @see ObjectBoxStore for database initialization
 * @see appModule for dependency injection configuration
 */
class ChatApplication : Application(), KoinComponent {
    companion object {
        /** Tag used for logging application lifecycle events */
        private const val TAG = "ChatApplication"
    }

    /**
     * Called when the application is starting, before any activity, service, or receiver objects
     * have been created.
     *
     * This method performs the following initialization steps:
     * 1. Initializes ObjectBox database for storing documents and embeddings
     * 2. Starts Koin dependency injection framework (if not already started)
     *
     * @throws Exception if ObjectBox initialization fails
     */
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "ChatApplication onCreate called")
        
        // Initialize ObjectBox
        try {
            Log.d(TAG, "Initializing ObjectBox...")
            ObjectBoxStore.init(this)
            Log.d(TAG, "ObjectBox initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectBox", e)
            throw e
        }
        
        // Initialize Koin only if not already started
        if (GlobalContext.getOrNull() == null) {
            Log.d(TAG, "Starting Koin...")
            startKoin {
                androidLogger()
                androidContext(this@ChatApplication)
                modules(appModule)
            }
            Log.d(TAG, "Koin started successfully")
        } else {
            Log.d(TAG, "Koin already initialized, skipping...")
        }
        
        Log.d(TAG, "ChatApplication initialization complete")
    }

    /**
     * Releases all ML models and performs garbage collection.
     *
     * This method is responsible for cleaning up machine learning resources to free memory:
     * - Releases the sentence embedding model used for vector search
     * - Triggers garbage collection to reclaim memory
     * - Forces finalizers to run for complete cleanup
     *
     * This method is called when the app is stopping or terminating to ensure proper
     * resource cleanup and prevent memory leaks from native ML models.
     *
     * @see SentenceEmbeddingProvider.release for model-specific cleanup
     */
    fun releaseModels() {
        try {
            Log.d(TAG, "Releasing models from ChatApplication")
            // Try to get and release sentence encoder
            val sentenceEncoder = getKoin().getOrNull<SentenceEmbeddingProvider>()
            sentenceEncoder?.release()
            System.gc()
            System.runFinalization() // Force finalizers to run
            Log.d(TAG, "Models released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing models", e)
        }
    }

    /**
     * Called when the application is terminating.
     *
     * Note: This callback is never called in production Android systems. It's included
     * for cleanup purposes during testing and emulator scenarios.
     *
     * Ensures all ML models are released before the application terminates.
     */
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "ChatApplication onTerminate called")
        releaseModels()
    }
}