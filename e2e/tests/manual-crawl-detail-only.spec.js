// e2e/tests/manual-crawl-detail-only.spec.js
// M4 爬取执行 DETAIL_ONLY 主链路(用户提供 URLs)
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const FIXTURE_URL = 'http://localhost:7777/sample-list.html'
const CONFIG_NAME = 'E2E Manual Crawl Detail Only'

test.describe('M4 手工爬取 - DETAIL_ONLY', () => {
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
          pageType: 'DETAIL_ONLY',
          selectorType: 'CSS',
          startUrl: 'https://example.com/dummy',
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
        pageType: 'DETAIL_ONLY',
        selectorType: 'CSS',
        startUrl: 'https://example.com/dummy',
        fields: [
          { pageType: 'DETAIL', fieldName: 'title', fieldType: 'TEXT', selector: '.title' }
        ]
      },
      headers: { 'Content-Type': 'application/json' }
    })
  })

  test('POST /api/v1/tasks with urls → task COMPLETED,articles ≥ 1', async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const resp = await api.post('/api/v1/tasks', {
      data: { configId, urls: [FIXTURE_URL] },
      headers: { 'Content-Type': 'application/json' }
    })
    expect(resp.ok()).toBeTruthy()
    const created = await resp.json()
    taskId = created.data.id

    let task
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000))
      const get = await api.get(`/api/v1/tasks/${taskId}`)
      const body = await get.json()
      task = body.data
      if (task.status !== 'RUNNING') break
    }
    expect(task.status).toBe('COMPLETED')
    expect(task.crawledItems).toBeGreaterThanOrEqual(1)
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