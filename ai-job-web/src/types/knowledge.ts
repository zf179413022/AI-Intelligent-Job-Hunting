export type KnowledgeDocStatus =
  | 'UPLOADED'
  | 'PARSED'
  | 'CHUNKED'
  | 'EMBEDDED'
  | 'READY'
  | 'FAILED'

export interface KnowledgeDocument {
  id: number
  userId: number
  title: string
  fileName: string
  filePath: string
  fileType: string
  fileSize: number | null
  status: KnowledgeDocStatus | string
  pageCount: number | null
  chunkCount: number | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface KnowledgeChunk {
  id: number
  documentId: number
  userId: number
  chunkIndex: number
  content: string
  tokenEstimate: number | null
  vectorId: string | null
  createdAt: string
}

export interface KnowledgeIngestResult {
  document: KnowledgeDocument
  chunkCount: number
  embeddingModel: string
  embeddingDimension: number
  sampleVectorLength: number
  chromaCollection: string
  chromaVectorCount: number
  sampleVectorId: string | null
}

export interface KnowledgeAskRequest {
  question: string
  topK?: number
  documentId?: number
}

export interface KnowledgeSource {
  documentId: number
  title: string
  chunkId: number
  snippet: string
  score: number
}

export interface KnowledgeAskResult {
  question: string
  answer: string
  sources: KnowledgeSource[]
  topK: number
  hitCount: number
  provider: string
  qaId: number | null
}
