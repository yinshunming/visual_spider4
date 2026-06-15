import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('Articles API Error:', error)
    return Promise.reject(error)
  }
)

/**
 * 分页文章列表。
 * - configId 可选(不传 → 全部)
 * - keyword 可选(走 JSON 文本 LIKE)
 */
export function listArticles({ configId, keyword, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (configId != null) params.config_id = configId
  if (keyword) params.keyword = keyword
  return apiClient.get('/articles', { params }).then(r => r.data)
}

/** 获取文章详情(含 raw_html + custom_fields) */
export function getArticle(id) {
  return apiClient.get(`/articles/${id}`).then(r => r.data)
}

/**
 * 导出当前过滤结果。
 * 返回 Blob + filename,调用方负责触发下载。
 */
export function exportArticles({ format = 'JSON', configId, keyword } = {}) {
  const params = { format }
  if (configId != null) params.config_id = configId
  if (keyword) params.keyword = keyword
  return apiClient.post('/articles/export', null, {
    params,
    responseType: 'blob'
  }).then(r => ({
    blob: r.data,
    filename: extractFilename(r.headers['content-disposition']) || `articles.${format.toLowerCase()}`
  }))
}

function extractFilename(contentDisposition) {
  if (!contentDisposition) return null
  const match = /filename="?([^";]+)"?/.exec(contentDisposition)
  return match ? match[1] : null
}

export default apiClient