package bisman.thesis.qualcomm.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import bisman.thesis.qualcomm.MainActivity
import bisman.thesis.qualcomm.R
import bisman.thesis.qualcomm.data.Chunk
import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.Document
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.domain.readers.Readers
import bisman.thesis.qualcomm.domain.splitters.WhiteSpaceSplitter
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Timer
import java.util.TimerTask

class DocumentSyncService : Service(), KoinComponent {
    companion object {
        private const val TAG = "DocumentSyncService"
        private const val CHANNEL_ID = "document_sync_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "DocQAPrefs"
        private const val PREF_WATCHED_FOLDER = "watched_folder_path"
        private const val SYNC_INTERVAL = 60000L // 1 minute
        
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == DocumentSyncService::class.java.name }
        }
    }
    
    private val documentsDB: DocumentsDB by inject()
    private val chunksDB: ChunksDB by inject()
    private val sentenceEncoder: SentenceEmbeddingProvider by inject()
    
    private var fileObserver: FileObserver? = null
    private lateinit var prefs: SharedPreferences
    private var watchedFolderPath: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncTimer: Timer? = null
    private var lastSyncTime = 0L
    private var isProcessing = false
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        watchedFolderPath = prefs.getString(PREF_WATCHED_FOLDER, null)
        
        Log.d(TAG, "DocumentSyncService created")
        
        // Must start foreground within 5 seconds of service start
        createNotificationChannel()
        startForegroundService()
        
        // Start watching folder if configured
        watchedFolderPath?.let { path ->
            startWatchingFolder(path)
            // Initial sync
            serviceScope.launch {
                syncFolderWithDatabase()
            }
        }
        
        // Start periodic sync
        startPeriodicSync()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Document Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Syncs documents in the background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
        val stopIntent = Intent(this, DocumentSyncService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Document Sync Active")
            .setContentText(getStatusText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, DocumentSyncService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Document Sync Active")
            .setContentText(getStatusText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun getStatusText(): String {
        return when {
            isProcessing -> "Processing documents..."
            watchedFolderPath == null -> "No folder selected"
            lastSyncTime == 0L -> "Watching: ${File(watchedFolderPath!!).name}"
            else -> {
                val timeSince = (System.currentTimeMillis() - lastSyncTime) / 1000
                "Last sync: ${timeSince}s ago • ${File(watchedFolderPath!!).name}"
            }
        }
    }
    
    private fun startWatchingFolder(path: String) {
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            Log.e(TAG, "Invalid folder path: $path")
            return
        }
        
        fileObserver?.stopWatching()
        
        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(
                folder,
                CREATE or DELETE or MODIFY or MOVED_TO or MOVED_FROM
            ) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(event, path, folder)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(
                folder.absolutePath,
                CREATE or DELETE or MODIFY or MOVED_TO or MOVED_FROM
            ) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(event, path, folder)
                }
            }
        }
        
        fileObserver?.startWatching()
        Log.d(TAG, "Started watching folder: $path")
    }
    
    private fun handleFileEvent(event: Int, path: String?, folder: File) {
        path?.let {
            try {
                val file = File(folder, it)
                Log.d(TAG, "File event: $event for ${file.name}")
                
                serviceScope.launch {
                    try {
                        when (event) {
                            FileObserver.CREATE, FileObserver.MOVED_TO -> handleFileAdded(file)
                            FileObserver.DELETE, FileObserver.MOVED_FROM -> handleFileDeleted(file)
                            FileObserver.MODIFY -> handleFileModified(file)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file event for ${file.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling file event", e)
            }
        }
    }
    
    private fun startPeriodicSync() {
        syncTimer?.cancel()
        syncTimer = Timer()
        syncTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                serviceScope.launch {
                    Log.d(TAG, "Running periodic sync")
                    syncFolderWithDatabase()
                }
            }
        }, SYNC_INTERVAL, SYNC_INTERVAL)
    }
    
    private suspend fun syncFolderWithDatabase() = withContext(Dispatchers.IO) {
        if (isProcessing) {
            Log.d(TAG, "Sync already in progress, skipping")
            return@withContext
        }
        
        val folderPath = watchedFolderPath ?: return@withContext
        val folder = File(folderPath)
        if (!folder.exists()) {
            Log.e(TAG, "Watched folder no longer exists: $folderPath")
            return@withContext
        }
        
        isProcessing = true
        updateNotification()
        
        try {
            Log.d(TAG, "Starting folder sync for: $folderPath")
            
            // Get all valid documents in folder
            val folderFiles = folder.listFiles()
                ?.filter { it.isFile && isValidDocument(it) }
                ?.associateBy { it.absolutePath }
                ?: emptyMap()
            
            // Get all documents from database
            val dbDocuments = documentsDB.getAllDocumentsMap()
            
            Log.d(TAG, "Found ${folderFiles.size} files in folder, ${dbDocuments.size} documents in database")
            
            // Process new or modified files
            folderFiles.forEach { (path, file) ->
                val dbDoc = dbDocuments[path]
                if (dbDoc == null) {
                    Log.d(TAG, "New file found: ${file.name}")
                    processNewDocument(file)
                } else if (file.lastModified() > dbDoc.fileLastModified) {
                    Log.d(TAG, "Modified file found: ${file.name}")
                    processModifiedDocument(file, dbDoc)
                }
            }
            
            // Remove documents for deleted files
            dbDocuments.forEach { (path, doc) ->
                if (!folderFiles.containsKey(path)) {
                    Log.d(TAG, "File no longer exists: ${doc.docFileName}")
                    removeDocumentFromDatabase(doc)
                }
            }
            
            lastSyncTime = System.currentTimeMillis()
            Log.d(TAG, "Sync completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
        } finally {
            isProcessing = false
            updateNotification()
        }
    }
    
    private suspend fun handleFileAdded(file: File) = withContext(Dispatchers.IO) {
        if (!isValidDocument(file)) return@withContext
        
        // Check if already in database
        if (documentsDB.documentExistsWithPath(file.absolutePath)) {
            Log.d(TAG, "File already in database: ${file.name}")
            return@withContext
        }
        
        processNewDocument(file)
    }
    
    private suspend fun handleFileDeleted(file: File) = withContext(Dispatchers.IO) {
        val document = documentsDB.getDocumentByFilePath(file.absolutePath)
        if (document != null) {
            removeDocumentFromDatabase(document)
        }
    }
    
    private suspend fun handleFileModified(file: File) = withContext(Dispatchers.IO) {
        if (!isValidDocument(file)) return@withContext
        
        val document = documentsDB.getDocumentByFilePath(file.absolutePath)
        if (document != null && file.lastModified() > document.fileLastModified) {
            processModifiedDocument(file, document)
        }
    }
    
    private suspend fun processNewDocument(file: File) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing new document: ${file.name}")
            
            val documentType = when (file.extension.lowercase()) {
                "pdf" -> Readers.DocumentType.PDF
                "docx", "doc" -> Readers.DocumentType.MS_DOCX
                else -> return@withContext
            }
            
            file.inputStream().use { inputStream ->
                val text = Readers.getReaderForDocType(documentType)
                    .readFromInputStream(inputStream) ?: return@withContext
                
                val document = Document(
                    docText = text,
                    docFileName = file.name,
                    docAddedTime = System.currentTimeMillis(),
                    docFilePath = file.absolutePath,
                    fileLastModified = file.lastModified(),
                    fileSize = file.length()
                )
                
                val docId = documentsDB.addDocument(document)
                
                // Create and store chunks
                val chunks = WhiteSpaceSplitter.createChunks(text, chunkSize = 500, chunkOverlap = 50)
                chunks.forEach { chunkText ->
                    val embedding = sentenceEncoder.encodeText(chunkText)
                    chunksDB.addChunk(
                        Chunk(
                            docId = docId,
                            docFileName = file.name,
                            chunkData = chunkText,
                            chunkEmbedding = embedding
                        )
                    )
                }
                
                Log.d(TAG, "Successfully processed document: ${file.name} with ${chunks.size} chunks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing document: ${file.name}", e)
        }
    }
    
    private suspend fun processModifiedDocument(file: File, document: Document) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing modified document: ${file.name}")
            
            // Remove old chunks
            chunksDB.removeChunks(document.docId)
            
            // Re-process document
            val documentType = when (file.extension.lowercase()) {
                "pdf" -> Readers.DocumentType.PDF
                "docx", "doc" -> Readers.DocumentType.MS_DOCX
                else -> return@withContext
            }
            
            file.inputStream().use { inputStream ->
                val text = Readers.getReaderForDocType(documentType)
                    .readFromInputStream(inputStream) ?: return@withContext
                
                // Update document
                document.docText = text
                document.fileLastModified = file.lastModified()
                document.fileSize = file.length()
                documentsDB.addDocument(document) // This will update existing
                
                // Create new chunks
                val chunks = WhiteSpaceSplitter.createChunks(text, chunkSize = 500, chunkOverlap = 50)
                chunks.forEach { chunkText ->
                    val embedding = sentenceEncoder.encodeText(chunkText)
                    chunksDB.addChunk(
                        Chunk(
                            docId = document.docId,
                            docFileName = file.name,
                            chunkData = chunkText,
                            chunkEmbedding = embedding
                        )
                    )
                }
                
                Log.d(TAG, "Successfully updated document: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating document: ${file.name}", e)
        }
    }
    
    private suspend fun removeDocumentFromDatabase(document: Document) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Removing document from database: ${document.docFileName}")
            chunksDB.removeChunks(document.docId)
            documentsDB.removeDocument(document.docId)
            Log.d(TAG, "Successfully removed document: ${document.docFileName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing document: ${document.docFileName}", e)
        }
    }
    
    private fun isValidDocument(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        val extension = file.extension.lowercase()
        return extension in listOf("pdf", "docx", "doc")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Update watched folder if changed
        intent?.getStringExtra("folder_path")?.let { newPath ->
            if (newPath != watchedFolderPath) {
                watchedFolderPath = newPath
                prefs.edit().putString(PREF_WATCHED_FOLDER, newPath).apply()
                startWatchingFolder(newPath)
                
                // Sync immediately for new folder
                serviceScope.launch {
                    syncFolderWithDatabase()
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DocumentSyncService destroyed")
        
        fileObserver?.stopWatching()
        syncTimer?.cancel()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}