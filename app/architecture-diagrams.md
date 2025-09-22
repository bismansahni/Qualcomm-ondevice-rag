# System Architecture Diagrams

## System Component Architecture

```mermaid
graph TB
    subgraph "UI Layer"
        MainActivity[MainActivity]
        ChatScreen[ChatScreen]
        DocsScreen[DocsScreen]
        ChatVM[ChatViewModel]
        DocsVM[DocsViewModel]
    end

    subgraph "Background Service"
        DocSyncService[DocumentSyncService<br/>- File watching<br/>- Periodic sync]
    end

    subgraph "Domain Layer"
        SentenceEncoder[SentenceEmbeddingProvider<br/>- ONNX Runtime<br/>- DSP/GPU acceleration]
        Readers[Document Readers<br/>- PDF Reader<br/>- DOCX Reader]
        Splitter[WhiteSpaceSplitter<br/>- Paragraph tracking<br/>- Chunk creation]
        Hasher[ContentHasher<br/>- SHA-256 hashing<br/>- Change detection]
    end

    subgraph "Data Layer"
        DocsDB[(DocumentsDB<br/>- Document metadata<br/>- Paragraph hashes)]
        ChunksDB[(ChunksDB<br/>- Chunk data<br/>- Vector embeddings<br/>- HNSW index)]
    end

    subgraph "LLM Layer"
        GenieWrapper[GenieWrapper<br/>- Qualcomm AI Hub<br/>- On-device LLM]
    end

    subgraph "File System"
        WatchedFolder[Watched Folder<br/>- PDF/DOCX files]
    end

    %% UI interactions
    ChatScreen --> ChatVM
    DocsScreen --> DocsVM

    %% Chat flow
    ChatVM --> SentenceEncoder
    ChatVM --> ChunksDB
    ChatVM --> GenieWrapper

    %% Document management
    DocsVM --> DocsDB
    DocsVM --> ChunksDB
    DocsVM --> DocSyncService

    %% Background service flow
    DocSyncService --> WatchedFolder
    DocSyncService --> Readers
    DocSyncService --> Splitter
    DocSyncService --> Hasher
    DocSyncService --> SentenceEncoder
    DocSyncService --> DocsDB
    DocSyncService --> ChunksDB

    %% Data relationships
    DocsDB -.->|1:many| ChunksDB
```

## Sequence Diagram - Chat Query Flow

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ChatVM as ChatViewModel
    participant Encoder as SentenceEncoder
    participant ChunksDB
    participant Genie as GenieWrapper

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

    ChatVM->>Genie: getResponseForPrompt(context + query)
    Genie-->>ChatVM: First token
    Note over ChatVM: Log TTFT (Time to First Token)
    ChatVM->>ChatScreen: Update UI with token

    loop For each token
        Genie-->>ChatVM: Next token
        ChatVM->>ChatScreen: Append to response
    end

    Note over ChatVM: Log performance metrics
    ChatVM->>ChatScreen: Generation complete
```

## Sequence Diagram - Document Processing Flow

```mermaid
sequenceDiagram
    participant FileSystem
    participant DocSync as DocumentSyncService
    participant Hasher as ContentHasher
    participant Splitter as WhiteSpaceSplitter
    participant Encoder as SentenceEncoder
    participant DocsDB
    participant ChunksDB

    FileSystem->>DocSync: File change detected
    DocSync->>DocSync: Check if valid document

    alt New document
        DocSync->>FileSystem: Read file
        DocSync->>Splitter: getParagraphs(text)
        Splitter-->>DocSync: Paragraphs list
        DocSync->>Hasher: hashParagraphs(paragraphs)
        Hasher-->>DocSync: Paragraph hashes
        DocSync->>Encoder: ensureInitialized()

        DocSync->>Splitter: createChunksWithParagraphTracking()
        Splitter-->>DocSync: Chunks with paragraph indices

        loop For each chunk
            DocSync->>Encoder: encodeText(chunk)
            Encoder-->>DocSync: Embedding vector
            DocSync->>ChunksDB: addChunk(chunk + embedding + paragraphIndex)
        end

        DocSync->>DocsDB: addDocument(doc + paragraphHashes)
        DocSync->>Encoder: release()
    else Modified document
        DocSync->>FileSystem: Read new content
        DocSync->>Splitter: getParagraphs(newText)
        DocSync->>Hasher: findChangedParagraphs(new, oldHashes)
        Hasher-->>DocSync: Changed paragraph indices

        DocSync->>Encoder: ensureInitialized()

        loop For changed paragraphs only
            DocSync->>ChunksDB: removeChunksByParagraph(docId, index)
            DocSync->>Splitter: createChunksWithParagraphTracking()
            DocSync->>Encoder: encodeText(chunk)
            Encoder-->>DocSync: Embedding vector
            DocSync->>ChunksDB: addChunk(chunk + embedding + paragraphIndex)
        end

        DocSync->>DocsDB: updateDocument(doc + newHashes)
        DocSync->>Encoder: release()
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

### 2. Resource Management
- Sentence encoder (ONNX) loads on-demand
- Released after processing to free GPU/DSP
- Prevents memory exhaustion

### 3. Background Processing
- FileObserver watches for file changes
- Periodic sync every 60 seconds
- Runs as foreground service

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