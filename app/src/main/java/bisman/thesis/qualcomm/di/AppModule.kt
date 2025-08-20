package bisman.thesis.qualcomm.di

import bisman.thesis.qualcomm.data.ChunksDB
import bisman.thesis.qualcomm.data.DocumentsDB
import bisman.thesis.qualcomm.domain.embeddings.SentenceEmbeddingProvider
import bisman.thesis.qualcomm.ui.screens.chat.ChatViewModel
import bisman.thesis.qualcomm.ui.screens.docs.DocsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DocumentsDB() }
    single { ChunksDB() }
    single { SentenceEmbeddingProvider(get()) }
    
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { DocsViewModel(get(), get(), get()) }
}