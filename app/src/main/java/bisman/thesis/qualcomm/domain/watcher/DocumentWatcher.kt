package bisman.thesis.qualcomm.domain.watcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.Document
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.domain.readers.Readers
import bisman.thesis.qualcomm.ui.screens.docs.DocsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Monitors a folder for document changes and automatically processes new/modified files.
 *
 * This class implements automatic document ingestion for the RAG system:
 * - **File Monitoring**: Uses Android FileObserver to detect file system events
 * - **Auto-Processing**: Automatically reads, chunks, and embeds new documents
 * - **Change Detection**: Detects modified files and re-processes them
 * - **Deletion Handling**: Removes documents when source files are deleted
 * - **Folder Sync**: Synchronizes database with folder contents on startup
 *
 * Supported file types: PDF, DOCX, DOC
 *
 * The watcher uses a lifecycle-aware coroutine scope for background processing,
 * ensuring all operations are properly cancelled when the watcher is cleaned up.
 *
 * State Management:
 * - Exposes reactive StateFlows for UI updates (isWatching, processingStatus, etc.)
 * - Persists watched folder path in SharedPreferences
 * - Uses database as source of truth for processed documents
 *
 * @param context Android context for file system access and preferences
 * @param docsViewModel ViewModel providing document processing operations
 *
 * @see DocumentSyncService for foreground service wrapper
 * @see DocsViewModel for document processing logic
 */
class DocumentWatcher(
    private val context: Context,
    private val docsViewModel: DocsViewModel
) {
    companion object {
        private const val TAG = "DocumentWatcher"
        /** Default folder name for document watching */
        const val WATCHED_FOLDER_NAME = "DocQA_Watch"
        private const val PREFS_NAME = "DocQAPrefs"
        private const val PREF_WATCHED_FOLDER = "watched_folder_path"
    }

    /** Lifecycle-aware coroutine scope for all background operations */
    private val watcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Android FileObserver for monitoring file system events */
    private var fileObserver: FileObserver? = null

    /** StateFlow indicating whether folder watching is active */
    private val _isWatching = MutableStateFlow(false)
    val isWatching: StateFlow<Boolean> = _isWatching

    /** StateFlow tracking the most recently processed file name */
    private val _lastProcessedFile = MutableStateFlow<String?>(null)
    val lastProcessedFile: StateFlow<String?> = _lastProcessedFile

    /** StateFlow exposing current processing status for UI feedback */
    private val _processingStatus = MutableStateFlow<ProcessingStatus>(ProcessingStatus.Idle)
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus

    /** SharedPreferences for persisting watched folder path */
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** StateFlow of the currently watched folder path (null if none selected) */
    private val _watchedFolderPath = MutableStateFlow<String?>(loadWatchedFolderPath())
    val watchedFolderPath: StateFlow<String?> = _watchedFolderPath

    /**
     * Sealed class representing document processing status.
     *
     * Used to provide real-time feedback to the UI about document processing operations.
     */
    sealed class ProcessingStatus {
        /** No processing activity */
        object Idle : ProcessingStatus()
        /** Currently processing a document */
        data class Processing(val fileName: String) : ProcessingStatus()
        /** Successfully processed a document */
        data class Success(val fileName: String) : ProcessingStatus()
        /** Error occurred during processing */
        data class Error(val fileName: String, val error: String) : ProcessingStatus()
    }

    /**
     * Returns the currently watched folder as a File object.
     *
     * @return The watched folder File, or null if no folder is configured
     */
    fun getWatchedFolderPath(): File? {
        val savedPath = _watchedFolderPath.value
        return if (!savedPath.isNullOrEmpty()) {
            File(savedPath)
        } else {
            null  // Return null if no folder is selected
        }
    }

    /**
     * Sets the folder to watch for document changes.
     *
     * Stops watching the previous folder (if any), saves the new path to preferences,
     * and updates the StateFlow. Does not automatically start watching the new folder.
     *
     * @param path Absolute path to the folder to watch
     */
    fun setWatchedFolderPath(path: String) {
        // Stop watching the old folder
        if (_isWatching.value) {
            stopWatching()
        }

        // Save the new path
        prefs.edit().putString(PREF_WATCHED_FOLDER, path).apply()
        _watchedFolderPath.value = path

        // No need to clear anything as we use database as source of truth

        Log.d(TAG, "Watched folder path updated to: $path")
    }

    /**
     * Loads the watched folder path from SharedPreferences.
     *
     * @return The saved folder path, or null if none saved
     */
    private fun loadWatchedFolderPath(): String? {
        return prefs.getString(PREF_WATCHED_FOLDER, null)
    }

    /**
     * Starts monitoring the watched folder for file changes.
     *
     * Initialization sequence:
     * 1. Validates that a folder has been selected and exists
     * 2. Synchronizes database with existing files in the folder
     * 3. Sets up FileObserver to monitor CREATE, MOVED_TO, and DELETE events
     * 4. Starts watching for file system events
     *
     * File events handled:
     * - CREATE/MOVED_TO: New documents are automatically processed
     * - DELETE: Removed documents are deleted from database
     *
     * All processing happens asynchronously in the watcherScope.
     */
    fun startWatching() {
        val watchedFolder = getWatchedFolderPath()
        if (watchedFolder == null) {
            Log.e(TAG, "No folder selected for watching")
            _processingStatus.value = ProcessingStatus.Error("No folder selected", "Please select a folder to watch")
            return
        }

        if (!watchedFolder.exists()) {
            Log.e(TAG, "Selected folder does not exist: ${watchedFolder.absolutePath}")
            _processingStatus.value = ProcessingStatus.Error("Folder not found", "Selected folder does not exist")
            return
        }

        // Sync existing files with database on startup
        watcherScope.launch {
            syncFolderWithDatabase(watchedFolder)
        }

        fileObserver = object : FileObserver(watchedFolder.absolutePath, CREATE or MOVED_TO or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                path?.let {
                    val file = File(watchedFolder, it)
                    Log.d(TAG, "File event detected: $it, event type: $event")

                    when (event) {
                        CREATE, MOVED_TO -> {
                            if (isValidDocument(file)) {
                                // Check database instead of memory set
                                watcherScope.launch {
                                    val exists = docsViewModel.documentsDB.documentExistsWithPath(file.absolutePath)
                                    if (!exists) {
                                        Log.d(TAG, "Processing new document: ${file.name}")
                                        processDocument(file)
                                    }
                                }
                            }
                        }
                        DELETE -> {
                            Log.d(TAG, "File deleted: ${file.name}")
                            handleFileDeleted(file)
                        }
                    }
                }
            }
        }

        fileObserver?.startWatching()
        _isWatching.value = true
        Log.d(TAG, "Started watching folder: ${watchedFolder.absolutePath}")
    }

    /**
     * Stops monitoring the watched folder.
     *
     * Cleans up the FileObserver and updates the watching state.
     * Does not cancel in-flight processing operations.
     */
    fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
        _isWatching.value = false
        Log.d(TAG, "Stopped watching folder")
    }

    /**
     * Checks if a file is a supported document type.
     *
     * @param file The file to validate
     * @return true if file exists, is a regular file, and has a supported extension
     */
    private fun isValidDocument(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        val extension = file.extension.lowercase()
        return extension in listOf("pdf", "docx", "doc")
    }

    private fun processDocument(file: File) {
        watcherScope.launch {
            try {
                _processingStatus.value = ProcessingStatus.Processing(file.name)
                _lastProcessedFile.value = file.name

                val documentType = when (file.extension.lowercase()) {
                    "pdf" -> Readers.DocumentType.PDF
                    "docx", "doc" -> Readers.DocumentType.MS_DOCX
                    else -> Readers.DocumentType.UNKNOWN
                }

                if (documentType != Readers.DocumentType.UNKNOWN) {
                    Log.d(TAG, "Starting to process document: ${file.name}")
                    file.inputStream().use { inputStream ->
                        docsViewModel.addDocument(
                            inputStream,
                            file.name,
                            documentType,
                            file.absolutePath,
                            file.lastModified(),
                            file.length()
                        )
                    }

                    _processingStatus.value = ProcessingStatus.Success(file.name)
                    Log.d(TAG, "Successfully processed and added document: ${file.name}")
                } else {
                    _processingStatus.value = ProcessingStatus.Error(file.name, "Unsupported document type")
                    Log.e(TAG, "Unsupported document type for file: ${file.name}")
                }
            } catch (e: Exception) {
                _processingStatus.value = ProcessingStatus.Error(file.name, e.message ?: "Unknown error")
                Log.e(TAG, "Error processing document: ${file.name}", e)
            }
        }
    }

    /**
     * Synchronizes the database with the current folder contents.
     *
     * Performs a bidirectional sync:
     * 1. Processes new files found in folder but not in database
     * 2. Re-processes modified files (based on lastModified timestamp)
     * 3. Removes database entries for files that no longer exist
     *
     * Called automatically when watching starts to ensure database consistency.
     *
     * @param folder The folder to synchronize
     */
    private suspend fun syncFolderWithDatabase(folder: File) {
        Log.d(TAG, "Starting folder sync for: ${folder.absolutePath}")
        
        // Get all valid files in folder
        val folderFiles = folder.listFiles()
            ?.filter { isValidDocument(it) }
            ?.associateBy { it.absolutePath }
            ?: emptyMap()
        
        // Get all documents from database
        val dbDocuments = docsViewModel.documentsDB.getAllDocumentsMap()
        
        // Process new files not in database
        folderFiles.forEach { (path, file) ->
            if (!dbDocuments.containsKey(path)) {
                Log.d(TAG, "New file found during sync: ${file.name}")
                processDocument(file)
            } else {
                // Check if file was modified
                val dbDoc = dbDocuments[path]
                if (dbDoc != null && file.lastModified() > dbDoc.fileLastModified) {
                    Log.d(TAG, "Modified file found during sync: ${file.name}")
                    // Re-process the document
                    handleFileDeleted(file) // Remove old version
                    processDocument(file) // Add new version
                }
            }
        }
        
        // Remove documents for files that no longer exist
        dbDocuments.forEach { (path, doc) ->
            if (!folderFiles.containsKey(path)) {
                Log.d(TAG, "File no longer exists: ${doc.docFileName}")
                docsViewModel.removeDocument(doc.docId)
            }
        }
        
        Log.d(TAG, "Folder sync completed")
    }
    
    private fun handleFileDeleted(file: File) {
        watcherScope.launch {
            try {
                _processingStatus.value = ProcessingStatus.Processing("Removing ${file.name}")

                // Remove from database
                docsViewModel.removeDocumentByFilePath(file.absolutePath)

                _processingStatus.value = ProcessingStatus.Success("Removed ${file.name}")
                Log.d(TAG, "Successfully removed document: ${file.name}")
            } catch (e: Exception) {
                _processingStatus.value = ProcessingStatus.Error(file.name, "Failed to remove: ${e.message}")
                Log.e(TAG, "Error removing document: ${file.name}", e)
            }
        }
    }

    /**
     * Cleans up the document watcher and cancels all background operations.
     *
     * Should be called when the watcher is no longer needed (e.g., when the
     * ViewModel is cleared) to prevent memory leaks and cancel in-flight operations.
     */
    fun cleanup() {
        stopWatching()
        watcherScope.cancel()
        Log.d(TAG, "DocumentWatcher cleanup complete, all coroutines cancelled")
    }
}