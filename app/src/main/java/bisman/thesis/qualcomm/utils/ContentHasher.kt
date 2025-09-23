package bisman.thesis.qualcomm.utils

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ContentHasher {
    companion object {
        private const val HASH_ALGORITHM = "SHA-256"

        fun hashText(text: String): String {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hashBytes = digest.digest(text.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        fun hashParagraphs(paragraphs: List<String>): String {
            val jsonArray = JSONArray()
            paragraphs.forEach { paragraph ->
                val hash = hashText(paragraph)
                jsonArray.put(hash)
            }
            return jsonArray.toString()
        }

        fun parseParagraphHashes(hashesJson: String): List<String> {
            if (hashesJson.isEmpty()) return emptyList()

            return try {
                val jsonArray = JSONArray(hashesJson)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

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

    data class ParagraphChanges(
        val changedIndices: List<Int>,
        val addedIndices: List<Int>,
        val deletedIndices: List<Int>,
        val newHashes: List<String>
    )
}