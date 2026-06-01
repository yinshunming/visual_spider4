import { defineStore } from 'pinia'
import * as configApi from '../api/config'

export const useConfigStore = defineStore('config', {
  state: () => ({
    list: [],
    current: null,
    loading: false,
    error: null
  }),

  actions: {
    async fetchConfigs(page = 0, size = 10) {
      this.loading = true
      this.error = null
      try {
        const response = await configApi.getConfigs(page, size)
        this.list = response.data
      } catch (e) {
        this.error = e.message
      } finally {
        this.loading = false
      }
    },

    async fetchConfigById(id) {
      this.loading = true
      this.error = null
      try {
        const response = await configApi.getConfig(id)
        this.current = response.data
      } catch (e) {
        this.error = e.message
      } finally {
        this.loading = false
      }
    },

    async createConfig(data) {
      const response = await configApi.createConfig(data)
      this.current = response.data
      return response.data
    },

    async updateConfig(id, data) {
      const response = await configApi.updateConfig(id, data)
      this.current = response.data
      return response.data
    },

    async deleteConfig(id) {
      await configApi.deleteConfig(id)
    }
  }
})
