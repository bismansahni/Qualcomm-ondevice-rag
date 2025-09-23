package bisman.thesis.qualcomm.data
import android.util.Log
import io.objectbox.Box
import org.koin.core.annotation.Single

@Single
class ChunksDB {
    companion object {
        private const val TAG = "ChunksDB"
    }
    
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

    fun addChunk(chunk: Chunk) {
        Log.d(TAG, "Adding chunk for document: ${chunk.docFileName}")
        chunksBox.put(chunk)
        Log.d(TAG, "Chunk added successfully, total chunks: ${chunksBox.count()}")
    }

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
