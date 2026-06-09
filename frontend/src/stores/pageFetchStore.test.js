import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePageFetchStore } from './pageFetchStore'
import * as api from '../api/pageFetch'

describe('usePageFetchStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('§16 初始状态', () => {
    it('初始 status=idle，lastResult/lastError 都为 null', () => {
      const store = usePageFetchStore()
      expect(store.status).toBe('idle')
      expect(store.lastResult).toBeNull()
      expect(store.lastError).toBeNull()
    })
  })

  describe('§17 成功路径', () => {
    it('fetch 期间 status=loading，成功后 status=success 且 lastResult 存值', async () => {
      const payload = {
        status: 'SUCCESS',
        finalUrl: 'https://example.com',
        title: 'Example Domain',
        contentLength: 1234,
        fetchedAt: '2026-06-01T00:00:00Z'
      }
      vi.spyOn(api, 'fetchPage').mockResolvedValue({
        code: 200,
        data: payload,
        message: 'success'
      })

      const store = usePageFetchStore()
      const promise = store.fetch({ url: 'https://example.com' })
      expect(store.status).toBe('loading')

      await promise
      expect(store.status).toBe('success')
      expect(store.lastResult).toEqual(payload)
      expect(store.lastError).toBeNull()
    })
  })

  describe('§18 错误路径', () => {
    it('API 抛错 → status=error 且 lastError 含 message', async () => {
      vi.spyOn(api, 'fetchPage').mockRejectedValue({
        response: { data: { code: 4003, message: '目标地址被禁止访问' } }
      })

      const store = usePageFetchStore()
      await store.fetch({ url: 'http://localhost' })

      expect(store.status).toBe('error')
      expect(store.lastError).toBe('目标地址被禁止访问')
      expect(store.lastResult).toBeNull()
    })

    it('返回 200 但 code !== 200 → status=error 且 lastError=message', async () => {
      vi.spyOn(api, 'fetchPage').mockResolvedValue({
        code: 4003,
        data: null,
        message: '目标地址被禁止访问'
      })

      const store = usePageFetchStore()
      await store.fetch({ url: 'http://localhost' })

      expect(store.status).toBe('error')
      expect(store.lastError).toBe('目标地址被禁止访问')
    })
  })
})
