package bisman.thesis.qualcomm.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DocumentProcessingState {
    // Map of document file path to processing status
    private val _processingDocuments = MutableStateFlow<Set<String>>(emptySet())
    val processingDocuments: StateFlow<Set<String>> = _processingDocuments.asStateFlow()

    // Map of document file path to progress (0-100)
    private val _processingProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val processingProgress: StateFlow<Map<String, Int>> = _processingProgress.asStateFlow()

    fun startProcessing(filePath: String) {
        _processingDocuments.value = _processingDocuments.value + filePath
        _processingProgress.value = _processingProgress.value + (filePath to 0)
    }

    fun updateProgress(filePath: String, progress: Int) {
        _processingProgress.value = _processingProgress.value + (filePath to progress)
    }

    fun finishProcessing(filePath: String) {
        _processingDocuments.value = _processingDocuments.value - filePath
        _processingProgress.value = _processingProgress.value - filePath
    }

    fun isProcessing(filePath: String): Boolean {
        return _processingDocuments.value.contains(filePath)
    }
}