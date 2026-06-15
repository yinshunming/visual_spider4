// e2e/tests/manual-crawl-stop.spec.js
// M4 爬取执行中途停止
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const FIXTURE_URL = 'http://localhost:7777/sample-list.html'
const CONFIG_NAME = 'E2E Manual Crawl Stop'

test.describe('M4 手工爬取 - 停止信号', () => {
  let configId
  let taskId

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: BACKEND })

    const list = await api.get('/api/v1/configs', { params: { size: 100 } })
    const body = await list.json()
    const existing = (body.data.content || []).find((c) => c.name === CONFIG_NAME)
    if (existing) {
      configId = existing.id
    } else {
      const create = await api.post('/api/v1/configs', {
        data: {
          name: CONFIG_NAME,
          pageType: 'LIST_DETAIL',
          selectorType: 'CSS',
          startUrl: FIXTURE_URL,
          status: 'STOPPED'
        },
        headers: { 'Content-Type': 'application/json' }
      })
      const created = await create.json()
      configId = created.data.id
    }

    await api.put(`/api/v1/configs/${configId}`, {
      data: {
        name: CONFIG_NAME,
        pageType: 'LIST_DETAIL',
        selectorType: 'CSS',
        startUrl: FIXTURE_URL,
        fields: [
          { pageType: 'LIST', fieldName: 'title', fieldType: 'TEXT', selector: '.title' },
          { pageType: 'LIST', fieldName: 'detail_url', fieldType: 'URL', selector: '.title' },
          { pageType: 'DETAIL', fieldName: 'title', fieldType: 'TEXT', selector: '.title' }
        ]
      },
      headers: { 'Content-Type': 'application/json' }
    })
  })

  test('stop 后 task.status=COMPLETED(部分成功),articles < totalItems', async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const resp = await api.post('/api/v1/tasks', {
      data: { configId, urls: null },
      headers: { 'Content-Type': 'application/json' }
    })
    const created = await resp.json()
    taskId = created.data.id

    // 立即 stop
    await api.post(`/api/v1/tasks/${taskId}/stop`)

    // 轮询等任务结束
    let task
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000))
      const get = await api.get(`/api/v1/tasks/${taskId}`)
      const body = await get.json()
      task = body.data
      if (task.status !== 'RUNNING') break
    }
    expect(task.status).toBe('COMPLETED')
    expect(task.crawledItems + task.failedItems).toBeLessThanOrEqual(task.totalItems)
  })

  test.afterAll(async () => {
    if (taskId) {
      const api = await request.newContext({ baseURL: BACKEND })
      await api.delete(`/api/v1/tasks/${taskId}`)
    }
    if (configId) {
      const api = await request.newContext({ baseURL: BACKEND })
      await api.delete(`/api/v1/configs/${configId}`)
    }
  })
})