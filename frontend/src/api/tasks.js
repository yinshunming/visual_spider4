import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('Tasks API Error:', error)
    return Promise.reject(error)
  }
)

/**
 * 创建爬取任务。LIST_DETAIL 时 urls 传 null;DETAIL_ONLY 时 urls 必填。
 */
export function createTask(configId, urls) {
  return apiClient.post('/tasks', { configId, urls }).then(r => r.data)
}

/** 获取单个任务详情 */
export function getTask(id) {
  return apiClient.get(`/tasks/${id}`).then(r => r.data)
}

/** 分页任务列表;configId 可选(不传 → 全部) */
export function listTasks({ configId, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (configId != null) params.config_id = configId
  return apiClient.get('/tasks', { params }).then(r => r.data)
}

/** 优雅停止任务 */
export function stopTask(id) {
  return apiClient.post(`/tasks/${id}/stop`).then(r => r.data)
}

/** 级联删除任务 */
export function deleteTask(id) {
  return apiClient.delete(`/tasks/${id}`).then(r => r.data)
}

export default apiClient