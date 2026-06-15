import { defineStore } from 'pinia'

export const useExtractionPreviewStore = defineStore('extractionPreview', {
  state: () => ({
    results: { LIST: null, DETAIL: null },
    warnings: { LIST: [], DETAIL: [] },
    isLoading: false,
    _ws: null,
    _onMessage: null,
    _pending: null
  }),

  actions: {
    setWs(conn) {
      this._ws = conn
      const originalOnMessage = conn.ws.onmessage
      conn.ws.onmessage = (evt) => {
        if (originalOnMessage) originalOnMessage(evt)
        try {
          const msg = JSON.parse(evt.data)
          if (msg && msg.type === 'previewTemplateResult' && this._pending) {
            const { pageType, resolve } = this._pending
            const result = msg.payload?.result || { fields: [], warnings: [] }
            this.results[pageType] = result
            this.warnings[pageType] = result.warnings || []
            this.isLoading = false
            this._pending = null
            resolve(result)
          }
        } catch (e) {
          console.error('extractionStore parse error', e)
        }
      }
    },

    triggerPreview(pageType) {
      if (this.isLoading) {
        return Promise.resolve(null)
      }
      if (!this._ws) {
        return Promise.reject(new Error('WebSocket 未连接'))
      }
      this.isLoading = true
      this._ws.send({ type: 'previewTemplate', payload: { pageType } })
      return new Promise((resolve) => {
        this._pending = { pageType, resolve }
      })
    }
  },

  getters: {
    getResult: (state) => (pageType) => state.results[pageType] || null,
    getWarnings: (state) => (pageType) => state.warnings[pageType] || []
  }
})

export default useExtractionPreviewStore
