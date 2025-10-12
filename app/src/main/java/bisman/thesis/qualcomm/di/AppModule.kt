package bisman.thesis.qualcomm.di

import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.ui.screens.chat.ChatViewModel
import bisman.thesis.qualcomm.ui.screens.docs.DocsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module defining application-wide dependencies.
 *
 * This module configures the dependency graph for the RAG application:
 *
 * **Singletons** (shared across app lifecycle):
 * - DocumentsDB: Repository for document metadata and content
 * - ChunksDB: Repository for document chunks and vector embeddings
 * - SentenceEmbeddingProvider: On-device embedding model (ONNX)
 *
 * **ViewModels** (lifecycle-aware, recreated per screen):
 * - ChatViewModel: Manages chat UI and RAG query pipeline
 * - DocsViewModel: Manages document management UI and processing
 *
 * Dependencies are resolved automatically via Koin's get() function.
 * The module is installed in ChatApplication.onCreate().
 *
 * @see ChatApplication for module initialization
 */
val appModule = module {
    // Singleton repositories
    single { DocumentsDB() }
    single { ChunksDB() }
    single { SentenceEmbeddingProvider(get()) }

    // ViewModels with automatic dependency injection
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { DocsViewModel(get(), get(), get()) }
}