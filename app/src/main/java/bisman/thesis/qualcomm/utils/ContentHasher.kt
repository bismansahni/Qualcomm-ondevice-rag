package bisman.thesis.qualcomm.utils

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Utility class for computing content hashes to detect document changes.
 *
 * This class enables incremental document updates for the RAG system by:
 * - Computing SHA-256 hashes of paragraph content
 * - Comparing paragraph hashes between document versions
 * - Identifying which paragraphs have changed, been added, or been deleted
 *
 * By tracking paragraph-level changes, the system can re-process only the modified
 * portions of a document, avoiding expensive full document re-embedding.
 *
 * Example workflow:
 * 1. When document is first added: compute and store paragraph hashes
 * 2. When document is modified: compute new paragraph hashes
 * 3. Compare old vs new hashes to find changes
 * 4. Re-process only changed paragraphs (remove old chunks, generate new chunks/embeddings)
 *
 * @see DocumentSyncService for usage in document modification detection
 */
class ContentHasher {
    companion object {
        /** Hash algorithm used for content hashing */
        private const val HASH_ALGORITHM = "SHA-256"

        /**
         * Computes SHA-256 hash of text content.
         *
         * @param text The text to hash
         * @return Hexadecimal string representation of the hash
         */
        fun hashText(text: String): String {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hashBytes = digest.digest(text.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Computes hashes for all paragraphs and returns as JSON array string.
         *
         * The returned JSON string is stored in the Document entity's paragraphHashes field
         * for later comparison when the document is modified.
         *
         * @param paragraphs List of paragraph strings
         * @return JSON array string containing paragraph hashes in order
         */
        fun hashParagraphs(paragraphs: List<String>): String {
            val jsonArray = JSONArray()
            paragraphs.forEach { paragraph ->
                val hash = hashText(paragraph)
                jsonArray.put(hash)
            }
            return jsonArray.toString()
        }

        /**
         * Parses JSON array string of paragraph hashes back into a list.
         *
         * @param hashesJson JSON array string from Document.paragraphHashes
         * @return List of hash strings, or empty list if parsing fails
         */
        fun parseParagraphHashes(hashesJson: String): List<String> {
            if (hashesJson.isEmpty()) return emptyList()

            return try {
                val jsonArray = JSONArray(hashesJson)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Compares new paragraphs against old hashes to find changes.
         *
         * Performs position-based comparison to determine:
         * - **Changed**: Paragraphs at same position with different content
         * - **Added**: Paragraphs beyond the old document length
         * - **Deleted**: Old paragraphs that don't exist in new document
         *
         * @param newParagraphs List of new paragraph strings
         * @param oldHashes List of old paragraph hashes
         * @return ParagraphChanges object containing indices of all changes
         */
        fun findChangedParagraphs(
            newParagraphs: List<String>,
            oldHashes: List<String>
        ): ParagraphChanges {
            val newHashes = newParagraphs.map { hashText(it) }
            val changedIndices = mutableListOf<Int>()
            val addedIndices = mutableListOf<Int>()
            val deletedIndices = mutableListOf<Int>()

            // Find changed and new paragraphs
            newHashes.forEachIndexed { index, newHash ->
                when {
                    index >= oldHashes.size -> addedIndices.add(index)
                    newHash != oldHashes[index] -> changedIndices.add(index)
                }
            }

            // Find deleted paragraphs
            if (oldHashes.size > newHashes.size) {
                for (i in newHashes.size until oldHashes.size) {
                    deletedIndices.add(i)
                }
            }

            return ParagraphChanges(
                changedIndices = changedIndices,
                addedIndices = addedIndices,
                deletedIndices = deletedIndices,
                newHashes = newHashes
            )
        }
    }

    /**
     * Data class representing paragraph-level changes between document versions.
     *
     * @property changedIndices Indices of paragraphs that exist in both but have different content
     * @property addedIndices Indices of newly added paragraphs (beyond old document length)
     * @property deletedIndices Indices of paragraphs that were removed
     * @property newHashes Complete list of hashes for the new document version
     */
    data class ParagraphChanges(
        val changedIndices: List<Int>,
        val addedIndices: List<Int>,
        val deletedIndices: List<Int>,
        val newHashes: List<String>
    )
}