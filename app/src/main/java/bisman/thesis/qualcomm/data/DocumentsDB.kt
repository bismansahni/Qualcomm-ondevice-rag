package bisman.thesis.qualcomm.data

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

@Single
class DocumentsDB {
    private val docsBox = ObjectBoxStore.store.boxFor(Document::class.java)

    fun addDocument(document: Document): Long {
        val id = docsBox.put(document)
        android.util.Log.d("DocumentsDB", "Added document: ${document.docFileName} with ID: $id")
        android.util.Log.d("DocumentsDB", "Total documents now: ${docsBox.count()}")
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
            android.util.Log.d("DocumentsDB", "Removed document: ${document.docFileName} with path: $filePath")
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