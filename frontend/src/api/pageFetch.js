import apiClient from './index'

/**
 * 同步抓取目标页面元信息
 * @param {{ url: string }} payload
 * @returns {Promise<{code:number,data:object,message:string}>}
 */
export function fetchPage({ url }) {
  return apiClient.post('/page-fetch', { url }).then(r => r.data)
}

export default apiClient
