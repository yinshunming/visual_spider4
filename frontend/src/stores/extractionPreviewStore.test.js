import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

function mockConn(send) {
  return { ws: { onmessage: null }, send }
}

function emit(conn, msg) {
  if (conn.ws.onmessage) {
    conn.ws.onmessage({ data: JSON.stringify(msg) })
  }
}

describe('useExtractionPreviewStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('triggerPreview("LIST") 后 getResult("LIST") 返回服务端 fields', async () => {
    const { useExtractionPreviewStore } = await import('./extractionPreviewStore.js')
    const store = useExtractionPreviewStore()
    const send = vi.fn()
    const conn = mockConn(send)
    store.setWs(conn)

    const promise = store.triggerPreview('LIST')
    expect(send).toHaveBeenCalledWith({ type: 'previewTemplate', payload: { pageType: 'LIST' } })
    expect(store.isLoading).toBe(true)

    emit(conn, {
      type: 'previewTemplateResult',
      payload: { result: { fields: [{ fieldName: 'a' }], warnings: [] } }
    })
    await promise
    expect(store.isLoading).toBe(false)
    const r = store.getResult('LIST')
    expect(r).toBeTruthy()
    expect(r.fields).toEqual([{ fieldName: 'a' }])
  })

  it('LIST 与 DETAIL 预览结果互不覆盖', async () => {
    const { useExtractionPreviewStore } = await import('./extractionPreviewStore.js')
    const store = useExtractionPreviewStore()
    const conn = mockConn(vi.fn())
    store.setWs(conn)

    const p1 = store.triggerPreview('LIST')
    emit(conn, {
      type: 'previewTemplateResult',
      payload: { result: { fields: [{ fieldName: 'L' }], warnings: [] } }
    })
    await p1

    const p2 = store.triggerPreview('DETAIL')
    emit(conn, {
      type: 'previewTemplateResult',
      payload: { result: { fields: [{ fieldName: 'D' }], warnings: [] } }
    })
    await p2

    expect(store.getResult('LIST').fields[0].fieldName).toBe('L')
    expect(store.getResult('DETAIL').fields[0].fieldName).toBe('D')
  })

  it('isLoading 在 trigger 前 false → trigger 中 true → 收到结果后 false', async () => {
    const { useExtractionPreviewStore } = await import('./extractionPreviewStore.js')
    const store = useExtractionPreviewStore()
    const conn = mockConn(vi.fn())
    store.setWs(conn)

    expect(store.isLoading).toBe(false)
    const p = store.triggerPreview('LIST')
    expect(store.isLoading).toBe(true)

    emit(conn, {
      type: 'previewTemplateResult',
      payload: { result: { fields: [], warnings: [] } }
    })
    await p
    expect(store.isLoading).toBe(false)
  })
})
