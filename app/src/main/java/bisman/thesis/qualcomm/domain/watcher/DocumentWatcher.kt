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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class DocumentWatcher(
    private val context: Context,
    private val docsViewModel: DocsViewModel
) {
    companion object {
        private const val TAG = "DocumentWatcher"
        const val WATCHED_FOLDER_NAME = "DocQA_Watch"
        private const val PREFS_NAME = "DocQAPrefs"
        private const val PREF_WATCHED_FOLDER = "watched_folder_path"
    }

    private var fileObserver: FileObserver? = null
    private val _isWatching = MutableStateFlow(false)
    val isWatching: StateFlow<Boolean> = _isWatching

    private val _lastProcessedFile = MutableStateFlow<String?>(null)
    val lastProcessedFile: StateFlow<String?> = _lastProcessedFile

    private val _processingStatus = MutableStateFlow<ProcessingStatus>(ProcessingStatus.Idle)
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _watchedFolderPath = MutableStateFlow<String?>(loadWatchedFolderPath())
    val watchedFolderPath: StateFlow<String?> = _watchedFolderPath

    sealed class ProcessingStatus {
        object Idle : ProcessingStatus()
        data class Processing(val fileName: String) : ProcessingStatus()
        data class Success(val fileName: String) : ProcessingStatus()
        data class Error(val fileName: String, val error: String) : ProcessingStatus()
    }

    fun getWatchedFolderPath(): File? {
        val savedPath = _watchedFolderPath.value
        return if (!savedPath.isNullOrEmpty()) {
            File(savedPath)
        } else {
            null  // Return null if no folder is selected
        }
    }

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

    private fun loadWatchedFolderPath(): String? {
        return prefs.getString(PREF_WATCHED_FOLDER, null)
    }

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
        CoroutineScope(Dispatchers.IO).launch {
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
                                CoroutineScope(Dispatchers.IO).launch {
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

    fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
        _isWatching.value = false
        Log.d(TAG, "Stopped watching folder")
    }

    private fun isValidDocument(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        val extension = file.extension.lowercase()
        return extension in listOf("pdf", "docx", "doc")
    }

    private fun processDocument(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
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
        CoroutineScope(Dispatchers.IO).launch {
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
}