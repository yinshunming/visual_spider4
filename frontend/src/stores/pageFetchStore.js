import { defineStore } from 'pinia'
import * as pageFetchApi from '../api/pageFetch'

export const usePageFetchStore = defineStore('pageFetch', {
  state: () => ({
    status: 'idle',
    lastResult: null,
    lastError: null
  }),

  actions: {
    async fetch({ url }) {
      this.status = 'loading'
      this.lastResult = null
      this.lastError = null
      try {
        const response = await pageFetchApi.fetchPage({ url })
        if (response && response.code === 200) {
          this.status = 'success'
          this.lastResult = response.data
        } else {
          this.status = 'error'
          this.lastError = (response && response.message) || '请求失败'
        }
      } catch (e) {
        this.status = 'error'
        const apiMessage = e && e.response && e.response.data && e.response.data.message
        this.lastError = apiMessage || (e && e.message) || '请求失败'
      }
    }
  }
})
