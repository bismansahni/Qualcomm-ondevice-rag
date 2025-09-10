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
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.min
import bisman.thesis.qualcomm.domain.watcher.DocumentWatcher

class DocsViewModel(
    val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {

    lateinit var documentWatcher: DocumentWatcher

    fun initDocumentWatcher(context: Context) {
        documentWatcher = DocumentWatcher(context, this)
    }
    
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
    
    fun stopSyncService(context: Context) {
        val intent = android.content.Intent(context, DocumentSyncService::class.java)
        context.stopService(intent)
        
        // Save service disabled preference
        val prefs = context.getSharedPreferences("DocQAPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sync_service_enabled", false).apply()
        
        Log.d("DocsViewModel", "Stopped document sync service")
    }
    
    fun isSyncServiceRunning(context: Context): Boolean {
        return DocumentSyncService.isRunning(context)
    }
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
        
        // Reset stats before processing
        sentenceEncoder.resetStats()
        
        chunks.forEachIndexed { index, s ->
            val embedding = sentenceEncoder.encodeText(s, suppressLogs = true)
            chunksDB.addChunk(
                Chunk(
                    docId = newDocId,
                    docFileName = fileName,
                    chunkData = s,
                    chunkEmbedding = embedding,
                ),
            )
        }
        
        // Print average stats after processing
        sentenceEncoder.printAverageStats()
        
        Log.d("DocsViewModel", "Successfully added document and chunks for: $fileName")
    }

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

    fun getAllDocuments(): Flow<List<Document>> = documentsDB.getAllDocuments()

    fun removeDocument(docId: Long) {
        documentsDB.removeDocument(docId)
        chunksDB.removeChunks(docId)
    }
    
    fun removeDocumentByFilePath(filePath: String) {
        val document = documentsDB.getDocumentByFilePath(filePath)
        if (document != null) {
            removeDocument(document.docId)
            Log.d("DocsViewModel", "Removed document from DB: ${document.docFileName}")
        }
    }

    fun getDocsCount(): Long = documentsDB.getDocsCount()

    // Extracts the file name from the URL
    // Source: https://stackoverflow.com/a/11576046/13546426
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
