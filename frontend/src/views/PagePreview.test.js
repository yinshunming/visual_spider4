import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PagePreview from './PagePreview.vue'
import { usePageFetchStore } from '../stores/pageFetchStore'

describe('PagePreview.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('§19 初始渲染', () => {
    it('包含 URL 输入框与加载按钮（初始 disabled）', () => {
      const wrapper = mount(PagePreview, {
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-alert': true } }
      })

      const buttons = wrapper.findAll('el-button-stub')
      // 第一个是返回按钮，第二个是"加载"按钮
      expect(buttons.length).toBeGreaterThanOrEqual(2)
      const loadButton = buttons[buttons.length - 1]
      expect(loadButton.exists()).toBe(true)
      // 初始未输入 URL → disabled=true
      expect(loadButton.attributes('disabled')).toBe('true')
    })
  })

  describe('§20 点击触发请求', () => {
    it('输入合法 URL 后点击按钮，store.fetch 被调一次', async () => {
      const wrapper = mount(PagePreview, {
        global: { stubs: { 'el-input': false, 'el-button': false, 'el-alert': true } }
      })
      const store = usePageFetchStore()
      const fetchSpy = vi.spyOn(store, 'fetch').mockResolvedValue()

      const vm = wrapper.vm
      vm.url = 'https://example.com'
      await vm.$nextTick()
      await vm.onLoad()

      expect(fetchSpy).toHaveBeenCalledTimes(1)
      expect(fetchSpy).toHaveBeenCalledWith({ url: 'https://example.com' })
    })
  })

  describe('§21 成功展示', () => {
    it('store.status=success + lastResult.title 时页面展示 title', async () => {
      const wrapper = mount(PagePreview, {
        global: {
          stubs: {
            'el-input': true,
            'el-button': true,
            'el-alert': true,
            'el-descriptions': { template: '<div class="desc"><slot /></div>' },
            'el-descriptions-item': { template: '<div class="desc-item"><span class="label">{{ label }}</span><span class="value"><slot /></span></div>', props: ['label'] }
          }
        }
      })
      const store = usePageFetchStore()
      store.status = 'success'
      store.lastResult = {
        status: 'SUCCESS',
        finalUrl: 'https://example.com',
        title: 'Example Domain',
        contentLength: 1234,
        fetchedAt: '2026-06-01T00:00:00Z'
      }
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('Example Domain')
      expect(wrapper.text()).toContain('https://example.com')
    })
  })

  describe('§22 错误展示', () => {
    it('store.status=error + lastError → 页面用红色提示展示', async () => {
      const wrapper = mount(PagePreview, {
        global: {
          stubs: {
            'el-input': true,
            'el-button': true,
            'el-alert': { template: '<div class="el-alert" :data-type="type">{{ title }}</div>', props: ['type', 'title', 'closable'] }
          }
        }
      })
      const store = usePageFetchStore()
      store.status = 'error'
      store.lastError = '目标地址被禁止访问'
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('目标地址被禁止访问')
      const alert = wrapper.find('.el-alert')
      expect(alert.exists()).toBe(true)
      expect(alert.attributes('data-type')).toBe('error')
    })
  })
})
