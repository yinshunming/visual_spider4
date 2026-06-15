// e2e/tests/extraction-preview-happy.spec.js
// 按模板预览主链路 + 软警告
// 前置：PG 已起 + Chromium 已装 + jar 已打包 + start-stack.js 启动后端 / 前端 / fixture 静态服务
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const FIXTURE_URL = 'http://localhost:7777/sample-list.html'
const CONFIG_NAME = 'E2E Extract Happy'

test.describe('按模板预览 - 主链路 + 软警告', () => {
  let configId
  let baselineFieldCount

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const list = await api.get('/api/v1/configs', { params: { size: 100 } })
    expect(list.ok()).toBeTruthy()
    const body = await list.json()
    const items = (body.data && body.data.content) || []
    const existing = items.find((c) => c.name === CONFIG_NAME)
    if (existing) {
      configId = existing.id
    } else {
      const create = await api.post('/api/v1/configs', {
        data: {
          name: CONFIG_NAME,
          pageType: 'LIST_DETAIL',
          selectorType: 'CSS',
          status: 'STOPPED'
        },
        headers: { 'Content-Type': 'application/json' }
      })
      expect(create.ok()).toBeTruthy()
      const created = await create.json()
      configId = created.data.id
    }
    // 清空已有 LIST 字段,确保测试可重复
    const fields = await api.get(`/api/v1/configs/${configId}/fields`)
    const fieldsBody = await fields.json()
    const list2 = (fieldsBody.data && fieldsBody.data.content) || fieldsBody.data || []
    baselineFieldCount = list2.length
    for (const f of list2) {
      if (f.pageType === 'LIST') {
        await api.delete(`/api/v1/fields/${f.id}`)
      }
    }
    // 加 3 个 LIST 字段
    for (const spec of [
      { name: 'title', type: 'TEXT', selector: '.title' },
      { name: 'price', type: 'NUMBER', selector: '.price' },
      { name: 'cover', type: 'URL', selector: '.cover' }
    ]) {
      await api.post(`/api/v1/configs/${configId}/fields`, {
        data: { pageType: 'LIST', fieldName: spec.name, fieldType: spec.type, selector: spec.selector },
        headers: { 'Content-Type': 'application/json' }
      })
    }
    await api.dispose()
  })

  test('LIST 预览:3 字段全 OK + 顶部 detail_url 软警告', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto(`/configs/${configId}/preview`)
    await expect(page.locator('h1')).toContainText('页面预览')

    await page.locator('input[placeholder*="http"]').fill(FIXTURE_URL)
    await page.getByRole('button', { name: '加载' }).click()
    const screenshot = page.locator('img.screenshot')
    await expect(screenshot).toBeVisible({ timeout: 30_000 })

    // 切到 Tab2
    await page.getByRole('tab', { name: '按模板预览' }).click()
    await expect(page.locator('.extract-block')).toBeVisible()
    // 默认 LIST
    await page.getByRole('button', { name: '按当前模板预览' }).click()
    // 等结果表出现
    const table = page.locator('.extract-table')
    await expect(table).toBeVisible({ timeout: 15_000 })
    const rows = table.locator('tbody tr')
    await expect(rows).toHaveCount(3)
    // 警告横幅
    const warn = page.locator('.extract-warning').first()
    await expect(warn).toContainText('detail_url')
  })

  test('DETAIL 预览:0 字段 + 顶部空模板警告,LIST 结果仍在', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto(`/configs/${configId}/preview`)
    await expect(page.locator('h1')).toContainText('页面预览')

    await page.locator('input[placeholder*="http"]').fill(FIXTURE_URL)
    await page.getByRole('button', { name: '加载' }).click()
    const screenshot = page.locator('img.screenshot')
    await expect(screenshot).toBeVisible({ timeout: 30_000 })

    await page.getByRole('tab', { name: '按模板预览' }).click()
    // 先点 LIST
    await page.getByRole('button', { name: '按当前模板预览' }).click()
    await expect(page.locator('.extract-table')).toBeVisible({ timeout: 15_000 })

    // 切到 DETAIL
    await page.getByRole('radio', { name: '详情页' }).click()
    await page.getByRole('button', { name: '按当前模板预览' }).click()
    await page.waitForTimeout(2000)
    const emptyHint = page.locator('.empty-result')
    await expect(emptyHint).toBeVisible({ timeout: 5_000 })
    await expect(emptyHint).toContainText('未命中')

    // 切回 LIST,LIST 结果仍在
    await page.getByRole('radio', { name: '列表页' }).click()
    await expect(page.locator('.extract-table')).toBeVisible()
    const rows = page.locator('.extract-table tbody tr')
    await expect(rows).toHaveCount(3)
  })
})
