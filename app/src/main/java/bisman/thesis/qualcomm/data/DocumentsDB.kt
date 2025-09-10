package bisman.thesis.qualcomm.data

import android.util.Log
import io.objectbox.Box
import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

@Single
class DocumentsDB {
    companion object {
        private const val TAG = "DocumentsDB"
    }
    
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

    fun addDocument(document: Document): Long {
        val id = docsBox.put(document)
        Log.d(TAG, "Added document: ${document.docFileName} with ID: $id")
        Log.d(TAG, "Total documents now: ${docsBox.count()}")
        return id
    }

    fun removeDocument(docId: Long) {
        docsBox.remove(docId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllDocuments(): Flow<MutableList<Document>> =
        docsBox
            .query(Document_.docId.notNull())
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    fun getDocsCount(): Long = docsBox.count()
    
    fun getDocumentByFilePath(filePath: String): Document? {
        return docsBox
            .query(Document_.docFilePath.equal(filePath))
            .build()
            .findFirst()
    }
    
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
    
    fun getAllDocumentFilePaths(): List<String> {
        return docsBox.all.mapNotNull { doc ->
            doc.docFilePath.takeIf { it.isNotEmpty() }
        }
    }
    
    fun documentExistsWithPath(filePath: String): Boolean {
        return docsBox
            .query(Document_.docFilePath.equal(filePath))
            .build()
            .count() > 0
    }
    
    fun getAllDocumentsMap(): Map<String, Document> {
        return docsBox.all.associateBy { it.docFilePath }.filterKeys { it.isNotEmpty() }
    }
    
    fun updateDocumentMetadata(docId: Long, lastModified: Long, fileSize: Long) {
        val doc = docsBox.get(docId)
        doc?.let {
            it.fileLastModified = lastModified
            it.fileSize = fileSize
            docsBox.put(it)
        }
    }
}