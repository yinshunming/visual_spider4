import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PagePreview from './PagePreview.vue'
import { useBrowserSessionStore } from '../stores/browserSessionStore'

describe('PagePreview.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('initial render', () => {
    it('包含页面标题、URL 输入框、占位提示', () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true,
            'el-button': true,
            'el-form': true,
            'el-form-item': true,
            'el-alert': true,
            'el-radio': true,
            'el-radio-group': true,
            'el-select': true,
            'el-option': true
          }
        }
      })
      expect(wrapper.text()).toContain('页面预览')
      expect(wrapper.text()).toContain('尚未加载')
      const buttons = wrapper.findAll('el-button-stub')
      expect(buttons.length).toBeGreaterThanOrEqual(1)
    })

    it('空 URL 时 isValidUrl=false, computed 阻止 load', () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      expect(wrapper.vm.isValidUrl).toBe(false)
    })

    it('进入页面时自动 connect + openSession', async () => {
      const store = useBrowserSessionStore()
      const connectSpy = vi.spyOn(store, 'connect').mockResolvedValue()
      const openSpy = vi.spyOn(store, 'openSession').mockResolvedValue()
      mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      await new Promise(r => setTimeout(r, 10))
      expect(connectSpy).toHaveBeenCalled()
      expect(openSpy).toHaveBeenCalled()
    })
  })

  describe('click triggers store action', () => {
    it('点击截图区调 browserSessionStore.click(x, y)', async () => {
      const store = useBrowserSessionStore()
      store.lastScreenshot = 'BASE64PNG'
      const clickSpy = vi.spyOn(store, 'click').mockReturnValue()
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      await wrapper.vm.$nextTick()
      const img = wrapper.find('img.screenshot')
      await img.trigger('click', { offsetX: 100, offsetY: 50 })
      expect(clickSpy).toHaveBeenCalledWith(100, 50)
    })
  })

  describe('selectors panel', () => {
    it('默认显示 CSS 候选,切换到 XPath 显示 xpath', async () => {
      const store = useBrowserSessionStore()
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      store.selectors = {
        css: { selector: 'div.title', matchCount: 3, samples: ['a', 'b', 'c'] },
        xpath: { selector: '//div[contains(@class,"title")]', matchCount: 3, samples: ['a', 'b'] }
      }
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('div.title')
      wrapper.vm.selectedType = 'xpath'
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('//div[contains(@class,"title")]')
    })
  })
})
