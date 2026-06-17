import { defineStore } from 'pinia'
import * as tasksApi from '../api/tasks'

const POLL_INTERVAL_MS = 1500
const RUNNING_STATUS = 'RUNNING'

export const useTaskStore = defineStore('task', {
  state: () => ({
    current: null,
    list: [],
    total: 0,
    isLoading: false,
    error: null,
    _pollTimer: null
  }),

  actions: {
    /**
     * 创建任务后立即开始轮询进度。
     * @param {number} configId
     * @param {string[]} [urls]
     */
    async createAndPoll(configId, urls) {
      const resp = await tasksApi.createTask(configId, urls || null)
      const task = resp.data
      this.current = task
      this.startPolling(task.id)
      return task
    },

    /**
     * 开启轮询:每 1.5s 拉取一次任务详情。
     * 当 status 离开 RUNNING 时自动停止。
     */
    startPolling(taskId) {
      this.stopPolling()
      const tick = async () => {
        try {
          const resp = await tasksApi.getTask(taskId)
          const task = resp.data
          this.current = task
          if (task && task.status && task.status !== RUNNING_STATUS) {
            this.stopPolling()
          }
        } catch (e) {
          this.error = e.message
          this.stopPolling()
        }
      }
      // 立即拉一次,然后开定时器
      tick()
      this._pollTimer = setInterval(tick, POLL_INTERVAL_MS)
    },

    stopPolling() {
      if (this._pollTimer) {
        clearInterval(this._pollTimer)
        this._pollTimer = null
      }
    },

    async stopTask(taskId) {
      await tasksApi.stopTask(taskId)
    },

    async deleteTask(taskId) {
      await tasksApi.deleteTask(taskId)
    },

    async fetchList(configId, page = 0, size = 20) {
      this.isLoading = true
      try {
        const resp = await tasksApi.listTasks({ configId, page, size })
        const pageData = resp.data || {}
        this.list = pageData.content || []
        this.total = pageData.totalElements || 0
        return this.list
      } catch (e) {
        this.error = e.message
        throw e
      } finally {
        this.isLoading = false
      }
    }
  },

  getters: {
    isPolling: (state) => state._pollTimer != null,
    progressPercent: (state) => {
      if (!state.current || !state.current.totalItems) return 0
      return Math.round(((state.current.crawledItems + state.current.failedItems)
          / state.current.totalItems) * 100)
    }
  }
})

export default useTaskStore