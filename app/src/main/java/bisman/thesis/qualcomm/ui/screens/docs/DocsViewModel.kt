package bisman.thesis.qualcomm.ui.screens.docs

import android.content.Context
import androidx.lifecycle.ViewModel
import bisman.thesis.qualcomm.data.Chunk
import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.Document
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.domain.readers.Readers
import bisman.thesis.qualcomm.domain.splitters.WhiteSpaceSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.os.Build
import bisman.thesis.qualcomm.services.DocumentSyncService
import org.koin.android.annotation.KoinViewModel
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.min
import bisman.thesis.qualcomm.domain.watcher.DocumentWatcher

/**
 * ViewModel for the Documents Management screen.
 *
 * This ViewModel handles all document-related operations for the RAG system:
 * - **Document Import**: Upload PDFs and DOCX files from device storage or URLs
 * - **Text Processing**: Extract text content from uploaded documents
 * - **Chunking**: Split documents into overlapping chunks for semantic search
 * - **Embedding Generation**: Create vector embeddings for each chunk
 * - **Batch Processing**: Efficiently insert chunks in batches (30 at a time)
 * - **Folder Watching**: Monitor a folder for new/modified documents
 * - **Background Sync**: Manage foreground service for automatic document processing
 *
 * Document Processing Pipeline:
 * 1. Read document using appropriate reader (PDF/DOCX)
 * 2. Store full document text in DocumentsDB
 * 3. Split text into chunks (500 chars, 50 char overlap)
 * 4. Generate embedding for each chunk using sentence transformer
 * 5. Batch insert chunks with embeddings into ChunksDB
 *
 * @param documentsDB Repository for storing and retrieving document metadata and content
 * @param chunksDB Repository for storing and retrieving document chunks with embeddings
 * @param sentenceEncoder Provider for generating text embeddings
 *
 * @see DocumentWatcher for folder monitoring functionality
 * @see DocumentSyncService for background document processing
 * @see Readers for document parsing
 */
@KoinViewModel
class DocsViewModel(
    val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {

    /** Document watcher instance for monitoring folder changes */
    lateinit var documentWatcher: DocumentWatcher

    /**
     * Initializes the document watcher for monitoring a folder.
     *
     * @param context Android context for accessing file system
     */
    fun initDocumentWatcher(context: Context) {
        documentWatcher = DocumentWatcher(context, this)
    }
    
    /**
     * Starts the foreground document synchronization service.
     *
     * Launches a foreground service that monitors the watched folder for changes and
     * automatically processes new or modified documents. The service persists across
     * app restarts via SharedPreferences.
     *
     * @param context Android context for starting the service
     */
    fun startSyncService(context: Context) {
        val watchedPath = documentWatcher.watchedFolderPath.value
        if (!watchedPath.isNullOrEmpty()) {
            try {
                val intent = android.content.Intent(context, DocumentSyncService::class.java).apply {
                    putExtra("folder_path", watchedPath)
                }

                // Save service enabled preference
                val prefs = context.getSharedPreferences("DocQAPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("sync_service_enabled", true).apply()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d("DocsViewModel", "Started document sync service")
            } catch (e: Exception) {
                Log.e("DocsViewModel", "Failed to start sync service", e)
            }
        }
    }

    /**
     * Stops the foreground document synchronization service.
     *
     * Disables automatic document monitoring and updates SharedPreferences.
     *
     * @param context Android context for stopping the service
     */
    fun stopSyncService(context: Context) {
        val intent = android.content.Intent(context, DocumentSyncService::class.java)
        context.stopService(intent)

        // Save service disabled preference
        val prefs = context.getSharedPreferences("DocQAPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sync_service_enabled", false).apply()

        Log.d("DocsViewModel", "Stopped document sync service")
    }

    /**
     * Checks if the document synchronization service is currently running.
     *
     * @param context Android context
     * @return true if service is running, false otherwise
     */
    fun isSyncServiceRunning(context: Context): Boolean {
        return DocumentSyncService.isRunning(context)
    }

    /**
     * Adds a document to the RAG system and processes it for vector search.
     *
     * This method performs the complete document ingestion pipeline:
     * 1. Reads and extracts text from the input stream using appropriate reader
     * 2. Stores the document metadata and full text in DocumentsDB
     * 3. Chunks the text into overlapping segments (500 chars, 50 overlap)
     * 4. Generates embeddings for each chunk
     * 5. Batch inserts chunks with embeddings into ChunksDB
     *
     * The operation runs on the IO dispatcher to avoid blocking the main thread.
     *
     * @param inputStream The input stream containing document data
     * @param fileName Name of the document file
     * @param documentType Type of document (PDF, DOCX, etc.)
     * @param filePath Optional file system path (used for folder watching)
     * @param fileLastModified Optional last modified timestamp (used for change detection)
     * @param fileSize Optional file size in bytes
     */
    suspend fun addDocument(
        inputStream: InputStream,
        fileName: String,
        documentType: Readers.DocumentType,
        filePath: String = "",
        fileLastModified: Long = 0,
        fileSize: Long = 0
    ) = withContext(Dispatchers.IO) {
        Log.d("DocsViewModel", "Adding document: $fileName")
        val text =
            Readers.getReaderForDocType(documentType).readFromInputStream(inputStream)
                ?: return@withContext
        Log.d("DocsViewModel", "Read text from document, length: ${text.length}")
        val newDocId =
            documentsDB.addDocument(
                Document(
                    docText = text,
                    docFileName = fileName,
                    docAddedTime = System.currentTimeMillis(),
                    docFilePath = filePath,
                    fileLastModified = fileLastModified,
                    fileSize = fileSize
                ),
            )
        Log.d("DocsViewModel", "Added document to DB with ID: $newDocId")
        // Don't show progress dialog for auto import (background process)
        val chunks = WhiteSpaceSplitter.createChunks(text, chunkSize = 500, chunkOverlap = 50)
        val size = chunks.size
        Log.d("DocsViewModel", "Created $size chunks")

        // Collect all chunks for batch insertion
        val chunksToInsert = mutableListOf<Chunk>()
        chunks.forEach { chunkText ->
            val embedding = sentenceEncoder.encodeText(chunkText)
            chunksToInsert.add(
                Chunk(
                    docId = newDocId,
                    docFileName = fileName,
                    chunkData = chunkText,
                    chunkEmbedding = embedding,
                ),
            )
        }

        // Batch insert all chunks (in batches of 30)
        chunksDB.addChunksBatch(chunksToInsert)
        Log.d("DocsViewModel", "Successfully added document and chunks for: $fileName")
    }

    /**
     * Downloads and adds a document from a URL.
     *
     * Downloads the document from the specified URL, saves it to cache, determines the
     * document type from file extension, and processes it through the standard document
     * ingestion pipeline.
     *
     * Supported file types: PDF, DOCX, DOC
     *
     * @param url The URL to download the document from
     * @param context Android context for accessing cache directory
     * @param onDownloadComplete Callback invoked with success/failure status on main thread
     */
    suspend fun addDocumentFromUrl(
        url: String,
        context: Context,
        onDownloadComplete: (Boolean) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val fileName = getFileNameFromURL(url)
                val file = File(context.cacheDir, fileName)

                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // Determine the document type based on the file extension
                // Add handle for unknown types if supported
                val documentType =
                    when (fileName.substringAfterLast(".", "").lowercase()) {
                        "pdf" -> Readers.DocumentType.PDF
                        "docx" -> Readers.DocumentType.MS_DOCX
                        "doc" -> Readers.DocumentType.MS_DOCX
                        else -> Readers.DocumentType.UNKNOWN
                    }

                // Pass file to your document handling logic
                addDocument(file.inputStream(), fileName, documentType)

                withContext(Dispatchers.Main) {
                    onDownloadComplete(true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onDownloadComplete(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onDownloadComplete(false)
            }
        }
    }

    /**
     * Returns a Flow of all documents in the database.
     *
     * The Flow emits updated lists whenever documents are added or removed,
     * enabling reactive UI updates in Compose.
     *
     * @return Flow emitting lists of Document objects
     */
    fun getAllDocuments(): Flow<List<Document>> = documentsDB.getAllDocuments()

    /**
     * Removes a document and all its associated chunks from the database.
     *
     * @param docId The unique identifier of the document to remove
     */
    fun removeDocument(docId: Long) {
        documentsDB.removeDocument(docId)
        chunksDB.removeChunks(docId)
    }

    /**
     * Removes a document by its file system path.
     *
     * Used primarily by the folder watcher when detecting deleted files.
     *
     * @param filePath The file system path of the document to remove
     */
    fun removeDocumentByFilePath(filePath: String) {
        val document = documentsDB.getDocumentByFilePath(filePath)
        if (document != null) {
            removeDocument(document.docId)
            Log.d("DocsViewModel", "Removed document from DB: ${document.docFileName}")
        }
    }

    /**
     * Returns the total count of documents in the database.
     *
     * @return Number of documents stored
     */
    fun getDocsCount(): Long = documentsDB.getDocsCount()

    /**
     * Extracts the file name from a URL string.
     *
     * Parses the URL and extracts the filename portion, removing query parameters
     * and fragment identifiers.
     *
     * Source: https://stackoverflow.com/a/11576046/13546426
     *
     * @param url The URL string to parse
     * @return The extracted filename, or empty string if parsing fails
     */
    private fun getFileNameFromURL(url: String?): String {
        if (url == null) {
            return ""
        }
        try {
            val resource = URL(url)
            val host = resource.host
            if (host.isNotEmpty() && url.endsWith(host)) {
                return ""
            }
        } catch (e: MalformedURLException) {
            return ""
        }
        val startIndex = url.lastIndexOf('/') + 1
        val length = url.length
        var lastQMPos = url.lastIndexOf('?')
        if (lastQMPos == -1) {
            lastQMPos = length
        }
        var lastHashPos = url.lastIndexOf('#')
        if (lastHashPos == -1) {
            lastHashPos = length
        }
        val endIndex = min(lastQMPos.toDouble(), lastHashPos.toDouble()).toInt()
        return url.substring(startIndex, endIndex)
    }
}
