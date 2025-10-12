package bisman.thesis.qualcomm.domain.splitters

import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for splitting documents into overlapping chunks for semantic search.
 *
 * The splitter implements a hierarchical chunking strategy:
 * 1. Split document into paragraphs (by double newline)
 * 2. Split each paragraph into chunks at word boundaries
 * 3. Create overlapping chunks between adjacent chunks for context preservation
 *
 * Chunking is essential for RAG systems because:
 * - Embedding models have maximum input length limits
 * - Smaller chunks enable more precise semantic matching
 * - Overlapping chunks preserve context across chunk boundaries
 *
 * Example: With chunkSize=500 and chunkOverlap=50:
 * - Chunk 1: [0...500]
 * - Overlap chunk: [450...550] (last 50 chars of chunk 1 + first 50 of chunk 2)
 * - Chunk 2: [500...1000]
 */
class WhiteSpaceSplitter {
    companion object {
        /**
         * Creates overlapping chunks from document text.
         *
         * Splits text at paragraph boundaries first, then at word boundaries within
         * each paragraph. Generates additional overlapping chunks between adjacent chunks
         * to preserve cross-boundary context.
         *
         * @param docText The full document text to chunk
         * @param chunkSize Maximum size of each chunk in characters
         * @param chunkOverlap Number of characters to overlap between adjacent chunks
         * @param separatorParagraph Delimiter for paragraph boundaries (default: "\n\n")
         * @param separator Delimiter for word boundaries (default: " ")
         * @return List of text chunks (includes both primary and overlapping chunks)
         */
        fun createChunks(
            docText: String,
            chunkSize: Int,
            chunkOverlap: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<String> {
            val textChunks = ArrayList<String>()
            docText.split(separatorParagraph).forEach { paragraph ->
                var currChunk = ""
                val chunks = ArrayList<String>()
                paragraph.split(separator).forEach { word ->
                    val newChunk =
                        currChunk +
                                (
                                        if (currChunk.isNotEmpty()) {
                                            separator
                                        } else {
                                            ""
                                        }
                                        ) +
                                word
                    if (newChunk.length <= chunkSize) {
                        currChunk = newChunk
                    } else {
                        if (currChunk.isNotEmpty()) {
                            chunks.add(currChunk)
                        }
                        currChunk = word
                    }
                }
                if (currChunk.isNotEmpty()) {
                    chunks.add(currChunk)
                }

                val overlappingChunks = ArrayList<String>(chunks)
                if (chunkOverlap > 1 && chunks.size > 0) {
                    for (i in 0..<chunks.size - 1) {
                        val overlapStart = max(0, chunks[i].length - chunkOverlap)
                        val overlapEnd = min(chunkOverlap, chunks[i + 1].length)
                        overlappingChunks.add(
                            chunks[i].substring(overlapStart) +
                                    " " +
                                    chunks[i + 1].substring(0..<overlapEnd),
                        )
                    }
                }

                textChunks.addAll(overlappingChunks)
            }
            return textChunks
        }

        /**
         * Creates overlapping chunks with paragraph index tracking.
         *
         * Similar to createChunks() but tracks which paragraph each chunk originates from.
         * This enables incremental updates: when a paragraph changes, only its chunks
         * need to be regenerated and re-embedded.
         *
         * @param docText The full document text to chunk
         * @param chunkSize Maximum size of each chunk in characters
         * @param chunkOverlap Number of characters to overlap between adjacent chunks
         * @param separatorParagraph Delimiter for paragraph boundaries (default: "\n\n")
         * @param separator Delimiter for word boundaries (default: " ")
         * @return List of ChunkWithParagraph objects containing text and paragraph index
         */
        fun createChunksWithParagraphTracking(
            docText: String,
            chunkSize: Int,
            chunkOverlap: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<ChunkWithParagraph> {
            val chunksWithParagraphs = ArrayList<ChunkWithParagraph>()
            val paragraphs = docText.split(separatorParagraph)

            paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                var currChunk = ""
                val chunks = ArrayList<String>()
                paragraph.split(separator).forEach { word ->
                    val newChunk =
                        currChunk +
                                (
                                        if (currChunk.isNotEmpty()) {
                                            separator
                                        } else {
                                            ""
                                        }
                                        ) +
                                word
                    if (newChunk.length <= chunkSize) {
                        currChunk = newChunk
                    } else {
                        if (currChunk.isNotEmpty()) {
                            chunks.add(currChunk)
                        }
                        currChunk = word
                    }
                }
                if (currChunk.isNotEmpty()) {
                    chunks.add(currChunk)
                }

                val overlappingChunks = ArrayList<String>(chunks)
                if (chunkOverlap > 1 && chunks.size > 0) {
                    for (i in 0..<chunks.size - 1) {
                        val overlapStart = max(0, chunks[i].length - chunkOverlap)
                        val overlapEnd = min(chunkOverlap, chunks[i + 1].length)
                        overlappingChunks.add(
                            chunks[i].substring(overlapStart) +
                                    " " +
                                    chunks[i + 1].substring(0..<overlapEnd),
                        )
                    }
                }

                overlappingChunks.forEach { chunkText ->
                    chunksWithParagraphs.add(
                        ChunkWithParagraph(
                            text = chunkText,
                            paragraphIndex = paragraphIndex
                        )
                    )
                }
            }
            return chunksWithParagraphs
        }

        /**
         * Extracts paragraphs from document text.
         *
         * Simple utility method for splitting documents into paragraph segments.
         * Used for incremental update logic to determine paragraph-level changes.
         *
         * @param docText The full document text
         * @param separatorParagraph Delimiter for paragraph boundaries (default: "\n\n")
         * @return List of paragraph strings
         */
        fun getParagraphs(
            docText: String,
            separatorParagraph: String = "\n\n"
        ): List<String> {
            return docText.split(separatorParagraph)
        }
    }

    /**
     * Data class representing a text chunk with its source paragraph index.
     *
     * Used for incremental document updates to track which chunks belong to which paragraph.
     *
     * @property text The chunk text content
     * @property paragraphIndex The zero-based index of the source paragraph
     */
    data class ChunkWithParagraph(
        val text: String,
        val paragraphIndex: Int
    )
}
