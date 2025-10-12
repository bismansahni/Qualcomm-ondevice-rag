package bisman.thesis.qualcomm.data
import android.util.Log
import io.objectbox.Box
import org.koin.core.annotation.Single

/**
 * Repository for managing document chunks with vector embeddings in the ObjectBox database.
 *
 * This class provides the core vector search functionality for the RAG system:
 * - **Batch Insertion**: Efficiently inserts chunks in batches to optimize performance
 * - **Vector Search**: Performs HNSW-based approximate nearest neighbor search
 * - **Chunk Management**: Handles chunk lifecycle including removal by document or paragraph
 *
 * The repository uses lazy initialization to defer database access until first use.
 * All operations are thread-safe through ObjectBox's internal synchronization.
 *
 * @see Chunk for the data model
 * @see ObjectBoxStore for the database instance
 */
@Single
class ChunksDB {
    companion object {
        private const val TAG = "ChunksDB"
    }

    /**
     * Lazy-initialized ObjectBox reference to the Chunk entity box.
     * Initialization is deferred until first access to avoid startup delays.
     */
    private val chunksBox: Box<Chunk> by lazy {
        Log.d(TAG, "Initializing chunksBox lazily")
        try {
            val box = ObjectBoxStore.store.boxFor(Chunk::class.java)
            Log.d(TAG, "ChunksBox initialized successfully, current count: ${box.count()}")
            box
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize chunksBox", e)
            throw e
        }
    }

    /**
     * Adds a single chunk to the database.
     *
     * @param chunk The chunk to insert (with text content and embedding)
     */
    fun addChunk(chunk: Chunk) {
        Log.d(TAG, "Adding chunk for document: ${chunk.docFileName}")
        chunksBox.put(chunk)
        Log.d(TAG, "Chunk added successfully, total chunks: ${chunksBox.count()}")
    }

    /**
     * Adds multiple chunks to the database in batches for optimal performance.
     *
     * Large batch insertions are split into smaller batches to balance memory usage
     * and transaction overhead. Default batch size of 30 provides good performance
     * for typical document processing scenarios.
     *
     * @param chunks List of chunks to insert
     * @param batchSize Number of chunks to insert per transaction (default: 30)
     */
    fun addChunksBatch(chunks: List<Chunk>, batchSize: Int = 30) {
        Log.d(TAG, "Batch adding ${chunks.size} chunks with batch size: $batchSize")

        chunks.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            chunksBox.put(batch)
            Log.d(TAG, "Batch ${batchIndex + 1} inserted: ${batch.size} chunks")
        }

        Log.d(TAG, "Batch add complete, total chunks: ${chunksBox.count()}")
    }

    /**
     * Performs vector similarity search to find chunks most relevant to the query.
     *
     * Uses HNSW (Hierarchical Navigable Small World) index for fast approximate
     * nearest neighbor search. The implementation searches through 25 candidates
     * and returns the top N results for better quality (configurable via HNSW "ef" parameter).
     *
     * This is the core retrieval mechanism for the RAG system.
     *
     * @param queryEmbedding The query embedding vector (384 dimensions)
     * @param n Number of top similar chunks to return (default: 5)
     * @return List of (similarity score, chunk) pairs sorted by relevance
     */
    fun getSimilarChunks(
        queryEmbedding: FloatArray,
        n: Int = 5,
    ): List<Pair<Float, Chunk>> {
        Log.d(TAG, "Getting similar chunks, requested: $n")
        /*
        Use maxResultCount to set the maximum number of objects to return by the ANN condition.
        Hint: it can also be used as the "ef" HNSW parameter to increase the search quality in combination
        with a query limit. For example, use maxResultCount of 100 with a Query limit of 10 to have 10 results
        that are of potentially better quality than just passing in 10 for maxResultCount
        (quality/performance tradeoff).
         */
        val results = chunksBox
            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 25))
            .build()
            .findWithScores()
            .map { result -> Pair(result.score.toFloat(), result.get()) }
            .take(n)

        Log.d(TAG, "Found ${results.size} similar chunks")
        return results
    }

    /**
     * Removes all chunks associated with a specific document.
     *
     * Used when a document is deleted from the system.
     *
     * @param docId The document ID whose chunks should be removed
     */
    fun removeChunks(docId: Long) {
        Log.d(TAG, "Removing chunks for document ID: $docId")
        val idsToRemove = chunksBox
            .query(Chunk_.docId.equal(docId))
            .build()
            .findIds()
            .toList()

        Log.d(TAG, "Found ${idsToRemove.size} chunks to remove")
        chunksBox.removeByIds(idsToRemove)
        Log.d(TAG, "Chunks removed successfully")
    }

    /**
     * Removes chunks belonging to a specific paragraph in a document.
     *
     * Enables incremental updates when specific paragraphs of a document change,
     * avoiding the need to reprocess the entire document.
     *
     * @param docId The document ID
     * @param paragraphIndex The paragraph index whose chunks should be removed
     */
    fun removeChunksByParagraph(docId: Long, paragraphIndex: Int) {
        Log.d(TAG, "Removing chunks for document ID: $docId, paragraph: $paragraphIndex")
        val idsToRemove = chunksBox
            .query(
                Chunk_.docId.equal(docId)
                    .and(Chunk_.paragraphIndex.equal(paragraphIndex.toLong()))
            )
            .build()
            .findIds()
            .toList()

        Log.d(TAG, "Found ${idsToRemove.size} chunks to remove from paragraph $paragraphIndex")
        chunksBox.removeByIds(idsToRemove)
        Log.d(TAG, "Paragraph chunks removed successfully")
    }

    /**
     * Retrieves all chunks belonging to a specific paragraph in a document.
     *
     * Used for incremental update logic to determine which chunks need reprocessing.
     *
     * @param docId The document ID
     * @param paragraphIndex The paragraph index
     * @return List of chunks in the specified paragraph
     */
    fun getChunksByParagraph(docId: Long, paragraphIndex: Int): List<Chunk> {
        return chunksBox
            .query(
                Chunk_.docId.equal(docId)
                    .and(Chunk_.paragraphIndex.equal(paragraphIndex.toLong()))
            )
            .build()
            .find()
    }
}
