package bisman.thesis.qualcomm.domain.readers

/**
 * Factory class for creating document readers based on file type.
 *
 * Provides a centralized way to obtain the appropriate Reader implementation
 * for a given document format. Currently supports PDF and Microsoft Word documents.
 */
class Readers {

    /**
     * Enumeration of supported document types.
     *
     * @property PDF Adobe PDF documents (.pdf)
     * @property MS_DOCX Microsoft Word documents (.docx, .doc)
     * @property UNKNOWN Unsupported or unrecognized document format
     */
    enum class DocumentType {
        PDF,
        MS_DOCX,
        UNKNOWN
    }

    companion object {

        /**
         * Returns the appropriate Reader implementation for the specified document type.
         *
         * @param docType The document type to get a reader for
         * @return A Reader instance capable of parsing the specified document type
         * @throws IllegalArgumentException if docType is UNKNOWN
         */
        fun getReaderForDocType(docType: DocumentType): Reader {
            return when (docType) {
                DocumentType.PDF -> PDFReader()
                DocumentType.MS_DOCX -> DOCXReader()
                DocumentType.UNKNOWN -> throw IllegalArgumentException("Unsupported document type.")
            }
        }
    }
}
