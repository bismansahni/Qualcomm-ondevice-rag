package bisman.thesis.qualcomm.data

import android.util.Log
import io.objectbox.Box
import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

/**
 * Repository for managing documents in the ObjectBox database.
 *
 * Provides CRUD operations for document metadata and content:
 * - **Document Storage**: Stores full document text and metadata
 * - **Duplicate Prevention**: Checks file paths to avoid duplicate processing
 * - **Change Detection**: Tracks last modified timestamps and file sizes
 * - **Reactive Updates**: Exposes documents as Kotlin Flows for reactive UI
 *
 * The repository uses indexed queries on file paths for efficient lookups
 * during folder watching and duplicate detection.
 *
 * @see Document for the data model
 * @see ObjectBoxStore for the database instance
 */
@Single
class DocumentsDB {
    companion object {
        private const val TAG = "DocumentsDB"
    }

    /**
     * Lazy-initialized ObjectBox reference to the Document entity box.
     * Initialization is deferred until first access to avoid startup delays.
     */
    private val docsBox: Box<Document> by lazy {
        Log.d(TAG, "Initializing docsBox lazily")
        try {
            val box = ObjectBoxStore.store.boxFor(Document::class.java)
            Log.d(TAG, "DocsBox initialized successfully, current count: ${box.count()}")
            box
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize docsBox", e)
            throw e
        }
    }

    /**
     * Adds or updates a document in the database.
     *
     * If a document with the same file path already exists, updates it instead
     * of creating a duplicate. This prevents duplicate processing when folder
     * watching detects the same file multiple times.
     *
     * @param document The document to add or update
     * @return The document ID (newly generated or existing)
     */
    fun addDocument(document: Document): Long {
        // Check if document with same file path already exists
        val existingDoc = getDocumentByFilePath(document.docFilePath)
        if (existingDoc != null && document.docId == 0L) {
            // If we're trying to add a new document (docId=0) but one already exists,
            // update the existing one instead
            document.docId = existingDoc.docId
            Log.d(TAG, "Updating existing document instead of creating duplicate: ${document.docFileName}")
        }

        val id = docsBox.put(document)
        Log.d(TAG, "Added/Updated document: ${document.docFileName} with ID: $id")
        Log.d(TAG, "Total documents now: ${docsBox.count()}")
        return id
    }

    /**
     * Removes a document from the database by its ID.
     *
     * Note: This does not automatically remove associated chunks. Use ChunksDB.removeChunks()
     * separately to ensure complete cleanup.
     *
     * @param docId The document ID to remove
     */
    fun removeDocument(docId: Long) {
        docsBox.remove(docId)
    }

    /**
     * Returns a reactive Flow of all documents in the database.
     *
     * The Flow automatically emits updated lists whenever documents are added, removed,
     * or modified, enabling reactive UI updates in Compose.
     *
     * @return Flow emitting mutable lists of documents on IO dispatcher
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllDocuments(): Flow<MutableList<Document>> =
        docsBox
            .query(Document_.docId.notNull())
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    /**
     * Returns the total count of documents in the database.
     *
     * @return Number of documents stored
     */
    fun getDocsCount(): Long = docsBox.count()

    /**
     * Retrieves a document by its file system path.
     *
     * Uses indexed query for efficient lookup during folder watching and duplicate detection.
     *
     * @param filePath The file system path to search for
     * @return The matching document, or null if not found
     */
    fun getDocumentByFilePath(filePath: String): Document? {
        return docsBox
            .query(Document_.docFilePath.equal(filePath))
            .build()
            .findFirst()
    }

    /**
     * Removes a document by its file system path.
     *
     * Convenience method for folder watcher when file deletions are detected.
     *
     * @param filePath The file system path of the document to remove
     * @return true if a document was found and removed, false otherwise
     */
    fun removeDocumentByFilePath(filePath: String): Boolean {
        val document = getDocumentByFilePath(filePath)
        return if (document != null) {
            docsBox.remove(document.docId)
            Log.d(TAG, "Removed document: ${document.docFileName} with path: $filePath")
            true
        } else {
            false
        }
    }

    /**
     * Returns all document file paths in the database.
     *
     * Used by folder watcher to compare existing documents against current folder contents.
     * Empty paths are filtered out.
     *
     * @return List of non-empty file paths
     */
    fun getAllDocumentFilePaths(): List<String> {
        return docsBox.all.mapNotNull { doc ->
            doc.docFilePath.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Checks if a document with the specified file path exists.
     *
     * More efficient than getDocumentByFilePath when only existence check is needed.
     *
     * @param filePath The file path to check
     * @return true if a document exists with this path, false otherwise
     */
    fun documentExistsWithPath(filePath: String): Boolean {
        return docsBox
            .query(Document_.docFilePath.equal(filePath))
            .build()
            .count() > 0
    }

    /**
     * Returns all documents as a map keyed by file path.
     *
     * Used by folder watcher for efficient lookup of existing documents during sync.
     * Documents without file paths are excluded.
     *
     * @return Map of file path to Document
     */
    fun getAllDocumentsMap(): Map<String, Document> {
        return docsBox.all.associateBy { it.docFilePath }.filterKeys { it.isNotEmpty() }
    }

    /**
     * Updates document metadata without modifying content.
     *
     * Used when a file's metadata changes but content remains the same. Also updates
     * the "last processed" timestamp.
     *
     * @param docId The document ID to update
     * @param lastModified New last modified timestamp
     * @param fileSize New file size in bytes
     */
    fun updateDocumentMetadata(docId: Long, lastModified: Long, fileSize: Long) {
        val doc = docsBox.get(docId)
        doc?.let {
            it.fileLastModified = lastModified
            it.fileSize = fileSize
            it.docAddedTime = System.currentTimeMillis() // Update the "last processed" time
            docsBox.put(it)
            Log.d(TAG, "Updated metadata for document: ${it.docFileName}")
        }
    }
}