# System Architecture Diagrams

## System Component Architecture

```mermaid
graph TB
    subgraph "UI Layer"
        MainActivity[MainComposeActivity<br/>- Model path initialization<br/>- Static model config]
        ChatScreen[ChatScreen]
        DocsScreen[DocsScreen<br/>- Processing indicators]
        ChatVM[ChatViewModel<br/>- Thread-safe Genie access]
        DocsVM[DocsViewModel]
    end

    subgraph "Background Service"
        DocSyncService[DocumentSyncService<br/>- File watching<br/>- Periodic sync<br/>- Progress tracking]
    end

    subgraph "Domain Layer"
        SentenceEncoder[SentenceEmbeddingProvider<br/>- ONNX Runtime<br/>- DSP/GPU acceleration<br/>- On-demand loading]
        Readers[Document Readers<br/>- PDF Reader<br/>- DOCX Reader]
        Splitter[WhiteSpaceSplitter<br/>- Paragraph tracking<br/>- Chunk creation]
        Hasher[ContentHasher<br/>- SHA-256 hashing<br/>- Change detection<br/>- Paragraph-level diffing]
    end

    subgraph "State Management"
        ProcState[DocumentProcessingState<br/>- Progress tracking<br/>- Processing status<br/>- StateFlow updates]
    end

    subgraph "Data Layer"
        DocsDB[(DocumentsDB<br/>- Document metadata<br/>- Paragraph hashes)]
        ChunksDB[(ChunksDB<br/>- Chunk data<br/>- Vector embeddings<br/>- HNSW index)]
    end

    subgraph "LLM Layer"
        GenieWrapper[GenieWrapper<br/>- Singleton instance<br/>- Thread-safe access<br/>- Qualcomm AI Hub<br/>- On-device LLM]
    end

    subgraph "File System"
        WatchedFolder[Watched Folder<br/>- PDF/DOCX files]
    end

    %% UI interactions
    ChatScreen --> ChatVM
    DocsScreen --> DocsVM
    MainActivity --> GenieWrapper

    %% Chat flow
    ChatVM --> SentenceEncoder
    ChatVM --> ChunksDB
    ChatVM --> GenieWrapper

    %% Document management
    DocsVM --> DocsDB
    DocsVM --> ChunksDB
    DocsVM --> DocSyncService
    DocsVM --> ProcState
    DocsScreen --> ProcState

    %% Background service flow
    DocSyncService --> WatchedFolder
    DocSyncService --> Readers
    DocSyncService --> Splitter
    DocSyncService --> Hasher
    DocSyncService --> SentenceEncoder
    DocSyncService --> DocsDB
    DocSyncService --> ChunksDB
    DocSyncService --> ProcState

    %% Data relationships
    DocsDB -.->|1:many| ChunksDB
    ProcState -.->|monitors| DocSyncService
```

## Sequence Diagram - Chat Query Flow

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ChatVM as ChatViewModel
    participant Encoder as SentenceEncoder
    participant ChunksDB
    participant Genie as GenieWrapper (Singleton)

    User->>ChatScreen: Ask question
    ChatScreen->>ChatVM: getAnswer(query)
    ChatVM->>ChatVM: Start performance timer

    alt Has documents
        ChatVM->>Encoder: encodeText(query)
        Encoder-->>ChatVM: query embedding
        ChatVM->>ChunksDB: getSimilarChunks(embedding, n=3)
        ChunksDB-->>ChatVM: Similar chunks (HNSW search)
        ChatVM->>ChatVM: Build context from chunks
        Note over ChatVM: Log RAG retrieval time
    end

    ChatVM->>ChatVM: Acquire genieLock
    Note over ChatVM: Thread-safe access
    ChatVM->>Genie: getResponseForPrompt(context + query)
    Genie-->>ChatVM: First token
    Note over ChatVM: Log TTFT (Time to First Token)
    ChatVM->>ChatScreen: Update UI with token

    loop For each token
        Genie-->>ChatVM: Next token
        ChatVM->>ChatScreen: Append to response
    end

    ChatVM->>ChatVM: Release genieLock
    Note over ChatVM: Log performance metrics
    ChatVM->>ChatScreen: Generation complete
```

## Sequence Diagram - Document Processing Flow

```mermaid
sequenceDiagram
    participant FileSystem
    participant DocSync as DocumentSyncService
    participant ProcState as DocumentProcessingState
    participant Hasher as ContentHasher
    participant Splitter as WhiteSpaceSplitter
    participant Encoder as SentenceEncoder
    participant DocsDB
    participant ChunksDB

    FileSystem->>DocSync: File change detected
    DocSync->>DocSync: Check if valid document
    DocSync->>ProcState: startProcessing(filePath)

    alt New document
        DocSync->>FileSystem: Read file
        DocSync->>ProcState: updateProgress(filePath, 10%)
        DocSync->>Splitter: getParagraphs(text)
        Splitter-->>DocSync: Paragraphs list
        DocSync->>Hasher: hashParagraphs(paragraphs)
        Hasher-->>DocSync: Paragraph hashes
        DocSync->>ProcState: updateProgress(filePath, 30%)
        DocSync->>Encoder: ensureInitialized()

        DocSync->>Splitter: createChunksWithParagraphTracking()
        Splitter-->>DocSync: Chunks with paragraph indices

        loop For each chunk
            DocSync->>Encoder: encodeText(chunk)
            Encoder-->>DocSync: Embedding vector
            DocSync->>ChunksDB: addChunk(chunk + embedding + paragraphIndex)
            DocSync->>ProcState: updateProgress(filePath, 30% + chunk%)
        end

        DocSync->>DocsDB: addDocument(doc + paragraphHashes)
        DocSync->>Encoder: release()
        DocSync->>ProcState: finishProcessing(filePath)
    else Modified document
        DocSync->>FileSystem: Read new content
        DocSync->>ProcState: updateProgress(filePath, 10%)
        DocSync->>Splitter: getParagraphs(newText)
        DocSync->>Hasher: findChangedParagraphs(new, oldHashes)
        Hasher-->>DocSync: Changed paragraph indices
        DocSync->>ProcState: updateProgress(filePath, 30%)

        DocSync->>Encoder: ensureInitialized()

        loop For changed paragraphs only
            DocSync->>ChunksDB: removeChunksByParagraph(docId, index)
            DocSync->>Splitter: createChunksWithParagraphTracking()
            DocSync->>Encoder: encodeText(chunk)
            Encoder-->>DocSync: Embedding vector
            DocSync->>ChunksDB: addChunk(chunk + embedding + paragraphIndex)
            DocSync->>ProcState: updateProgress(filePath, 30% + chunk%)
        end

        DocSync->>DocsDB: updateDocument(doc + newHashes)
        DocSync->>Encoder: release()
        DocSync->>ProcState: finishProcessing(filePath)
    end
```

## Sequence Diagram - Background Service Lifecycle

```mermaid
sequenceDiagram
    participant User
    participant DocsScreen
    participant DocsVM as DocsViewModel
    participant DocSync as DocumentSyncService
    participant FileObserver
    participant Timer

    User->>DocsScreen: Select folder
    DocsScreen->>DocsVM: selectFolder(path)
    DocsVM->>DocSync: startService(folderPath)

    DocSync->>DocSync: onCreate()
    DocSync->>DocSync: startForeground()
    DocSync->>FileObserver: startWatching(folder)
    DocSync->>DocSync: syncFolderWithDatabase()
    DocSync->>Timer: scheduleAtFixedRate(60s)

    loop File monitoring
        FileObserver->>DocSync: onEvent(CREATE/MODIFY/DELETE)
        DocSync->>DocSync: handleFileEvent()
    end

    loop Periodic sync
        Timer->>DocSync: run()
        DocSync->>DocSync: syncFolderWithDatabase()
    end

    User->>DocsScreen: Stop monitoring
    DocsScreen->>DocSync: stopService()
    DocSync->>FileObserver: stopWatching()
    DocSync->>Timer: cancel()
    DocSync->>DocSync: onDestroy()
```

## Data Flow Diagram

```mermaid
flowchart LR
    subgraph Input
        PDF[PDF Files]
        DOCX[DOCX Files]
        Query[User Query]
    end

    subgraph Processing
        Reader[Document Reader]
        Splitter[Text Splitter]
        Hasher[Content Hasher]
        Encoder[Sentence Encoder]
    end

    subgraph Storage
        DocDB[(Document DB)]
        ChunkDB[(Chunks DB)]
        VectorIndex[(HNSW Index)]
    end

    subgraph Output
        Context[Retrieved Context]
        Response[LLM Response]
    end

    PDF --> Reader
    DOCX --> Reader
    Reader --> Splitter
    Splitter --> Hasher
    Splitter --> Encoder
    Hasher --> DocDB
    Encoder --> VectorIndex
    Encoder --> ChunkDB

    Query --> Encoder
    Encoder --> VectorIndex
    VectorIndex --> Context
    Context --> Response
    Query --> Response
```

## Key Features

### 1. Smart Chunking with Paragraph Hashing
- Documents split into paragraphs
- Each paragraph gets SHA-256 hash
- Only changed paragraphs re-processed on updates
- Preserves embeddings for unchanged content
- ContentHasher performs paragraph-level diffing

### 2. Resource Management
- GenieWrapper managed as singleton instance
- Thread-safe access via synchronized locks
- Sentence encoder (ONNX) loads on-demand
- Released after processing to free GPU/DSP
- Prevents memory exhaustion
- Model paths initialized statically in MainComposeActivity

### 3. Background Processing
- FileObserver watches for file changes
- Periodic sync every 60 seconds
- Runs as foreground service
- Real-time progress tracking via DocumentProcessingState
- Visual indicators for processing status in UI

### 4. Performance Metrics
- Time to First Token (TTFT)
- RAG retrieval time
- Total inference time
- Tokens per second
- End-to-end latency

### 5. Vector Search
- HNSW index for fast similarity search
- 384-dimensional embeddings
- Top-k retrieval for context

### 6. On-Device Processing
- All processing happens locally
- Uses Qualcomm DSP/GPU acceleration
- No cloud dependencies
- Optimized for Snapdragon 8 Gen2/Gen3/Elite SoCs