// e2e/tests/manual-crawl-list-detail.spec.js
// M4 爬取执行 LIST_DETAIL 主链路
// 前置:PG 已起 + Chromium 已装 + jar 已打包 + start-stack.js 启动后端 / 前端 / fixture 静态服务
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const FIXTURE_URL = 'http://localhost:7777/sample-list.html'
const CONFIG_NAME = 'E2E Manual Crawl List Detail'

test.describe('M4 手工爬取 - LIST_DETAIL 主链路', () => {
  let configId
  let taskId

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: BACKEND })

    // 创建或复用 LIST_DETAIL config(startUrl 指向本地 fixture)
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

    // 同步字段:LIST title + LIST detail_url
    await api.put(`/api/v1/configs/${configId}`, {
      data: {
        name: CONFIG_NAME,
        pageType: 'LIST_DETAIL',
        selectorType: 'CSS',
        startUrl: FIXTURE_URL,
        fields: [
          { pageType: 'LIST', fieldName: 'title', fieldType: 'TEXT', selector: '.title' },
          { pageType: 'LIST', fieldName: 'detail_url', fieldType: 'URL', selector: '.title' }
        ]
      },
      headers: { 'Content-Type': 'application/json' }
    })
  })

  test('POST /api/v1/tasks → task COMPLETED,articles ≥ 1', async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const resp = await api.post('/api/v1/tasks', {
      data: { configId, urls: null },
      headers: { 'Content-Type': 'application/json' }
    })
    expect(resp.ok()).toBeTruthy()
    const created = await resp.json()
    taskId = created.data.id
    expect(taskId).toBeGreaterThan(0)

    // 轮询等任务结束(最多 60s)
    let task
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000))
      const get = await api.get(`/api/v1/tasks/${taskId}`)
      const body = await get.json()
      task = body.data
      if (task.status !== 'RUNNING') break
    }
    expect(task.status).toBe('COMPLETED')
    expect(task.crawledItems + task.failedItems).toBe(task.totalItems)

    // 验证 articles
    const arts = await api.get('/api/v1/articles', { params: { config_id: configId, size: 10 } })
    const artsBody = await arts.json()
    expect(artsBody.data.content.length).toBeGreaterThanOrEqual(1)
  })

  test.afterAll(async () => {
    // 清理
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