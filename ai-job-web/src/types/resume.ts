export interface Resume {
  id: number
  userId: number
  fileName: string
  filePath?: string
  fileType: string
  fileSize: number
  content?: string
  status: string
  createdAt: string
  updatedAt: string
}
