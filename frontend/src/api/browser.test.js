import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { connectWs } from './browser.js'

class MockWebSocket {
  constructor(url) {
    this.url = url
    this.readyState = 0
    this.sent = []
    this.onmessage = null
    this._listeners = {}
    setTimeout(() => {
      this.readyState = 1
      if (this.onopen) this.onopen()
      this._listeners['open']?.forEach(fn => fn())
    }, 0)
  }
  send(data) {
    this.sent.push(data)
  }
  close() {
    this.readyState = 3
  }
  addEventListener(type, fn) {
    if (!this._listeners[type]) this._listeners[type] = []
    this._listeners[type].push(fn)
  }
  emit(msg) {
    if (this.onmessage) this.onmessage({ data: JSON.stringify(msg) })
  }
}

describe('api/browser connectWs', () => {
  beforeEach(() => {
    global.WebSocket = MockWebSocket
  })
  afterEach(() => {
    delete global.WebSocket
  })

  it('收到 screenshot 消息时回调被调', async () => {
    const onMessage = vi.fn()
    const conn = connectWs(onMessage)
    await new Promise(r => setTimeout(r, 10))
    conn.ws.emit({ type: 'screenshot', payload: { data: 'BASE64' } })
    expect(onMessage).toHaveBeenCalledWith({ type: 'screenshot', payload: { data: 'BASE64' } })
    conn.close()
  })

  it('send 序列化并发送 JSON', async () => {
    const onMessage = vi.fn()
    const conn = connectWs(onMessage)
    await new Promise(r => setTimeout(r, 10))
    conn.send({ type: 'load', payload: { url: 'https://x.com', configId: 1 } })
    expect(conn.ws.sent.length).toBe(1)
    const parsed = JSON.parse(conn.ws.sent[0])
    expect(parsed.type).toBe('load')
    expect(parsed.payload.url).toBe('https://x.com')
    conn.close()
  })
})
