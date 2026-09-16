import request from './request'

/** 知识库文档列表 */
export const listDocuments = () => request.get('/knowledge/documents')

/** 新增文档 */
export const addDocument = (data) => request.post('/knowledge/documents', data)

/** 删除文档（连同它的切片） */
export const deleteDocument = (id) => request.delete(`/knowledge/documents/${id}`)

/** 切片列表（不传 documentId 则返回全部） */
export const listChunks = (documentId) =>
  request.get('/knowledge/chunks', { params: documentId ? { documentId } : {} })

/** 重建索引：重新切片 + 向量化 */
export const reindex = () => request.post('/knowledge/reindex')

/** 向量接口自检：返回模型名、向量维度、耗时 */
export const testEmbedding = () => request.get('/knowledge/test-embedding')

/** 检索测试 */
export const searchKnowledge = (q, topK = 3) =>
  request.get('/knowledge/search', { params: { q, topK } })
