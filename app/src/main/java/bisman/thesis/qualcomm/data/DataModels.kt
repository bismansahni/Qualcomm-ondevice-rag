package bisman.thesis.qualcomm.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * Entity representing a document chunk with its embedding for vector search.
 *
 * Documents are split into chunks to enable more granular semantic search. Each chunk
 * contains a portion of the document text along with its vector embedding for similarity
 * matching.
 *
 * The HNSW (Hierarchical Navigable Small World) index on the embedding field enables
 * fast approximate nearest neighbor search for RAG retrieval.
 *
 * @property chunkId Unique identifier for the chunk (auto-generated)
 * @property docId Foreign key to the parent Document
 * @property docFileName Name of the source document (denormalized for convenience)
 * @property chunkData The actual text content of this chunk
 * @property chunkEmbedding 384-dimensional vector embedding (from sentence transformer model)
 * @property paragraphIndex Index of the paragraph this chunk belongs to (for incremental updates)
 */
@Entity
data class Chunk(
    @Id var chunkId: Long = 0,
    @Index var docId: Long = 0,
    var docFileName: String = "",
    var chunkData: String = "",
    @HnswIndex(dimensions = 384) var chunkEmbedding: FloatArray = floatArrayOf(),
    @Index var paragraphIndex: Int = 0, // Track which paragraph this chunk belongs to - indexed for removeChunksByParagraph queries
)

/**
 * Entity representing a document in the RAG system.
 *
 * Stores the full document text along with metadata for tracking file changes
 * and enabling incremental updates when documents are modified.
 *
 * @property docId Unique identifier for the document (auto-generated)
 * @property docText Full text content extracted from the document
 * @property docFileName Name of the document file
 * @property docAddedTime Timestamp when document was added/last processed (milliseconds since epoch)
 * @property docFilePath File system path (used for folder watching and duplicate detection)
 * @property fileLastModified Last modified timestamp of the source file (for change detection)
 * @property fileSize Size of the source file in bytes (for change detection)
 * @property paragraphHashes JSON array of paragraph hashes (for incremental chunk updates)
 */
@Entity
data class Document(
    @Id var docId: Long = 0,
    var docText: String = "",
    var docFileName: String = "",
    var docAddedTime: Long = 0,
    @Index var docFilePath: String = "", // Indexed for getDocumentByFilePath and documentExistsWithPath lookups
    var fileLastModified: Long = 0,
    var fileSize: Long = 0,
    var paragraphHashes: String = "", // JSON array of paragraph hashes
)

/**
 * Data class representing a retrieved context chunk for display in the UI.
 *
 * Used to show users which document chunks were retrieved during RAG to answer their query.
 *
 * @property fileName Name of the source document
 * @property context The chunk text content
 */
data class RetrievedContext(
    val fileName: String,
    val context: String,
)