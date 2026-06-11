import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PagePreview from './PagePreview.vue'
import { useBrowserSessionStore } from '../stores/browserSessionStore'

describe('PagePreview.vue 全链路流程', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('模拟 screenshot → selectors → previewResult → saveFieldResult 流程,UI 正确展示', async () => {
    const store = useBrowserSessionStore()
    store._ws = { send: vi.fn() }
    vi.spyOn(store, 'connect').mockResolvedValue()
    vi.spyOn(store, 'openSession').mockResolvedValue()

    const ElAlertStub = {
      name: 'ElAlert',
      template: '<div class="el-alert" :data-type="type" :title="title">{{ title }}</div>',
      props: ['type', 'title', 'closable']
    }
    const wrapper = mount(PagePreview, {
      props: { id: 1 },
      global: { stubs: { 'el-input': true, 'el-button': true, 'el-form': true, 'el-form-item': true, 'el-alert': ElAlertStub, 'el-radio': true, 'el-radio-group': true, 'el-select': true, 'el-option': true } }
    })
    store.lastScreenshot = 'BASE64PNG'
    store.selectors = {
      css: { selector: 'div.title', matchCount: 3, samples: ['a', 'b', 'c'] },
      xpath: { selector: '//div', matchCount: 3, samples: [] }
    }
    store.previewResult = { matchCount: 3, samples: ['a', 'b', 'c'] }
    store.saveFieldResult = { ok: true, fieldId: 99, message: null }
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('div.title')
    expect(wrapper.text()).toContain('匹配到 3 个元素')
    expect(wrapper.text()).toContain('已保存')
  })
})
