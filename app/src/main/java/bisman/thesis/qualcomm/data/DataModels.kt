package bisman.thesis.qualcomm.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Chunk(
    @Id var chunkId: Long = 0,
    @Index var docId: Long = 0,
    var docFileName: String = "",
    var chunkData: String = "",
    @HnswIndex(dimensions = 384) var chunkEmbedding: FloatArray = floatArrayOf(),
    var paragraphIndex: Int = 0, // Track which paragraph this chunk belongs to
)

@Entity
data class Document(
    @Id var docId: Long = 0,
    var docText: String = "",
    var docFileName: String = "",
    var docAddedTime: Long = 0,
    var docFilePath: String = "",
    var fileLastModified: Long = 0,
    var fileSize: Long = 0,
    var paragraphHashes: String = "", // JSON array of paragraph hashes
)

data class RetrievedContext(
    val fileName: String,
    val context: String,
)