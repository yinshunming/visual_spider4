import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useBrowserSessionStore } from './browserSessionStore'

describe('useBrowserSessionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('initial state', () => {
    it('初始 status=idle', () => {
      const store = useBrowserSessionStore()
      expect(store.status).toBe('idle')
      expect(store.sessionId).toBeNull()
      expect(store.lastScreenshot).toBeNull()
    })
  })

  describe('loadUrl', () => {
    it('调 ws.send 发出 {type:load, payload:{url, configId}}', () => {
      const store = useBrowserSessionStore()
      const send = vi.fn()
      store._ws = { send }
      store.loadUrl({ url: 'https://example.com', configId: 1 })
      expect(send).toHaveBeenCalledWith({
        type: 'load',
        payload: { url: 'https://example.com', configId: 1 }
      })
    })
  })

  describe('click', () => {
    it('调 ws.send 发出 {type:click, payload:{x,y}}', () => {
      const store = useBrowserSessionStore()
      const send = vi.fn()
      store._ws = { send }
      store.click(10, 20)
      expect(send).toHaveBeenCalledWith({ type: 'click', payload: { x: 10, y: 20 } })
    })
  })

  describe('preview', () => {
    it('调 ws.send 发出 preview 消息', () => {
      const store = useBrowserSessionStore()
      const send = vi.fn()
      store._ws = { send }
      store.preview('css', 'div.title')
      expect(send).toHaveBeenCalledWith({ type: 'preview', payload: { selectorType: 'css', selector: 'div.title' } })
    })
  })

  describe('handleMessage screenshot', () => {
    it('收到 screenshot 消息时 lastScreenshot 被设值', () => {
      const store = useBrowserSessionStore()
      store._handleMessage({ type: 'screenshot', payload: { data: 'BASE64PNG' } })
      expect(store.lastScreenshot).toBe('BASE64PNG')
    })
  })
})
