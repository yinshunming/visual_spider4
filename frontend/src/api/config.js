import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

/**
 * 配置 CRUD
 */
export function getConfigs(page = 0, size = 10) {
  return apiClient.get('/configs', { params: { page, size } }).then(r => r.data)
}

export function getConfig(id) {
  return apiClient.get(`/configs/${id}`).then(r => r.data)
}

export function createConfig(data) {
  return apiClient.post('/configs', data).then(r => r.data)
}

export function updateConfig(id, data) {
  return apiClient.put(`/configs/${id}`, data).then(r => r.data)
}

export function deleteConfig(id) {
  return apiClient.delete(`/configs/${id}`).then(r => r.data)
}

/**
 * 字段 CRUD（作为配置的子资源）
 */
export function listFields(configId) {
  return apiClient.get(`/configs/${configId}/fields`).then(r => r.data)
}

export function addField(configId, data) {
  return apiClient.post(`/configs/${configId}/fields`, data).then(r => r.data)
}

export function updateField(id, data) {
  return apiClient.put(`/fields/${id}`, data).then(r => r.data)
}

export function deleteField(id) {
  return apiClient.delete(`/fields/${id}`).then(r => r.data)
}

export default apiClient
