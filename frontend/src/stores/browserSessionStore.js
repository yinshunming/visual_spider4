import { defineStore } from 'pinia'
import * as browserApi from '../api/browser'

export const useBrowserSessionStore = defineStore('browserSession', {
  state: () => ({
    status: 'idle',
    sessionId: null,
    currentUrl: null,
    lastScreenshot: null,
    selectors: null,
    previewResult: null,
    saveFieldResult: null,
    error: null,
    _ws: null
  }),

  actions: {
    async connect() {
      if (this._ws) return
      this._ws = browserApi.connectWs((msg) => this._handleMessage(msg))
    },

    disconnect() {
      if (this._ws) {
        this._ws.close()
        this._ws = null
      }
    },

    async openSession() {
      if (this.sessionId && this.status === 'ACTIVE') return
      try {
        const resp = await browserApi.openSession()
        if (resp && resp.code === 200) {
          this.sessionId = resp.data.sessionId
          this.status = resp.data.status
        } else {
          this.error = resp.message
        }
      } catch (e) {
        if (e && e.response && e.response.status === 409) {
          this.status = 'ACTIVE'
        } else {
          this.error = e.message
        }
      }
    },

    async closeSession() {
      if (!this.sessionId) return
      try {
        await browserApi.closeSession(this.sessionId)
      } catch (e) {
        this.error = e.message
      } finally {
        this.status = 'CLOSED'
        this.sessionId = null
      }
    },

    loadUrl({ url, configId }) {
      if (!this._ws) return
      this._ws.send({ type: 'load', payload: { url, configId } })
    },

    click(x, y) {
      if (!this._ws) return
      this._ws.send({ type: 'click', payload: { x, y } })
    },

    preview(selectorType, selector) {
      if (!this._ws) return
      this._ws.send({ type: 'preview', payload: { selectorType, selector } })
    },

    saveField(payload) {
      if (!this._ws) return
      this._ws.send({ type: 'saveField', payload })
    },

    closeWs() {
      if (!this._ws) return
      this._ws.send({ type: 'close' })
    },

    _handleMessage(msg) {
      if (!msg || !msg.type) return
      switch (msg.type) {
        case 'state':
          this.status = msg.payload?.state || this.status
          if (msg.payload?.message) this.error = msg.payload.message
          break
        case 'screenshot':
          this.lastScreenshot = msg.payload?.data || null
          break
        case 'selectors':
          this.selectors = msg.payload
          break
        case 'previewResult':
          this.previewResult = msg.payload
          break
        case 'saveFieldResult':
          this.saveFieldResult = msg.payload
          break
        case 'error':
          this.error = `${msg.payload?.code}: ${msg.payload?.message}`
          break
      }
    }
  }
})

export default useBrowserSessionStore
