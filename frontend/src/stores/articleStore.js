import { defineStore } from 'pinia'
import * as articlesApi from '@/api/articles'

export const useArticleStore = defineStore('article', {
  state: () => ({
    list: [],
    current: null,
    page: 0,
    size: 20,
    total: 0,
    keyword: '',
    configId: null,
    isLoading: false,
    error: null
  }),

  actions: {
    async fetchList() {
      this.isLoading = true
      try {
        const resp = await articlesApi.listArticles({
          configId: this.configId,
          keyword: this.keyword,
          page: this.page,
          size: this.size
        })
        this.list = resp.data.content || []
        this.total = resp.data.totalElements || 0
      } catch (e) {
        this.error = e.message
      } finally {
        this.isLoading = false
      }
    },

    async fetchOne(id) {
      this.isLoading = true
      try {
        const resp = await articlesApi.getArticle(id)
        this.current = resp.data
        return resp.data
      } catch (e) {
        this.error = e.message
        throw e
      } finally {
        this.isLoading = false
      }
    },

    setKeyword(kw) {
      this.keyword = kw
      this.page = 0
    },

    setConfigId(id) {
      this.configId = id
      this.page = 0
    },

    async exportFile(format = 'JSON') {
      const { blob, filename } = await articlesApi.exportArticles({
        format,
        configId: this.configId,
        keyword: this.keyword
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    }
  }
})

export default useArticleStore