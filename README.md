# Qualcomm On-Device RAG Chat Application

A high-performance Android application implementing Retrieval Augmented Generation (RAG) using on-device AI models powered by Qualcomm's Neural Processing SDK. The app enables intelligent document Q&A without requiring cloud connectivity, leveraging Qualcomm's Hexagon Tensor Processor (HTP) for efficient inference.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [System Requirements](#system-requirements)
- [Project Structure](#project-structure)
- [Core Components](#core-components)
- [RAG Pipeline](#rag-pipeline)
- [Document Processing](#document-processing)
- [Database Schema](#database-schema)
- [Performance Optimization](#performance-optimization)
- [Setup & Installation](#setup--installation)
- [Usage Guide](#usage-guide)
- [API Documentation](#api-documentation)
- [Contributing](#contributing)

---

## Overview

This application demonstrates a complete RAG system running entirely on-device using Qualcomm Snapdragon processors. It combines:

- **On-device LLM**: Qualcomm Genie for text generation
- **Embedding Model**: all-MiniLM-L6-V2 (ONNX) for semantic search
- **Vector Database**: ObjectBox with HNSW indexing
- **Document Processing**: PDF and DOCX support with incremental updates
- **Folder Monitoring**: Automatic document sync with background service

### What is RAG?

Retrieval Augmented Generation enhances language model responses by:
1. Retrieving relevant context from a knowledge base
2. Augmenting the user query with retrieved information
3. Generating informed responses based on the enriched context

This approach enables the LLM to answer questions about specific documents without requiring fine-tuning.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                       │
│  ┌──────────────┐                          ┌──────────────┐    │
│  │  ChatScreen  │                          │  DocsScreen  │    │
│  └──────┬───────┘                          └──────┬───────┘    │
│         │                                          │             │
└─────────┼──────────────────────────────────────────┼─────────────┘
          │                                          │
┌─────────┼──────────────────────────────────────────┼─────────────┐
│         │         ViewModel Layer                  │             │
│  ┌──────▼───────┐                          ┌──────▼───────┐    │
│  │ChatViewModel │                          │DocsViewModel │    │
│  │              │                          │              │    │
│  │ • RAG Query  │                          │ • Doc Upload │    │
│  │ • LLM Prompt │                          │ • Chunking   │    │
│  │ • Streaming  │                          │ • Embedding  │    │
│  └──────┬───────┘                          └──────┬───────┘    │
└─────────┼──────────────────────────────────────────┼─────────────┘
          │                                          │
┌─────────┼──────────────────────────────────────────┼─────────────┐
│         │         Domain Layer                     │             │
│  ┌──────▼──────────────────┐              ┌───────▼────────┐   │
│  │ SentenceEmbeddingProvider│              │   Readers      │   │
│  │  (all-MiniLM-L6-V2)     │              │  • PDFReader   │   │
│  └─────────────────────────┘              │  • DOCXReader  │   │
│                                            └────────────────┘   │
│  ┌─────────────────────────┐              ┌────────────────┐   │
│  │    GenieWrapper         │              │ WhiteSpace     │   │
│  │  (Qualcomm LLM SDK)     │              │   Splitter     │   │
│  └─────────────────────────┘              └────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
          │                                          │
┌─────────┼──────────────────────────────────────────┼─────────────┐
│         │          Data Layer                      │             │
│  ┌──────▼───────┐                          ┌──────▼───────┐    │
│  │   ChunksDB   │                          │ DocumentsDB  │    │
│  │              │                          │              │    │
│  │ • HNSW Index │                          │ • Full Text  │    │
│  │ • Embeddings │                          │ • Metadata   │    │
│  │ • Vec Search │                          │ • Hashes     │    │
│  └──────────────┘                          └──────────────┘    │
│                                                                  │
│                   ObjectBox Database                            │
└─────────────────────────────────────────────────────────────────┘
          │
┌─────────▼─────────────────────────────────────────────────────┐
│                    Background Services                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │         DocumentSyncService (Foreground Service)         │ │
│  │                                                           │ │
│  │  • FileObserver for real-time monitoring                │ │
│  │  • Periodic sync (60s interval)                         │ │
│  │  • Incremental updates (paragraph-level change detect)  │ │
│  │  • Lazy model initialization                            │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Features

### On-Device AI
- **No Cloud Dependency**: All inference happens locally
- **Privacy First**: Documents never leave the device
- **Qualcomm Acceleration**: Leverages HTP for efficient inference
- **Optimized Configuration**: Configured specifically for Snapdragon 8 Gen 3

### Document Management
- **Multi-Format Support**: PDF, DOCX, DOC
- **Folder Watching**: Automatic processing of new/modified files
- **Incremental Updates**: Only re-processes changed paragraphs
- **Background Sync**: Foreground service for continuous monitoring

### Semantic Search
- **Vector Embeddings**: 384-dimensional sentence transformers
- **HNSW Indexing**: Fast approximate nearest neighbor search
- **Batch Processing**: Optimized chunk insertion (30 per batch)
- **Overlap Strategy**: 50-character overlap for context preservation

### Chat Interface
- **Streaming Responses**: Token-by-token LLM output
- **Context Display**: Shows retrieved document chunks
- **Performance Metrics**: Tracks RAG time, TTFT, tokens/sec
- **Conversation History**: Maintains chat state

### Performance
- **Lazy Initialization**: Models loaded on-demand
- **Resource Management**: Automatic model cleanup
- **Coroutine-Based**: Non-blocking background operations
- **Efficient Batching**: Minimizes database transactions

---

## Technology Stack

### Core Framework
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with ViewModels
- **DI**: Koin for dependency injection
- **Concurrency**: Kotlin Coroutines + Flow

### AI/ML
- **LLM**: Qualcomm Genie (via Qualcomm AI Hub)
- **Embeddings**: all-MiniLM-L6-V2 (ONNX Runtime)
- **Acceleration**: Qualcomm HTP (Hexagon Tensor Processor)

### Data
- **Database**: ObjectBox (NoSQL with vector support)
- **Indexing**: HNSW for vector similarity search
- **Document Parsing**: Apache POI (DOCX), PDFBox (PDF)

### Services
- **Background**: Foreground Service for document sync
- **File Monitoring**: Android FileObserver
- **Persistence**: SharedPreferences for config

---

## System Requirements

### Minimum Requirements
- **Android Version**: Android 11+ (API 30+)
- **Processor**: Qualcomm Snapdragon 8 Gen 3 (SM8650)
- **RAM**: 8GB minimum (12GB recommended)
- **Storage**: 5GB free space for models
- **Display**: 1080p minimum

### Supported SoCs
- **SM8650** (Snapdragon 8 Gen 3)

### Model Files Required
- `llm/` - Genie LLM model files (QNN format)
- `all-MiniLM-L6-V2.onnx` - Sentence transformer model
- `tokenizer.json` - BERT tokenizer
- `qualcomm-snapdragon-8-gen3.json` - HTP configuration for Snapdragon 8 Gen 3

---

## Project Structure

```
app/src/main/java/bisman/thesis/qualcomm/
│
├── UI Layer
│   ├── screens/
│   │   ├── chat/
│   │   │   ├── ChatScreen.kt          # Chat UI composable
│   │   │   └── ChatViewModel.kt       # RAG query logic
│   │   └── docs/
│   │       ├── DocsScreen.kt          # Document management UI
│   │       └── DocsViewModel.kt       # Document processing
│   ├── components/                    # Reusable UI components
│   └── theme/                         # Material Design theme
│
├── Domain Layer
│   ├── embeddings/
│   │   └── SentenceEmbeddingProvider.kt  # ONNX embedding model
│   ├── readers/
│   │   ├── Reader.kt                  # Abstract document reader
│   │   ├── PDFReader.kt               # PDF parsing
│   │   └── DOCXReader.kt              # DOCX parsing
│   ├── splitters/
│   │   └── WhiteSpaceSplitter.kt      # Document chunking
│   └── watcher/
│       └── DocumentWatcher.kt         # Folder monitoring
│
├── Data Layer
│   ├── DataModels.kt                  # ObjectBox entities
│   ├── DocumentsDB.kt                 # Document repository
│   ├── ChunksDB.kt                    # Vector chunk repository
│   └── ObjectBoxStore.kt              # Database singleton
│
├── Services
│   ├── DocumentSyncService.kt         # Background sync service
│   └── BootReceiver.kt                # Auto-start on boot
│
├── Utilities
│   ├── ContentHasher.kt               # SHA-256 paragraph hashing
│   └── DocumentProcessingState.kt     # Progress tracking
│
├── Dependency Injection
│   └── di/AppModule.kt                # Koin module
│
└── Application
    ├── ChatApplication.kt             # App class
    └── MainComposeActivity.kt         # Entry point
```

---

## Core Components

### 1. ChatViewModel

**Purpose**: Orchestrates the RAG query pipeline and LLM inference.

**Key Responsibilities**:
- Encode user queries into embeddings
- Retrieve top-K similar chunks from vector database
- Build augmented prompts with context
- Stream LLM responses token-by-token
- Track performance metrics (RAG time, TTFT, tokens/sec)

**Performance Metrics**:
```kotlin
// Example output
RAG Retrieval: 245ms
Time to First Token: 1000-4000ms
Total Inference: 3421ms
Total Tokens: 87
Tokens/Second: 25.43
Total Time: 3666ms
```

**Code Reference**: `ui/screens/chat/ChatViewModel.kt:137-272`

### 2. DocsViewModel

**Purpose**: Manages document upload, processing, and folder watching.

**Key Responsibilities**:
- Accept documents from file picker or URLs
- Extract text using appropriate readers
- Split documents into overlapping chunks (500 chars, 50 overlap)
- Generate embeddings for each chunk
- Batch insert into database (30 chunks per transaction)
- Monitor folder for changes

**Processing Pipeline**:
```
Document Upload → Text Extraction → Chunking → Embedding → Database Storage
                                      ↓
                            (500 char chunks, 50 char overlap)
```

**Code Reference**: `ui/screens/docs/DocsViewModel.kt:134-198`

### 3. SentenceEmbeddingProvider

**Purpose**: Manages the ONNX sentence transformer model lifecycle.

**Architecture**:
- **Lazy Initialization**: Model loaded on first use
- **Thread Safety**: Double-checked locking for concurrent access
- **Resource Management**: Explicit release() for cleanup
- **Model**: all-MiniLM-L6-V2 (384-dimensional embeddings)

**Usage**:
```kotlin
// Lazy init pattern
sentenceEncoder.ensureInitialized()  // Loads model if not loaded
val embedding = sentenceEncoder.encodeText("Your text here")
// Later when done
sentenceEncoder.release()  // Frees DSP/NPU resources
```

**Code Reference**: `domain/embeddings/SentenceEmbeddingProvider.kt:48-97`

### 4. ChunksDB

**Purpose**: Vector database for semantic similarity search.

**Key Features**:
- **HNSW Indexing**: Hierarchical Navigable Small World graphs
- **Batch Insertion**: 30 chunks per transaction for performance
- **Vector Search**: Approximate nearest neighbor queries
- **Paragraph Tracking**: For incremental updates

**Search Configuration**:
```kotlin
// Search for top-3 similar chunks
// Uses ef=25 for quality/performance tradeoff
val results = chunksDB.getSimilarChunks(
    queryEmbedding = embedding,
    n = 3
)
```

**Code Reference**: `data/ChunksDB.kt:74-108`

### 5. DocumentSyncService

**Purpose**: Background service for continuous document monitoring.

**Architecture**:
- **Foreground Service**: Persistent notification, won't be killed
- **Dual Monitoring**: FileObserver (real-time) + Timer (periodic sync)
- **Incremental Updates**: Paragraph-level change detection
- **Lazy Model Init**: Only loads embeddings when needed

**Sync Strategy**:
1. **Real-time**: FileObserver detects CREATE/MODIFY/DELETE instantly
2. **Periodic**: Full folder sync every 60 seconds (catches missed events)
3. **Smart Processing**: Compares paragraph hashes to find changes
4. **Resource Efficient**: Releases models after batch processing

**Code Reference**: `services/DocumentSyncService.kt:31-595`

### 6. ContentHasher

**Purpose**: Enables incremental document updates via paragraph hashing.

**Algorithm**:
```
Document → Split into Paragraphs → SHA-256 Hash Each → Store JSON Array

On Modification:
New Document → Hash Paragraphs → Compare with Old Hashes
              → Identify: Changed, Added, Deleted paragraphs
              → Re-process only affected paragraphs
```

**Code Reference**: `utils/ContentHasher.kt:78-120`

---

## RAG Pipeline

### Query Flow

```
User Query
    │
    ▼
┌───────────────────────────────────────────────────────┐
│ 1. Encode Query                                       │
│    • Use SentenceEmbeddingProvider                   │
│    • Generate 384-dim embedding vector               │
└───────────────────────────┬───────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────┐
│ 2. Vector Similarity Search                          │
│    • ChunksDB.getSimilarChunks(embedding, n=3)       │
│    • HNSW index returns top-K chunks                 │
│    • Each chunk has: text, filename, similarity      │
└───────────────────────────┬───────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────┐
│ 3. Build Augmented Prompt                            │
│    Format:                                            │
│    "Context: [chunk1] [chunk2] [chunk3]              │
│                                                       │
│     Query: [user question]"                          │
└───────────────────────────┬───────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────┐
│ 4. LLM Inference                                      │
│    • GenieWrapper.getResponseForPrompt()             │
│    • Streams tokens via StringCallback               │
│    • Updates UI token-by-token                       │
└───────────────────────────┬───────────────────────────┘
                            │
                            ▼
                     Display Response
```

### Performance Considerations

**Embedding Generation**:
- Time: ~50-150ms per query (ONNX on HTP)
- Cached: Embedding model stays loaded during chat session
- Released: Automatic cleanup on app background

**Vector Search**:
- Time: ~20-100ms for 1000s of chunks (HNSW)
- Complexity: O(log n) approximate nearest neighbor
- Quality: ef=25 parameter balances speed vs accuracy

**LLM Inference**:
- TTFT: 1000-4000ms (Time to First Token)
- Throughput: 15-30 tokens/sec on Snapdragon 8 Gen 3
- Memory: ~4GB for LLM, shared with system

---

## Document Processing

### Supported Formats

| Format | Reader Class | Library | Features |
|--------|-------------|---------|----------|
| PDF | `PDFReader` | Apache PDFBox | Text extraction, multi-page |
| DOCX | `DOCXReader` | Apache POI | Text extraction, formatting ignored |
| DOC | `DOCXReader` | Apache POI | Legacy format support |

### Chunking Strategy

**Configuration**:
```kotlin
chunkSize = 500        // Maximum characters per chunk
chunkOverlap = 50      // Overlap between adjacent chunks
separatorParagraph = "\n\n"  // Paragraph delimiter
separator = " "        // Word delimiter
```

**Algorithm**:
1. Split document by paragraph boundaries (`\n\n`)
2. Within each paragraph, split at word boundaries
3. Build chunks up to 500 characters
4. Create overlap chunks between adjacent primary chunks
5. Track paragraph index for each chunk

**Example**:
```
Original Text (1200 chars):
"Paragraph 1 text..." (500 chars)
"Paragraph 2 text..." (700 chars)

Output Chunks:
[0-500]   → Chunk 1 (para 0)
[450-550] → Overlap (para 0-1)
[500-1000] → Chunk 2 (para 1)
[950-1200] → Chunk 3 (para 1)
```

**Code Reference**: `domain/splitters/WhiteSpaceSplitter.kt:26-89`

### Incremental Update Logic

**When a document is modified**:

1. **Read new content**: Extract text from file
2. **Split into paragraphs**: Use same `\n\n` delimiter
3. **Hash each paragraph**: SHA-256 for comparison
4. **Compare with stored hashes**: Position-based diff
5. **Identify changes**:
   - Changed: Different hash at same position
   - Added: New paragraphs beyond old length
   - Deleted: Old paragraphs missing in new version
6. **Selective re-processing**:
   - Delete chunks from changed/deleted paragraphs
   - Generate new chunks only for changed/added paragraphs
   - Generate embeddings only for new chunks
   - Batch insert new chunks
7. **Update document**: Store new text and hashes

**Code Reference**: `services/DocumentSyncService.kt:446-552`

---

## Database Schema

### ObjectBox Entities

#### Chunk Entity
```kotlin
@Entity
data class Chunk(
    @Id
    var chunkId: Long = 0,

    @Index
    var docId: Long = 0,                     // Foreign key to Document

    var docFileName: String = "",            // Denormalized for display

    var chunkData: String = "",              // The actual text content

    @HnswIndex(dimensions = 384)            // Vector index
    var chunkEmbedding: FloatArray = floatArrayOf(),

    @Index
    var paragraphIndex: Int = 0              // For incremental updates
)
```

**Indexes**:
- `chunkId`: Primary key (auto-generated)
- `docId`: For finding all chunks of a document
- `chunkEmbedding`: HNSW index for vector similarity
- `paragraphIndex`: For removing chunks by paragraph

**Storage**:
- Average chunk: ~500 chars + 384 floats (1.5KB)
- 1000 chunks ≈ 1.5MB

#### Document Entity
```kotlin
@Entity
data class Document(
    @Id
    var docId: Long = 0,

    var docText: String = "",                // Full document text

    var docFileName: String = "",

    var docAddedTime: Long = 0,              // Timestamp (millis)

    @Index
    var docFilePath: String = "",            // For folder watching

    var fileLastModified: Long = 0,          // Change detection

    var fileSize: Long = 0,                  // Change detection

    var paragraphHashes: String = ""         // JSON array of SHA-256
)
```

**Indexes**:
- `docId`: Primary key
- `docFilePath`: For quick lookups during folder sync

### Query Examples

**Find similar chunks**:
```kotlin
val results = chunksBox
    .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 25))
    .build()
    .findWithScores()
    .take(3)
```

**Get document by path**:
```kotlin
val document = docsBox
    .query(Document_.docFilePath.equal(filePath))
    .build()
    .findFirst()
```

**Remove chunks by document**:
```kotlin
val idsToRemove = chunksBox
    .query(Chunk_.docId.equal(docId))
    .build()
    .findIds()
chunksBox.removeByIds(idsToRemove)
```

**Code Reference**: `data/DataModels.kt:8-73`

---

## Performance Optimization

### Model Management

**Lazy Initialization**:
```kotlin
// Don't load at app start - load on first use
sentenceEncoder.ensureInitialized()  // Thread-safe lazy init
```

**Aggressive Cleanup**:
```kotlin
// Release immediately after processing to free DSP
sentenceEncoder.release()
System.gc()
System.runFinalization()
```

**Activity Lifecycle**:
```kotlin
override fun onStop() {
    // Release when app goes to background
    (application as? ChatApplication)?.releaseModels()
}

override fun onDestroy() {
    if (isFinishing) {
        // Kill process for clean restart
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
```

### Batch Processing

**Chunk Insertion** (30 per batch):
```kotlin
chunks.chunked(30).forEach { batch ->
    chunksBox.put(batch)  // Single transaction
}
```

### Coroutine Usage

**Non-blocking Operations**:
```kotlin
viewModelScope.launch {
    withContext(Dispatchers.IO) {
        // Heavy work on background thread
        val embedding = sentenceEncoder.encodeText(text)
    }
    // UI update on main thread
    _uiState.value = result
}
```

**Structured Concurrency**:
```kotlin
private val watcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun cleanup() {
    watcherScope.cancel()  // Cancels all child jobs
}
```

### Database Optimization

**Lazy Box Initialization**:
```kotlin
private val chunksBox: Box<Chunk> by lazy {
    ObjectBoxStore.store.boxFor(Chunk::class.java)
}
```

**HNSW Parameters**:
```kotlin
@HnswIndex(
    dimensions = 384,
    // Defaults: M=16, efConstruction=200, efSearch=100
)
```

**Query Optimization**:
```kotlin
// Use maxResultCount (ef parameter) for quality
.nearestNeighbors(embedding, maxResultCount = 25)  // Search 25
.build()
.findWithScores()
.take(3)  // Return top 3
```

---

## Setup & Installation

### Prerequisites

1. **Android Studio**: Hedgehog (2023.1.1) or newer
2. **Qualcomm Device**: Snapdragon 8 Gen 3 (SM8650)
3. **Model Files**: Download from Qualcomm AI Hub

### Step 1: Clone Repository

```bash
git clone <repository-url>
cd QualcommThesisApp
```

### Step 2: Configure Model Files

Place the following files in `app/src/main/assets/`:

```
assets/
├── llm/
│   ├── genie.bin                    # LLM model
│   └── (additional QNN model files)
├── all-MiniLM-L6-V2.onnx           # Embedding model
├── tokenizer.json                   # BERT tokenizer
└── qualcomm-snapdragon-8-gen3.json  # HTP configuration for 8 Gen 3
```

### Step 3: Configure Gradle

Update `local.properties`:
```properties
sdk.dir=/path/to/Android/sdk
ndk.dir=/path/to/Android/ndk
```

### Step 4: Build & Run

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Run
adb shell am start -n bisman.thesis.qualcomm/.MainActivity
```

### Step 5: First Launch Setup

1. Grant storage permissions when prompted
2. Navigate to "Documents" tab
3. Upload a PDF or DOCX file (or select folder to watch)
4. Wait for processing to complete
5. Go to "Chat" tab and ask questions

---

## Usage Guide

### Adding Documents

**Method 1: File Picker**
1. Open "Documents" tab
2. Tap "Upload Document"
3. Select PDF or DOCX file
4. Wait for processing (progress bar shown)

**Method 2: Folder Watching**
1. Open "Documents" tab
2. Tap "Select Folder to Watch"
3. Choose folder with documents
4. Toggle "Enable Background Sync"
5. Documents automatically processed when added/modified

### Asking Questions

1. Open "Chat" tab
2. Type question in text field
3. Tap send button
4. Wait for streaming response
5. View retrieved context below response

**Tips**:
- Ask specific questions about document content
- Reference particular sections or topics
- Try follow-up questions for context
- Check retrieved chunks to see what context was used

### Managing Documents

**View Documents**:
- Documents screen shows list of all uploaded docs
- Shows filename, upload date, chunk count

**Delete Documents**:
- Swipe left on document
- Tap delete button
- Removes document and all chunks

**Background Sync**:
- Enable for automatic monitoring
- Service runs with persistent notification
- Handles file changes automatically
- Can disable anytime from notification

---

## API Documentation

### Key Classes

#### ChatViewModel

**Purpose**: Manages chat UI state and RAG pipeline.

**Methods**:

```kotlin
/**
 * Processes user query using RAG and generates response.
 *
 * @param query User's question or prompt
 */
fun getAnswer(query: String)

/**
 * Checks if any documents are available for RAG.
 *
 * @return true if documents exist, false otherwise
 */
fun checkNumDocuments(): Boolean
```

**State Flows**:

```kotlin
val questionState: StateFlow<String>              // Current question
val responseState: StateFlow<String>              // Streaming response
val isGeneratingResponseState: StateFlow<Boolean> // Loading indicator
val retrievedContextListState: StateFlow<List<RetrievedContext>> // Context chunks
```

#### DocsViewModel

**Purpose**: Manages document operations and folder watching.

**Methods**:

```kotlin
/**
 * Adds document to RAG system.
 *
 * @param inputStream Document data stream
 * @param fileName Name of the file
 * @param documentType PDF, DOCX, or UNKNOWN
 * @param filePath Optional file system path
 * @param fileLastModified Optional timestamp for change detection
 * @param fileSize Optional size in bytes
 */
suspend fun addDocument(
    inputStream: InputStream,
    fileName: String,
    documentType: Readers.DocumentType,
    filePath: String = "",
    fileLastModified: Long = 0,
    fileSize: Long = 0
)

/**
 * Starts background document sync service.
 *
 * @param context Android context
 */
fun startSyncService(context: Context)

/**
 * Stops background document sync service.
 *
 * @param context Android context
 */
fun stopSyncService(context: Context)
```

**State Flows**:

```kotlin
fun getAllDocuments(): Flow<List<Document>>  // Reactive document list
fun getDocsCount(): Long                     // Total document count
```

#### SentenceEmbeddingProvider

**Purpose**: Manages ONNX embedding model.

**Methods**:

```kotlin
/**
 * Ensures model is loaded (lazy initialization).
 * Thread-safe, multiple calls are safe.
 */
fun ensureInitialized()

/**
 * Encodes text into 384-dim embedding vector.
 *
 * @param text Text to encode
 * @return Float array of 384 dimensions
 */
fun encodeText(text: String): FloatArray

/**
 * Releases model and frees resources.
 * Should be called when done to free DSP/NPU.
 */
fun release()
```

#### ChunksDB

**Purpose**: Vector database for semantic search.

**Methods**:

```kotlin
/**
 * Adds single chunk to database.
 *
 * @param chunk Chunk with text and embedding
 */
fun addChunk(chunk: Chunk)

/**
 * Adds multiple chunks in batches.
 *
 * @param chunks List of chunks to insert
 * @param batchSize Chunks per transaction (default: 30)
 */
fun addChunksBatch(chunks: List<Chunk>, batchSize: Int = 30)

/**
 * Performs vector similarity search.
 *
 * @param queryEmbedding Query embedding vector
 * @param n Number of results to return
 * @return List of (score, chunk) pairs
 */
fun getSimilarChunks(
    queryEmbedding: FloatArray,
    n: Int = 5
): List<Pair<Float, Chunk>>

/**
 * Removes all chunks for a document.
 *
 * @param docId Document ID
 */
fun removeChunks(docId: Long)

/**
 * Removes chunks from specific paragraph.
 * Used for incremental updates.
 *
 * @param docId Document ID
 * @param paragraphIndex Paragraph index
 */
fun removeChunksByParagraph(docId: Long, paragraphIndex: Int)
```

#### ContentHasher

**Purpose**: Paragraph hashing for change detection.

**Methods**:

```kotlin
/**
 * Computes SHA-256 hash of text.
 *
 * @param text Text to hash
 * @return Hexadecimal hash string
 */
fun hashText(text: String): String

/**
 * Hashes all paragraphs and returns JSON array.
 *
 * @param paragraphs List of paragraph strings
 * @return JSON array string of hashes
 */
fun hashParagraphs(paragraphs: List<String>): String

/**
 * Compares new paragraphs against old hashes.
 *
 * @param newParagraphs New paragraph strings
 * @param oldHashes Old paragraph hashes
 * @return ParagraphChanges with changed/added/deleted indices
 */
fun findChangedParagraphs(
    newParagraphs: List<String>,
    oldHashes: List<String>
): ParagraphChanges
```

---

## Contributing

### Code Style

- **Language**: Kotlin
- **Formatting**: Android Studio default
- **Documentation**: KDoc for all public APIs
- **Testing**: Unit tests for business logic

### Documentation Standards

Follow KDoc conventions:

```kotlin
/**
 * Brief description of the class/function.
 *
 * Detailed explanation including:
 * - Purpose and responsibilities
 * - Architecture or algorithm details
 * - Usage examples if helpful
 *
 * @param paramName Description of parameter
 * @return Description of return value
 * @throws ExceptionType When this exception is thrown
 * @see RelatedClass for related functionality
 */
```

### Git Workflow

1. Create feature branch: `git checkout -b feature/your-feature`
2. Commit with descriptive messages
3. Push and create pull request
4. Ensure all tests pass
5. Request code review

### Performance Guidelines

- Profile before optimizing
- Use coroutines for async work
- Release ML models when not in use
- Batch database operations
- Log performance metrics

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- **Qualcomm**: For AI Hub SDK and neural processing support
- **ObjectBox**: For high-performance vector database
- **Hugging Face**: For all-MiniLM-L6-V2 model
- **Apache**: For POI (DOCX) and PDFBox (PDF) libraries

---

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Contact: bismansahni@outlook.com
