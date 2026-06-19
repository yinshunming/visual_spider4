import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PagePreview from './PagePreview.vue'
import { useBrowserSessionStore } from '../stores/browserSessionStore'
import { useConfigStore } from '../stores/configStore'

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

  describe('滚轮转发', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('连续滚轮节流累积 deltaY,120ms 后发一次 scroll', async () => {
      const store = useBrowserSessionStore()
      store.lastScreenshot = 'BASE64PNG'
      const scrollSpy = vi.spyOn(store, 'scroll').mockReturnValue()
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      await wrapper.vm.$nextTick()
      const block = wrapper.find('.screenshot-block')
      await block.trigger('wheel', { deltaY: 100 })
      await block.trigger('wheel', { deltaY: 100 })
      await block.trigger('wheel', { deltaY: 100 })
      expect(scrollSpy).not.toHaveBeenCalled()
      vi.advanceTimersByTime(120)
      expect(scrollSpy).toHaveBeenCalledTimes(1)
      expect(scrollSpy).toHaveBeenCalledWith(300)
    })

    it('未加载截图时不转发滚轮', async () => {
      const store = useBrowserSessionStore()
      store.lastScreenshot = null
      const scrollSpy = vi.spyOn(store, 'scroll').mockReturnValue()
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      const block = wrapper.find('.screenshot-block')
      expect(block.exists()).toBe(false)
      vi.advanceTimersByTime(120)
      expect(scrollSpy).not.toHaveBeenCalled()
    })
  })

  describe('默认 URL 预填', () => {
    it('进入页面时用配置的 startUrl 预填 URL 框', async () => {
      const browserStore = useBrowserSessionStore()
      vi.spyOn(browserStore, 'connect').mockResolvedValue()
      vi.spyOn(browserStore, 'openSession').mockResolvedValue()
      const configStore = useConfigStore()
      vi.spyOn(configStore, 'fetchConfigById').mockImplementation(async () => {
        configStore.current = { id: 7, startUrl: 'https://sports.sina.com.cn/nba/' }
      })
      const wrapper = mount(PagePreview, {
        props: { id: 7 },
        global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
      })
      await new Promise(r => setTimeout(r, 10))
      expect(configStore.fetchConfigById).toHaveBeenCalledWith(7)
      expect(wrapper.vm.form.url).toBe('https://sports.sina.com.cn/nba/')
    })
  })

  describe('iframe / shadow DOM 提示', () => {
    const stubs = { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true }
    const MSG = '该元素位于 iframe / shadow DOM 内,本期不支持深入选择'

    it('selectors.nested=true 时显示提示', async () => {
      const store = useBrowserSessionStore()
      store.lastScreenshot = 'BASE64PNG'
      store.selectors = { css: { selector: 'iframe', matchCount: 1, samples: [] }, xpath: { selector: '//iframe', matchCount: 1, samples: [] }, nested: true }
      const wrapper = mount(PagePreview, { props: { id: 1 }, global: { stubs } })
      await wrapper.vm.$nextTick()
      const alert = wrapper.find('el-alert-stub')
      expect(alert.exists()).toBe(true)
      expect(alert.attributes('title')).toContain(MSG)
    })

    it('selectors.nested 缺省时不显示提示', async () => {
      const store = useBrowserSessionStore()
      store.lastScreenshot = 'BASE64PNG'
      store.selectors = { css: { selector: 'div.title', matchCount: 3, samples: ['a'] }, xpath: { selector: '//div', matchCount: 3, samples: ['a'] } }
      const wrapper = mount(PagePreview, { props: { id: 1 }, global: { stubs } })
      await wrapper.vm.$nextTick()
      expect(wrapper.find('el-alert-stub').exists()).toBe(false)
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

  describe('extract preview tab', () => {
    it('activeTab 切到 extract 后,vm.extractPageType 默认为 LIST', async () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.activeTab).toBe('craft')
      wrapper.vm.activeTab = 'extract'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.extractPageType).toBe('LIST')
    })

    it('warnings 非空时,vm.currentWarnings 返回该 pageType 警告', async () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      const extractionStore = (await import('../stores/extractionPreviewStore.js')).useExtractionPreviewStore()
      extractionStore.warnings.LIST = ['该模板未定义任何 LIST 字段']
      wrapper.vm.activeTab = 'extract'
      wrapper.vm.extractPageType = 'LIST'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.currentWarnings).toEqual(['该模板未定义任何 LIST 字段'])
    })

    it('isLoading 或未加载 URL 时,触发按钮 disabled', async () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      await wrapper.vm.$nextTick()
      wrapper.vm.activeTab = 'extract'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.isPreviewDisabled).toBe(true)
    })

    it('页面加载成功后,触发按钮 enabled', async () => {
      const browserStore = useBrowserSessionStore()
      browserStore.status = 'LOADED'
      browserStore.currentUrl = 'http://example.com'
      const extractionStore = (await import('../stores/extractionPreviewStore.js')).useExtractionPreviewStore()
      extractionStore.isLoading = false
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      await wrapper.vm.$nextTick()
      wrapper.vm.activeTab = 'extract'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.isPreviewDisabled).toBe(false)
    })

    it('预览请求飞行期间,触发按钮 disabled', async () => {
      const browserStore = useBrowserSessionStore()
      browserStore.status = 'LOADED'
      browserStore.currentUrl = 'http://example.com'
      const extractionStore = (await import('../stores/extractionPreviewStore.js')).useExtractionPreviewStore()
      extractionStore.isLoading = true
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      await wrapper.vm.$nextTick()
      wrapper.vm.activeTab = 'extract'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.isPreviewDisabled).toBe(true)
    })

    it('页面加载失败时,触发按钮 disabled', async () => {
      const browserStore = useBrowserSessionStore()
      browserStore.status = 'ERROR'
      const extractionStore = (await import('../stores/extractionPreviewStore.js')).useExtractionPreviewStore()
      extractionStore.isLoading = false
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      await wrapper.vm.$nextTick()
      wrapper.vm.activeTab = 'extract'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.isPreviewDisabled).toBe(true)
    })

    it('statusLabel/statusTagType 在四态下都返回非空', async () => {
      const wrapper = mount(PagePreview, {
        props: { id: 1 },
        global: {
          stubs: {
            'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true,
            'el-alert': true, 'el-radio': true, 'el-radio-group': true, 'el-select': true,
            'el-option': true, 'el-tabs': true, 'el-tab-pane': true,
            'el-table': true, 'el-table-column': true, 'el-tag': true
          }
        }
      })
      expect(wrapper.vm.statusLabel('OK')).toBe('OK')
      expect(wrapper.vm.statusLabel('TYPE_MISMATCH')).toBe('类型不符')
      expect(wrapper.vm.statusLabel('NO_MATCH')).toBe('未命中')
      expect(wrapper.vm.statusLabel('SELECTOR_INVALID')).toBe('选择器非法')
      expect(wrapper.vm.statusTagType('OK')).toBe('success')
      expect(wrapper.vm.statusTagType('TYPE_MISMATCH')).toBe('warning')
      expect(wrapper.vm.statusTagType('NO_MATCH')).toBe('danger')
      expect(wrapper.vm.statusTagType('SELECTOR_INVALID')).toBe('danger')
    })
  })
})
