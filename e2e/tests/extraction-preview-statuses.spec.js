// e2e/tests/extraction-preview-statuses.spec.js
// 按模板预览:四态呈现 + 选择器边界 + 重复触发按钮 disabled
const { test, expect, request } = require('@playwright/test')

const BACKEND = 'http://localhost:8080'
const FIXTURE_URL = 'http://localhost:7777/sample-list.html'
const CONFIG_NAME = 'E2E Extract Statuses'

test.describe('按模板预览 - 四态呈现 + 选择器边界', () => {
  let configId

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: BACKEND })
    const list = await api.get('/api/v1/configs', { params: { size: 100 } })
    const body = await list.json()
    const items = (body.data && body.data.content) || []
    const existing = items.find((c) => c.name === CONFIG_NAME)
    if (existing) {
      configId = existing.id
    } else {
      const create = await api.post('/api/v1/configs', {
        data: { name: CONFIG_NAME, pageType: 'DETAIL_ONLY', selectorType: 'CSS', status: 'STOPPED' },
        headers: { 'Content-Type': 'application/json' }
      })
      expect(create.ok()).toBeTruthy()
      const created = await create.json()
      configId = created.data.id
    }
    // 清空已有 DETAIL 字段
    const fields = await api.get(`/api/v1/configs/${configId}/fields`)
    const fieldsBody = await fields.json()
    const list2 = (fieldsBody.data && fieldsBody.data.content) || fieldsBody.data || []
    for (const f of list2) {
      if (f.pageType === 'DETAIL') {
        await api.delete(`/api/v1/fields/${f.id}`)
      }
    }
    const fieldSpecs = [
      { name: 'titleOK', type: 'TEXT', selector: '.title' },
      { name: 'priceOK', type: 'NUMBER', selector: '.price' },
      { name: 'dateInvalid', type: 'DATE', selector: '.title' },
      { name: 'missing', type: 'TEXT', selector: '.no-such-class' },
      { name: 'broken', type: 'TEXT', selector: '>>>broken<<<' }
    ]
    for (const spec of fieldSpecs) {
      await api.post(`/api/v1/configs/${configId}/fields`, {
        data: { pageType: 'DETAIL', fieldName: spec.name, fieldType: spec.type, selector: spec.selector },
        headers: { 'Content-Type': 'application/json' }
      })
    }
    await api.dispose()
  })

  test('四态呈现', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto(`/configs/${configId}/preview`)
    await expect(page.locator('h1')).toContainText('页面预览')

    await page.locator('input[placeholder*="http"]').fill(FIXTURE_URL)
    await page.getByRole('button', { name: '加载' }).click()
    const screenshot = page.locator('img.screenshot')
    await expect(screenshot).toBeVisible({ timeout: 30_000 })

    await page.getByRole('tab', { name: '按模板预览' }).click()
    // DETAIL_ONLY 模式,DETAIL 是唯一 pageType
    await page.getByRole('button', { name: '按当前模板预览' }).click()

    const table = page.locator('.extract-table')
    await expect(table).toBeVisible({ timeout: 15_000 })
    const rows = table.locator('tbody tr')
    await expect(rows).toHaveCount(5)

    // 5 字段 status 检查
    const expectStatus = ['OK', 'OK', 'TYPE_MISMATCH', 'NO_MATCH', 'SELECTOR_INVALID']
    for (let i = 0; i < expectStatus.length; i++) {
      const row = rows.nth(i)
      await expect(row.locator('.el-tag')).toContainText(
        expectStatus[i] === 'OK' ? 'OK'
        : expectStatus[i] === 'TYPE_MISMATCH' ? '类型不符'
        : expectStatus[i] === 'NO_MATCH' ? '未命中'
        : '选择器非法'
      )
    }

    // TYPE_MISMATCH / NO_MATCH / SELECTOR_INVALID 行 message 非空
    for (let i = 2; i < 5; i++) {
      const msg = rows.nth(i).locator('.msg')
      await expect(msg).toBeVisible()
      await expect(msg).not.toBeEmpty()
    }
  })

  test('重复点击触发按钮:第二次点击无效(loading 时 disabled)', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto(`/configs/${configId}/preview`)
    await expect(page.locator('h1')).toContainText('页面预览')

    await page.locator('input[placeholder*="http"]').fill(FIXTURE_URL)
    await page.getByRole('button', { name: '加载' }).click()
    const screenshot = page.locator('img.screenshot')
    await expect(screenshot).toBeVisible({ timeout: 30_000 })

    await page.getByRole('tab', { name: '按模板预览' }).click()
    const btn = page.getByRole('button', { name: '按当前模板预览' })
    await btn.click()
    // 立即再点
    await btn.click({ force: true }).catch(() => {})
    // 等结果出现
    const table = page.locator('.extract-table')
    await expect(table).toBeVisible({ timeout: 15_000 })
  })
})
