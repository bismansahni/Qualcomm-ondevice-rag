package bisman.thesis.qualcomm.data

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore

/**
 * Singleton object managing the ObjectBox database instance.
 *
 * ObjectBox is a high-performance embedded database that provides:
 * - Fast object persistence with minimal boilerplate
 * - HNSW vector indexing for efficient similarity search
 * - Reactive queries via Kotlin Flows
 * - ACID transactions
 *
 * This singleton ensures only one BoxStore instance exists throughout the app lifecycle,
 * which is required for proper ObjectBox operation. The store must be initialized in
 * Application.onCreate() before any database access occurs.
 *
 * The MyObjectBox class is generated at compile time by ObjectBox's annotation processor
 * based on @Entity annotated classes (Chunk, Document).
 *
 * @see Chunk for the chunk entity with vector embeddings
 * @see Document for the document entity
 */
object ObjectBoxStore {
    private const val TAG = "ObjectBoxStore"

    /**
     * The BoxStore instance providing access to all entity boxes.
     * Must be initialized via init() before use.
     */
    lateinit var store: BoxStore
        private set

    /**
     * Initializes the ObjectBox database.
     *
     * This method must be called once during app startup (typically in Application.onCreate())
     * before any database operations. Subsequent calls are safely ignored.
     *
     * The initialization:
     * 1. Checks if store is already initialized to prevent duplicate initialization
     * 2. Builds the BoxStore using the Android context
     * 3. Creates/opens the database file
     * 4. Sets up all entity boxes and indexes
     *
     * @param context Android application context (not activity context)
     * @throws Exception if initialization fails (e.g., database corruption, insufficient space)
     */
    fun init(context: Context) {
        Log.d(TAG, "Initializing ObjectBox store...")
        try {
            if (::store.isInitialized) {
                Log.w(TAG, "Store already initialized, skipping...")
                return
            }

            store = MyObjectBox.builder().androidContext(context).build()
            Log.d(TAG, "ObjectBox store initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectBox store", e)
            throw e
        }
    }
}
