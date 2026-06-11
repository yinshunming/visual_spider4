import apiClient from './index'

export function openSession() {
  return apiClient.post('/browser/sessions').then(r => r.data)
}

export function closeSession(id) {
  return apiClient.delete(`/browser/sessions/${id}`).then(r => r.data)
}

export function getStatus() {
  return apiClient.get('/browser/sessions').then(r => r.data)
}

export function connectWs(onMessage) {
  const isHttps = window.location.protocol === 'https:'
  const proto = isHttps ? 'wss' : 'ws'
  const host = window.location.host
  const apiBase = (import.meta && import.meta.env && import.meta.env.VITE_API_BASE) || ''
  let url
  if (apiBase) {
    url = `${apiBase.replace(/^http/, 'ws')}/api/v1/ws/page`
  } else if (host.startsWith('5173') || host.includes(':5173')) {
    url = `ws://localhost:8080/api/v1/ws/page`
  } else {
    url = `${proto}://${host}/api/v1/ws/page`
  }
  const ws = new WebSocket(url)
  ws.onmessage = (evt) => {
    try {
      const data = JSON.parse(evt.data)
      onMessage(data)
    } catch (e) {
      console.error('WS parse error', e)
    }
  }
  return {
    ws,
    send(obj) {
      if (ws.readyState === 1) {
        ws.send(JSON.stringify(obj))
      } else {
        ws.addEventListener?.('open', () => ws.send(JSON.stringify(obj)), { once: true })
      }
    },
    close() {
      if (ws.readyState === 1 || ws.readyState === 0) {
        ws.close()
      }
    }
  }
}

export default apiClient
