package bisman.thesis.qualcomm.domain.readers

import java.io.InputStream

/**
 * Abstract base class for document readers.
 *
 * Provides a common interface for extracting text content from various document formats.
 * Concrete implementations handle format-specific parsing (PDF, DOCX, etc.).
 *
 * Implementations should handle:
 * - Text extraction from document structure
 * - Encoding detection and conversion
 * - Error handling for malformed documents
 *
 * @see PDFReader for PDF document parsing
 * @see DOCXReader for Microsoft Word document parsing
 */
abstract class Reader {

    /**
     * Reads and extracts text content from a document input stream.
     *
     * Implementations should close the input stream after reading or document errors.
     *
     * @param inputStream The input stream containing the document data
     * @return Extracted text content, or null if reading fails
     */
    abstract fun readFromInputStream(inputStream: InputStream): String?
}
